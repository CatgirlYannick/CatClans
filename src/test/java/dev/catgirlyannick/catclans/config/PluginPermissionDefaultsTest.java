package dev.catgirlyannick.catclans.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPermissionDefaultsTest {

    @Test
    void grantsPlayerCommandsWithoutRequiringLuckPerms() {
        YamlConfiguration plugin = loadPluginConfiguration();

        assertTrue(plugin.getBoolean("permissions.catclans.default.*.default"));
        assertTrue(plugin.getBoolean(
                "permissions.catclans.default.*.children.catclans.command.clan"
        ));
        assertTrue(plugin.getBoolean(
                "permissions.catclans.default.*.children.catclans.clan.ranking.view"
        ));
    }

    @Test
    void keepsPrivilegedBundlesDeniedByDefault() {
        YamlConfiguration plugin = loadPluginConfiguration();

        assertFalse(plugin.getBoolean("permissions.catclans.support.*.default"));
        assertFalse(plugin.getBoolean("permissions.catclans.management.*.default"));
        assertFalse(plugin.getBoolean("permissions.catclans.admin.*.default"));
        assertFalse(plugin.getBoolean("permissions.catclans.admin.war.end.default"));
        assertTrue(plugin.getBoolean(
                "permissions.catclans.admin.*.children.catclans.admin.war.end"
        ));
    }

    @Test
    void declaresInteractiveChatAsOptionalDependencyWithoutAddingPermissions() {
        YamlConfiguration plugin = loadPluginConfiguration();

        assertTrue(plugin.getStringList("softdepend").contains("InteractiveChat"));
        assertFalse(plugin.getStringList("softdepend").contains("TAB"));
    }

    @Test
    void declaresEzEconomyInsteadOfEssentialsX() {
        YamlConfiguration plugin = loadPluginConfiguration();

        assertTrue(plugin.getStringList("softdepend").contains("EzEconomy"));
        assertFalse(plugin.getStringList("softdepend").contains("Essentials"));
    }

    private YamlConfiguration loadPluginConfiguration() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream, "plugin.yml fehlt im Test-Classpath");
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
    }
}
