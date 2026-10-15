package com.csoftware.termix.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves executables using the configured search PATH.
 * Default search order:
 * 1. /system/bin
 * 2. /xbin
 * 3. /system/xbin
 * 4. /sbin
 * 5. /system_ext/bin
 * 6. /bin
 */
public class PathResolver {

    public static class PathEntry {
        public final String path;
        public final boolean exists;
        public final boolean isDirectory;

        public PathEntry(String path, boolean exists, boolean isDirectory) {
            this.path = path;
            this.exists = exists;
            this.isDirectory = isDirectory;
        }
    }

    /**
     * Inspects all directories configured in the PATH string without modifying the PATH.
     */
    public static List<PathEntry> inspectPath(String pathString) {
        List<PathEntry> entries = new ArrayList<>();
        if (pathString == null || pathString.trim().isEmpty()) {
            return entries;
        }

        String[] parts = pathString.split(":");
        for (String part : parts) {
            String dirPath = part.trim();
            if (!dirPath.isEmpty()) {
                File dir = new File(dirPath);
                boolean exists = dir.exists();
                boolean isDir = exists && dir.isDirectory();
                entries.add(new PathEntry(dirPath, exists, isDir));
            }
        }
        return entries;
    }

    /**
     * Finds an executable binary in the configured PATH, following the exact search order.
     * Only checks existing directories.
     *
     * @param commandName The binary to find (e.g., "ls", "sh", "su")
     * @param pathString  The PATH environment variable string
     * @return The File representing the executable, or null if not found
     */
    public static File findExecutable(String commandName, String pathString) {
        if (commandName == null || commandName.trim().isEmpty()) {
            return null;
        }

        // If commandName already contains a slash, test direct path
        if (commandName.contains("/")) {
            File directFile = new File(commandName);
            if (directFile.exists() && directFile.canExecute() && !directFile.isDirectory()) {
                return directFile;
            }
            return null;
        }

        if (pathString == null || pathString.trim().isEmpty()) {
            return null;
        }

        String[] directories = pathString.split(":");
        for (String dirPath : directories) {
            String cleanDir = dirPath.trim();
            if (cleanDir.isEmpty()) continue;

            File dir = new File(cleanDir);
            // Non-destructive check: verify whether directory exists before looking for binary
            if (dir.exists() && dir.isDirectory()) {
                File candidate = new File(dir, commandName);
                if (candidate.exists() && candidate.canExecute() && !candidate.isDirectory()) {
                    return candidate;
                }
            }
        }

        return null;
    }

    /**
     * Checks if a specific file path is an executable.
     */
    public static boolean isExecutable(String filePath) {
        if (filePath == null) return false;
        File file = new File(filePath);
        return file.exists() && file.canExecute() && !file.isDirectory();
    }
}
