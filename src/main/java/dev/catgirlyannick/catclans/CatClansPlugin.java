package dev.catgirlyannick.catclans;

import dev.catgirlyannick.catclans.audit.TextAuditLogService;
import dev.catgirlyannick.catclans.command.ClanCommand;
import dev.catgirlyannick.catclans.config.ConfigBundle;
import dev.catgirlyannick.catclans.config.RankPolicy;
import dev.catgirlyannick.catclans.gui.ClanGuiController;
import dev.catgirlyannick.catclans.gui.ClanFeatureGuiController;
import dev.catgirlyannick.catclans.integration.IntegrationRegistry;
import dev.catgirlyannick.catclans.message.MessageService;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.BattlepassRewardType;
import dev.catgirlyannick.catclans.service.ClanRules;
import dev.catgirlyannick.catclans.service.ClanService;
import dev.catgirlyannick.catclans.service.ClanSnapshotCache;
import dev.catgirlyannick.catclans.service.BattlepassActivityListener;
import dev.catgirlyannick.catclans.service.BattlepassCurve;
import dev.catgirlyannick.catclans.service.BattlepassSettings;
import dev.catgirlyannick.catclans.service.BankSettings;
import dev.catgirlyannick.catclans.service.HomeSettings;
import dev.catgirlyannick.catclans.service.LoginStreakCalculator;
import dev.catgirlyannick.catclans.service.DiplomacySettings;
import dev.catgirlyannick.catclans.service.RankingActivityListener;
import dev.catgirlyannick.catclans.service.RankingSettings;
import dev.catgirlyannick.catclans.service.VaultSettings;
import dev.catgirlyannick.catclans.storage.ClanRepository;
import dev.catgirlyannick.catclans.storage.MySqlClanRepository;
import dev.catgirlyannick.catclans.storage.SqliteClanRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

import dev.catgirlyannick.catclans.model.ClanRole;

public final class CatClansPlugin extends JavaPlugin {

    private ClanService clanService;
    private IntegrationRegistry integrations;
    private BattlepassActivityListener battlepassActivity;
    private RankingActivityListener rankingActivity;
    private ClanFeatureGuiController featureGui;

    @Override
    public void onEnable() {
        ClanRepository pendingRepository = null;
        try {
            ConfigBundle configs = new ConfigBundle(this);
            configs.load();
            List<String> configErrors = configs.validate();
            if (!configErrors.isEmpty()) {
                configErrors.forEach(error -> getLogger().severe("Configuration error: " + error));
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            if (!configs.main().getBoolean("general.enabled", true)
                    || !configs.main().getBoolean("features.clan-core.enabled", true)) {
                getLogger().warning("CatClans is disabled in config.yml.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            MessageService messages = new MessageService(configs);
            RankPolicy rankPolicy = new RankPolicy(configs.ranks());
            TextAuditLogService audit = createAudit(configs);
            TextAuditLogService vaultAudit = createVaultAudit(configs);
            TextAuditLogService bankAudit = createBankAudit(configs);
            pendingRepository = createRepository(configs);
            pendingRepository.initialize();
            ClanSnapshotCache cache = createCache(configs, pendingRepository);
            clanService = createClanService(
                    configs,
                    pendingRepository,
                    audit,
                    rankPolicy,
                    cache,
                    vaultAudit,
                    bankAudit
            );
            pendingRepository = null;
            integrations = new IntegrationRegistry(this, configs);
            integrations.detect();
            validateRuntimeIntegrations(configs, integrations);

            ClanGuiController clanGui = new ClanGuiController(
                    this,
                    configs,
                    clanService,
                    messages,
                    rankPolicy
            );
            featureGui = new ClanFeatureGuiController(
                    this,
                    configs,
                    clanService,
                    messages,
                    integrations.economy(),
                    clanGui
            );
            clanGui.featureGui(featureGui);
            getServer().getPluginManager().registerEvents(clanGui, this);
            getServer().getPluginManager().registerEvents(featureGui, this);
            battlepassActivity = createBattlepassActivity(configs, messages);
            getServer().getPluginManager().registerEvents(battlepassActivity, this);
            battlepassActivity.start();
            if (clanService.rankingsEnabled()) {
                rankingActivity = createRankingActivity(configs);
                getServer().getPluginManager().registerEvents(rankingActivity, this);
                rankingActivity.start();
            }
            registerCommands(configs, messages, clanGui, integrations);
            integrations.registerPlaceholderExpansion(clanService);

            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                audit.cleanupExpiredFiles();
                vaultAudit.cleanupExpiredFiles();
                bankAudit.cleanupExpiredFiles();
            });
            if (configs.performance().getBoolean("diagnostics.startup-summary", true)) {
                getLogger().info("CatClans " + getPluginMeta().getVersion()
                        + " started with " + configs.storage().getString(
                        "database.type",
                        "sqlite"
                ).toUpperCase() + "-Schema " + SqliteClanRepository.SCHEMA_VERSION
                        + " and " + cache.size()
                        + " cached clans.");
            }
        } catch (Exception exception) {
            closePendingRepository(pendingRepository);
            getLogger().severe("CatClans could not start safely: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (featureGui != null) {
            featureGui.shutdown();
        }
        if (battlepassActivity != null) {
            battlepassActivity.stop();
        }
        if (rankingActivity != null) {
            rankingActivity.stop();
        }
        if (integrations != null) {
            try {
                integrations.shutdown();
            } catch (RuntimeException exception) {
                getLogger().severe("Integrations could not be shut down cleanly: "
                        + exception.getMessage());
            }
        }
        if (clanService == null) {
            return;
        }
        try {
            clanService.close();
        } catch (Exception exception) {
            getLogger().severe("CatClans could not close storage cleanly: "
                    + exception.getMessage());
        }
    }

    private void closePendingRepository(ClanRepository repository) {
        if (repository == null) {
            return;
        }
        try {
            repository.close();
        } catch (Exception closeException) {
            getLogger().severe("Database could not be closed after a startup failure: "
                    + closeException.getMessage());
        }
    }

    private ClanRepository createRepository(ConfigBundle configs) {
        YamlConfiguration storage = configs.storage();
        String type = storage.getString("database.type", "sqlite").toLowerCase();
        if ("mysql".equals(type)) {
            return new MySqlClanRepository(
                    storage.getString("database.mysql.host", "localhost"),
                    storage.getInt("database.mysql.port", 3306),
                    storage.getString("database.mysql.database", "catclans"),
                    storage.getString(
                            "database.mysql.username-environment-variable",
                            "CATCLANS_DATABASE_USER"
                    ),
                    storage.getString(
                            "database.mysql.password-environment-variable",
                            "CATCLANS_DATABASE_PASSWORD"
                    ),
                    storage.getBoolean("database.mysql.use-ssl", false),
                    storage.getBoolean(
                            "database.mysql.verify-server-certificate",
                            true
                    ),
                    storage.getInt(
                            "database.mysql.connect-timeout-milliseconds",
                            5000
                    ),
                    storage.getInt(
                            "database.mysql.socket-timeout-milliseconds",
                            10000
                    ),
                    storage.getInt(
                            "database.mysql.validation-interval-seconds",
                            30
                    )
            );
        }
        Path databaseFile = configs.resolveDataPath(
                storage.getString("database.sqlite.file", "data/clans.db")
        );
        return new SqliteClanRepository(
                databaseFile,
                storage.getBoolean("database.sqlite.enable-write-ahead-log", true),
                storage.getInt("database.sqlite.busy-timeout-milliseconds", 5000),
                storage.getString("database.sqlite.synchronous-mode", "NORMAL"),
                storage.getInt("database.sqlite.wal-auto-checkpoint-pages", 1000),
                storage.getBoolean("database.sqlite.optimize-on-close", true)
        );
    }

    private TextAuditLogService createAudit(ConfigBundle configs) {
        YamlConfiguration storage = configs.storage();
        boolean logErrors = storage.getBoolean(
                "logging.text-audit.log-to-console-on-write-error",
                true
        );
        return new TextAuditLogService(
                configs.resolveDataPath(storage.getString(
                        "logging.text-audit.directory",
                        "logs/clans"
                )),
                storage.getBoolean("logging.text-audit.enabled", true),
                storage.getInt("logging.text-audit.retention-days", 14),
                storage.getString("logging.text-audit.file-pattern", "yyyy-MM-dd'.log'"),
                storage.getString(
                        "logging.text-audit.timestamp-pattern",
                        "yyyy-MM-dd HH:mm:ss.SSS XXX"
                ),
                message -> {
                    if (logErrors) {
                        getLogger().severe(message);
                    }
                }
        );
    }

    private TextAuditLogService createVaultAudit(ConfigBundle configs) {
        YamlConfiguration storage = configs.storage();
        boolean logErrors = storage.getBoolean(
                "logging.vault-audit.log-to-console-on-write-error",
                true
        );
        return new TextAuditLogService(
                configs.resolveDataPath(storage.getString(
                        "logging.vault-audit.directory",
                        "logs/vault"
                )),
                storage.getBoolean("logging.vault-audit.enabled", true),
                storage.getInt("logging.vault-audit.retention-days", 14),
                storage.getString("logging.vault-audit.file-pattern", "yyyy-MM-dd'.log'"),
                storage.getString(
                        "logging.vault-audit.timestamp-pattern",
                        "yyyy-MM-dd HH:mm:ss.SSS XXX"
                ),
                message -> {
                    if (logErrors) {
                        getLogger().severe(message);
                    }
                }
        );
    }

    private TextAuditLogService createBankAudit(ConfigBundle configs) {
        YamlConfiguration storage = configs.storage();
        boolean logErrors = storage.getBoolean(
                "logging.bank-audit.log-to-console-on-write-error",
                true
        );
        return new TextAuditLogService(
                configs.resolveDataPath(storage.getString(
                        "logging.bank-audit.directory",
                        "logs/bank"
                )),
                storage.getBoolean("logging.bank-audit.enabled", true),
                storage.getInt("logging.bank-audit.retention-days", 14),
                storage.getString("logging.bank-audit.file-pattern", "yyyy-MM-dd'.log'"),
                storage.getString(
                        "logging.bank-audit.timestamp-pattern",
                        "yyyy-MM-dd HH:mm:ss.SSS XXX"
                ),
                message -> {
                    if (logErrors) {
                        getLogger().severe(message);
                    }
                }
        );
    }

    private ClanService createClanService(
            ConfigBundle configs,
            ClanRepository repository,
            TextAuditLogService audit,
            RankPolicy rankPolicy,
            ClanSnapshotCache cache,
            TextAuditLogService vaultAudit,
            TextAuditLogService bankAudit
    ) throws Exception {
        YamlConfiguration main = configs.main();
        ClanRules rules = new ClanRules(
                main.getInt("clans.names.min-length"),
                main.getInt("clans.names.max-length"),
                main.getString("clans.names.allowed-pattern", ""),
                main.getInt("clans.tags.min-length"),
                main.getInt("clans.tags.max-length"),
                main.getString("clans.tags.allowed-pattern", ""),
                main.getInt("clans.tags.maximum-format-length", 256),
                main.getInt("clans.roles.name-max-length", 24),
                main.getString(
                        "clans.roles.allowed-pattern",
                        "^[\\p{L}\\p{N} _-]+$"
                ),
                configs.messages().getBoolean(
                        "formatting.minimessage.rgb-enabled",
                        true
                ),
                configs.messages().getBoolean(
                        "formatting.minimessage.gradients-enabled",
                        true
                )
        );
        var rightsSection = configs.permissions()
                .getConfigurationSection("clan-rights");
        if (rightsSection == null) {
            throw new IllegalStateException("permissions.yml: clan-rights is missing");
        }
        List<String> knownRights = rightsSection.getValues(false).values().stream()
                .map(String::valueOf)
                .distinct()
                .toList();
        Map<UUID, List<ClanRole>> preloadedRoles = repository.findAllRoles();
        return new ClanService(
                repository,
                audit,
                rankPolicy,
                rules,
                JoinMode.valueOf(main.getString("clans.join.default-mode")),
                main.getInt("clans.members.default-max-members"),
                Duration.ofHours(main.getInt("clans.join.invite-expiration-hours")),
                main.getBoolean("security.prevent-owner-leaving", false),
                main.getString("advanced.service-worker-name", "CatClans-Service"),
                cache,
                configs.performance().getInt("database.maximum-queued-operations", 2048),
                configs.performance().getInt("database.shutdown-timeout-seconds", 10),
                main.getInt("clans.roles.default-max-roles", 5),
                main.getInt("clans.roles.absolute-max-roles", 10),
                knownRights,
                preloadedRoles,
                battlepassSettings(configs),
                new VaultSettings(
                        configs.main().getBoolean("features.item-vault.enabled", true)
                                && configs.vault().getBoolean("general.enabled", true),
                        configs.vault().getInt("general.storage-slots-per-page", 45),
                        configs.vault().getBoolean("logging.log-deposits", true),
                        configs.vault().getBoolean("logging.log-withdrawals", true),
                        configs.vault().getBoolean("logging.log-replacements", true),
                        configs.vault().getInt(
                                "general.max-serialized-item-bytes",
                                1_048_576
                        )
                ),
                repository.findAllBattlepassProgress(),
                vaultAudit,
                new DiplomacySettings(
                        configs.diplomacy().getBoolean("general.enabled", true)
                                && configs.main().getBoolean(
                                "features.alliances.enabled",
                                true
                        ),
                        configs.diplomacy().getBoolean("general.enabled", true)
                                && configs.main().getBoolean(
                                "features.clan-wars.enabled",
                                true
                        ),
                        Duration.ofHours(configs.diplomacy().getInt(
                                "requests.expiration-hours",
                                24
                        )),
                        new HashSet<>(configs.diplomacy().getIntegerList(
                                "wars.allowed-duration-hours"
                        )),
                        configs.diplomacy().getInt(
                                "requests.maximum-pending-per-clan",
                                25
                        )
                ),
                rankingSettings(configs),
                repository.findAllRankingStats(),
                bankSettings(configs),
                bankAudit,
                homeSettings(configs)
        );
    }

    private HomeSettings homeSettings(ConfigBundle configs) {
        YamlConfiguration homes = configs.homes();
        return new HomeSettings(
                configs.main().getBoolean("features.clan-homes.enabled", true)
                        && homes.getBoolean("general.enabled", true),
                homes.getInt("limits.default-slots", 3),
                homes.getInt("limits.absolute-max-slots", 103),
                homes.getInt("teleport.cooldown-seconds", 0),
                homes.getBoolean("teleport.allow-cross-world", true)
        );
    }

    private BankSettings bankSettings(ConfigBundle configs) {
        YamlConfiguration economy = configs.economy();
        int scale = economy.getInt("clan-bank.decimal-scale", 2);
        RoundingMode roundingMode = RoundingMode.valueOf(
                economy.getString("clan-bank.rounding-mode", "HALF_UP")
        );
        return new BankSettings(
                configs.main().getBoolean("features.bank.enabled", true)
                        && configs.main().getBoolean("features.economy.enabled", true)
                        && economy.getBoolean("general.enabled", true)
                        && economy.getBoolean("clan-bank.enabled", true),
                economy.getString("currency.display-name", "Coins"),
                decimal(economy, "clan-bank.minimum-deposit"),
                decimal(economy, "clan-bank.minimum-withdrawal"),
                scale,
                roundingMode,
                economy.getStringList("clan-bank.quick-amounts").stream()
                        .map(BigDecimal::new)
                        .toList(),
                economy.getBoolean("clan-bank.logging.log-deposits", true),
                economy.getBoolean("clan-bank.logging.log-withdrawals", true),
                economy.getInt("clan-bank.logging.maximum-gui-entries", 18)
        );
    }

    private RankingSettings rankingSettings(ConfigBundle configs) {
        YamlConfiguration rankings = configs.rankings();
        return new RankingSettings(
                configs.main().getBoolean("features.rankings.enabled", true)
                        && rankings.getBoolean("general.enabled", true),
                ZoneId.of(rankings.getString(
                        "activity.timezone",
                        "Europe/Berlin"
                )),
                Duration.ofSeconds(rankings.getInt(
                        "activity.maintenance-interval-seconds",
                        60
                )),
                Duration.ofMinutes(rankings.getInt(
                        "pvp.repeated-victim-cooldown-minutes",
                        15
                )),
                decimal(rankings, "pvp.points-per-valid-kill"),
                decimal(rankings, "scoring.points-per-member"),
                decimal(rankings, "scoring.money-amount-per-point"),
                decimal(rankings, "scoring.points-per-war-win"),
                decimal(rankings, "scoring.points-per-war-loss"),
                decimal(rankings, "scoring.negative-point-multiplier"),
                decimal(rankings, "scoring.points-per-active-day"),
                configs.main().getBoolean("features.bank.enabled", false)
                        && configs.main().getBoolean("features.economy.enabled", true)
                        && configs.economy().getBoolean("general.enabled", true)
                        && configs.economy().getBoolean("clan-bank.enabled", false),
                "mysql".equalsIgnoreCase(configs.storage().getString(
                        "database.type",
                        "sqlite"
                ))
        );
    }

    private BattlepassSettings battlepassSettings(ConfigBundle configs) {
        YamlConfiguration battlepass = configs.battlepass();
        int scale = battlepass.getInt("progression.decimal-scale", 2);
        RoundingMode roundingMode = RoundingMode.valueOf(
                battlepass.getString("progression.rounding-mode", "HALF_UP")
        );
        return new BattlepassSettings(
                configs.main().getBoolean("features.battlepass.enabled", true)
                        && battlepass.getBoolean("general.enabled", true),
                new BattlepassCurve(
                        decimal(battlepass, "progression.base-xp-required"),
                        decimal(battlepass, "progression.growth-percent-per-level"),
                        scale,
                        roundingMode
                ),
                new LoginStreakCalculator(
                        decimal(battlepass, "sources.daily-login.base-xp"),
                        decimal(battlepass, "sources.daily-login.early-streak-multiplier"),
                        battlepass.getInt(
                                "sources.daily-login.late-growth-starts-at-day",
                                10
                        ),
                        decimal(
                                battlepass,
                                "sources.daily-login.late-growth-percent-per-day"
                        ),
                        scale,
                        roundingMode
                ),
                ZoneId.of(battlepass.getString(
                        "sources.daily-login.timezone",
                        "Europe/Berlin"
                )),
                decimal(
                        battlepass,
                        "sources.online-activity.xp-per-online-clan-member"
                ),
                battlepass.getInt("sources.online-activity.interval-minutes", 30),
                decimal(battlepass, "sources.pvp.xp-per-kill"),
                battlepass.getInt(
                        "sources.pvp.repeated-victim-cooldown-minutes",
                        15
                ),
                battlepass.getBoolean("sources.pvp.allow-same-clan-kills", false),
                configs.main().getInt("clans.members.absolute-max-members", 500),
                configs.main().getInt("clans.roles.absolute-max-roles", 10),
                configs.vault().getInt("general.absolute-max-pages", 7),
                battlepass.getInt("limits.absolute-max-bonus-home-slots", 100),
                Arrays.stream(BattlepassRewardType.values())
                        .filter(type -> battlepass.getBoolean(
                                "rewards.supported-types." + type.configKey() + ".enabled",
                                true
                        ))
                        .collect(Collectors.toUnmodifiableSet())
        );
    }

    private BattlepassActivityListener createBattlepassActivity(
            ConfigBundle configs,
            MessageService messages
    ) {
        YamlConfiguration battlepass = configs.battlepass();
        return new BattlepassActivityListener(
                this,
                clanService,
                messages,
                battlepass.getBoolean("sources.daily-login.enabled", true),
                battlepass.getBoolean("sources.pvp.enabled", true),
                battlepass.getBoolean("sources.online-activity.enabled", true),
                battlepass.getInt("sources.online-activity.interval-minutes", 30)
        );
    }

    private RankingActivityListener createRankingActivity(ConfigBundle configs) {
        return new RankingActivityListener(
                this,
                clanService,
                configs.rankings().getInt(
                        "activity.maintenance-interval-seconds",
                        60
                )
        );
    }

    private static BigDecimal decimal(YamlConfiguration configuration, String path) {
        Object value = configuration.get(path);
        if (value == null) {
            throw new IllegalArgumentException("Fehlender Dezimalwert: " + path);
        }
        return new BigDecimal(String.valueOf(value));
    }

    private ClanSnapshotCache createCache(ConfigBundle configs, ClanRepository repository)
            throws Exception {
        ClanSnapshotCache cache = new ClanSnapshotCache(
                configs.performance().getInt("cache.maximum-clans", 100_000)
        );
        cache.preload(repository.findAll());
        return cache;
    }

    private void validateRuntimeIntegrations(
            ConfigBundle configs,
            IntegrationRegistry integrations
    ) {
        boolean economyRequired = configs.main().getBoolean("features.economy.enabled", true)
                && configs.economy().getBoolean("general.enabled", true)
                && configs.economy().getBoolean("clan-creation.charge-enabled", false)
                && configs.integrations().getBoolean(
                "vault.require-economy-provider-when-used",
                true
        );
        if (economyRequired && !integrations.economy().available()) {
            throw new IllegalStateException("Paid clan creation requires Vault "
                    + "and an active economy provider");
        }
    }

    private void registerCommands(
            ConfigBundle configs,
            MessageService messages,
            ClanGuiController clanGui,
            IntegrationRegistry integrations
    ) {
        PluginCommand command = getCommand("clan");
        if (command == null) {
            throw new IllegalStateException("Command clan is missing from plugin.yml");
        }
        ClanCommand clanCommand = new ClanCommand(
                this,
                configs,
                clanService,
                messages,
                clanGui,
                integrations.economy()
        );
        command.setExecutor(clanCommand);
        command.setTabCompleter(clanCommand);
        PluginCommand clanTop = getCommand("clantop");
        if (clanTop == null) {
            throw new IllegalStateException("Command clantop is missing from plugin.yml");
        }
        clanTop.setExecutor(clanCommand);
        clanTop.setTabCompleter(clanCommand);
        PluginCommand clanAdmin = getCommand("clanadmin");
        if (clanAdmin == null) {
            throw new IllegalStateException("Command clanadmin is missing from plugin.yml");
        }
        clanAdmin.setExecutor(clanCommand);
        clanAdmin.setTabCompleter(clanCommand);
    }
}
