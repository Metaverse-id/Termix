package com.csoftware.termix.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Investigates Android shell availability and manages safe, non-destructive root detection.
 */
public class ShellDetector {

    private static final String TAG = "ShellDetector";

    private static final String PREFS_NAME = "termix_root_prefs";
    private static final String KEY_ROOT_CHOICE = "root_user_choice"; // 0=unasked, 1=normal, 2=root
    private static final String KEY_REMEMBER_CHOICE = "root_remember_choice";

    public static final int CHOICE_UNASKED = 0;
    public static final int CHOICE_CONTINUE_WITHOUT_ROOT = 1;
    public static final int CHOICE_GRANT_ROOT = 2;

    // Known locations for su binary
    private static final String[] KNOWN_SU_PATHS = new String[]{
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
    };

    // Known shell candidate paths
    private static final String[] KNOWN_SHELL_PATHS = new String[]{
            "/system/bin/sh",
            "/bin/sh",
            "/system/bin/mksh",
            "/system/bin/toybox",
            "/system/bin/toolbox"
    };

    /**
     * Finds the primary available shell executable on the Android device.
     */
    public static String detectShellBinary() {
        for (String path : KNOWN_SHELL_PATHS) {
            File file = new File(path);
            if (file.exists() && file.canExecute()) {
                return path;
            }
        }
        return "/system/bin/sh"; // Default Android shell location
    }

    /**
     * Non-destructive check to determine if root capability is present on the device.
     * Does NOT call su or attempt privilege escalation.
     *
     * @param pathString current PATH to search
     * @return true if an executable su binary or test-keys build is detected
     */
    public static boolean isRootAvailable(String pathString) {
        // 1. Check known binary locations
        for (String suPath : KNOWN_SU_PATHS) {
            File suFile = new File(suPath);
            if (suFile.exists()) {
                Log.d(TAG, "Root binary detected at: " + suPath);
                return true;
            }
        }

        // 2. Check current PATH
        File foundInPath = PathResolver.findExecutable("su", pathString);
        if (foundInPath != null) {
            Log.d(TAG, "Root binary found in PATH: " + foundInPath.getAbsolutePath());
            return true;
        }

        // 3. Check build tags (often present on rooted/custom ROMs)
        String buildTags = Build.TAGS;
        if (buildTags != null && buildTags.contains("test-keys")) {
            Log.d(TAG, "Custom test-keys build detected: " + buildTags);
            return true;
        }

        return false;
    }

    /**
     * Controlled verification of root access via su.
     * Called ONLY when the user explicitly chooses to Grant Root.
     * Times out safely after 8 seconds to prevent freezing if root manager prompts or hangs.
     */
    public static boolean verifyRootAccessGranted() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                Process process = null;
                try {
                    process = new ProcessBuilder("su", "-c", "id -u")
                            .redirectErrorStream(true)
                            .start();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String output = reader.readLine();
                    int exitCode = process.waitFor();

                    if (exitCode == 0 && output != null && output.trim().equals("0")) {
                        Log.i(TAG, "Root identity verified (uid=0)");
                        return true;
                    }
                    Log.w(TAG, "Root command returned exit " + exitCode + ", output: " + output);
                    return false;
                } catch (Exception e) {
                    Log.w(TAG, "Root verification failed or denied: " + e.getMessage());
                    return false;
                } finally {
                    if (process != null) {
                        try {
                            process.destroy();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });

        try {
            return future.get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            Log.w(TAG, "Root verification timed out or was interrupted: " + e.getMessage());
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Reads saved user root choice.
     */
    public static int getSavedRootChoice(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean remember = prefs.getBoolean(KEY_REMEMBER_CHOICE, false);
        if (!remember) {
            return CHOICE_UNASKED;
        }
        return prefs.getInt(KEY_ROOT_CHOICE, CHOICE_UNASKED);
    }

    /**
     * Saves user root choice and remember flag.
     */
    public static void saveRootChoice(Context context, int choice, boolean remember) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_ROOT_CHOICE, choice)
                .putBoolean(KEY_REMEMBER_CHOICE, remember)
                .apply();
    }

    /**
     * Resets root choice so user can be prompted or reconfigure.
     */
    public static void resetRootChoice(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
