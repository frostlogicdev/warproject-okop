package com.frostlogic.warproject.server.backup;

import com.frostlogic.warproject.WarProject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates an on-demand zip snapshot of the live world directory under
 * {@code <serverRoot>/server/backups/world_backup_<timestamp>.zip}.
 * <p>
 * Two important guarantees:
 * <ul>
 *   <li>The server is asked to flush all level data ({@code save(flush=true,
 *       forced=true, skipSave=false)}) before zipping, so the snapshot
 *       represents a consistent state on disk.</li>
 *   <li>The work runs on a background daemon thread to avoid blocking the
 *       main server thread for tens of seconds on large worlds. The caller
 *       receives a result via the returned {@link java.util.concurrent.CompletableFuture}.</li>
 * </ul>
 * <p>
 * The format intentionally mirrors {@link com.frostlogic.warproject.env.WorldRelocationTask}'s
 * one-shot backup format ({@code world/<...>}), so existing tooling /
 * tests that look for {@code world_backup_*.zip} entries with {@code "world/"}
 * prefix continue to work.
 */
public final class WorldBackupService {

    private static final DateTimeFormatter TS = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());

    private WorldBackupService() {
    }

    public sealed interface Result {
        record Success(Path archive, long bytes) implements Result {}
        record Failure(String message) implements Result {}
    }

    /**
     * Snapshot the live world to a fresh zip in the backup directory.
     *
     * @param server the running Minecraft server (must not be {@code null})
     * @return a future that completes with the result. Never completes
     *         exceptionally — failures are wrapped in {@link Result.Failure}.
     */
    public static java.util.concurrent.CompletableFuture<Result> snapshotAsync(MinecraftServer server) {
        if (server == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new Result.Failure("server is null"));
        }
        // Flush on the main thread first — required for a consistent snapshot.
        boolean saved;
        try {
            saved = server.saveEverything(true, true, false);
        } catch (Throwable t) {
            WarProject.LOGGER.error("[WP Backup] saveEverything failed", t);
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new Result.Failure("save failed: " + t.getMessage()));
        }
        if (!saved) {
            // Server reported partial save; we still proceed because Minecraft
            // returns false in benign cases (e.g. nothing to save). Just log.
            WarProject.LOGGER.debug("[WP Backup] saveEverything reported false (continuing)");
        }

        Path serverRoot = server.getServerDirectory().toAbsolutePath().normalize();
        Path worldDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path backupDir = serverRoot.resolve("server").resolve("backups");
        String timestamp = TS.format(Instant.now());
        Path archive = backupDir.resolve("world_backup_" + timestamp + ".zip");

        var future = new java.util.concurrent.CompletableFuture<Result>();
        Thread t = new Thread(() -> {
            try {
                Files.createDirectories(backupDir);
                zipWorldDirectory(worldDir, archive);
                long bytes = Files.size(archive);
                WarProject.LOGGER.info("[WP Backup] World snapshot written: {} ({} bytes)",
                        archive, bytes);
                future.complete(new Result.Success(archive, bytes));
            } catch (Throwable ex) {
                WarProject.LOGGER.error("[WP Backup] Snapshot failed", ex);
                // Best-effort cleanup of partial archive.
                try { Files.deleteIfExists(archive); } catch (IOException ignored) {}
                future.complete(new Result.Failure(ex.getMessage()));
            }
        }, "WP-Backup");
        t.setDaemon(true);
        t.start();
        return future;
    }

    /**
     * Writes {@code worldDir} into {@code archive} with entries prefixed by
     * {@code world/<relative-path>} — same format as {@link com.frostlogic.warproject.env.WorldRelocationTask#zipDirectory}.
     */
    private static void zipWorldDirectory(Path worldDir, Path archive) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archive))) {
            Files.walkFileTree(worldDir, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Skip session.lock — it's exclusively held by the running server
                    // and trying to read it on Windows triggers an
                    // AccessDeniedException. The lock has no meaningful content
                    // to back up anyway.
                    if (file.getFileName().toString().equals("session.lock")) {
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                    String entryName = "world/" + worldDir.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    try {
                        Files.copy(file, zos);
                    } catch (IOException ex) {
                        // Some files (level.dat_old etc.) may be locked momentarily
                        // by the save process; log and skip rather than fail.
                        WarProject.LOGGER.warn("[WP Backup] Skipping unreadable {}: {}", file, ex.getMessage());
                    }
                    zos.closeEntry();
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(worldDir)) {
                        String entryName = "world/" + worldDir.relativize(dir).toString().replace('\\', '/') + "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /** Convenience for callers that want a localized "ts" suffix without exposing the formatter. */
    public static String timestamp() {
        return LocalDateTime.now().format(TS);
    }
}
