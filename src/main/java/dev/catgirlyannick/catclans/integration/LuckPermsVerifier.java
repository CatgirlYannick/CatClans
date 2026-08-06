package dev.catgirlyannick.catclans.integration;

import dev.catgirlyannick.catclans.config.ConfigBundle;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

final class LuckPermsVerifier {

    private LuckPermsVerifier() {
    }

    static List<String> missingConfiguredGroups(JavaPlugin plugin, ConfigBundle configs) {
        RegisteredServiceProvider<LuckPerms> registration =
                plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (registration == null || registration.getProvider() == null) {
            return List.of("LuckPerms API is not registered in the Bukkit ServicesManager");
        }

        LuckPerms luckPerms = registration.getProvider();
        List<String> missing = new ArrayList<>();
        for (String key : List.of("default", "support", "management", "administration")) {
            String groupName = configs.integrations().getString("luckperms.groups." + key, "");
            if (luckPerms.getGroupManager().getGroup(groupName) == null) {
                missing.add(groupName);
            }
        }
        return List.copyOf(missing);
    }
}
