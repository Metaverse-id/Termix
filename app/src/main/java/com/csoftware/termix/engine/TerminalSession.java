package com.csoftware.termix.engine;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.csoftware.termix.config.EnvironmentConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates an active interactive shell process session.
 * Manages stdin/stdout streams, environment variables, working directory,
 * command history, Ctrl+C interruption, and terminal buffer.
 */
public class TerminalSession {

    private static final String TAG = "TerminalSession";
    private static final int MAX_BUFFER_CHARS = 120_000;

    public interface SessionListener {
        void onOutput(TerminalSession session, String text);
        void onWorkingDirectoryChanged(TerminalSession session, String cwd);
        void onProcessTerminated(TerminalSession session, int exitCode);
    }

    private final String id;
    private final String title;
    private final boolean isRoot;
    private final EnvironmentConfig config;
    private final Handler mainHandler;

    private Process process;
    private OutputStream stdin;
    private InputStream stdout;
    private Thread outputThread;

    private final StringBuilder buffer = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    private String currentWorkingDirectory;
    private boolean isRunning = false;
    private SessionListener listener;

    public TerminalSession(String id, String title, boolean isRoot, EnvironmentConfig config) {
        this.id = id;
        this.title = title;
        this.isRoot = isRoot;
        this.config = config;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.currentWorkingDirectory = config.getHome();
    }

    public void setListener(SessionListener listener) {
        this.listener = listener;
    }

    /**
     * Starts the terminal shell process with configured environment variables and working directory.
     */
    public synchronized void start() {
        if (isRunning) return;

        try {
            // Ensure HOME directory exists for initial cwd
            File initialDir = new File(config.getHome());
            if (!initialDir.exists()) {
                initialDir.mkdirs();
            }
            if (!initialDir.exists() || !initialDir.isDirectory()) {
                initialDir = new File("/data/data/com.csoftware.termix");
            }
            if (!initialDir.exists()) {
                initialDir = new File("/");
            }
            this.currentWorkingDirectory = initialDir.getAbsolutePath();

            List<String> commandList = new ArrayList<>();
            if (isRoot) {
                commandList.add("su");
            } else {
                String shellPath = ShellDetector.detectShellBinary();
                commandList.add(shellPath);
            }

            ProcessBuilder pb = new ProcessBuilder(commandList);
            pb.directory(initialDir);
            pb.redirectErrorStream(true);

            // Pass Termix environment variables
            Map<String, String> pbEnv = pb.environment();
            pbEnv.putAll(config.getEnvironmentMap());
            pbEnv.put("SHELL", isRoot ? "su" : ShellDetector.detectShellBinary());

            this.process = pb.start();
            this.stdin = process.getOutputStream();
            this.stdout = process.getInputStream();
            this.isRunning = true;

            // Start reader thread for stdout/stderr
            startOutputReader();

            // Initial banner and load ENV configuration file
            appendBanner();
            loadEnvStartupFile();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start shell process: " + e.getMessage(), e);
            appendOutput("\n[Termix Error] Failed to launch shell: " + e.getMessage() + "\n");
            isRunning = false;
        }
    }

    /**
     * Sources the ENV file into the newly started interactive shell.
     */
    private void loadEnvStartupFile() {
        File envFile = new File(config.getEnv());
        if (envFile.exists() && envFile.isFile()) {
            writeDirect("if [ -f \"" + config.getEnv() + "\" ]; then . \"" + config.getEnv() + "\"; fi\n");
        }
        // Sync working directory
        writeDirect("pwd\n");
    }

    private void appendBanner() {
        String banner = "========================================\n"
                + "  Termix — Native Android Terminal\n"
                + "  Session: " + title + (isRoot ? " [ROOT #]" : " [NORMAL $]") + "\n"
                + "  HOME: " + config.getHome() + "\n"
                + "  ENV:  " + config.getEnv() + "\n"
                + "  Type 'help', 'env', 'pwd', or 'exit'\n"
                + "========================================\n\n";
        appendOutput(banner);
    }

    /**
     * Reads process output stream continuously on a background thread.
     */
    private void startOutputReader() {
        outputThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] byteBuffer = new byte[4096];
                int bytesRead;
                try {
                    while (isRunning && stdout != null && (bytesRead = stdout.read(byteBuffer)) != -1) {
                        final String text = new String(byteBuffer, 0, bytesRead, StandardCharsets.UTF_8);
                        inspectOutputForStateChanges(text);
                        appendOutput(text);
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        Log.d(TAG, "Output stream closed: " + e.getMessage());
                    }
                } finally {
                    handleProcessTermination();
                }
            }
        }, "Termix-OutputReader-" + id);
        outputThread.setDaemon(true);
        outputThread.start();
    }

    /**
     * Inspects output lines to detect directory changes or shell state updates.
     */
    private void inspectOutputForStateChanges(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("/") && !trimmed.contains(" ") && new File(trimmed).isDirectory()) {
                currentWorkingDirectory = trimmed;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onWorkingDirectoryChanged(TerminalSession.this, currentWorkingDirectory);
                        }
                    }
                });
            }
        }
    }

    private synchronized void appendOutput(final String text) {
        buffer.append(text);
        if (buffer.length() > MAX_BUFFER_CHARS) {
            buffer.delete(0, buffer.length() - MAX_BUFFER_CHARS);
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onOutput(TerminalSession.this, text);
                }
            }
        });
    }

    /**
     * Executes a user command in this terminal session.
     */
    public void executeCommand(String command) {
        if (command == null) return;
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return;

        // Record in history
        history.add(trimmed);
        historyIndex = history.size();

        // Print command echo with appropriate prompt
        String prompt = isRoot ? "# " : "$ ";
        appendOutput(prompt + trimmed + "\n");

        // Built-in commands intercept
        if (trimmed.equals("clear") || trimmed.equals("cls")) {
            clearBuffer();
            return;
        }

        if (trimmed.equals("exit")) {
            destroy();
            return;
        }

        if (trimmed.equals("help")) {
            String helpText = "\nTermix Terminal Commands Help:\n"
                    + "  ls, pwd, cd <dir>      File navigation\n"
                    + "  echo, env, export      Environment variables\n"
                    + "  which <cmd>            Lookup binary in PATH\n"
                    + "  clear                  Clear terminal screen\n"
                    + "  exit                   Close current session\n"
                    + "  Ctrl+C button          Interrupt running command\n"
                    + "  Quick toolbar          Tab, Esc, history navigation\n\n";
            appendOutput(helpText);
            writeDirect("echo \"\"\n");
            return;
        }

        // For cd command, append pwd hook so working directory updates automatically
        if (trimmed.startsWith("cd ") || trimmed.equals("cd")) {
            writeDirect(trimmed + " && pwd\n");
            return;
        }

        // Check if command exists in PATH or has direct path to give helpful error if missing
        String primaryCmd = trimmed.split("\\s+")[0];
        if (!isRoot && !isShellBuiltin(primaryCmd)) {
            File executable = PathResolver.findExecutable(primaryCmd, config.getPath());
            if (executable == null && primaryCmd.contains("/")) {
                File direct = new File(primaryCmd);
                if (!direct.exists()) {
                    appendOutput("termix: " + primaryCmd + ": No such file or directory\n");
                    return;
                } else if (!direct.canExecute()) {
                    appendOutput("termix: " + primaryCmd + ": Permission denied\n");
                    return;
                }
            }
        }

        // Send to interactive shell stdin
        writeDirect(trimmed + "\n");
    }

    private boolean isShellBuiltin(String cmd) {
        return cmd.equals("cd") || cmd.equals("pwd") || cmd.equals("echo")
                || cmd.equals("export") || cmd.equals("env") || cmd.equals("set")
                || cmd.equals("unset") || cmd.equals("alias") || cmd.equals("source")
                || cmd.equals(".") || cmd.equals("which") || cmd.equals("type")
                || cmd.equals("exit") || cmd.equals("help");
    }

    /**
     * Writes raw bytes directly to the process stdin.
     */
    public synchronized void writeDirect(String text) {
        if (!isRunning || stdin == null) return;
        try {
            stdin.write(text.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to stdin: " + e.getMessage());
        }
    }

    /**
     * Sends ASCII ETX (Ctrl+C, byte 0x03) to interrupt any running foreground child process.
     */
    public synchronized void sendInterrupt() {
        if (!isRunning || stdin == null) return;
        try {
            stdin.write(0x03);
            stdin.flush();
            appendOutput("^C\n");
        } catch (IOException e) {
            Log.e(TAG, "Error sending Ctrl+C: " + e.getMessage());
        }
    }

    /**
     * Clears the session buffer.
     */
    public synchronized void clearBuffer() {
        buffer.setLength(0);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onOutput(TerminalSession.this, "");
                }
            }
        });
    }

    /**
     * History navigation: previous command.
     */
    public String getPreviousHistoryCommand() {
        if (history.isEmpty()) return "";
        if (historyIndex > 0) {
            historyIndex--;
        }
        if (historyIndex >= 0 && historyIndex < history.size()) {
            return history.get(historyIndex);
        }
        return "";
    }

    /**
     * History navigation: next command.
     */
    public String getNextHistoryCommand() {
        if (history.isEmpty()) return "";
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            return history.get(historyIndex);
        } else {
            historyIndex = history.size();
            return "";
        }
    }

    private void handleProcessTermination() {
        int exitCode = -1;
        if (process != null) {
            try {
                exitCode = process.exitValue();
            } catch (IllegalThreadStateException ignored) {
            }
        }
        isRunning = false;
        final int finalExitCode = exitCode;
        appendOutput("\n[Process terminated with exit code " + finalExitCode + "]\n");

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onProcessTerminated(TerminalSession.this, finalExitCode);
                }
            }
        });
    }

    /**
     * Terminates process and closes streams safely.
     */
    public synchronized void destroy() {
        isRunning = false;
        if (stdin != null) {
            try {
                stdin.write("exit\n".getBytes(StandardCharsets.UTF_8));
                stdin.flush();
                stdin.close();
            } catch (Exception ignored) {
            }
            stdin = null;
        }

        if (stdout != null) {
            try {
                stdout.close();
            } catch (Exception ignored) {
            }
            stdout = null;
        }

        if (process != null) {
            try {
                process.destroy();
            } catch (Exception ignored) {
            }
            process = null;
        }

        if (outputThread != null) {
            outputThread.interrupt();
            outputThread = null;
        }
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public String getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }

    public String getBufferContent() {
        return buffer.toString();
    }

    public boolean isRunning() {
        return isRunning;
    }
}
