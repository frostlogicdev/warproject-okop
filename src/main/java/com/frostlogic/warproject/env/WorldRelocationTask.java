package com.frostlogic.warproject.env;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Relocates the Minecraft world from {@code runs/server/world} to {@code <serverRoot>/world},
 * creating a zip backup beforehand and rolling back on failure.
 * <p>
 * Algorithm (Design §8.1, Requirements 1.1–1.5):
 * <ol>
 *   <li>Verify source directory exists</li>
 *   <li>Create zip backup in {@code server/backups/}</li>
 *   <li>Verify zip integrity</li>
 *   <li>Move world directory to server root</li>
 *   <li>Delete {@code runs/server/}</li>
 *   <li>Update {@code server.properties} with {@code level-name=world}</li>
 * </ol>
 * On failure during move, the backup is restored to the source location (rollback).
 * <p>
 * This class has no Minecraft dependencies — pure Java IO.
 */
public class WorldRelocationTask {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

    /**
     * Executes the world relocation procedure.
     *
     * @param serverRoot the root directory of the server (e.g. project root or dedicated server dir)
     * @throws WorldRelocationException if the operation fails and rollback was performed (or source doesn't exist)
     */
    public void execute(Path serverRoot) throws WorldRelocationException {
        Path src = serverRoot.resolve("runs").resolve("server").resolve("world");
        Path dst = serverRoot.resolve("world");
        Path backupDir = serverRoot.resolve("server").resolve("backups");

        // Step 0: Verify source exists
        if (!Files.isDirectory(src)) {
            throw new WorldRelocationException("Nothing to relocate: source directory does not exist: " + src);
        }

        // Step 1: Create backup
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        Path backup = backupDir.resolve("world_backup_" + timestamp + ".zip");

        try {
            Files.createDirectories(backupDir);
            zipDirectory(src, backup);
            verifyZipIntegrity(backup);
        } catch (IOException e) {
            // Clean up partial backup if it exists
            deleteQuietly(backup);
            throw new WorldRelocationException("Backup failed: " + e.getMessage(), e);
        }

        // Step 2: Move world directory
        try {
            moveDirectory(src, dst);
        } catch (IOException e) {
            // Rollback: restore from backup
            try {
                deleteDirectoryRecursive(dst);
                unzip(backup, dst.getParent());
            } catch (IOException rollbackEx) {
                WorldRelocationException ex = new WorldRelocationException(
                        "Relocation failed AND rollback failed: " + rollbackEx.getMessage(), e);
                ex.addSuppressed(rollbackEx);
                throw ex;
            }
            throw new WorldRelocationException("Relocation failed, rolled back from backup: " + e.getMessage(), e);
        }

        // Step 3: Clean up runs/server
        Path runsServer = serverRoot.resolve("runs").resolve("server");
        try {
            deleteDirectoryRecursive(runsServer);
        } catch (IOException e) {
            // Non-fatal: world is already moved successfully
            // Log but don't fail the operation
        }

        // Step 4: Update server.properties
        Path serverProperties = serverRoot.resolve("server.properties");
        try {
            ensureProperty(serverProperties, "level-name", "world");
        } catch (IOException e) {
            // Non-fatal: world is already moved successfully
            // The admin can fix server.properties manually
        }
    }

    /**
     * Creates a zip archive of the given directory.
     */
    void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = sourceDir.getParent().relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(sourceDir)) {
                        String entryName = sourceDir.getParent().relativize(dir).toString().replace('\\', '/') + "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Verifies zip integrity by reading all entries.
     */
    void verifyZipIntegrity(Path zipFile) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    try (InputStream is = zf.getInputStream(entry)) {
                        while (is.read(buffer) != -1) {
                            // Read through to verify no corruption
                        }
                    }
                }
            }
        }
    }

    /**
     * Moves a directory tree from src to dst. Attempts atomic move first,
     * falls back to recursive copy + delete.
     */
    void moveDirectory(Path src, Path dst) throws IOException {
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException e) {
            // Atomic move not supported (e.g. cross-filesystem), do recursive copy + delete
            copyDirectoryRecursive(src, dst);
            deleteDirectoryRecursive(src);
        }
    }

    /**
     * Recursively copies a directory tree.
     */
    private void copyDirectoryRecursive(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = dst.resolve(src.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = dst.resolve(src.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Recursively deletes a directory and all its contents.
     * Does nothing if the path does not exist.
     */
    void deleteDirectoryRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Extracts a zip file to the target directory, preserving the directory structure
     * stored in the zip entries.
     */
    void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = targetDir.resolve(entry.getName());
                // Guard against zip-slip
                if (!entryPath.normalize().startsWith(targetDir.normalize())) {
                    throw new IOException("Zip entry outside target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream is = zf.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /**
     * Ensures a property file contains the given key=value pair.
     * If the key exists, its value is updated. If not, the property is appended.
     * Creates the file if it does not exist.
     */
    void ensureProperty(Path propertiesFile, String key, String value) throws IOException {
        if (!Files.exists(propertiesFile)) {
            Files.writeString(propertiesFile, key + "=" + value + System.lineSeparator());
            return;
        }

        Path tempFile = propertiesFile.resolveSibling(propertiesFile.getFileName() + ".tmp");
        boolean found = false;

        try (BufferedReader reader = Files.newBufferedReader(propertiesFile);
             BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith(key + "=") || trimmed.equals(key)) {
                    writer.write(key + "=" + value);
                    writer.newLine();
                    found = true;
                } else {
                    writer.write(line);
                    writer.newLine();
                }
            }
            if (!found) {
                writer.write(key + "=" + value);
                writer.newLine();
            }
        }

        Files.move(tempFile, propertiesFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort cleanup
        }
    }
}
