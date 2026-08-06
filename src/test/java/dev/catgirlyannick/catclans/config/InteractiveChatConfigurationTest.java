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

class InteractiveChatConfigurationTest {

    @Test
    void enablesInteractiveChatWithoutReintroducingNameSuffixes() {
        YamlConfiguration integrations = load("integrations.yml");
        YamlConfiguration placeholders = load("placeholders.yml");

        assertEquals(36, integrations.getInt("config-version"));
        assertTrue(integrations.getBoolean("interactivechat.enabled"));
        assertEquals(
                "InteractiveChat",
                integrations.getString("interactivechat.plugin-name")
        );
        assertFalse(integrations.contains("tab"));
        assertFalse(integrations.contains("player-display"));
        assertFalse(placeholders.contains(
                "enabled-placeholders.player-name-with-tag-formatted"
        ));
        assertFalse(placeholders.contains(
                "enabled-placeholders.player-name-with-tag-colored"
        ));
    }

    @Test
    void migrationRemovesSuffixConfigurationAndAddsInteractiveChat() {
        YamlConfiguration integrations = new YamlConfiguration();
        integrations.set("tab.enabled", true);
        integrations.set("player-display.chat.enabled", true);

        ConfigMigrationService.applyVersionTwentyNineInteractiveChat(
                "integrations.yml",
                integrations
        );

        assertFalse(integrations.contains("tab"));
        assertFalse(integrations.contains("player-display"));
        assertTrue(integrations.getBoolean("interactivechat.enabled"));
        assertFalse(integrations.getBoolean("interactivechat.required"));
    }

    private YamlConfiguration load(String resourceName) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
        assertNotNull(stream, resourceName + " fehlt im Test-Classpath");
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
    }
}
