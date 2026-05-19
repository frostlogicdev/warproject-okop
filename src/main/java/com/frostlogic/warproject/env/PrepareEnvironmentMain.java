package com.frostlogic.warproject.env;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone entry point for running {@link WorldRelocationTask} via Gradle's {@code JavaExec}.
 * <p>
 * Accepts the server root directory as:
 * <ul>
 *   <li>First command-line argument, OR</li>
 *   <li>System property {@code serverDir}</li>
 * </ul>
 * If neither is provided, defaults to the current working directory.
 * <p>
 * Exit codes:
 * <ul>
 *   <li>0 — success</li>
 *   <li>1 — relocation failed</li>
 * </ul>
 */
public class PrepareEnvironmentMain {

    public static void main(String[] args) {
        Path serverRoot;
        if (args.length > 0 && !args[0].isBlank()) {
            serverRoot = Paths.get(args[0]).toAbsolutePath();
        } else {
            String prop = System.getProperty("serverDir");
            if (prop != null && !prop.isBlank()) {
                serverRoot = Paths.get(prop).toAbsolutePath();
            } else {
                serverRoot = Paths.get(".").toAbsolutePath();
            }
        }

        System.out.println("[PrepareEnvironment] Server root: " + serverRoot);

        WorldRelocationTask task = new WorldRelocationTask();
        try {
            task.execute(serverRoot);
            System.out.println("[PrepareEnvironment] World relocation completed successfully.");
        } catch (WorldRelocationException e) {
            System.err.println("[PrepareEnvironment] ERROR: " + e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace(System.err);
            }
            System.exit(1);
        }
    }
}
