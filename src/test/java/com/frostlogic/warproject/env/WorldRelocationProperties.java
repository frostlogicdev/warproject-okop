package com.frostlogic.warproject.env;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link WorldRelocationTask} — world relocation round-trip.
 * <p>
 * Property 1: For a generated file tree in {@code runs/server/world/}, after {@code execute(serverRoot)}:
 * <ul>
 *   <li>All files from {@code runs/server/world/} exist in {@code <serverRoot>/world/} with identical byte content</li>
 *   <li>{@code runs/server/} directory no longer exists</li>
 *   <li>A backup zip exists in {@code server/backups/}</li>
 * </ul>
 * <p>
 * Property 2: After {@code execute(serverRoot)}:
 * <ul>
 *   <li>{@code server.properties} contains {@code level-name=world}</li>
 *   <li>All other existing keys in {@code server.properties} remain unchanged</li>
 * </ul>
 * <p>
 * <b>Validates: Requirements 1.1, 1.2, 1.3, 1.4</b>
 * <p>
 * Design: §12 Property 1, 2
 */
class WorldRelocationProperties {

    private Path tempDir;

    @BeforeTry
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wp-relocation-test-");
    }

    @AfterTry
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    // ==================== Property 1: Round-trip file relocation ====================

    @Property(tries = 50)
    void allFilesRelocatedWithIdenticalContent(
            @ForAll("fileTrees") Map<String, byte[]> fileTree
    ) throws Exception {
        // Arrange: create the file tree under runs/server/world/
        Path serverRoot = tempDir;
        Path worldSrc = serverRoot.resolve("runs").resolve("server").resolve("world");
        Files.createDirectories(worldSrc);

        for (Map.Entry<String, byte[]> entry : fileTree.entrySet()) {
            Path filePath = worldSrc.resolve(entry.getKey());
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, entry.getValue());
        }

        // Also create server.properties so ensureProperty doesn't fail
        Path serverProps = serverRoot.resolve("server.properties");
        Files.writeString(serverProps, "level-name=default\n");

        // Act
        WorldRelocationTask task = new WorldRelocationTask();
        task.execute(serverRoot);

        // Assert: all files exist in <serverRoot>/world/ with identical byte content
        Path worldDst = serverRoot.resolve("world");
        for (Map.Entry<String, byte[]> entry : fileTree.entrySet()) {
            Path relocated = worldDst.resolve(entry.getKey());
            assertThat(relocated)
                    .as("File %s should exist in destination", entry.getKey())
                    .exists();
            byte[] actual = Files.readAllBytes(relocated);
            assertThat(actual)
                    .as("File %s should have identical byte content", entry.getKey())
                    .isEqualTo(entry.getValue());
        }

        // Assert: runs/server/ directory no longer exists
        Path runsServer = serverRoot.resolve("runs").resolve("server");
        assertThat(runsServer).doesNotExist();

        // Assert: a backup zip exists in server/backups/
        Path backupDir = serverRoot.resolve("server").resolve("backups");
        assertThat(backupDir).isDirectory();
        try (var backups = Files.list(backupDir)) {
            List<Path> zipFiles = backups
                    .filter(p -> p.getFileName().toString().startsWith("world_backup_"))
                    .filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .collect(Collectors.toList());
            assertThat(zipFiles)
                    .as("At least one backup zip should exist")
                    .isNotEmpty();

            // Verify the backup zip contains the original files
            Path backupZip = zipFiles.get(0);
            try (ZipFile zf = new ZipFile(backupZip.toFile())) {
                for (String relativePath : fileTree.keySet()) {
                    // Zip entries are stored with "world/" prefix
                    String entryName = "world/" + relativePath.replace('\\', '/');
                    ZipEntry ze = zf.getEntry(entryName);
                    assertThat(ze)
                            .as("Backup zip should contain entry: %s", entryName)
                            .isNotNull();
                }
            }
        }
    }

    // ==================== Property 2: server.properties preservation ====================

    @Property(tries = 50)
    void serverPropertiesPreservesOtherKeys(
            @ForAll("fileTrees") Map<String, byte[]> fileTree,
            @ForAll("propertiesMaps") Map<String, String> existingProperties
    ) throws Exception {
        // Arrange: create the file tree under runs/server/world/
        Path serverRoot = tempDir;
        Path worldSrc = serverRoot.resolve("runs").resolve("server").resolve("world");
        Files.createDirectories(worldSrc);

        for (Map.Entry<String, byte[]> entry : fileTree.entrySet()) {
            Path filePath = worldSrc.resolve(entry.getKey());
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, entry.getValue());
        }

        // Create server.properties with existing keys
        Path serverProps = serverRoot.resolve("server.properties");
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : existingProperties.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        Files.writeString(serverProps, sb.toString());

        // Act
        WorldRelocationTask task = new WorldRelocationTask();
        task.execute(serverRoot);

        // Assert: server.properties contains level-name=world
        Properties propsAfter = new Properties();
        try (var reader = Files.newBufferedReader(serverProps)) {
            propsAfter.load(reader);
        }
        assertThat(propsAfter.getProperty("level-name"))
                .as("level-name should be 'world' after execution")
                .isEqualTo("world");

        // Assert: all other existing keys remain unchanged
        for (Map.Entry<String, String> entry : existingProperties.entrySet()) {
            if (!"level-name".equals(entry.getKey())) {
                assertThat(propsAfter.getProperty(entry.getKey()))
                        .as("Key '%s' should remain unchanged", entry.getKey())
                        .isEqualTo(entry.getValue());
            }
        }
    }

    // ==================== Generators ====================

    @Provide
    Arbitrary<Map<String, byte[]>> fileTrees() {
        // Generate file names: lowercase ASCII letters + digits only.
        // This avoids collisions on case-insensitive filesystems (e.g. Windows NTFS),
        // where "A/z.dat" and "A/Z.dat" would map to the same physical file.
        Arbitrary<String> dirNames = Arbitraries.strings()
                .withCharRange('a', 'z').withCharRange('0', '9')
                .ofMinLength(1).ofMaxLength(8);
        Arbitrary<String> fileNames = Arbitraries.strings()
                .withCharRange('a', 'z').withCharRange('0', '9')
                .ofMinLength(1).ofMaxLength(8)
                .map(name -> name + ".dat");

        Arbitrary<String> relativePaths = Combinators.combine(dirNames, fileNames)
                .as((dir, file) -> dir + "/" + file);

        // Also allow top-level files
        Arbitrary<String> topLevelFiles = fileNames;

        Arbitrary<String> allPaths = Arbitraries.oneOf(relativePaths, topLevelFiles);

        // Generate file content: random bytes, small sizes for speed
        Arbitrary<byte[]> content = Arbitraries.bytes().array(byte[].class)
                .ofMinSize(1).ofMaxSize(256);

        // Generate a map of 1-5 files
        return allPaths.flatMap(path ->
                content.map(bytes -> Map.entry(path, bytes))
        ).list().ofMinSize(1).ofMaxSize(5)
                .map(entries -> {
                    // Defense in depth: dedupe by case-insensitive path so two distinct keys
                    // can never collide on a case-insensitive filesystem.
                    Map<String, byte[]> map = new LinkedHashMap<>();
                    Set<String> seenLower = new HashSet<>();
                    for (Map.Entry<String, byte[]> e : entries) {
                        String lower = e.getKey().toLowerCase(Locale.ROOT);
                        if (seenLower.add(lower)) {
                            map.put(e.getKey(), e.getValue());
                        }
                    }
                    return map;
                })
                .filter(m -> !m.isEmpty());
    }

    @Provide
    Arbitrary<Map<String, String>> propertiesMaps() {
        // Generate valid Java properties keys (no '=' or whitespace in keys)
        Arbitrary<String> keys = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(12)
                .map(k -> k.toLowerCase(Locale.ROOT))
                .filter(k -> !k.equals("levelname") && !k.equals("level"));

        Arbitrary<String> values = Arbitraries.strings()
                .alpha().numeric().ofMinLength(1).ofMaxLength(16);

        return Combinators.combine(keys, values)
                .as(Map::entry)
                .list().ofMinSize(1).ofMaxSize(5)
                .map(entries -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    for (Map.Entry<String, String> e : entries) {
                        // Avoid generating "level-name" as an existing key
                        // since that's the key we're testing
                        if (!"level-name".equals(e.getKey())) {
                            map.put(e.getKey(), e.getValue());
                        }
                    }
                    if (map.isEmpty()) {
                        map.put("motd", "test-server");
                    }
                    return map;
                });
    }
}
