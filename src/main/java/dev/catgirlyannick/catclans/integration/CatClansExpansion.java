package dev.catgirlyannick.catclans.integration;

import dev.catgirlyannick.catclans.CatClansPlugin;
import dev.catgirlyannick.catclans.config.ConfigBundle;
import dev.catgirlyannick.catclans.message.SmallCapsFormatter;
import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.service.ClanService;
import dev.catgirlyannick.catclans.service.ClanTagFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class CatClansExpansion extends PlaceholderExpansion {

    private final CatClansPlugin plugin;
    private final ClanService clanService;
    private final String identifier;
    private final Set<String> enabledPlaceholders;
    private final String noPlayer;
    private final String noClan;
    private final String unavailable;
    private final String rankingTier;
    private final String battlepassLevel;
    private final String noAlly;
    private final String noRival;
    private final String serverName;
    private final String pluginName;
    private final String authorName;
    private final boolean smallCapsEnabled;
    private final boolean rgbEnabled;
    private final boolean gradientsEnabled;

    CatClansExpansion(
            CatClansPlugin plugin,
            ConfigBundle configs,
            ClanService clanService
    ) {
        this.plugin = plugin;
        this.clanService = clanService;
        this.smallCapsEnabled = configs.messages().getBoolean(
                "formatting.small-caps.enabled",
                true
        );
        this.rgbEnabled = configs.messages().getBoolean(
                "formatting.minimessage.rgb-enabled",
                true
        );
        this.gradientsEnabled = configs.messages().getBoolean(
                "formatting.minimessage.gradients-enabled",
                true
        );
        this.identifier = configs.placeholders().getString("general.identifier", "catclans");
        var enabledSection = configs.placeholders()
                .getConfigurationSection("enabled-placeholders");
        this.enabledPlaceholders = enabledSection == null
                ? Set.of()
                : enabledSection.getKeys(false)
                        .stream()
                        .filter(key -> configs.placeholders().getBoolean(
                                "enabled-placeholders." + key,
                                false
                        ))
                        .map(key -> key.replace('-', '_'))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.noPlayer = fallback(configs, "no-player");
        this.noClan = fallback(configs, "no-clan");
        this.unavailable = fallback(configs, "unavailable");
        this.rankingTier = fallback(configs, "ranking-tier");
        this.battlepassLevel = fallback(configs, "battlepass-level");
        this.noAlly = fallback(configs, "no-ally");
        this.noRival = fallback(configs, "no-rival");
        this.serverName = configs.main().getString("branding.server-name", "{{SERVER_NAME}}");
        this.pluginName = configs.main().getString("branding.plugin-name", "CatClans");
        this.authorName = configs.main().getString("branding.author-name", "CatgirlYannick");
    }

    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = canonicalKey(params.toLowerCase(Locale.ROOT));
        if (!enabledPlaceholders.contains(key)) {
            return null;
        }

        String brandingValue = switch (key) {
            case "server_name" -> serverName;
            case "plugin_name" -> pluginName;
            case "author_name" -> authorName;
            default -> null;
        };
        if (brandingValue != null) {
            return display(brandingValue);
        }
        if (player == null) {
            return display(noPlayer);
        }

        Optional<Clan> found = clanService.findCachedClanForPlayer(player.getUniqueId());
        if (found.isEmpty()) {
            return display(noClan);
        }
        Clan clan = found.get();
        return switch (key) {
            case "clan_name" -> display(clan.name());
            case "clan_tag" -> display(clan.tag());
            case "clan_tag_formatted" -> ClanTagFormatter.safeMarkup(
                    display(clan.formattedTag()),
                    rgbEnabled,
                    gradientsEnabled
            );
            case "clan_tag_colored" -> ClanTagFormatter.legacy(
                    display(clan.formattedTag()),
                    rgbEnabled,
                    gradientsEnabled
            );
            case "rank" -> clanService.findCachedMember(player.getUniqueId())
                    .map(member -> display(clanService.displayRole(clan.id(), member)))
                    .orElse(display(unavailable));
            case "member_count" -> Integer.toString(clan.members().size());
            case "max_members" -> Integer.toString(clan.maxMembers());
            case "ranking_tier" -> clanService.cachedRankingPosition(clan.id())
                    .stream()
                    .mapToObj(Integer::toString)
                    .findFirst()
                    .map(this::display)
                    .orElseGet(() -> display(rankingTier));
            case "ranking_points" -> decimal(
                    clanService.cachedRankingPoints(clan.id())
            );
            case "bank_balance" -> decimal(
                    clanService.cachedBankBalance(clan.id())
            );
            case "battlepass_level" -> Integer.toString(
                    clanService.findCachedBattlepass(clan.id()).level()
            );
            case "battlepass_xp" -> decimal(
                    clanService.findCachedBattlepass(clan.id()).currentXp()
            );
            case "battlepass_required_xp" -> decimal(
                    clanService.requiredBattlepassXp(
                            clanService.findCachedBattlepass(clan.id()).level()
                    )
            );
            case "ally" -> display(noAlly);
            case "rival" -> display(noRival);
            default -> null;
        };
    }

    private String display(String value) {
        return smallCapsEnabled ? SmallCapsFormatter.formatValue(value) : value;
    }

    private static String canonicalKey(String key) {
        return switch (key) {
            case "name" -> "clan_name";
            case "tag" -> "clan_tag";
            case "tag_formatted" -> "clan_tag_formatted";
            case "tag_colored" -> "clan_tag_colored";
            case "members" -> "member_count";
            case "battlepass_current_xp" -> "battlepass_xp";
            case "balance", "clan_balance" -> "bank_balance";
            default -> key;
        };
    }

    private static String decimal(java.math.BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String fallback(ConfigBundle configs, String key) {
        return configs.placeholders().getString("fallbacks." + key, "");
    }
}
