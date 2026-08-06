package dev.catgirlyannick.catclans.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminWarEndConfigurationTest {

    private static final String OLD_GENERAL =
            "<yellow>Der Krieg zwischen {clan_one} und {clan_two} wurde von der "
                    + "Administration neutral beendet.";
    private static final String OLD_ADMIN =
            "<green>Der Krieg zwischen {clan_one} und {clan_two} wurde neutral beendet.";

    @Test
    void providesScoredWarResultMessages() {
        InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("messages.yml");
        assertNotNull(stream, "messages.yml fehlt im Test-Classpath");
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

        assertEquals(36, messages.getInt("config-version"));
        assertTrue(messages.getString("admin.war-ended", "").contains("{result}"));
        assertTrue(messages.getString("admin.war-ended", "")
                .contains("{clan_one_deaths}"));
        assertTrue(messages.getString("general.war-ended-by-admin", "")
                .contains("{clan_two_deaths}"));
    }

    @Test
    void migratesOnlyUnchangedNeutralDefaults() {
        YamlConfiguration messages = new YamlConfiguration();
        messages.set("general.war-ended-by-admin", OLD_GENERAL);
        messages.set("admin.war-ended", OLD_ADMIN);

        ConfigMigrationService.applyVersionThirtyThreeScoredAdminWarEnd(
                "messages.yml",
                messages
        );

        assertTrue(messages.getString("general.war-ended-by-admin", "")
                .contains("{result}"));
        assertTrue(messages.getString("admin.war-ended", "")
                .contains("{clan_one_deaths}"));

        messages.set("admin.war-ended", "Eigener Text");
        ConfigMigrationService.applyVersionThirtyThreeScoredAdminWarEnd(
                "messages.yml",
                messages
        );
        assertEquals("Eigener Text", messages.getString("admin.war-ended"));
    }
}
