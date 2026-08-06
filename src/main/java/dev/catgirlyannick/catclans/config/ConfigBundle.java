package dev.catgirlyannick.catclans.config;

import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankId;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.math.RoundingMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ConfigBundle {

    private static final int CONFIG_VERSION = 36;
    private static final List<String> FILES = List.of(
            "config.yml",
            "messages.yml",
            "gui.yml",
            "permissions.yml",
            "ranks.yml",
            "storage.yml",
            "integrations.yml",
            "economy.yml",
            "placeholders.yml",
            "performance.yml",
            "battlepass.yml",
            "vault.yml",
            "diplomacy.yml",
            "rankings.yml",
            "homes.yml"
    );

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> configurations = new java.util.HashMap<>();

    public ConfigBundle(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Plugin data folder could not be created");
        }

        configurations.clear();
        Map<String, File> files = new java.util.LinkedHashMap<>();
        for (String fileName : FILES) {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                plugin.saveResource(fileName, false);
            }
            files.put(fileName, file);
        }

        files.forEach((fileName, file) -> {
            int version = YamlConfiguration.loadConfiguration(file)
                    .getInt("config-version", -1);
            if (version < 1 || version > CONFIG_VERSION) {
                throw new IllegalStateException(fileName + ": configuration version is " + version
                        + "; supported range is 1 to " + CONFIG_VERSION);
            }
        });

        ConfigMigrationService migrationService = new ConfigMigrationService(plugin);
        files.forEach((fileName, file) -> {
            migrationService.migrateIfSupported(fileName, file, CONFIG_VERSION);
            configurations.put(fileName, YamlConfiguration.loadConfiguration(file));
        });
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        for (String fileName : FILES) {
            int version = get(fileName).getInt("config-version", -1);
            if (version != CONFIG_VERSION) {
                errors.add(fileName + ": configuration version is " + version
                        + ", expected " + CONFIG_VERSION);
            }
        }

        YamlConfiguration main = main();
        validateRequiredText(errors, "config.yml: branding.server-name",
                main.getString("branding.server-name", ""), 64);
        validateRequiredText(errors, "config.yml: branding.author-name",
                main.getString("branding.author-name", ""), 64);
        validateRequiredText(errors, "config.yml: branding.plugin-name",
                main.getString("branding.plugin-name", ""), 64);
        int backupSnapshots = storage().getInt(
                "backups.config-migrations.max-snapshots",
                -1
        );
        if (backupSnapshots < 1 || backupSnapshots > 2) {
            errors.add("storage.yml: backups.config-migrations.max-snapshots must be "
                    + "between 1 and 2");
        }
        int nameMin = main.getInt("clans.names.min-length", -1);
        int nameMax = main.getInt("clans.names.max-length", -1);
        int tagMin = main.getInt("clans.tags.min-length", -1);
        int tagMax = main.getInt("clans.tags.max-length", -1);
        int tagFormatMax = main.getInt("clans.tags.maximum-format-length", -1);
        int defaultMembers = main.getInt("clans.members.default-max-members", -1);
        int absoluteMembers = main.getInt("clans.members.absolute-max-members", -1);
        int defaultRoles = main.getInt("clans.roles.default-max-roles", -1);
        int absoluteRoles = main.getInt("clans.roles.absolute-max-roles", -1);
        int roleNameMax = main.getInt("clans.roles.name-max-length", -1);
        int inviteHours = main.getInt("clans.join.invite-expiration-hours", -1);

        validateRange(errors, "config.yml: clans.names", nameMin, nameMax, 1, 64);
        validateRange(errors, "config.yml: clans.tags", tagMin, tagMax, 1, 16);
        if (tagFormatMax < tagMax || tagFormatMax > 4096) {
            errors.add("config.yml: clans.tags.maximum-format-length must be at least "
                    + "max-length and at most 4096 sein");
        }
        if (defaultMembers < 1 || absoluteMembers < defaultMembers) {
            errors.add("config.yml: Mitgliederlimits must be positive and absolute-max-members "
                    + "must be at least default-max-members entsprechen");
        }
        if (defaultRoles < RankId.values().length
                || absoluteRoles < defaultRoles
                || absoluteRoles > 10) {
            errors.add("config.yml: Role limits must start at five or more, "
                    + "be ordered, and absolute-max-roles must not exceed 10");
        }
        if (roleNameMax < 1 || roleNameMax > 64) {
            errors.add("config.yml: clans.roles.name-max-length must be between 1 and 64");
        }
        if (inviteHours < 1 || inviteHours > 24 * 365) {
            errors.add("config.yml: clans.join.invite-expiration-hours must be between 1 and 8760");
        }
        for (String feature : List.of(
                "claim-hook",
                "admin-config-gui"
        )) {
            if (main.getBoolean("features." + feature + ".enabled", false)) {
                errors.add("config.yml: features." + feature
                        + " is not implemented and must remain disabled");
            }
        }
        validatePattern(errors, "config.yml: clans.names.allowed-pattern",
                main.getString("clans.names.allowed-pattern", ""));
        validatePattern(errors, "config.yml: clans.tags.allowed-pattern",
                main.getString("clans.tags.allowed-pattern", ""));
        validatePattern(errors, "config.yml: clans.roles.allowed-pattern",
                main.getString("clans.roles.allowed-pattern", ""));
        try {
            JoinMode.valueOf(main.getString("clans.join.default-mode", ""));
        } catch (IllegalArgumentException exception) {
            errors.add("config.yml: clans.join.default-mode must be INVITE_ONLY or OPEN");
        }

        validateGui(errors, defaultMembers);
        validateRanks(errors);
        validateStorage(errors);
        validateEconomy(errors);
        validateIntegrations(errors);
        validatePlaceholders(errors);
        validatePerformance(errors, defaultMembers);
        validateMessages(errors);
        validateBattlepass(errors, absoluteMembers, absoluteRoles);
        validateVault(errors);
        validateDiplomacy(errors);
        validateRankings(errors);
        validateHomes(errors);
        return List.copyOf(errors);
    }

    public YamlConfiguration main() {
        return get("config.yml");
    }

    public YamlConfiguration messages() {
        return get("messages.yml");
    }

    public YamlConfiguration gui() {
        return get("gui.yml");
    }

    public YamlConfiguration ranks() {
        return get("ranks.yml");
    }

    public YamlConfiguration permissions() {
        return get("permissions.yml");
    }

    public YamlConfiguration storage() {
        return get("storage.yml");
    }

    public YamlConfiguration integrations() {
        return get("integrations.yml");
    }

    public YamlConfiguration economy() {
        return get("economy.yml");
    }

    public YamlConfiguration placeholders() {
        return get("placeholders.yml");
    }

    public YamlConfiguration performance() {
        return get("performance.yml");
    }

    public YamlConfiguration battlepass() {
        return get("battlepass.yml");
    }

    public YamlConfiguration vault() {
        return get("vault.yml");
    }

    public YamlConfiguration diplomacy() {
        return get("diplomacy.yml");
    }

    public YamlConfiguration rankings() {
        return get("rankings.yml");
    }

    public YamlConfiguration homes() {
        return get("homes.yml");
    }

    public Path resolveDataPath(String configuredPath) {
        Path dataRoot = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path resolved = dataRoot.resolve(configuredPath).normalize();
        if (!resolved.startsWith(dataRoot)) {
            throw new IllegalArgumentException("Path leaves the plugin data folder: " + configuredPath);
        }
        return resolved;
    }

    private YamlConfiguration get(String fileName) {
        YamlConfiguration configuration = configurations.get(fileName);
        if (configuration == null) {
            throw new IllegalStateException("Configuration was not loaded: " + fileName);
        }
        return configuration;
    }

    private void validateGui(List<String> errors, int defaultMembers) {
        YamlConfiguration gui = gui();
        String roleNameCancelKeyword = gui.getString(
                "input-menu.role-name.chat.cancel-keyword",
                ""
        ).trim();
        if (roleNameCancelKeyword.isEmpty()
                || roleNameCancelKeyword.codePointCount(
                0,
                roleNameCancelKeyword.length()
        ) > 32) {
            errors.add("gui.yml: input-menu.role-name.chat.cancel-keyword must be "
                    + "between 1 and 32 characters long");
        }
        String clanEditCancelKeyword = gui.getString(
                "input-menu.clan-edit.chat.cancel-keyword",
                ""
        ).trim();
        if (clanEditCancelKeyword.isEmpty()
                || clanEditCancelKeyword.codePointCount(
                0,
                clanEditCancelKeyword.length()
        ) > 32) {
            errors.add("gui.yml: input-menu.clan-edit.chat.cancel-keyword must be "
                    + "between 1 and 32 characters long");
        }
        validateMenuLayout(errors, gui, "main-menu", List.of(), List.of(
                "profile", "members", "join-mode", "create",
                "browse", "rankings", "invitations", "leave",
                "admin-battlepass", "close"
        ));
        validateMenuLayout(errors, gui, "clan-list-menu", List.of("clan-slots"), List.of(
                "back", "previous", "next", "close"
        ));
        validateMenuLayout(
                errors,
                gui,
                "ranking-menu",
                List.of("entry-slots"),
                List.of(
                        "categories.total",
                        "categories.combat",
                        "categories.members",
                        "categories.money",
                        "categories.wars-won",
                        "categories.wars-lost",
                        "categories.activity",
                        "previous",
                        "info",
                        "next",
                        "back",
                        "close"
                )
        );
        validateMenuLayout(errors, gui, "profile-menu", List.of(), List.of(
                "info", "members", "permissions", "edit", "battlepass", "bank", "homes", "vault",
                "primary-action", "deny", "ally-action", "war-action", "back", "close"
        ));
        validateMenuLayout(errors, gui, "clan-edit-menu", List.of(), List.of(
                "info", "name", "tag", "back", "close"
        ));
        validateMenuLayout(errors, gui, "invites-menu", List.of("invite-slots"), List.of(
                "back", "close"
        ));
        validateMenuLayout(errors, gui, "confirmation-menu", List.of(), List.of(
                "confirm", "cancel"
        ));
        validateMenuLayout(errors, gui, "war-duration-menu", List.of("duration-slots"), List.of(
                "back", "close"
        ));
        validateMenuLayout(errors, gui, "diplomacy-response-menu", List.of(), List.of(
                "accept", "deny", "back", "close"
        ));
        validateMenuLayout(errors, gui, "ally-requests-menu", List.of("request-slots"), List.of(
                "previous", "next", "back", "close"
        ));
        validateMenuLayout(errors, gui, "permission-home-menu", List.of(), List.of(
                "members", "roles", "back", "close"
        ));
        validateMenuLayout(errors, gui, "home-menu", List.of("home-slots"), List.of(
                "info", "extensions", "previous", "next", "back", "close"
        ));
        validateMenuLayout(errors, gui, "home-confirmation-menu", List.of(), List.of(
                "subject", "confirm", "cancel"
        ));
        validateMenuLayout(errors, gui, "role-list-menu", List.of("role-slots"), List.of(
                "create", "back", "close"
        ));
        validateMenuLayout(
                errors,
                gui,
                "member-permission-list-menu",
                List.of("member-slots"),
                List.of("back", "close")
        );
        validateMenuLayout(
                errors,
                gui,
                "role-permissions-menu",
                List.of("permission-slots"),
                List.of("rename", "move-up", "move-down", "priority", "back", "close")
        );
        validateMenuLayout(
                errors,
                gui,
                "member-permissions-menu",
                List.of("permission-slots"),
                List.of("role", "back", "close")
        );
        validateMenuLayout(
                errors,
                gui,
                "role-assignment-menu",
                List.of("role-slots"),
                List.of("back", "close")
        );
        validateMenuLayout(
                errors,
                gui,
                "battlepass-menu",
                List.of("level-slots"),
                List.of("info", "previous", "next", "back", "close")
        );
        if (gui.getInt("battlepass-menu.levels-per-page", -1)
                != gui.getIntegerList("battlepass-menu.level-slots").size()) {
            errors.add("gui.yml: battlepass-menu.levels-per-page must exactly match the "
                    + "Anzahl unter level-slots entsprechen");
        }
        validateMenuLayout(
                errors,
                gui,
                "battlepass-reward-editor-menu",
                List.of(),
                List.of("instruction", "back", "close")
        );
        validateMenuLayout(
                errors,
                gui,
                "bank-menu",
                List.of(),
                List.of(
                        "overview", "deposit", "withdraw", "log", "permissions",
                        "back", "close"
                )
        );
        validateMenuLayout(
                errors,
                gui,
                "bank-amount-menu",
                List.of("quick-amount-slots"),
                List.of("info", "custom", "back", "close")
        );
        validateMenuLayout(
                errors,
                gui,
                "bank-log-members-menu",
                List.of("member-slots"),
                List.of("previous", "info", "next", "back", "close")
        );
        validateMenuLayout(
                errors,
                gui,
                "bank-log-entries-menu",
                List.of("entry-slots"),
                List.of("empty", "info", "back", "close")
        );
        try {
            DateTimeFormatter.ofPattern(gui.getString(
                    "bank-log-entries-menu.date-time-pattern",
                    ""
            ));
        } catch (RuntimeException exception) {
            errors.add("gui.yml: bank-log-entries-menu.date-time-pattern is invalid");
        }
        validateMenuLayout(
                errors,
                gui,
                "vault-menu",
                List.of(),
                List.of(
                        "previous", "log", "info", "extensions", "next",
                        "back", "close"
                )
        );
        validateMenuLayout(
                errors,
                gui,
                "vault-log-members-menu",
                List.of("member-slots"),
                List.of("previous", "info", "next", "back", "close")
        );
        validateMenuLayout(
                errors,
                gui,
                "vault-log-entries-menu",
                List.of("entry-slots"),
                List.of("empty", "info", "back", "close")
        );
        try {
            DateTimeFormatter.ofPattern(gui.getString(
                    "vault-log-entries-menu.date-time-pattern",
                    ""
            ));
        } catch (RuntimeException exception) {
            errors.add("gui.yml: vault-log-entries-menu.date-time-pattern is invalid");
        }
        Set<Integer> rewardSlots = new HashSet<>();
        for (String type : List.of(
                "member-slots",
                "home-slots",
                "vault-pages",
                "role-slots"
        )) {
            int slot = gui.getInt(
                    "battlepass-reward-editor-menu.reward-slots." + type,
                    -1
            );
            if (slot < 0 || slot >= gui.getInt(
                    "battlepass-reward-editor-menu.size",
                    -1
            ) || !rewardSlots.add(slot)) {
                errors.add("gui.yml: invalid or duplicate reward editor slot "
                        + type + "=" + slot);
            }
        }
        int vaultStorageSlots = gui.getInt("vault-menu.storage-slots", -1);
        if (vaultStorageSlots < 1 || vaultStorageSlots > 45) {
            errors.add("gui.yml: vault-menu.storage-slots must be between 1 and 45");
        }

        int confirmationSize = gui.getInt("confirmation-menu.size", -1);
        int subjectSlot = gui.getInt("confirmation-menu.subject-slot", -1);
        if (subjectSlot < 0 || subjectSlot >= confirmationSize) {
            errors.add("gui.yml: confirmation-menu.subject-slot is outside the menu size");
        }

        int size = gui.getInt("members-menu.size", -1);
        if (size < 9 || size > 54 || size % 9 != 0) {
            errors.add("gui.yml: members-menu.size must be a multiple of 9 between 9 and 54");
            return;
        }

        List<Integer> slots = gui.getIntegerList("members-menu.member-slots");
        if (slots.size() < defaultMembers) {
            errors.add("gui.yml: members-menu.member-slots requires at least "
                    + defaultMembers + " eindeutige Slots");
        }
        Set<Integer> unique = new HashSet<>();
        for (int slot : slots) {
            if (slot < 0 || slot >= size) {
                errors.add("gui.yml: invalid member slot " + slot + " for menu size " + size);
            }
            if (!unique.add(slot)) {
                errors.add("gui.yml: duplicate member slot " + slot);
            }
        }
        for (String controlPath : List.of(
                "members-menu.info.slot",
                "members-menu.back.slot",
                "members-menu.close.slot"
        )) {
            int slot = gui.getInt(controlPath, -1);
            if (slot < 0 || slot >= size) {
                errors.add("gui.yml: " + controlPath + " is outside the menu size " + size);
            } else if (!unique.add(slot)) {
                errors.add("gui.yml: " + controlPath + " collides with another slot: " + slot);
            }
        }

        for (String path : List.of(
                "common.filler.material",
                "common.frame.material",
                "main-menu.profile.material",
                "main-menu.members.material",
                "main-menu.join-mode.material",
                "main-menu.create.material",
                "main-menu.browse.material",
                "main-menu.rankings.material",
                "main-menu.invitations.material",
                "main-menu.leave.material",
                "main-menu.admin-battlepass.material",
                "main-menu.close.material",
                "clan-list-menu.clan.material",
                "clan-list-menu.back.material",
                "clan-list-menu.previous.material",
                "clan-list-menu.next.material",
                "clan-list-menu.close.material",
                "ranking-menu.categories.total.material",
                "ranking-menu.categories.combat.material",
                "ranking-menu.categories.members.material",
                "ranking-menu.categories.money.material",
                "ranking-menu.categories.wars-won.material",
                "ranking-menu.categories.wars-lost.material",
                "ranking-menu.categories.activity.material",
                "ranking-menu.entry.material",
                "ranking-menu.money-disabled.material",
                "ranking-menu.previous.material",
                "ranking-menu.info.material",
                "ranking-menu.next.material",
                "ranking-menu.back.material",
                "ranking-menu.close.material",
                "profile-menu.info.material",
                "profile-menu.members.material",
                "profile-menu.permissions.material",
                "profile-menu.edit.material",
                "profile-menu.battlepass.material",
                "profile-menu.bank.material",
                "profile-menu.homes.material",
                "profile-menu.vault.material",
                "profile-menu.ally-requests.material",
                "profile-menu.join.material",
                "profile-menu.accept.material",
                "profile-menu.deny.material",
                "profile-menu.back.material",
                "profile-menu.close.material",
                "clan-edit-menu.info.material",
                "clan-edit-menu.name.material",
                "clan-edit-menu.tag.material",
                "clan-edit-menu.back.material",
                "clan-edit-menu.close.material",
                "invites-menu.invite.material",
                "invites-menu.empty.material",
                "invites-menu.back.material",
                "invites-menu.close.material",
                "confirmation-menu.confirm.material",
                "confirmation-menu.cancel.material",
                "members-menu.filler.material",
                "members-menu.locked-slot.material",
                "members-menu.back.material",
                "members-menu.close.material",
                "permission-home-menu.members.material",
                "permission-home-menu.roles.material",
                "permission-home-menu.back.material",
                "permission-home-menu.close.material",
                "home-menu.home.material",
                "home-menu.empty.material",
                "home-menu.info.material",
                "home-menu.extensions.material",
                "home-menu.previous.material",
                "home-menu.next.material",
                "home-menu.back.material",
                "home-menu.close.material",
                "home-confirmation-menu.subject.material",
                "home-confirmation-menu.confirm.material",
                "home-confirmation-menu.cancel.material",
                "role-list-menu.role.material",
                "role-list-menu.create.material",
                "role-list-menu.locked.material",
                "role-list-menu.back.material",
                "role-list-menu.close.material",
                "member-permission-list-menu.back.material",
                "member-permission-list-menu.close.material",
                "role-permissions-menu.rename.material",
                "role-permissions-menu.move-up.material",
                "role-permissions-menu.move-down.material",
                "role-permissions-menu.priority.material",
                "role-permissions-menu.back.material",
                "role-permissions-menu.close.material",
                "member-permissions-menu.role.material",
                "member-permissions-menu.back.material",
                "member-permissions-menu.close.material",
                "role-assignment-menu.role.material",
                "role-assignment-menu.back.material",
                "role-assignment-menu.close.material",
                "permission-items.inherit.material",
                "permission-items.allow.material",
                "permission-items.deny.material"
                ,"battlepass-menu.info.material"
                ,"battlepass-menu.filler.material"
                ,"battlepass-menu.level-locked.material"
                ,"battlepass-menu.level-unlocked.material"
                ,"battlepass-menu.level-claimable.material"
                ,"battlepass-menu.level-claimed.material"
                ,"battlepass-menu.level-admin.material"
                ,"battlepass-menu.previous.material"
                ,"battlepass-menu.next.material"
                ,"battlepass-menu.back.material"
                ,"battlepass-menu.close.material"
                ,"battlepass-reward-editor-menu.instruction.material"
                ,"battlepass-reward-editor-menu.back.material"
                ,"battlepass-reward-editor-menu.close.material"
                ,"bank-menu.overview.material"
                ,"bank-menu.deposit.material"
                ,"bank-menu.withdraw.material"
                ,"bank-menu.log.material"
                ,"bank-menu.permissions.material"
                ,"bank-menu.back.material"
                ,"bank-menu.close.material"
                ,"bank-amount-menu.quick-amount.material"
                ,"bank-amount-menu.info.material"
                ,"bank-amount-menu.custom.material"
                ,"bank-amount-menu.back.material"
                ,"bank-amount-menu.close.material"
                ,"bank-log-members-menu.member.material"
                ,"bank-log-members-menu.previous.material"
                ,"bank-log-members-menu.info.material"
                ,"bank-log-members-menu.next.material"
                ,"bank-log-members-menu.back.material"
                ,"bank-log-members-menu.close.material"
                ,"bank-log-entries-menu.entry.material"
                ,"bank-log-entries-menu.empty.material"
                ,"bank-log-entries-menu.info.material"
                ,"bank-log-entries-menu.back.material"
                ,"bank-log-entries-menu.close.material"
                ,"vault-menu.previous.material"
                ,"vault-menu.log.material"
                ,"vault-menu.info.material"
                ,"vault-menu.extensions.material"
                ,"vault-menu.next.material"
                ,"vault-menu.back.material"
                ,"vault-menu.close.material"
                ,"vault-log-members-menu.member.material"
                ,"vault-log-members-menu.previous.material"
                ,"vault-log-members-menu.info.material"
                ,"vault-log-members-menu.next.material"
                ,"vault-log-members-menu.back.material"
                ,"vault-log-members-menu.close.material"
                ,"vault-log-entries-menu.entry.material"
                ,"vault-log-entries-menu.empty.material"
                ,"vault-log-entries-menu.info.material"
                ,"vault-log-entries-menu.back.material"
                ,"vault-log-entries-menu.close.material"
                ,"ally-requests-menu.request.material"
                ,"ally-requests-menu.empty.material"
                ,"ally-requests-menu.previous.material"
                ,"ally-requests-menu.next.material"
                ,"ally-requests-menu.back.material"
                ,"ally-requests-menu.close.material"
        )) {
            String value = gui.getString(path, "");
            if (Material.matchMaterial(value) == null) {
                errors.add("gui.yml: " + path + " contains invalid material " + value);
            }
        }
        for (String action : List.of(
                "invite", "kick", "join", "accept", "deny", "leave",
                "join-mode-open", "join-mode-invite"
        )) {
            String path = "confirmation-menu.actions." + action + ".material";
            String value = gui.getString(path, "");
            if (Material.matchMaterial(value) == null) {
                errors.add("gui.yml: " + path + " contains invalid material " + value);
            }
        }
        if (!"PLAYER_HEAD".equalsIgnoreCase(gui.getString("members-menu.info.material", ""))) {
            errors.add("gui.yml: members-menu.info.material must remain PLAYER_HEAD, "
                    + "because the owner head is the configured clan symbol");
        }
        try {
            DateTimeFormatter.ofPattern(gui.getString("members-menu.date-time-pattern", ""));
        } catch (IllegalArgumentException exception) {
            errors.add("gui.yml: members-menu.date-time-pattern is not a valid date format");
        }
    }

    private static void validateMenuLayout(
            List<String> errors,
            YamlConfiguration gui,
            String menuPath,
            List<String> slotListPaths,
            List<String> controlPaths
    ) {
        int size = gui.getInt(menuPath + ".size", -1);
        if (size < 9 || size > 54 || size % 9 != 0) {
            errors.add("gui.yml: " + menuPath
                    + ".size must be a multiple of 9 between 9 and 54");
            return;
        }
        Set<Integer> occupied = new HashSet<>();
        for (String listPath : slotListPaths) {
            List<Integer> slots = gui.getIntegerList(menuPath + "." + listPath);
            if (slots.isEmpty()) {
                errors.add("gui.yml: " + menuPath + "." + listPath + " must not be empty");
            }
            for (int slot : slots) {
                validateGuiSlot(errors, occupied, menuPath + "." + listPath, size, slot);
            }
        }
        for (String controlPath : controlPaths) {
            int slot = gui.getInt(menuPath + "." + controlPath + ".slot", -1);
            validateGuiSlot(errors, occupied, menuPath + "." + controlPath, size, slot);
        }
    }

    private static void validateGuiSlot(
            List<String> errors,
            Set<Integer> occupied,
            String path,
            int size,
            int slot
    ) {
        if (slot < 0 || slot >= size) {
            errors.add("gui.yml: " + path + " contains invalid slot " + slot);
        } else if (!occupied.add(slot)) {
            errors.add("gui.yml: " + path + " kollidiert auf Slot " + slot);
        }
    }

    private void validateStorage(List<String> errors) {
        YamlConfiguration storage = storage();
        String databaseType = storage.getString("database.type", "").toLowerCase();
        if (!Set.of("sqlite", "mysql").contains(databaseType)) {
            errors.add("storage.yml: database.type must be sqlite or mysql");
        }
        if ("mysql".equals(databaseType)) {
            int port = storage.getInt("database.mysql.port", -1);
            if (port < 1 || port > 65535) {
                errors.add("storage.yml: database.mysql.port must be between 1 and 65535");
            }
            for (String path : List.of(
                    "database.mysql.host",
                    "database.mysql.database"
            )) {
                if (storage.getString(path, "").isBlank()) {
                    errors.add("storage.yml: " + path + " must not be empty");
                }
            }
            if (!storage.getString("database.mysql.host", "")
                    .matches("[A-Za-z0-9.-]+")) {
                errors.add("storage.yml: database.mysql.host contains invalid characters");
            }
            if (!storage.getString("database.mysql.database", "")
                    .matches("[A-Za-z0-9_]+")) {
                errors.add("storage.yml: database.mysql.database may contain only letters, "
                        + "numbers, and underscores");
            }
            for (String path : List.of(
                    "database.mysql.username-environment-variable",
                    "database.mysql.password-environment-variable"
            )) {
                if (!storage.getString(path, "").matches("[A-Z_][A-Z0-9_]*")) {
                    errors.add("storage.yml: " + path
                            + " must be a valid environment variable name");
                }
            }
            for (String path : List.of(
                    "database.mysql.connect-timeout-milliseconds",
                    "database.mysql.socket-timeout-milliseconds"
            )) {
                int timeout = storage.getInt(path, -1);
                if (timeout < 100 || timeout > 120_000) {
                    errors.add("storage.yml: " + path
                            + " must be between 100 and 120000");
                }
            }
        }
        int retentionDays = storage.getInt("logging.text-audit.retention-days", -1);
        if (retentionDays < 1 || retentionDays > 3650) {
            errors.add("storage.yml: logging.text-audit.retention-days must be between 1 and 3650");
        }
        int vaultRetentionDays = storage.getInt("logging.vault-audit.retention-days", -1);
        if (vaultRetentionDays < 1 || vaultRetentionDays > 3650) {
            errors.add("storage.yml: logging.vault-audit.retention-days must be between "
                    + "1 and 3650");
        }
        int bankRetentionDays = storage.getInt("logging.bank-audit.retention-days", -1);
        if (bankRetentionDays < 1 || bankRetentionDays > 3650) {
            errors.add("storage.yml: logging.bank-audit.retention-days must be between "
                    + "1 and 3650");
        }
        int busyTimeout = storage.getInt("database.sqlite.busy-timeout-milliseconds", -1);
        if (busyTimeout < 0 || busyTimeout > 120_000) {
            errors.add("storage.yml: database.sqlite.busy-timeout-milliseconds must be between 0 and 120000");
        }
        String synchronousMode = storage.getString("database.sqlite.synchronous-mode", "");
        if (!Set.of("OFF", "NORMAL", "FULL", "EXTRA").contains(synchronousMode)) {
            errors.add("storage.yml: database.sqlite.synchronous-mode must be OFF, NORMAL, "
                    + "FULL or EXTRA");
        }
        int checkpointPages = storage.getInt("database.sqlite.wal-auto-checkpoint-pages", -1);
        if (checkpointPages < 1 || checkpointPages > 1_000_000) {
            errors.add("storage.yml: database.sqlite.wal-auto-checkpoint-pages must be between "
                    + "1 and 1000000");
        }
        for (String path : List.of(
                "logging.text-audit.file-pattern",
                "logging.text-audit.timestamp-pattern",
                "logging.vault-audit.file-pattern",
                "logging.vault-audit.timestamp-pattern",
                "logging.bank-audit.file-pattern",
                "logging.bank-audit.timestamp-pattern"
        )) {
            try {
                DateTimeFormatter.ofPattern(storage.getString(path, ""));
            } catch (IllegalArgumentException exception) {
                errors.add("storage.yml: " + path + " is not a valid date format");
            }
        }
        try {
            resolveDataPath(storage.getString("database.sqlite.file", ""));
            resolveDataPath(storage.getString("logging.text-audit.directory", ""));
            resolveDataPath(storage.getString("logging.vault-audit.directory", ""));
            resolveDataPath(storage.getString("logging.bank-audit.directory", ""));
        } catch (IllegalArgumentException exception) {
            errors.add("storage.yml: " + exception.getMessage());
        }
    }

    private void validateDiplomacy(List<String> errors) {
        YamlConfiguration diplomacy = diplomacy();
        int expiration = diplomacy.getInt("requests.expiration-hours", -1);
        if (expiration < 1 || expiration > 168) {
            errors.add("diplomacy.yml: requests.expiration-hours must be between 1 and 168");
        }
        int maximumPending = diplomacy.getInt("requests.maximum-pending-per-clan", -1);
        if (maximumPending < 1 || maximumPending > 1000) {
            errors.add("diplomacy.yml: requests.maximum-pending-per-clan must be between "
                    + "1 and 1000");
        }
        List<Integer> durations = diplomacy.getIntegerList(
                "wars.allowed-duration-hours"
        );
        if (durations.isEmpty()
                || durations.size() > 3
                || new HashSet<>(durations).size() != durations.size()
                || durations.stream().anyMatch(hours -> hours < 1 || hours > 168)) {
            errors.add("diplomacy.yml: wars.allowed-duration-hours braucht ein bis drei "
                    + "unique values between 1 and 168");
        }
        if (gui().getIntegerList("war-duration-menu.duration-slots").size()
                < durations.size()) {
            errors.add("gui.yml: war-duration-menu.duration-slots requires at least "
                    + "so viele Slots wie erlaubte Kriegszeiten");
        }
    }

    private void validateBattlepass(
            List<String> errors,
            int absoluteMembers,
            int absoluteRoles
    ) {
        YamlConfiguration battlepass = battlepass();
        double baseXp = battlepass.getDouble("progression.base-xp-required", -1);
        double growth = battlepass.getDouble("progression.growth-percent-per-level", -1);
        int scale = battlepass.getInt("progression.decimal-scale", -1);
        if (baseXp <= 0) {
            errors.add("battlepass.yml: progression.base-xp-required must be positive");
        }
        if (growth < 0 || growth > 100) {
            errors.add("battlepass.yml: growth-percent-per-level must be between 0 and 100");
        }
        if (scale < 0 || scale > 8) {
            errors.add("battlepass.yml: progression.decimal-scale must be between 0 and 8");
        }
        try {
            RoundingMode.valueOf(battlepass.getString(
                    "progression.rounding-mode",
                    ""
            ));
        } catch (IllegalArgumentException exception) {
            errors.add("battlepass.yml: progression.rounding-mode is invalid");
        }
        int interval = battlepass.getInt(
                "sources.online-activity.interval-minutes",
                -1
        );
        if (interval < 1 || interval > 24 * 60) {
            errors.add("battlepass.yml: Online-XP-Intervall must be between 1 and 1440 Minuten");
        }
        if (battlepass.getDouble(
                "sources.online-activity.xp-per-online-clan-member",
                -1
        ) < 0) {
            errors.add("battlepass.yml: Online-XP must not be negative");
        }
        if (battlepass.getDouble("sources.daily-login.base-xp", -1) <= 0
                || battlepass.getDouble(
                "sources.daily-login.early-streak-multiplier",
                -1
        ) <= 0) {
            errors.add("battlepass.yml: login XP and early streak multiplier "
                    + "must be positive");
        }
        int lateDay = battlepass.getInt(
                "sources.daily-login.late-growth-starts-at-day",
                -1
        );
        if (lateDay < 3) {
            errors.add("battlepass.yml: late streak growth must start no earlier than day 3");
        }
        try {
            ZoneId.of(battlepass.getString("sources.daily-login.timezone", ""));
        } catch (RuntimeException exception) {
            errors.add("battlepass.yml: sources.daily-login.timezone is invalid");
        }
        if (battlepass.getDouble("sources.pvp.xp-per-kill", -1) < 0) {
            errors.add("battlepass.yml: PvP-Kill-XP must not be negative");
        }
        int cooldown = battlepass.getInt(
                "sources.pvp.repeated-victim-cooldown-minutes",
                -1
        );
        if (cooldown < 0 || cooldown > 24 * 60) {
            errors.add("battlepass.yml: PvP-Cooldown must be between 0 and 1440 Minuten");
        }
        if (absoluteMembers < 1 || absoluteRoles < RankId.values().length) {
            errors.add("battlepass.yml: Clan-Grenzwerte aus config.yml sind inkonsistent");
        }
        for (String type : List.of(
                "member-slots",
                "home-slots",
                "vault-pages",
                "role-slots"
        )) {
            String path = "rewards.supported-types." + type + ".material";
            if (Material.matchMaterial(battlepass.getString(path, "")) == null) {
                errors.add("battlepass.yml: " + path + " contains an invalid material");
            }
        }
    }

    private void validateVault(List<String> errors) {
        YamlConfiguration vault = vault();
        int maximumPages = vault.getInt("general.absolute-max-pages", -1);
        int slots = vault.getInt("general.storage-slots-per-page", -1);
        int maximumItemBytes = vault.getInt("general.max-serialized-item-bytes", -1);
        if (maximumPages < 1 || maximumPages > 64) {
            errors.add("vault.yml: absolute-max-pages must be between 1 and 64");
        }
        if (slots < 1 || slots > 45) {
            errors.add("vault.yml: storage-slots-per-page must be between 1 and 45");
        }
        if (maximumItemBytes < 1024 || maximumItemBytes > 8_388_608) {
            errors.add("vault.yml: max-serialized-item-bytes must be between "
                    + "1024 and 8388608");
        }
    }

    private void validateRankings(List<String> errors) {
        YamlConfiguration rankings = rankings();
        if (!rankings.getBoolean("general.permanent", false)) {
            errors.add("rankings.yml: general.permanent must remain true");
        }
        try {
            ZoneId.of(rankings.getString("activity.timezone", ""));
        } catch (RuntimeException exception) {
            errors.add("rankings.yml: activity.timezone is not a valid time zone");
        }
        int maintenanceSeconds = rankings.getInt(
                "activity.maintenance-interval-seconds",
                -1
        );
        if (maintenanceSeconds < 10 || maintenanceSeconds > 300) {
            errors.add("rankings.yml: maintenance-interval-seconds must be between "
                    + "10 and 300");
        }
        int cooldownMinutes = rankings.getInt(
                "pvp.repeated-victim-cooldown-minutes",
                -1
        );
        if (cooldownMinutes < 0 || cooldownMinutes > 1440) {
            errors.add("rankings.yml: repeated-victim-cooldown-minutes must be between "
                    + "0 and 1440");
        }
        validateNonNegativeDecimal(
                errors,
                rankings,
                "pvp.points-per-valid-kill"
        );
        validateNonNegativeDecimal(
                errors,
                rankings,
                "scoring.points-per-member"
        );
        validateNonNegativeDecimal(
                errors,
                rankings,
                "scoring.points-per-war-win"
        );
        validateNonNegativeDecimal(
                errors,
                rankings,
                "scoring.points-per-active-day"
        );
        java.math.BigDecimal moneyAmount = decimalValue(
                errors,
                rankings,
                "scoring.money-amount-per-point"
        );
        if (moneyAmount != null && moneyAmount.signum() <= 0) {
            errors.add("rankings.yml: money-amount-per-point must be greater than 0");
        }
        java.math.BigDecimal lossPoints = decimalValue(
                errors,
                rankings,
                "scoring.points-per-war-loss"
        );
        if (lossPoints != null && lossPoints.signum() > 0) {
            errors.add("rankings.yml: points-per-war-loss must not be positive");
        }
        java.math.BigDecimal negativeMultiplier = decimalValue(
                errors,
                rankings,
                "scoring.negative-point-multiplier"
        );
        if (negativeMultiplier != null
                && (negativeMultiplier.signum() < 0
                || negativeMultiplier.compareTo(java.math.BigDecimal.ONE) > 0)) {
            errors.add("rankings.yml: negative-point-multiplier must be between 0 and 1");
        }
        if (!"CLAN_TAG_ASC".equals(rankings.getString("sorting.tie-breaker", ""))) {
            errors.add("rankings.yml: sorting.tie-breaker currently supports only CLAN_TAG_ASC");
        }
        if (!rankings.getBoolean("categories.total", false)) {
            errors.add("rankings.yml: The total category must remain enabled");
        }
    }

    private void validateHomes(List<String> errors) {
        YamlConfiguration homes = homes();
        int defaultSlots = homes.getInt("limits.default-slots", -1);
        int absoluteSlots = homes.getInt("limits.absolute-max-slots", -1);
        int bonusLimit = battlepass().getInt(
                "limits.absolute-max-bonus-home-slots",
                -1
        );
        if (defaultSlots < 1 || absoluteSlots < defaultSlots
                || absoluteSlots > 256) {
            errors.add("homes.yml: home limits must be between 1 and 256");
        }
        if (bonusLimit >= 0 && absoluteSlots < defaultSlots + bonusLimit) {
            errors.add("homes.yml: absolute-max-slots must cover the initial slots plus "
                    + "dem maximalen Battlepass-Bonus entsprechen");
        }
        int cooldown = homes.getInt("teleport.cooldown-seconds", -1);
        if (cooldown < 0 || cooldown > 86_400) {
            errors.add("homes.yml: teleport.cooldown-seconds must be between 0 and 86400");
        }
        List<String> unsafe = homes.getStringList("safety.unsafe-materials");
        if (unsafe.isEmpty()) {
            errors.add("homes.yml: safety.unsafe-materials must not be empty");
        }
        for (String material : unsafe) {
            if (Material.matchMaterial(material) == null) {
                errors.add("homes.yml: Invalid safety material: " + material);
            }
        }
    }

    private static void validateNonNegativeDecimal(
            List<String> errors,
            YamlConfiguration configuration,
            String path
    ) {
        java.math.BigDecimal value = decimalValue(errors, configuration, path);
        if (value != null && value.signum() < 0) {
            errors.add("rankings.yml: " + path + " must not be negative");
        }
    }

    private static java.math.BigDecimal decimalValue(
            List<String> errors,
            YamlConfiguration configuration,
            String path
    ) {
        Object value = configuration.get(path);
        if (value == null) {
            errors.add("rankings.yml: decimal value is missing: " + path);
            return null;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            errors.add("rankings.yml: Invalid decimal value at " + path);
            return null;
        }
    }

    private void validateRanks(List<String> errors) {
        var rightsSection = get("permissions.yml").getConfigurationSection("clan-rights");
        if (rightsSection == null) {
            errors.add("permissions.yml: clan-rights section is missing");
            return;
        }
        Set<String> knownRights = new HashSet<>(rightsSection
                .getValues(false)
                .values()
                .stream()
                .map(String::valueOf)
                .toList());
        Set<Integer> priorities = new HashSet<>();
        Integer previousPriority = null;
        for (RankId rank : RankId.values()) {
            String path = "standard-ranks." + rank.configKey();
            if (!ranks().isConfigurationSection(path)) {
                errors.add("ranks.yml: default rank is missing: " + rank.configKey());
                continue;
            }
            int priority = ranks().getInt(path + ".priority", Integer.MIN_VALUE);
            if (!priorities.add(priority)) {
                errors.add("ranks.yml: Duplicate rank priority: " + priority);
            }
            if (previousPriority != null && priority >= previousPriority) {
                errors.add("ranks.yml: Default rank order must remain strictly Owner > Co-Owner "
                        + "> Moderator > Member > Recruit");
            }
            previousPriority = priority;
            List<String> permissions = ranks().getStringList(path + ".permissions");
            for (String permission : permissions) {
                if (!permission.equals("*") && !knownRights.contains(permission)) {
                    errors.add("ranks.yml: Unbekanntes Clan-Recht bei "
                            + rank.configKey() + ": " + permission);
                }
            }
            if (rank == RankId.OWNER && !permissions.contains("*")) {
                errors.add("ranks.yml: Owner must have the * permission");
            }
            if (rank != RankId.OWNER && permissions.contains("*")) {
                errors.add("ranks.yml: Only Owner may have the * permission");
            }
        }
    }

    private void validateMessages(List<String> errors) {
        for (String path : List.of(
                "prefix",
                "errors.no-permission",
                "errors.internal",
                "errors.economy-unavailable",
                "errors.insufficient-funds",
                "errors.economy-transaction-failed",
                "errors.invalid-role-priority",
                "errors.role-priority-taken",
                "errors.bank-disabled",
                "errors.invalid-bank-amount",
                "errors.bank-insufficient-funds",
                "general.created",
                "general.name-changed",
                "general.clan-name-edit-prompt",
                "general.clan-tag-edit-prompt",
                "general.clan-edit-input-cancelled",
                "general.role-priority-prompt",
                "bank.deposit-prompt",
                "bank.withdraw-prompt",
                "bank.deposited",
                "bank.withdrawn",
                "profile.header"
        )) {
            if (!messages().isString(path)) {
                errors.add("messages.yml: Required text is missing or is not a string: " + path);
            }
        }
        if (!messages().isList("general.help")) {
            errors.add("messages.yml: general.help must be a list");
        }
        if (!messages().isList("general.create-command-help")) {
            errors.add("messages.yml: general.create-command-help must be a list");
        }
    }

    private void validateEconomy(List<String> errors) {
        YamlConfiguration economy = economy();
        String provider = economy.getString("general.provider", "");
        if (!"VAULT".equalsIgnoreCase(provider)) {
            errors.add("economy.yml: general.provider currently supports only VAULT");
        }
        double price = economy.getDouble("clan-creation.price", -1.0);
        if (!Double.isFinite(price) || price < 0.0) {
            errors.add("economy.yml: clan-creation.price must be a finite number greater than or equal to 0");
        }
        if (economy.getBoolean("clan-creation.charge-enabled", false)) {
            errors.add("economy.yml: clan-creation.charge-enabled must remain "
                    + "disabled until payment crash recovery is implemented");
        }
        String currency = economy.getString("currency.display-name", "").trim();
        if (currency.isEmpty() || currency.length() > 32) {
            errors.add("economy.yml: currency.display-name must contain 1 to 32 characters");
        }
        BigDecimal minimumDeposit = economyDecimal(errors, "clan-bank.minimum-deposit");
        BigDecimal minimumWithdrawal = economyDecimal(
                errors,
                "clan-bank.minimum-withdrawal"
        );
        if ((minimumDeposit != null && minimumDeposit.signum() <= 0)
                || (minimumWithdrawal != null && minimumWithdrawal.signum() <= 0)) {
            errors.add("economy.yml: Minimum bank amounts must be positive");
        }
        int scale = economy.getInt("clan-bank.decimal-scale", -1);
        if (scale < 0 || scale > 8) {
            errors.add("economy.yml: clan-bank.decimal-scale must be between 0 and 8");
        }
        try {
            RoundingMode.valueOf(economy.getString("clan-bank.rounding-mode", ""));
        } catch (IllegalArgumentException exception) {
            errors.add("economy.yml: clan-bank.rounding-mode is invalid");
        }
        List<String> quickAmounts = economy.getStringList("clan-bank.quick-amounts");
        if (quickAmounts.isEmpty() || quickAmounts.size() > 7
                || quickAmounts.size() > gui().getIntegerList(
                        "bank-amount-menu.quick-amount-slots"
                ).size()) {
            errors.add("economy.yml: clan-bank.quick-amounts requires 1 to 7 values "
                    + "with one GUI slot each");
        }
        for (String amount : quickAmounts) {
            try {
                BigDecimal quickAmount = new BigDecimal(amount);
                BigDecimal minimumQuickAmount = minimumDeposit == null
                        ? minimumWithdrawal
                        : minimumWithdrawal == null
                        ? minimumDeposit
                        : minimumDeposit.max(minimumWithdrawal);
                if (quickAmount.signum() <= 0
                        || minimumQuickAmount != null
                        && quickAmount.compareTo(minimumQuickAmount) < 0) {
                    throw new NumberFormatException("not positive");
                }
            } catch (NumberFormatException exception) {
                errors.add("economy.yml: invalid quick amount: " + amount);
            }
        }
        int logEntries = economy.getInt("clan-bank.logging.maximum-gui-entries", -1);
        if (logEntries < 1 || logEntries > 18) {
            errors.add("economy.yml: maximum-gui-entries must be between 1 and 18");
        }
    }

    private BigDecimal economyDecimal(List<String> errors, String path) {
        Object value = economy().get(path);
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            errors.add("economy.yml: " + path + " must be a valid decimal number");
            return null;
        }
    }

    private void validateIntegrations(List<String> errors) {
        YamlConfiguration integrations = integrations();
        String ezEconomyPluginName = integrations.getString(
                "ezeconomy.plugin-name",
                ""
        );
        if (ezEconomyPluginName == null || ezEconomyPluginName.isBlank()
                || ezEconomyPluginName.length() > 64) {
            errors.add("integrations.yml: ezeconomy.plugin-name must contain 1 to 64 characters");
        }
        for (String path : List.of(
                "ezeconomy.enabled",
                "ezeconomy.required",
                "ezeconomy.validate-economy-provider",
                "ezeconomy.provider-mismatch-is-error",
                "interactivechat.enabled",
                "interactivechat.required"
        )) {
            if (!integrations.isBoolean(path)) {
                errors.add("integrations.yml: " + path + " must be true or false");
            }
        }
        String interactiveChatPluginName = integrations.getString(
                "interactivechat.plugin-name",
                ""
        );
        if (interactiveChatPluginName == null || interactiveChatPluginName.isBlank()
                || interactiveChatPluginName.length() > 64) {
            errors.add("integrations.yml: interactivechat.plugin-name must "
                    + "1 bis 64 Zeichen haben");
        }
        if (integrations.getBoolean("luckperms.enable-group-sync", false)) {
            errors.add("integrations.yml: luckperms.enable-group-sync is not implemented yet "
                    + "and must remain false");
        }
        for (String key : List.of("default", "support", "management", "administration")) {
            String value = integrations.getString("luckperms.groups." + key, "");
            if (value == null || value.isBlank()) {
                errors.add("integrations.yml: luckperms.groups." + key + " must not be empty");
            }
        }
    }

    private void validatePlaceholders(List<String> errors) {
        String identifier = placeholders().getString("general.identifier", "");
        if (!identifier.matches("^[a-z0-9_-]{2,32}$")) {
            errors.add("placeholders.yml: general.identifier must contain 2 to 32 characters using "
                    + "a-z, 0-9, _, or -");
        }
        String integrationIdentifier = integrations().getString("placeholderapi.identifier", "");
        if (!identifier.equals(integrationIdentifier)) {
            errors.add("placeholders.yml and integrations.yml use different "
                    + "Placeholder-Identifier");
        }
        if (!placeholders().isConfigurationSection("enabled-placeholders")) {
            errors.add("placeholders.yml: enabled-placeholders section is missing");
        }
    }

    private void validatePerformance(List<String> errors, int defaultMembers) {
        YamlConfiguration performance = performance();
        if (!performance.getBoolean("cache.enabled", true)
                || !performance.getBoolean("cache.preload-on-start", true)) {
            errors.add("performance.yml: Cache and startup preload must remain enabled for thread-safe "
                    + "placeholders");
        }
        int maximumClans = performance.getInt("cache.maximum-clans", -1);
        if (maximumClans < 1 || maximumClans > 100_000) {
            errors.add("performance.yml: cache.maximum-clans must be between 1 and 100000");
        }
        int listEntries = performance.getInt("commands.maximum-list-entries", -1);
        if (listEntries < 1 || listEntries > 1000) {
            errors.add("performance.yml: commands.maximum-list-entries must be between 1 and 1000");
        }
        if (performance.getInt("database.worker-threads", -1) != 1) {
            errors.add("performance.yml: database.worker-threads must remain 1 for SQLite");
        }
        int maximumQueuedOperations =
                performance.getInt("database.maximum-queued-operations", -1);
        if (maximumQueuedOperations < 16 || maximumQueuedOperations > 100_000) {
            errors.add("performance.yml: database.maximum-queued-operations must be between "
                    + "16 and 100000");
        }
        int shutdownSeconds = performance.getInt("database.shutdown-timeout-seconds", -1);
        if (shutdownSeconds < 1 || shutdownSeconds > 60) {
            errors.add("performance.yml: database.shutdown-timeout-seconds must be between 1 and 60");
        }
        if (performance.getBoolean("diagnostics.periodic-performance-sampling", false)) {
            errors.add("performance.yml: periodic performance sampling is intentionally "
                    + "not implemented in this beta build");
        }
        if (gui().getIntegerList("members-menu.member-slots").size() < defaultMembers) {
            errors.add("performance.yml: Member GUI cannot represent the initial limit");
        }
    }

    private static void validateRange(
            List<String> errors,
            String path,
            int min,
            int max,
            int allowedMin,
            int allowedMax
    ) {
        if (min < allowedMin || max < min || max > allowedMax) {
            errors.add(path + ": min/max must be ordered between "
                    + allowedMin + " and " + allowedMax + "");
        }
    }

    private static void validateRequiredText(
            List<String> errors,
            String path,
            String value,
            int maximumLength
    ) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            errors.add(path + " must contain between 1 and "
                    + maximumLength + " characters");
        }
    }

    private static void validatePattern(List<String> errors, String path, String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException exception) {
            errors.add(path + " is not a valid regular expression: " + exception.getDescription());
        }
    }
}
