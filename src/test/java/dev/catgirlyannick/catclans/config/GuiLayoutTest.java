package dev.catgirlyannick.catclans.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiLayoutTest {

    @Test
    void usesPersistentDarkFrameWithoutDecorativeLore() {
        YamlConfiguration gui = loadGui();

        assertEquals(36, gui.getInt("config-version"));
        assertTrue(gui.getBoolean("common.filler.enabled"));
        assertEquals(
                "BLACK_STAINED_GLASS_PANE",
                gui.getString("common.filler.material")
        );
        assertTrue(gui.getBoolean("common.frame.enabled"));
        assertTrue(gui.getBoolean("common.frame.top-row"));
        assertTrue(gui.getBoolean("common.frame.bottom-row"));
        assertEquals(
                "PURPLE_STAINED_GLASS_PANE",
                gui.getString("common.frame.material")
        );
        assertTrue(gui.getStringList("common.filler.lore").isEmpty());
        assertTrue(gui.getStringList("common.frame.lore").isEmpty());
        assertFalse(gui.contains("input-menu.edit-name"));
        assertFalse(gui.contains("input-menu.edit-tag"));
        assertFalse(gui.contains("input-menu.result"));
        assertEquals(
                "cancel",
                gui.getString("input-menu.clan-edit.chat.cancel-keyword")
        );
    }

    @Test
    void keepsListsAndPermissionGroupsCentered() {
        YamlConfiguration gui = loadGui();

        assertMirrored(gui.getIntegerList("clan-list-menu.clan-slots"));
        assertMirrored(gui.getIntegerList("ranking-menu.entry-slots"));
        assertMirrored(gui.getIntegerList("invites-menu.invite-slots"));
        assertMirrored(gui.getIntegerList("ally-requests-menu.request-slots"));
        assertMirrored(gui.getIntegerList("members-menu.member-slots"));
        assertMirrored(gui.getIntegerList("member-permission-list-menu.member-slots"));
        assertMirrored(gui.getIntegerList("role-list-menu.role-slots"));
        assertMirrored(gui.getIntegerList("role-assignment-menu.role-slots"));
        assertMirrored(gui.getIntegerList("role-permissions-menu.permission-slots"));
        assertMirrored(gui.getIntegerList("member-permissions-menu.permission-slots"));
        assertMirrored(gui.getIntegerList("vault-log-members-menu.member-slots"));
        assertMirrored(gui.getIntegerList("vault-log-entries-menu.entry-slots"));
        assertMirrored(gui.getIntegerList("bank-log-members-menu.member-slots"));
        assertMirrored(gui.getIntegerList("bank-log-entries-menu.entry-slots"));
        assertMirrored(gui.getIntegerList("home-menu.home-slots"));
        assertEquals(
                List.of(
                        36, 37, 28, 19, 10,
                        11, 12, 21, 30, 39,
                        40, 41, 32, 23, 14,
                        15, 16, 25, 34, 43
                ),
                gui.getIntegerList("battlepass-menu.level-slots")
        );
        assertEquals(
                "GRAY_STAINED_GLASS_PANE",
                gui.getString("battlepass-menu.filler.material")
        );

        assertEquals(27, gui.getIntegerList("members-menu.member-slots").size());
        assertEquals(21, gui.getIntegerList("role-permissions-menu.permission-slots").size());
        assertEquals(21, gui.getIntegerList("member-permissions-menu.permission-slots").size());
        assertEquals(28, gui.getIntegerList("home-menu.home-slots").size());
        assertEquals(32, gui.getInt("profile-menu.homes.slot"));
        assertEquals(31, gui.getInt("profile-menu.bank.slot"));
        assertEquals(30, gui.getInt("profile-menu.edit.slot"));
        assertEquals(4, gui.getInt("home-menu.extensions.slot"));
    }

    @Test
    void keepsNavigationAtTheOuterBottomCorners() {
        YamlConfiguration gui = loadGui();

        assertEquals(54, gui.getInt("main-menu.size"));
        assertEquals(13, gui.getInt("main-menu.profile.slot"));
        assertEquals(53, gui.getInt("main-menu.close.slot"));

        for (String menu : List.of(
                "clan-list-menu",
                "ranking-menu",
                "profile-menu",
                "invites-menu",
                "members-menu",
                "role-list-menu",
                "member-permission-list-menu",
                "role-permissions-menu",
                "member-permissions-menu",
                "role-assignment-menu",
                "home-menu"
        )) {
            assertEquals(45, gui.getInt(menu + ".back.slot"), menu);
            assertEquals(53, gui.getInt(menu + ".close.slot"), menu);
        }
        assertEquals(18, gui.getInt("permission-home-menu.back.slot"));
        assertEquals(26, gui.getInt("permission-home-menu.close.slot"));
        assertEquals(18, gui.getInt("clan-edit-menu.back.slot"));
        assertEquals(26, gui.getInt("clan-edit-menu.close.slot"));
    }

    @Test
    void migratesFormattedTagPlaceholdersIntoGuiConfirmation() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set(
                "main-menu.profile.lore",
                List.of("<gray>Tag: <white>{tag}")
        );
        gui.set(
                "confirmation-menu.actions.create.display-name",
                "<gold>{clan} <gray>[<yellow>{tag}</yellow>]"
        );

        ConfigMigrationService.applyVersionSixFormattedTags("gui.yml", gui);

        assertEquals(
                List.of("<gray>Tag: <white>{formatted_tag}"),
                gui.getStringList("main-menu.profile.lore")
        );
        assertEquals(
                "<gold>{clan} <gray>[<yellow>{formatted_tag}</yellow>]",
                gui.getString("confirmation-menu.actions.create.display-name")
        );
    }

    @Test
    void preservesExistingFeatureDecisionsOnMigration() {
        YamlConfiguration main = new YamlConfiguration();
        main.set("features.battlepass.enabled", false);
        main.set("features.item-vault.enabled", false);
        main.set("features.bank.enabled", true);

        ConfigMigrationService.applyVersionSevenProgression("config.yml", main);

        assertFalse(main.getBoolean("features.battlepass.enabled"));
        assertFalse(main.getBoolean("features.item-vault.enabled"));
        assertTrue(main.getBoolean("features.bank.enabled"));
    }

    @Test
    void preservesCustomizedVaultRightsOnMigration() {
        YamlConfiguration ranks = new YamlConfiguration();
        ranks.set(
                "standard-ranks.moderator.permissions",
                List.of("bank.view", "bank.deposit", "vault.view", "vault.deposit")
        );
        ranks.set(
                "standard-ranks.co-owner.permissions",
                List.of("bank.view", "vault.view", "vault.deposit", "vault.withdraw")
        );

        ConfigMigrationService.applyVersionSevenProgression("ranks.yml", ranks);

        assertTrue(ranks.getStringList("standard-ranks.moderator.permissions")
                .contains("bank.deposit"));
        assertTrue(ranks.getStringList("standard-ranks.moderator.permissions")
                .contains("vault.deposit"));
        assertFalse(ranks.getStringList("standard-ranks.co-owner.permissions")
                .contains("vault.extensions.manage"));
    }

    @Test
    void activatesRequestedDiplomacyAndBattlepassDesignOnVersionEight() {
        YamlConfiguration main = new YamlConfiguration();
        main.set("features.alliances.enabled", false);
        main.set("features.clan-wars.enabled", false);
        main.set("features.battlepass.enabled", false);
        ConfigMigrationService.applyVersionEightDiplomacyAndBattlepass(
                "config.yml",
                main
        );

        assertTrue(main.getBoolean("features.alliances.enabled"));
        assertTrue(main.getBoolean("features.clan-wars.enabled"));
        assertTrue(main.getBoolean("features.battlepass.enabled"));

        YamlConfiguration gui = new YamlConfiguration();
        ConfigMigrationService.applyVersionEightDiplomacyAndBattlepass("gui.yml", gui);
        assertEquals(
                "RED_STAINED_GLASS_PANE",
                gui.getString("battlepass-menu.level-locked.material")
        );
        assertEquals(
                "RED_CONCRETE",
                gui.getString("battlepass-menu.level-milestone-locked.material")
        );
        assertEquals(
                "GREEN_CONCRETE",
                gui.getString("battlepass-menu.level-unlocked.material")
        );
    }

    @Test
    void addsCenteredRolePriorityOnVersionNine() {
        YamlConfiguration gui = new YamlConfiguration();

        ConfigMigrationService.applyVersionNineNavigation("gui.yml", gui);

        assertEquals(49, gui.getInt("role-permissions-menu.priority.slot"));
        assertEquals(
                "COMPARATOR",
                gui.getString("role-permissions-menu.priority.material")
        );
        assertTrue(
                gui.getStringList("role-permissions-menu.priority.lore")
                        .stream()
                        .anyMatch(line -> line.contains("{priority}"))
        );
    }

    @Test
    void movesBattlepassLeftAndUsesWhiteBackgroundOnVersionTen() {
        YamlConfiguration gui = new YamlConfiguration();

        ConfigMigrationService.applyVersionTenBattlepassPosition("gui.yml", gui);

        assertEquals(36, gui.getIntegerList("battlepass-menu.level-slots").getFirst());
        assertEquals(
                "WHITE_STAINED_GLASS_PANE",
                gui.getString("battlepass-menu.filler.material")
        );
    }

    @Test
    void replacesBattlepassFillerWithGrayGlassOnVersionThirty() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("battlepass-menu.filler.material", "WHITE_STAINED_GLASS_PANE");

        ConfigMigrationService.applyVersionThirtyBattlepassFiller("gui.yml", gui);

        assertEquals(
                "GRAY_STAINED_GLASS_PANE",
                gui.getString("battlepass-menu.filler.material")
        );
    }

    @Test
    void addsAllyRequestInboxAboveVaultOnVersionEleven() {
        YamlConfiguration gui = new YamlConfiguration();

        ConfigMigrationService.applyVersionElevenAllyRequests("gui.yml", gui);

        assertEquals(24, gui.getInt("profile-menu.primary-action.slot"));
        assertEquals(
                "BELL",
                gui.getString("profile-menu.ally-requests.material")
        );
    }

    @Test
    void separatesClanListLabelOnVersionTwelve() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("main-menu.browse.display-name", "<gold>Clanliste");

        ConfigMigrationService.applyVersionTwelveClanListLabel("gui.yml", gui);

        assertEquals(
                "<gold>Clan Liste",
                gui.getString("main-menu.browse.display-name")
        );
    }

    @Test
    void addsRankingEntryPointsAndMainMenuButtonOnVersionThirteen() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set(
                "profile-menu.info.lore",
                List.of("<gray>Mitglieder: <white>{members}/{max_members}")
        );

        ConfigMigrationService.applyVersionThirteenRankings("gui.yml", gui);

        assertEquals(31, gui.getInt("main-menu.rankings.slot"));
        assertEquals(
                "NETHER_STAR",
                gui.getString("main-menu.rankings.material")
        );
        assertTrue(gui.getStringList("profile-menu.info.lore")
                .stream()
                .anyMatch(line -> line.contains("{ranking_position}")));
        assertTrue(gui.getStringList("profile-menu.info.lore")
                .stream()
                .anyMatch(line -> line.contains("{ranking_points}")));
    }

    @Test
    void replacesRoleNameAnvilWithConfigurableChatInputOnVersionFourteen() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("input-menu.role-name.title", "Alter Amboss");
        gui.set("input-menu.role-name.input.material", "NAME_TAG");

        ConfigMigrationService.applyVersionFourteenRoleChatInput("gui.yml", gui);

        assertFalse(gui.contains("input-menu.role-name.title"));
        assertFalse(gui.contains("input-menu.role-name.input"));
        assertEquals(
                "abbrechen",
                gui.getString("input-menu.role-name.chat.cancel-keyword")
        );
    }

    @Test
    void activatesCompletedVaultOnVersionFifteen() {
        YamlConfiguration main = new YamlConfiguration();
        main.set("features.item-vault.enabled", false);
        ConfigMigrationService.applyVersionFifteenVault("config.yml", main);
        assertTrue(main.getBoolean("features.item-vault.enabled"));

        YamlConfiguration vault = new YamlConfiguration();
        vault.set("general.enabled", false);
        ConfigMigrationService.applyVersionFifteenVault("vault.yml", vault);
        assertTrue(vault.getBoolean("general.enabled"));
    }

    @Test
    void activatesBankAndExactPrioritiesOnVersionSixteen() {
        YamlConfiguration main = new YamlConfiguration();
        main.set("features.bank.enabled", false);
        ConfigMigrationService.applyVersionSixteenBankAndPriorities(
                "config.yml",
                main
        );
        assertTrue(main.getBoolean("features.bank.enabled"));

        YamlConfiguration economy = new YamlConfiguration();
        economy.set("clan-bank.enabled", false);
        ConfigMigrationService.applyVersionSixteenBankAndPriorities(
                "economy.yml",
                economy
        );
        assertTrue(economy.getBoolean("clan-bank.enabled"));
        assertEquals("AshenCoins", economy.getString("currency.display-name"));

        YamlConfiguration ranks = new YamlConfiguration();
        ranks.set("standard-ranks.co-owner.permissions", List.of("bank.view"));
        ConfigMigrationService.applyVersionSixteenBankAndPriorities(
                "ranks.yml",
                ranks
        );
        assertTrue(ranks.getStringList("standard-ranks.co-owner.permissions")
                .contains("bank.log"));
    }

    @Test
    void enablesOwnerSuccessionOnVersionSeventeen() {
        YamlConfiguration main = new YamlConfiguration();
        main.set("security.prevent-owner-leaving", true);

        ConfigMigrationService.applyVersionSeventeenClanLifecycle(
                "config.yml",
                main
        );

        assertFalse(main.getBoolean("security.prevent-owner-leaving"));
    }

    @Test
    void addsEssentialsXVaultValidationOnVersionEighteen() {
        YamlConfiguration integrations = new YamlConfiguration();
        integrations.set("essentialsx.provider-mismatch-is-error", true);

        ConfigMigrationService.applyVersionEighteenEssentialsX(
                "integrations.yml",
                integrations
        );

        assertTrue(integrations.getBoolean("essentialsx.enabled"));
        assertEquals("Essentials", integrations.getString("essentialsx.plugin-name"));
        assertTrue(integrations.getBoolean("essentialsx.validate-economy-provider"));
        assertTrue(integrations.getBoolean("essentialsx.provider-mismatch-is-error"));
    }

    @Test
    void addsCreateCommandChatHelpOnVersionNineteen() {
        YamlConfiguration messages = new YamlConfiguration();

        ConfigMigrationService.applyVersionNineteenCreateCommandHelp(
                "messages.yml",
                messages
        );

        List<String> help = messages.getStringList("general.create-command-help");
        assertFalse(help.isEmpty());
        assertTrue(help.stream().anyMatch(line -> line.contains("{usage}")));
        assertTrue(help.stream().anyMatch(line -> line.contains("{rgb_example}")));
    }

    @Test
    void addsCenteredClanEditingOnVersionTwenty() {
        YamlConfiguration gui = new YamlConfiguration();
        ConfigMigrationService.applyVersionTwentyClanEditing("gui.yml", gui);

        assertEquals(15, gui.getInt("profile-menu.edit.slot"));
        assertEquals(11, gui.getInt("clan-edit-menu.name.slot"));
        assertEquals(13, gui.getInt("clan-edit-menu.info.slot"));
        assertEquals(15, gui.getInt("clan-edit-menu.tag.slot"));
        assertEquals("Change clan name", gui.getString("permission-labels.name-change"));
        assertEquals(
                16,
                gui.getIntegerList("role-permissions-menu.permission-slots").size()
        );

        YamlConfiguration permissions = new YamlConfiguration();
        ConfigMigrationService.applyVersionTwentyClanEditing(
                "permissions.yml",
                permissions
        );
        assertEquals("name.change", permissions.getString("clan-rights.name-change"));
    }

    @Test
    void removesCreateAnvilFlowOnVersionTwentyOne() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("input-menu.create-tag.title", "Alter Clan-Tag-Amboss");
        gui.set("input-menu.create-name.title", "Alter Clanname-Amboss");
        gui.set("confirmation-menu.actions.create.material", "NAME_TAG");

        ConfigMigrationService.applyVersionTwentyOneChatOnlyCreate("gui.yml", gui);

        assertFalse(gui.contains("input-menu.create-tag"));
        assertFalse(gui.contains("input-menu.create-name"));
        assertFalse(gui.contains("confirmation-menu.actions.create"));
        assertFalse(gui.getStringList("main-menu.create.lore").isEmpty());
    }

    @Test
    void placesClanEditingDirectlyBelowClanBankOnVersionTwentyTwo() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("profile-menu.bank.slot", 31);
        gui.set("profile-menu.edit.slot", 15);

        ConfigMigrationService.applyVersionTwentyTwoClanEditPosition("gui.yml", gui);

        assertEquals(40, gui.getInt("profile-menu.edit.slot"));
        assertEquals(
                gui.getInt("profile-menu.bank.slot") + 9,
                gui.getInt("profile-menu.edit.slot")
        );
    }

    @Test
    void migratesCleanGuiStyleAndRemovesClanBankMaximumOnVersionTwentyThree() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("main-menu.title", "<dark_red>CATCLANS");
        gui.set("bank-menu.overview.lore", List.of(
                "<gray>Guthaben: <white>{balance}",
                "<gray>Maximum: <white>{maximum_balance}",
                "<gray>Währung: <white>{currency}"
        ));
        ConfigMigrationService.applyVersionTwentyThreeGuiStyleAndUnlimitedBank(
                "gui.yml",
                gui
        );
        assertEquals("<dark_gray>Clan <gold>Menu", gui.getString("main-menu.title"));
        assertFalse(gui.getStringList("bank-menu.overview.lore").stream()
                .anyMatch(line -> line.contains("{maximum_balance}")));

        YamlConfiguration economy = new YamlConfiguration();
        economy.set("clan-bank.maximum-balance", 1000000000.0D);
        ConfigMigrationService.applyVersionTwentyThreeGuiStyleAndUnlimitedBank(
                "economy.yml",
                economy
        );
        assertFalse(economy.contains("clan-bank.maximum-balance"));

        YamlConfiguration messages = new YamlConfiguration();
        messages.set("errors.bank-balance-limit", "Alte Meldung");
        ConfigMigrationService.applyVersionTwentyThreeGuiStyleAndUnlimitedBank(
                "messages.yml",
                messages
        );
        assertFalse(messages.contains("errors.bank-balance-limit"));
    }

    @Test
    void replacesSharpSInAllExistingMenuTextsOnVersionTwentyFour() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("main-menu.close.display-name", "<red>Schließen");
        gui.set("main-menu.close.lore", List.of(
                "<gray>Menü schließen",
                "<gray>STRAẞE"
        ));

        ConfigMigrationService.applyVersionTwentyFourMenuText("gui.yml", gui);

        assertEquals(
                "<red>Schliessen",
                gui.getString("main-menu.close.display-name")
        );
        assertEquals(
                List.of("<gray>Menü schliessen", "<gray>STRASSE"),
                gui.getStringList("main-menu.close.lore")
        );
    }

    @Test
    void activatesClanHomesAndExtendsPermissionMenusOnVersionTwentyFive() {
        YamlConfiguration main = new YamlConfiguration();
        main.set("features.clan-homes.enabled", false);
        ConfigMigrationService.applyVersionTwentyFiveClanHomes("config.yml", main);
        assertTrue(main.getBoolean("features.clan-homes.enabled"));

        YamlConfiguration gui = new YamlConfiguration();
        ConfigMigrationService.applyVersionTwentyFiveClanHomes("gui.yml", gui);
        assertEquals(32, gui.getInt("profile-menu.homes.slot"));
        assertEquals(4, gui.getInt("home-menu.extensions.slot"));
        assertEquals(
                21,
                gui.getIntegerList("role-permissions-menu.permission-slots").size()
        );

        YamlConfiguration ranks = new YamlConfiguration();
        ranks.set("standard-ranks.co-owner.permissions", List.of("bank.view"));
        ranks.set("standard-ranks.member.permissions", List.of("vault.view"));
        ConfigMigrationService.applyVersionTwentyFiveClanHomes("ranks.yml", ranks);
        assertTrue(ranks.getStringList("standard-ranks.co-owner.permissions")
                .contains("home.delete"));
        assertTrue(ranks.getStringList("standard-ranks.member.permissions")
                .contains("home.teleport"));
    }

    @Test
    void placesClanEditingDirectlyLeftOfClanBankOnVersionTwentySix() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("profile-menu.bank.slot", 31);
        gui.set("profile-menu.edit.slot", 40);

        ConfigMigrationService.applyVersionTwentySixClanEditPosition("gui.yml", gui);

        assertEquals(30, gui.getInt("profile-menu.edit.slot"));
        assertEquals(
                gui.getInt("profile-menu.bank.slot") - 1,
                gui.getInt("profile-menu.edit.slot")
        );
    }

    @Test
    void replacesClanEditAnvilsWithChatInputOnVersionTwentySeven() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("input-menu.edit-name.title", "Alter Clanname-Amboss");
        gui.set("input-menu.edit-tag.title", "Alter Clan-Tag-Amboss");
        gui.set("input-menu.result.material", "LIME_DYE");

        ConfigMigrationService.applyVersionTwentySevenClanEditChatInput(
                "gui.yml",
                gui
        );

        assertFalse(gui.contains("input-menu.edit-name"));
        assertFalse(gui.contains("input-menu.edit-tag"));
        assertFalse(gui.contains("input-menu.result"));
        assertEquals(
                "abbrechen",
                gui.getString("input-menu.clan-edit.chat.cancel-keyword")
        );

        YamlConfiguration messages = new YamlConfiguration();
        ConfigMigrationService.applyVersionTwentySevenClanEditChatInput(
                "messages.yml",
                messages
        );
        assertTrue(messages.isString("general.clan-name-edit-prompt"));
        assertTrue(messages.isString("general.clan-tag-edit-prompt"));
        assertTrue(messages.isString("general.clan-edit-input-cancelled"));
    }

    private static void assertMirrored(List<Integer> slots) {
        assertFalse(slots.isEmpty());
        for (int slot : slots) {
            int rowStart = slot / 9 * 9;
            int mirrored = rowStart + 8 - slot % 9;
            assertTrue(
                    slots.contains(mirrored),
                    () -> "Slot " + slot + " hat kein Gegenstück " + mirrored
            );
        }
    }

    private static YamlConfiguration loadGui() {
        InputStream stream = GuiLayoutTest.class.getClassLoader()
                .getResourceAsStream("gui.yml");
        if (stream == null) {
            throw new IllegalStateException("gui.yml fehlt im Test-Classpath");
        }
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
    }
}
