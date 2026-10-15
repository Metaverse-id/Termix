package com.csoftware.termix.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages Termix environment variables and persistence.
 * Exactly preserves:
 * HOME=/data/data/com.csoftware.termix/files/home
 * ENV=/data/data/com.csoftware.termix/env/home
 * USER=/data/user/0/com.csoftware.termix/files/home
 * PATH=/system/bin:/xbin:/system/xbin:/sbin:/system_ext/bin:/bin
 */
public class EnvironmentConfig {

    private static final String TAG = "EnvironmentConfig";

    public static final String DEFAULT_HOME = "/data/data/com.csoftware.termix/files/home";
    public static final String DEFAULT_ENV = "/data/data/com.csoftware.termix/env/home";
    public static final String DEFAULT_USER = "/data/user/0/com.csoftware.termix/files/home";
    public static final String DEFAULT_PATH = "/system/bin:/xbin:/system/xbin:/sbin:/system_ext/bin:/bin";
    public static final String DEFAULT_TERM = "xterm-256color";

    private static final String PREF_NAME = "termix_env_config";
    private static final String KEY_HOME = "env_home";
    private static final String KEY_ENV = "env_env";
    private static final String KEY_USER = "env_user";
    private static final String KEY_PATH = "env_path";
    private static final String KEY_TERM = "env_term";
    private static final String KEY_INITIALIZED = "env_initialized";

    private final Context context;
    private final SharedPreferences prefs;

    private String home;
    private String env;
    private String user;
    private String path;
    private String term;

    public EnvironmentConfig(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadConfiguration();
        ensureDirectoriesAndStartupFile();
    }

    /**
     * Loads saved configuration from SharedPreferences or falls back to exact defaults.
     */
    private void loadConfiguration() {
        this.home = prefs.getString(KEY_HOME, DEFAULT_HOME);
        this.env = prefs.getString(KEY_ENV, DEFAULT_ENV);
        this.user = prefs.getString(KEY_USER, DEFAULT_USER);
        this.path = prefs.getString(KEY_PATH, DEFAULT_PATH);
        this.term = prefs.getString(KEY_TERM, DEFAULT_TERM);
    }

    /**
     * Creates required application-private directories and ensures ENV is created as a file.
     */
    public void ensureDirectoriesAndStartupFile() {
        try {
            // 1. Create HOME directory
            File homeDir = new File(this.home);
            if (!homeDir.exists()) {
                boolean created = homeDir.mkdirs();
                Log.d(TAG, "Created HOME dir: " + this.home + " (result: " + created + ")");
            }

            // Fallback ensure internal files directory also exists
            File contextFiles = context.getFilesDir();
            if (contextFiles != null && !contextFiles.exists()) {
                contextFiles.mkdirs();
            }

            // 2. Ensure ENV parent directory exists
            File envFile = new File(this.env);
            File envDir = envFile.getParentFile();
            if (envDir != null && !envDir.exists()) {
                boolean created = envDir.mkdirs();
                Log.d(TAG, "Created ENV parent dir: " + envDir.getAbsolutePath() + " (result: " + created + ")");
            }

            // 3. Ensure ENV is a FILE, not a directory
            if (envFile.exists() && envFile.isDirectory()) {
                Log.w(TAG, "ENV path was a directory; removing to recreate as a file: " + this.env);
                envFile.delete();
            }

            if (!envFile.exists()) {
                boolean created = envFile.createNewFile();
                Log.d(TAG, "Created ENV startup script file: " + this.env + " (result: " + created + ")");
                writeDefaultEnvScript(envFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring Termix directories and ENV file: " + e.getMessage(), e);
        }
    }

    /**
     * Writes the default startup script content to the ENV configuration file.
     */
    private void writeDefaultEnvScript(File envFile) {
        String script = "# Termix Shell Startup Configuration\n"
                + "# Loaded automatically when a new interactive shell starts\n"
                + "export HOME=\"" + this.home + "\"\n"
                + "export ENV=\"" + this.env + "\"\n"
                + "export USER=\"" + this.user + "\"\n"
                + "export PATH=\"" + this.path + ":$PATH\"\n"
                + "export TERM=\"" + this.term + "\"\n"
                + "alias ll='ls -la'\n"
                + "alias l='ls -l'\n"
                + "alias la='ls -a'\n";
        try (FileOutputStream fos = new FileOutputStream(envFile)) {
            fos.write(script.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write default ENV script: " + e.getMessage(), e);
        }
    }

    /**
     * Synchronizes current environment configuration to the ENV file on disk.
     */
    public void syncToFile() {
        try {
            File envFile = new File(this.env);
            File parent = envFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            writeDefaultEnvScript(envFile);
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync environment to file: " + e.getMessage(), e);
        }
    }

    /**
     * Saves the environment configuration persistently.
     */
    public void saveConfiguration(String newHome, String newEnv, String newUser, String newPath) {
        this.home = (newHome != null && !newHome.trim().isEmpty()) ? newHome.trim() : DEFAULT_HOME;
        this.env = (newEnv != null && !newEnv.trim().isEmpty()) ? newEnv.trim() : DEFAULT_ENV;
        this.user = (newUser != null && !newUser.trim().isEmpty()) ? newUser.trim() : DEFAULT_USER;
        this.path = (newPath != null && !newPath.trim().isEmpty()) ? newPath.trim() : DEFAULT_PATH;

        prefs.edit()
                .putString(KEY_HOME, this.home)
                .putString(KEY_ENV, this.env)
                .putString(KEY_USER, this.user)
                .putString(KEY_PATH, this.path)
                .putBoolean(KEY_INITIALIZED, true)
                .apply();

        ensureDirectoriesAndStartupFile();
        syncToFile();
    }

    /**
     * Resets all environment variables back to Termix initial values.
     */
    public void resetToDefaults() {
        saveConfiguration(DEFAULT_HOME, DEFAULT_ENV, DEFAULT_USER, DEFAULT_PATH);
    }

    /**
     * Returns a map of environment variables to be passed to ProcessBuilder.
     */
    public Map<String, String> getEnvironmentMap() {
        Map<String, String> map = new HashMap<>();
        map.put("HOME", this.home);
        map.put("ENV", this.env);
        map.put("USER", this.user);
        map.put("PATH", this.path);
        map.put("TERM", this.term);
        map.put("LANG", "en_US.UTF-8");
        return map;
    }

    public String getHome() {
        return home;
    }

    public String getEnv() {
        return env;
    }

    public String getUser() {
        return user;
    }

    public String getPath() {
        return path;
    }

    public String getTerm() {
        return term;
    }
}
