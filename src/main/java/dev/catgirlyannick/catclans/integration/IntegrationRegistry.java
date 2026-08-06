package dev.catgirlyannick.catclans.integration;

import dev.catgirlyannick.catclans.CatClansPlugin;
import dev.catgirlyannick.catclans.config.ConfigBundle;
import dev.catgirlyannick.catclans.service.ClanService;
import org.bukkit.plugin.PluginManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class IntegrationRegistry {

    private final CatClansPlugin plugin;
    private final ConfigBundle configs;
    private EconomyBridge economy = EconomyBridge.unavailable();
    private Map<String, Boolean> states = Map.of();
    private Runnable placeholderShutdown = () -> {
    };

    public IntegrationRegistry(CatClansPlugin plugin, ConfigBundle configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public Map<String, Boolean> detect() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        Map<String, Boolean> detected = new LinkedHashMap<>();
        detected.put("Vault", detectOne(pluginManager, "vault", "Vault"));
        detected.put("EzEconomy", detectOne(
                pluginManager,
                "ezeconomy",
                configs.integrations().getString(
                        "ezeconomy.plugin-name",
                        "EzEconomy"
                ),
                "EzEconomy"
        ));
        detected.put("LuckPerms", detectOne(pluginManager, "luckperms", "LuckPerms"));
        detected.put("PlaceholderAPI", detectOne(pluginManager, "placeholderapi", "PlaceholderAPI"));
        detected.put("InteractiveChat", detectOne(
                pluginManager,
                "interactivechat",
                configs.integrations().getString(
                        "interactivechat.plugin-name",
                        "InteractiveChat"
                ),
                "InteractiveChat"
        ));
        states = Map.copyOf(detected);

        if (active("Vault")) {
            economy = VaultEconomyBridge.create(plugin).orElse(EconomyBridge.unavailable());
            plugin.getLogger().info("Vault-Economy-Provider: " + economy.providerName());
        }
        validateEzEconomy();
        validateInteractiveChat();
        if (active("LuckPerms")
                && configs.integrations().getBoolean("luckperms.validate-groups-on-start", true)) {
            verifyLuckPermsGroups();
        }
        return states;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public void registerPlaceholderExpansion(ClanService clanService) {
        if (!active("PlaceholderAPI")
                || !configs.main().getBoolean("features.placeholders.enabled", true)
                || !configs.placeholders().getBoolean("general.enabled", true)
                || !configs.integrations().getBoolean(
                "placeholderapi.register-internal-expansion",
                true
        )) {
            return;
        }
        CatClansExpansion expansion = new CatClansExpansion(
                plugin,
                configs,
                clanService
        );
        boolean registered = expansion.register();
        if (!registered) {
            throw new IllegalStateException("PlaceholderAPI expansion could not be registered");
        }
        placeholderShutdown = expansion::unregister;
        plugin.getLogger().info("PlaceholderAPI-Erweiterung %"
                + configs.placeholders().getString("general.identifier", "catclans")
                + "_...% was registered");
    }

    public void shutdown() {
        placeholderShutdown.run();
        placeholderShutdown = () -> {
        };
    }

    public boolean active(String pluginName) {
        return states.getOrDefault(pluginName, false);
    }

    private void verifyLuckPermsGroups() {
        List<String> missing = LuckPermsVerifier.missingConfiguredGroups(plugin, configs);
        if (missing.isEmpty()) {
            plugin.getLogger().info("LuckPerms group check: all configured groups are available");
            return;
        }
        String message = "LuckPerms-Gruppen fehlen: " + String.join(", ", missing);
        if (configs.integrations().getBoolean("luckperms.missing-groups-are-errors", false)) {
            throw new IllegalStateException(message);
        }
        plugin.getLogger().warning(message);
    }

    private void validateEzEconomy() {
        if (!configs.integrations().getBoolean("ezeconomy.enabled", true)
                || !configs.integrations().getBoolean(
                "ezeconomy.validate-economy-provider",
                true
        )) {
            return;
        }
        if (!active("EzEconomy")) {
            economy = EconomyBridge.unavailable();
            plugin.getLogger().warning(
                    "EzEconomy is not installed; the clan bank remains disabled"
            );
            return;
        }
        String problem = null;
        if (!active("Vault")) {
            problem = "EzEconomy is installed, but Vault is missing";
        } else if (!economy.available()) {
            problem = "EzEconomy hat keinen aktiven Vault-Economy-Provider registriert";
        } else if (!isEzEconomyProvider(economy)) {
            problem = "Vault is using a provider other than EzEconomy: " + economy.providerName()
                    + " (" + economy.providerImplementationName() + ")";
        }
        if (problem == null) {
            plugin.getLogger().info("EzEconomy is active through Vault: "
                    + economy.providerName());
            return;
        }
        if (configs.integrations().getBoolean(
                "ezeconomy.provider-mismatch-is-error",
                false
        )) {
            throw new IllegalStateException(problem);
        }
        economy = EconomyBridge.unavailable();
        plugin.getLogger().warning(problem + "; the clan bank remains disabled without a matching provider");
    }

    private void validateInteractiveChat() {
        if (!active("InteractiveChat")) {
            return;
        }
        if (!active("PlaceholderAPI")) {
            throw new IllegalStateException(
                    "InteractiveChat requires PlaceholderAPI for CatClans placeholders"
            );
        }
        plugin.getLogger().info("InteractiveChat compatibility is active through PlaceholderAPI");
    }

    static boolean isEzEconomyProvider(EconomyBridge economy) {
        String providerName = economy.providerName().toLowerCase(Locale.ROOT);
        String implementation = economy.providerImplementationName()
                .toLowerCase(Locale.ROOT);
        return providerName.equals("ezeconomy")
                || implementation.startsWith("com.skyblockexp.ezeconomy.");
    }

    private boolean detectOne(PluginManager manager, String configKey, String pluginName) {
        return detectOne(manager, configKey, pluginName, pluginName);
    }

    private boolean detectOne(
            PluginManager manager,
            String configKey,
            String pluginName,
            String displayName
    ) {
        boolean enabledInConfig = configs.integrations().getBoolean(configKey + ".enabled", true);
        boolean required = configs.integrations().getBoolean(configKey + ".required", false);
        boolean available = manager.isPluginEnabled(pluginName);

        if (enabledInConfig && required && !available) {
            throw new IllegalStateException("Required integration is missing: " + displayName);
        }
        if (enabledInConfig) {
            plugin.getLogger().info(displayName + "-Hook: "
                    + (available ? "available" : "not installed"));
        }
        return enabledInConfig && available;
    }
}
