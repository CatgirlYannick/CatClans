package dev.catgirlyannick.catclans.config;

import dev.catgirlyannick.catclans.util.MenuTextNormalizer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;

final class ConfigMigrationService {

    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JavaPlugin plugin;
    private File backupDirectory;
    private boolean backupRetentionApplied;

    ConfigMigrationService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void migrateIfSupported(String fileName, File file, int targetVersion) {
        applyBackupRetention();
        YamlConfiguration existing = YamlConfiguration.loadConfiguration(file);
        int currentVersion = existing.getInt("config-version", -1);
        if (currentVersion == targetVersion) {
            return;
        }

        try {
            backup(fileName, file, targetVersion);
            YamlConfiguration defaults = loadBundledDefaults(fileName);
            mergeMissingValues(existing, defaults.getValues(true));
            if (currentVersion < 4) {
                applyVersionFourRemovals(fileName, existing);
            }
            if (currentVersion < 5) {
                applyVersionFiveGuiDesign(fileName, existing);
            }
            if (currentVersion < 6) {
                applyVersionSixFormattedTags(fileName, existing);
            }
            if (currentVersion < 7) {
                applyVersionSevenProgression(fileName, existing);
            }
            if (currentVersion < 8) {
                applyVersionEightDiplomacyAndBattlepass(fileName, existing);
            }
            if (currentVersion < 9) {
                applyVersionNineNavigation(fileName, existing);
            }
            if (currentVersion < 10) {
                applyVersionTenBattlepassPosition(fileName, existing);
            }
            if (currentVersion < 11) {
                applyVersionElevenAllyRequests(fileName, existing);
            }
            if (currentVersion < 12) {
                applyVersionTwelveClanListLabel(fileName, existing);
            }
            if (currentVersion < 13) {
                applyVersionThirteenRankings(fileName, existing);
            }
            if (currentVersion < 14) {
                applyVersionFourteenRoleChatInput(fileName, existing);
            }
            if (currentVersion < 15) {
                applyVersionFifteenVault(fileName, existing);
            }
            if (currentVersion < 16) {
                applyVersionSixteenBankAndPriorities(fileName, existing);
            }
            if (currentVersion < 17) {
                applyVersionSeventeenClanLifecycle(fileName, existing);
            }
            if (currentVersion < 18) {
                applyVersionEighteenEssentialsX(fileName, existing);
            }
            if (currentVersion < 19) {
                applyVersionNineteenCreateCommandHelp(fileName, existing);
            }
            if (currentVersion < 20) {
                applyVersionTwentyClanEditing(fileName, existing);
            }
            if (currentVersion < 21) {
                applyVersionTwentyOneChatOnlyCreate(fileName, existing);
            }
            if (currentVersion < 22) {
                applyVersionTwentyTwoClanEditPosition(fileName, existing);
            }
            if (currentVersion < 23) {
                applyVersionTwentyThreeGuiStyleAndUnlimitedBank(fileName, existing);
            }
            if (currentVersion < 24) {
                applyVersionTwentyFourMenuText(fileName, existing);
            }
            if (currentVersion < 25) {
                applyVersionTwentyFiveClanHomes(fileName, existing);
            }
            if (currentVersion < 26) {
                applyVersionTwentySixClanEditPosition(fileName, existing);
            }
            if (currentVersion < 27) {
                applyVersionTwentySevenClanEditChatInput(fileName, existing);
            }
            if (currentVersion < 28) {
                applyVersionTwentyEightPlayerDisplay(fileName, existing);
            }
            if (currentVersion < 29) {
                applyVersionTwentyNineInteractiveChat(fileName, existing);
            }
            if (currentVersion < 30) {
                applyVersionThirtyBattlepassFiller(fileName, existing);
            }
            if (currentVersion < 31) {
                applyVersionThirtyOneEzEconomy(fileName, existing);
            }
            if (currentVersion < 32) {
                applyVersionThirtyTwoAdminWarEnd(fileName, existing);
            }
            if (currentVersion < 33) {
                applyVersionThirtyThreeScoredAdminWarEnd(fileName, existing);
            }
            if (currentVersion < 34) {
                applyVersionThirtyFourBackupRetention(fileName, existing);
            }
            if (currentVersion < 35) {
                applyVersionThirtyFiveBranding(fileName, existing);
            }
            if (currentVersion < 36) {
                applyVersionThirtySixBrandingPlaceholders(fileName, existing);
            }
            existing.set("config-version", targetVersion);
            existing.save(file);
            plugin.getLogger().info(fileName + " was migrated with a backup from configuration version "
                    + currentVersion + " to " + targetVersion + ".");
        } catch (IOException exception) {
            throw new IllegalStateException("Configuration migration for " + fileName
                    + " failed: " + exception.getMessage(), exception);
        }
    }

    private void backup(String fileName, File source, int targetVersion) throws IOException {
        if (backupDirectory == null) {
            backupDirectory = new File(
                    plugin.getDataFolder(),
                    "backups/config-to-v" + targetVersion + "-"
                            + BACKUP_TIMESTAMP.format(LocalDateTime.now())
            );
            Files.createDirectories(backupDirectory.toPath());
        }
        Files.copy(
                source.toPath(),
                new File(backupDirectory, fileName).toPath(),
                StandardCopyOption.COPY_ATTRIBUTES
        );
        pruneBackupDirectories(
                backupDirectory.getParentFile().toPath(),
                configuredBackupLimit(),
                backupDirectory.toPath()
        );
    }

    private void applyBackupRetention() {
        if (backupRetentionApplied) {
            return;
        }
        backupRetentionApplied = true;
        try {
            pruneBackupDirectories(
                    new File(plugin.getDataFolder(), "backups").toPath(),
                    configuredBackupLimit(),
                    null
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Old configuration backups could not be cleaned up: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private int configuredBackupLimit() {
        File storageFile = new File(plugin.getDataFolder(), "storage.yml");
        int configured = YamlConfiguration.loadConfiguration(storageFile)
                .getInt("backups.config-migrations.max-snapshots", 2);
        return Math.max(1, Math.min(2, configured));
    }

    static void pruneBackupDirectories(
            Path backupRoot,
            int maxSnapshots,
            Path currentBackup
    ) throws IOException {
        if (maxSnapshots < 1 || maxSnapshots > 2) {
            throw new IllegalArgumentException("maxSnapshots must be between 1 and 2");
        }
        Path normalizedRoot = backupRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            return;
        }
        Path normalizedCurrent = currentBackup == null
                ? null
                : currentBackup.toAbsolutePath().normalize();
        List<Path> snapshots;
        try (Stream<Path> children = Files.list(normalizedRoot)) {
            snapshots = children
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("config-to-v"))
                    .sorted((first, second) -> compareBackups(
                            first,
                            second,
                            normalizedCurrent
                    ))
                    .toList();
        }
        for (int index = maxSnapshots; index < snapshots.size(); index++) {
            deleteBackupDirectory(normalizedRoot, snapshots.get(index));
        }
    }

    private static int compareBackups(Path first, Path second, Path currentBackup) {
        if (currentBackup != null) {
            if (first.equals(currentBackup)) {
                return -1;
            }
            if (second.equals(currentBackup)) {
                return 1;
            }
        }
        int modifiedComparison = Long.compare(
                lastModified(second),
                lastModified(first)
        );
        return modifiedComparison != 0
                ? modifiedComparison
                : second.getFileName().toString()
                .compareTo(first.getFileName().toString());
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static void deleteBackupDirectory(Path backupRoot, Path backup) throws IOException {
        Path normalizedBackup = backup.toAbsolutePath().normalize();
        if (normalizedBackup.equals(backupRoot)
                || !normalizedBackup.startsWith(backupRoot)
                || !normalizedBackup.getFileName().toString().startsWith("config-to-v")) {
            throw new IOException("Unsafe backup path was rejected: " + backup);
        }
        try (Stream<Path> contents = Files.walk(normalizedBackup)) {
            for (Path path : contents.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private YamlConfiguration loadBundledDefaults(String fileName) throws IOException {
        InputStream resource = plugin.getResource(fileName);
        if (resource == null) {
            throw new IOException("Bundled default resource is missing: " + fileName);
        }
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static void mergeMissingValues(
            YamlConfiguration existing,
            Map<String, Object> defaultValues
    ) {
        defaultValues.forEach((path, value) -> {
            if (!(value instanceof ConfigurationSection) && !existing.contains(path)) {
                existing.set(path, value);
            }
        });
    }

    private static void applyVersionFourRemovals(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("gui.yml".equals(fileName)) {
            configuration.set("profile-menu.size", 54);
            configuration.set("profile-menu.back.slot", 45);
            configuration.set("profile-menu.close.slot", 53);
            for (String path : List.of(
                    "main-menu.invite",
                    "main-menu.search",
                    "clan-list-menu.search",
                    "invite-player-menu",
                    "input-menu.search"
            )) {
                configuration.set(path, null);
            }
        }
        if ("messages.yml".equals(fileName)) {
            List<String> filteredHelp = configuration.getStringList("general.help").stream()
                    .filter(line -> !line.contains("/clan search"))
                    .filter(line -> !line.contains("/clan suche"))
                    .toList();
            configuration.set("general.help", filteredHelp);
            configuration.set("general.search-result", null);
        }
    }

    private static void applyVersionFiveGuiDesign(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"gui.yml".equals(fileName)) {
            return;
        }

        configuration.set("common.filler.enabled", true);
        configuration.set("common.filler.material", "BLACK_STAINED_GLASS_PANE");
        configuration.set("common.filler.display-name", " ");
        configuration.set("common.frame.enabled", true);
        configuration.set("common.frame.material", "PURPLE_STAINED_GLASS_PANE");
        configuration.set("common.frame.display-name", " ");
        configuration.set("common.frame.top-row", true);
        configuration.set("common.frame.bottom-row", true);

        configuration.set("main-menu.size", 54);
        configuration.set("main-menu.profile.slot", 13);
        configuration.set("main-menu.members.slot", 20);
        configuration.set("main-menu.create.slot", 22);
        configuration.set("main-menu.join-mode.slot", 24);
        configuration.set("main-menu.browse.slot", 30);
        configuration.set("main-menu.invitations.slot", 32);
        configuration.set("main-menu.leave.slot", 40);
        configuration.set("main-menu.close.slot", 53);

        configuration.set("clan-list-menu.clan-slots", centeredListSlots());
        configuration.set("clan-list-menu.back.slot", 45);
        configuration.set("clan-list-menu.previous.slot", 48);
        configuration.set("clan-list-menu.next.slot", 50);
        configuration.set("clan-list-menu.close.slot", 53);

        configuration.set("profile-menu.size", 54);
        configuration.set("profile-menu.info.slot", 13);
        configuration.set("profile-menu.permissions.slot", 20);
        configuration.set("profile-menu.members.slot", 22);
        configuration.set("profile-menu.primary-action.slot", 24);
        configuration.set("profile-menu.deny.slot", 25);
        configuration.set("profile-menu.battlepass.slot", 29);
        configuration.set("profile-menu.bank.slot", 31);
        configuration.set("profile-menu.vault.slot", 33);
        configuration.set("profile-menu.back.slot", 45);
        configuration.set("profile-menu.close.slot", 53);

        configuration.set("invites-menu.invite-slots", centeredListSlots());
        configuration.set("invites-menu.empty.slot", 22);
        configuration.set("invites-menu.back.slot", 45);
        configuration.set("invites-menu.close.slot", 53);

        configuration.set("confirmation-menu.subject-slot", 13);
        configuration.set("confirmation-menu.confirm.slot", 11);
        configuration.set("confirmation-menu.cancel.slot", 15);

        configuration.set("members-menu.member-slots", centeredMemberSlots());
        configuration.set("members-menu.info.slot", 4);
        configuration.set("members-menu.back.slot", 45);
        configuration.set("members-menu.close.slot", 53);

        configuration.set("permission-home-menu.members.slot", 11);
        configuration.set("permission-home-menu.roles.slot", 15);
        configuration.set("permission-home-menu.back.slot", 18);
        configuration.set("permission-home-menu.close.slot", 26);

        configuration.set("role-list-menu.role-slots", centeredRoleSlots());
        configuration.set("role-list-menu.create.slot", 40);
        configuration.set("role-list-menu.locked.slot", 40);
        configuration.set("role-list-menu.back.slot", 45);
        configuration.set("role-list-menu.close.slot", 53);

        configuration.set(
                "member-permission-list-menu.member-slots",
                centeredMemberSlots()
        );
        configuration.set("member-permission-list-menu.back.slot", 45);
        configuration.set("member-permission-list-menu.close.slot", 53);

        configuration.set(
                "role-permissions-menu.permission-slots",
                centeredPermissionSlots()
        );
        configuration.set("role-permissions-menu.move-up.slot", 38);
        configuration.set("role-permissions-menu.rename.slot", 40);
        configuration.set("role-permissions-menu.move-down.slot", 42);
        configuration.set("role-permissions-menu.back.slot", 45);
        configuration.set("role-permissions-menu.close.slot", 53);

        configuration.set(
                "member-permissions-menu.permission-slots",
                centeredPermissionSlots()
        );
        configuration.set("member-permissions-menu.role.slot", 4);
        configuration.set("member-permissions-menu.back.slot", 45);
        configuration.set("member-permissions-menu.close.slot", 53);

        configuration.set("role-assignment-menu.role-slots", centeredRoleSlots());
        configuration.set("role-assignment-menu.back.slot", 45);
        configuration.set("role-assignment-menu.close.slot", 53);
    }

    private static List<Integer> centeredListSlots() {
        return List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        );
    }

    private static List<Integer> centeredMemberSlots() {
        return List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 41, 42, 43
        );
    }

    private static List<Integer> centeredRoleSlots() {
        return List.of(11, 12, 13, 14, 15, 20, 21, 22, 23, 24);
    }

    private static List<Integer> centeredPermissionSlots() {
        return List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25);
    }

    static void applyVersionSixFormattedTags(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("messages.yml".equals(fileName)) {
            for (String path : List.of(
                    "general.created",
                    "general.list-entry",
                    "profile.tag"
            )) {
                replaceTagPlaceholder(configuration, path);
            }
            List<String> help = new java.util.ArrayList<>(
                    configuration.getStringList("general.help")
            );
            if (help.stream().noneMatch(line -> line.contains("/clan tag"))) {
                help.add("<gold>/clan tag <MiniMessage-Tag></gold> "
                        + "<gray>- Change clan tag");
                configuration.set("general.help", help);
            }
        }
        if ("gui.yml".equals(fileName)) {
            for (String path : List.of(
                    "main-menu.profile.lore",
                    "clan-list-menu.clan.lore",
                    "profile-menu.info.lore",
                    "invites-menu.invite.lore",
                    "members-menu.info.lore",
                    "confirmation-menu.actions.create.display-name"
            )) {
                if (configuration.isList(path)) {
                    List<String> updated = configuration.getStringList(path).stream()
                            .map(line -> line.replace("{tag}", "{formatted_tag}"))
                            .toList();
                    configuration.set(path, updated);
                } else {
                    replaceTagPlaceholder(configuration, path);
                }
            }
        }
    }

    private static void replaceTagPlaceholder(
            YamlConfiguration configuration,
            String path
    ) {
        String value = configuration.getString(path);
        if (value != null) {
            configuration.set(path, value.replace("{tag}", "{formatted_tag}"));
        }
    }

    static void applyVersionSevenProgression(
            String fileName,
            YamlConfiguration configuration
    ) {
        // Version 7 only adds missing defaults through mergeMissingValues.
        // Existing feature flags and customized leaderboards remain unchanged.
    }

    static void applyVersionEightDiplomacyAndBattlepass(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("config.yml".equals(fileName)) {
            configuration.set("features.alliances.enabled", true);
            configuration.set("features.clan-wars.enabled", true);
            configuration.set("features.battlepass.enabled", true);
        }
        if ("battlepass.yml".equals(fileName)) {
            configuration.set("general.enabled", true);
        }
        if (!"gui.yml".equals(fileName)) {
            return;
        }
        configuration.set("profile-menu.ally-action.slot", 21);
        configuration.set("profile-menu.war-action.slot", 23);
        configuration.set("war-duration-menu.duration-slots", List.of(11, 13, 15));
        configuration.set("battlepass-menu.levels-per-page", 20);
        configuration.set(
                "battlepass-menu.level-slots",
                List.of(
                        37, 38, 29, 20, 11,
                        12, 13, 22, 31, 40,
                        41, 42, 33, 24, 15,
                        16, 17, 26, 35, 44
                )
        );
        configuration.set("battlepass-menu.level-locked.material", "RED_STAINED_GLASS_PANE");
        configuration.set("battlepass-menu.level-milestone-locked.material", "RED_CONCRETE");
        configuration.set("battlepass-menu.level-unlocked.material", "GREEN_CONCRETE");
        configuration.set("battlepass-menu.level-claimable.material", "GREEN_CONCRETE");
        configuration.set("battlepass-menu.level-claimed.material", "GREEN_CONCRETE");
        configuration.set("battlepass-menu.level-admin.material", "RED_STAINED_GLASS_PANE");
        configuration.set("battlepass-menu.level-admin-milestone.material", "RED_CONCRETE");
    }

    static void applyVersionNineNavigation(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"gui.yml".equals(fileName)) {
            return;
        }
        configuration.set("role-permissions-menu.priority.slot", 49);
        configuration.set("role-permissions-menu.priority.material", "COMPARATOR");
        configuration.set(
                "role-permissions-menu.priority.display-name",
                "<gold>Role priority"
        );
        configuration.set(
                "role-permissions-menu.priority.lore",
                List.of(
                        "<gray>Current priority: <white>{priority}",
                        "",
                        "<dark_gray>Higher roles may manage lower roles."
                )
        );
    }

    static void applyVersionTenBattlepassPosition(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"gui.yml".equals(fileName)) {
            return;
        }
        configuration.set(
                "battlepass-menu.level-slots",
                List.of(
                        36, 37, 28, 19, 10,
                        11, 12, 21, 30, 39,
                        40, 41, 32, 23, 14,
                        15, 16, 25, 34, 43
                )
        );
        configuration.set(
                "battlepass-menu.filler.material",
                "WHITE_STAINED_GLASS_PANE"
        );
        configuration.set("battlepass-menu.filler.display-name", " ");
    }

    static void applyVersionElevenAllyRequests(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"gui.yml".equals(fileName)) {
            return;
        }
        configuration.set("profile-menu.primary-action.slot", 24);
        configuration.set("profile-menu.ally-requests.material", "BELL");
        configuration.set(
                "profile-menu.ally-requests.display-name",
                "<gold>Alliance requests"
        );
        configuration.set(
                "profile-menu.ally-requests.lore",
                List.of("<gray>Shows incoming alliance requests.")
        );
    }

    static void applyVersionTwelveClanListLabel(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"gui.yml".equals(fileName)) {
            return;
        }
        configuration.set(
                "main-menu.browse.display-name",
                "<gold>Clan Liste"
        );
    }

    static void applyVersionThirteenRankings(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("config.yml".equals(fileName)) {
            configuration.set("features.rankings.enabled", true);
        }
        if ("gui.yml".equals(fileName)) {
            configuration.set("main-menu.rankings.slot", 31);
            configuration.set("main-menu.rankings.material", "NETHER_STAR");
            configuration.set(
                    "main-menu.rankings.display-name",
                    "<gold>Rangliste"
            );
            List<String> profileLore = new java.util.ArrayList<>(
                    configuration.getStringList("profile-menu.info.lore")
            );
            if (profileLore.stream().noneMatch(line -> line.contains("{ranking_position}"))) {
                profileLore.add("<gray>Rangliste: <white>{ranking_position}");
                profileLore.add("<gray>Gesamtpunkte: <white>{ranking_points}");
                configuration.set("profile-menu.info.lore", profileLore);
            }
        }
        if ("messages.yml".equals(fileName)) {
            List<String> help = new java.util.ArrayList<>(
                    configuration.getStringList("general.help")
            );
            if (help.stream().noneMatch(line -> line.contains("/clantop"))) {
                help.add("<gold>/clantop</gold> <gray>- Open clan leaderboard");
                configuration.set("general.help", help);
            }
        }
    }

    static void applyVersionFourteenRoleChatInput(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"gui.yml".equals(fileName)) {
            return;
        }
        configuration.set("input-menu.role-name.title", null);
        configuration.set("input-menu.role-name.input", null);
        String cancelKeyword = configuration.getString(
                "input-menu.role-name.chat.cancel-keyword",
                ""
        );
        if (cancelKeyword == null || cancelKeyword.isBlank()) {
            configuration.set(
                    "input-menu.role-name.chat.cancel-keyword",
                    "abbrechen"
            );
        }
    }

    static void applyVersionFifteenVault(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("config.yml".equals(fileName)) {
            configuration.set("features.item-vault.enabled", true);
        }
        if ("vault.yml".equals(fileName)) {
            configuration.set("general.enabled", true);
        }
        if ("gui.yml".equals(fileName)) {
            configuration.set(
                    "profile-menu.vault.display-name",
                    "<light_purple>Clan-Vault"
            );
        }
    }

    static void applyVersionSixteenBankAndPriorities(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("config.yml".equals(fileName)) {
            configuration.set("features.bank.enabled", true);
        }
        if ("economy.yml".equals(fileName)) {
            configuration.set("clan-bank.enabled", true);
            configuration.set("currency.display-name", "AshenCoins");
            configuration.set("formatting.use-provider-format", null);
        }
        if ("vault.yml".equals(fileName)) {
            configuration.set("bank-preview", null);
        }
        if ("gui.yml".equals(fileName)) {
            configuration.set("bank-menu.status", null);
            configuration.set("bank-menu.size", 54);
            configuration.set("bank-menu.back.slot", 45);
            configuration.set("bank-menu.close.slot", 53);
            configuration.set(
                    "role-permissions-menu.priority.lore",
                    List.of(
                            "<gray>Current priority: <white>{priority}",
                            "",
                            "<dark_gray>Higher roles may manage lower roles.",
                            "<yellow>Click to enter a value from 0 to 100."
                    )
            );
        }
        if ("ranks.yml".equals(fileName)) {
            List<String> coOwner = configuration.getStringList(
                    "standard-ranks.co-owner.permissions"
            );
            if (!coOwner.contains("bank.log")) {
                coOwner.add("bank.log");
                configuration.set("standard-ranks.co-owner.permissions", coOwner);
            }
        }
    }

    static void applyVersionSeventeenClanLifecycle(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("config.yml".equals(fileName)) {
            configuration.set("security.prevent-owner-leaving", false);
        }
    }

    static void applyVersionEighteenEssentialsX(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"integrations.yml".equals(fileName)) {
            return;
        }
        Map.of(
                "essentialsx.enabled", true,
                "essentialsx.required", false,
                "essentialsx.plugin-name", "Essentials",
                "essentialsx.validate-economy-provider", true,
                "essentialsx.provider-mismatch-is-error", false
        ).forEach((path, value) -> {
            if (!configuration.contains(path)) {
                configuration.set(path, value);
            }
        });
    }

    static void applyVersionNineteenCreateCommandHelp(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"messages.yml".equals(fileName)
                || configuration.isList("general.create-command-help")) {
            return;
        }
        configuration.set("general.create-command-help", List.of(
                "<gold>Clan erstellen",
                "<gray>Enter the complete command in chat:",
                "<yellow>{usage}",
                "<gray>Order: tag first, complete clan name second.",
                "<gray>Tag: {tag_min} bis {tag_max} visible letters or numbers; MiniMessage colors go directly before the tag.",
                "<gray>Clan name: at most {name_max} characters and spaces are allowed.",
                "<gray>Example without color: <white>{example}",
                "<gray>Example with RGB: <white>{rgb_example}"
        ));
    }

    static void applyVersionTwentyClanEditing(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("permissions.yml".equals(fileName)
                && !configuration.contains("clan-rights.name-change")) {
            configuration.set("clan-rights.name-change", "name.change");
        }
        if ("messages.yml".equals(fileName)
                && !configuration.isString("general.name-changed")) {
            configuration.set(
                    "general.name-changed",
                    "<green>The clan name is now <white>{clan}</white>."
            );
        }
        if (!"gui.yml".equals(fileName)) {
            return;
        }
        Map.of(
                "profile-menu.edit.slot", 15,
                "clan-edit-menu.size", 27,
                "clan-edit-menu.info.slot", 13,
                "clan-edit-menu.name.slot", 11,
                "clan-edit-menu.tag.slot", 15,
                "clan-edit-menu.back.slot", 18,
                "clan-edit-menu.close.slot", 26
        ).forEach((path, value) -> {
            if (!configuration.contains(path)) {
                configuration.set(path, value);
            }
        });
        if (!configuration.contains("permission-labels.name-change")) {
            configuration.set("permission-labels.name-change", "Change clan name");
        }
        configuration.set(
                "role-permissions-menu.permission-slots",
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 30, 32)
        );
        configuration.set(
                "member-permissions-menu.permission-slots",
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 30, 32)
        );
        if (!configuration.contains("permission-labels.bank-log")) {
            configuration.set("permission-labels.bank-log", "Clanbank-Log ansehen");
        }
    }

    static void applyVersionTwentyOneChatOnlyCreate(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"gui.yml".equals(fileName)) {
            return;
        }
        configuration.set("input-menu.create-tag", null);
        configuration.set("input-menu.create-name", null);
        configuration.set("confirmation-menu.actions.create", null);
        if (!configuration.isList("main-menu.create.lore")) {
            configuration.set(
                    "main-menu.create.lore",
                    List.of("<gray>Shows the complete chat command.")
            );
        }
    }

    static void applyVersionTwentyTwoClanEditPosition(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("gui.yml".equals(fileName)) {
            configuration.set("profile-menu.edit.slot", 40);
        }
    }

    static void applyVersionTwentyThreeGuiStyleAndUnlimitedBank(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("gui.yml".equals(fileName)) {
            configuration.set("main-menu.title", "<dark_gray>Clan <gold>Menu");
            configuration.set(
                    "bank-menu.overview.lore",
                    configuration.getStringList("bank-menu.overview.lore").stream()
                            .filter(line -> !line.contains("{maximum_balance}"))
                            .toList()
            );
        }
        if ("economy.yml".equals(fileName)) {
            configuration.set("clan-bank.maximum-balance", null);
        }
        if ("messages.yml".equals(fileName)) {
            configuration.set("errors.bank-balance-limit", null);
        }
    }

    static void applyVersionTwentyFourMenuText(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"gui.yml".equals(fileName)) {
            return;
        }
        for (String path : configuration.getKeys(true).stream().toList()) {
            Object value = configuration.get(path);
            if (value instanceof String text) {
                configuration.set(path, MenuTextNormalizer.normalize(text));
            } else if (value instanceof List<?> values) {
                configuration.set(
                        path,
                        values.stream()
                                .map(entry -> entry instanceof String text
                                        ? MenuTextNormalizer.normalize(text)
                                        : entry)
                                .toList()
                );
            }
        }
    }

    static void applyVersionTwentyFiveClanHomes(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("config.yml".equals(fileName)) {
            configuration.set("features.clan-homes.enabled", true);
        }
        if ("gui.yml".equals(fileName)) {
            configuration.set("profile-menu.homes.slot", 32);
            configuration.set("home-menu.extensions.slot", 4);
            List<Integer> permissionSlots = List.of(
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34
            );
            configuration.set("role-permissions-menu.permission-slots", permissionSlots);
            configuration.set("member-permissions-menu.permission-slots", permissionSlots);
        }
        if ("ranks.yml".equals(fileName)) {
            appendPermissions(
                    configuration,
                    "standard-ranks.co-owner.permissions",
                    List.of("home.view", "home.teleport", "home.set", "home.delete")
            );
            for (String rank : List.of("moderator", "member", "recruit")) {
                appendPermissions(
                        configuration,
                        "standard-ranks." + rank + ".permissions",
                        List.of("home.view", "home.teleport")
                );
            }
        }
    }

    static void applyVersionTwentySixClanEditPosition(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("gui.yml".equals(fileName)) {
            configuration.set("profile-menu.edit.slot", 30);
        }
    }

    static void applyVersionTwentySevenClanEditChatInput(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("gui.yml".equals(fileName)) {
            configuration.set("input-menu.edit-tag", null);
            configuration.set("input-menu.edit-name", null);
            configuration.set("input-menu.result", null);
            if (!configuration.isString("input-menu.clan-edit.chat.cancel-keyword")) {
                configuration.set(
                        "input-menu.clan-edit.chat.cancel-keyword",
                        "abbrechen"
                );
            }
        }
        if ("messages.yml".equals(fileName)) {
            Map.of(
                    "general.clan-name-edit-prompt",
                    "<gold>Schreibe den neuen Clannamen in den Chat.</gold> "
                            + "<gray>Maximum: {max} characters. Enter <yellow>{cancel}</yellow> "
                            + "to cancel.",
                    "general.clan-tag-edit-prompt",
                    "<gold>Schreibe den neuen formatierten Clan-Tag in den Chat.</gold> "
                            + "<gray>Sichtbar {min} bis {max} Zeichen, Format maximal "
                            + "{format_max} characters. Enter <yellow>{cancel}</yellow> to cancel.",
                    "general.clan-edit-input-cancelled",
                    "<yellow>Clan editing was cancelled."
            ).forEach((path, value) -> {
                if (!configuration.isString(path)) {
                    configuration.set(path, value);
                }
            });
        }
    }

    static void applyVersionTwentyEightPlayerDisplay(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"integrations.yml".equals(fileName)) {
            return;
        }
        Map.ofEntries(
                Map.entry("tab.enabled", true),
                Map.entry("tab.required", false),
                Map.entry("tab.plugin-name", "TAB"),
                Map.entry("player-display.enabled", true),
                Map.entry("player-display.format.prefix", " <dark_gray>[</dark_gray>"),
                Map.entry("player-display.format.suffix", "<dark_gray>]</dark_gray>"),
                Map.entry("player-display.format.small-caps", true),
                Map.entry("player-display.chat.enabled", true),
                Map.entry("player-display.nametag.enabled", true),
                Map.entry("player-display.scoreboard.enabled", true),
                Map.entry("player-display.tablist.enabled", true),
                Map.entry("player-display.tab.prefer-api", true),
                Map.entry(
                        "player-display.vanilla-scoreboard.preserve-foreign-teams",
                        true
                ),
                Map.entry("player-display.luckperms.read-only-metadata", true)
        ).forEach((path, value) -> {
            if (!configuration.contains(path)) {
                configuration.set(path, value);
            }
        });
    }

    static void applyVersionTwentyNineInteractiveChat(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"integrations.yml".equals(fileName)) {
            return;
        }
        configuration.set("tab", null);
        configuration.set("player-display", null);
        configuration.set("interactivechat.enabled", true);
        configuration.set("interactivechat.required", false);
        configuration.set("interactivechat.plugin-name", "InteractiveChat");
    }

    static void applyVersionThirtyBattlepassFiller(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("gui.yml".equals(fileName)) {
            configuration.set(
                    "battlepass-menu.filler.material",
                    "GRAY_STAINED_GLASS_PANE"
            );
        }
    }

    static void applyVersionThirtyOneEzEconomy(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"integrations.yml".equals(fileName)) {
            return;
        }
        configuration.set("essentialsx", null);
        Map.of(
                "ezeconomy.enabled", true,
                "ezeconomy.required", false,
                "ezeconomy.plugin-name", "EzEconomy",
                "ezeconomy.validate-economy-provider", true,
                "ezeconomy.provider-mismatch-is-error", false
        ).forEach((path, value) -> {
            if (!configuration.contains(path)) {
                configuration.set(path, value);
            }
        });
    }

    static void applyVersionThirtyTwoAdminWarEnd(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"messages.yml".equals(fileName)) {
            return;
        }
        Map.of(
                "errors.war-not-active",
                "<yellow>There is no active clan war between {clan_one} and {clan_two}.",
                "general.war-ended-by-admin",
                "<yellow>The war between {clan_one} and {clan_two} was "
                        + "ended neutrally by an administrator.",
                "admin.war-ended",
                "<green>The war between {clan_one} and {clan_two} ended neutrally."
        ).forEach((path, value) -> {
            if (!configuration.isString(path)) {
                configuration.set(path, value);
            }
        });
    }

    static void applyVersionThirtyThreeScoredAdminWarEnd(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"messages.yml".equals(fileName)) {
            return;
        }
        migrateAdminWarResultMessage(
                configuration,
                "general.war-ended-by-admin",
                List.of(
                        "<yellow>Der Krieg zwischen {clan_one} und {clan_two} wurde von der "
                                + "Administration neutral beendet.",
                        "<yellow>The war between {clan_one} and {clan_two} was "
                                + "ended neutrally by an administrator."
                ),
                "<yellow>The war between {clan_one} and {clan_two} was scored and ended "
                        + "by an administrator.</yellow> <white>{result}</white> <gray>Deaths: "
                        + "{clan_one} {clan_one_deaths}, {clan_two} {clan_two_deaths}."
        );
        migrateAdminWarResultMessage(
                configuration,
                "admin.war-ended",
                List.of(
                        "<green>Der Krieg zwischen {clan_one} und {clan_two} wurde neutral beendet.",
                        "<green>The war between {clan_one} and {clan_two} ended neutrally."
                ),
                "<green>The war between {clan_one} and {clan_two} was scored and ended.</green> "
                        + "<white>{result}</white> <gray>Deaths: {clan_one} {clan_one_deaths}, "
                        + "{clan_two} {clan_two_deaths}."
        );
    }

    private static void migrateAdminWarResultMessage(
            YamlConfiguration configuration,
            String path,
            List<String> oldDefaults,
            String replacement
    ) {
        if (oldDefaults.contains(configuration.getString(path))) {
            configuration.set(path, replacement);
        }
    }

    static void applyVersionThirtyFourBackupRetention(
            String fileName,
            YamlConfiguration configuration
    ) {
        if ("storage.yml".equals(fileName)
                && !configuration.contains("backups.config-migrations.max-snapshots")) {
            configuration.set("backups.config-migrations.max-snapshots", 2);
        }
    }

    static void applyVersionThirtyFiveBranding(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"config.yml".equals(fileName)) {
            return;
        }
        Map.of(
                "branding.server-name", "{{SERVER_NAME}}",
                "branding.author-name", "CatgirlYannick",
                "branding.plugin-name", "CatClans"
        ).forEach((path, value) -> {
            if (!configuration.contains(path)) {
                configuration.set(path, value);
            }
        });
    }

    static void applyVersionThirtySixBrandingPlaceholders(
            String fileName,
            YamlConfiguration configuration
    ) {
        if (!"placeholders.yml".equals(fileName)) {
            return;
        }
        for (String key : List.of("server-name", "plugin-name", "author-name")) {
            String path = "enabled-placeholders." + key;
            if (!configuration.contains(path)) {
                configuration.set(path, true);
            }
        }
    }

    private static void appendPermissions(
            YamlConfiguration configuration,
            String path,
            List<String> additions
    ) {
        List<String> permissions = new java.util.ArrayList<>(
                configuration.getStringList(path)
        );
        additions.stream()
                .filter(permission -> !permissions.contains(permission))
                .forEach(permissions::add);
        configuration.set(path, permissions);
    }
}
