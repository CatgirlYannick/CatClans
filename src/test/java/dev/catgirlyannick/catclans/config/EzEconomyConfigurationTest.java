package dev.catgirlyannick.catclans.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EzEconomyConfigurationTest {

    @Test
    void usesEzEconomyAsDefaultVaultProvider() {
        YamlConfiguration integrations = loadIntegrations();

        assertEquals(36, integrations.getInt("config-version"));
        assertTrue(integrations.getBoolean("ezeconomy.enabled"));
        assertEquals("EzEconomy", integrations.getString("ezeconomy.plugin-name"));
        assertTrue(integrations.getBoolean("ezeconomy.validate-economy-provider"));
        assertFalse(integrations.contains("essentialsx"));
    }

    @Test
    void migrationReplacesEssentialsXWithEzEconomy() {
        YamlConfiguration integrations = new YamlConfiguration();
        integrations.set("essentialsx.enabled", true);
        integrations.set("essentialsx.plugin-name", "Essentials");

        ConfigMigrationService.applyVersionThirtyOneEzEconomy(
                "integrations.yml",
                integrations
        );

        assertFalse(integrations.contains("essentialsx"));
        assertTrue(integrations.getBoolean("ezeconomy.enabled"));
        assertEquals("EzEconomy", integrations.getString("ezeconomy.plugin-name"));
        assertTrue(integrations.getBoolean("ezeconomy.validate-economy-provider"));
    }

    private YamlConfiguration loadIntegrations() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "integrations.yml"
        );
        assertNotNull(stream, "integrations.yml fehlt im Test-Classpath");
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
    }
}
