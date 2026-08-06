package dev.catgirlyannick.catclans.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRetentionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsOnlyTwoNewestConfigBackupsAndUnrelatedDirectories() throws Exception {
        Path oldest = backup("config-to-v30-20260801-100000", 1);
        Path second = backup("config-to-v31-20260801-110000", 2);
        Path newest = backup("config-to-v32-20260801-120000", 3);
        Path current = backup("config-to-v34-20260801-130000", 4);
        Path unrelated = Files.createDirectory(temporaryDirectory.resolve("manual-export"));

        ConfigMigrationService.pruneBackupDirectories(
                temporaryDirectory,
                2,
                current
        );

        assertFalse(Files.exists(oldest));
        assertFalse(Files.exists(second));
        assertTrue(Files.isDirectory(newest));
        assertTrue(Files.isDirectory(current));
        assertTrue(Files.isDirectory(unrelated));
    }

    @Test
    void rejectsLimitsAboveTheHardMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigMigrationService.pruneBackupDirectories(
                        temporaryDirectory,
                        3,
                        null
                )
        );
    }

    @Test
    void exposesTheConfiguredMaximumOfTwo() {
        InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("storage.yml");
        assertNotNull(stream, "storage.yml fehlt im Test-Classpath");
        YamlConfiguration storage = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

        assertEquals(36, storage.getInt("config-version"));
        assertEquals(2, storage.getInt("backups.config-migrations.max-snapshots"));
    }

    private Path backup(String name, long modifiedSeconds) throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve(name));
        Files.writeString(directory.resolve("config.yml"), "config-version: 1");
        Files.setLastModifiedTime(directory, FileTime.fromMillis(modifiedSeconds * 1000));
        return directory;
    }
}
