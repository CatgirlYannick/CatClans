package dev.catgirlyannick.catclans.command;

import dev.catgirlyannick.catclans.CatClansPlugin;
import dev.catgirlyannick.catclans.config.ConfigBundle;
import dev.catgirlyannick.catclans.gui.ClanGuiController;
import dev.catgirlyannick.catclans.integration.EconomyBridge;
import dev.catgirlyannick.catclans.integration.EconomyTransaction;
import dev.catgirlyannick.catclans.message.MessageService;
import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankingCategory;
import dev.catgirlyannick.catclans.service.AdminWarEndResult;
import dev.catgirlyannick.catclans.service.ClanService;
import dev.catgirlyannick.catclans.service.OperationCode;
import dev.catgirlyannick.catclans.service.OperationResult;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class ClanCommand implements TabExecutor {

    private static final UUID CONSOLE_ACTOR_ID = new UUID(0L, 0L);

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "accept", "deny", "join", "leave",
            "delete", "joinmode", "tag", "profile", "members", "list", "top"
    );

    private final CatClansPlugin plugin;
    private final ConfigBundle configs;
    private final ClanService clanService;
    private final MessageService messages;
    private final ClanGuiController clanGui;
    private final EconomyBridge economy;

    public ClanCommand(
            CatClansPlugin plugin,
            ConfigBundle configs,
            ClanService clanService,
            MessageService messages,
            ClanGuiController clanGui,
            EconomyBridge economy
    ) {
        this.plugin = plugin;
        this.configs = configs;
        this.clanService = clanService;
        this.messages = messages;
        this.clanGui = clanGui;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (command.getName().equalsIgnoreCase("clanadmin")) {
            adminCommand(sender, args);
            return true;
        }
        if (command.getName().equalsIgnoreCase("clantop")) {
            top(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            adminCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (!sender.hasPermission("catclans.command.clan")) {
            messages.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player
                    && configs.main().getBoolean("features.gui.enabled", true)
                    && configs.gui().getBoolean("general.enabled", true)) {
                clanGui.openMain(player);
            } else {
                messages.sendList(sender, "general.help");
            }
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "create" -> create(sender, args);
            case "invite" -> invite(sender, args);
            case "accept" -> accept(sender, args);
            case "deny" -> deny(sender, args);
            case "join", "beitreten" -> join(sender, args);
            case "leave" -> leave(sender, args);
            case "delete" -> deleteOwnedClan(sender, args);
            case "joinmode" -> joinMode(sender, args);
            case "tag" -> tag(sender, args);
            case "profile" -> profile(sender, args);
            case "members" -> members(sender, args);
            case "list" -> list(sender);
            case "top" -> top(sender);
            default -> messages.sendList(sender, "general.help");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (command.getName().equalsIgnoreCase("clanadmin")) {
            return adminTabComplete(sender, args);
        }
        if (!sender.hasPermission("catclans.command.clan")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(SUBCOMMANDS);
            if (canDeleteAnyClan(sender) || canEndAnyWar(sender)) {
                options.add("admin");
            }
            return filter(options, args[0]);
        }
        if (args[0].equalsIgnoreCase("admin")) {
            return adminTabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("joinmode")) {
            return filter(List.of("invite", "open"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            return filter(List.of("confirm"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> startsWithIgnoreCase(name, args[1]))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("tag")
                && sender.hasPermission("catclans.clan.tag.change")) {
            return filter(List.of(
                    "<red>TAG",
                    "<#55D6C2>TAG",
                    "&#D67DE9&l&oTAG",
                    "<gradient:#FF0000:#00FFFF>TAG",
                    "<strikethrough>TAG"
            ), args[1]);
        }
        return List.of();
    }

    private void create(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "catclans.clan.create")) {
            return;
        }
        if (args.length < 3) {
            messages.sendCreateCommandHelp(player);
            return;
        }
        String tag = args[1];
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        double creationPrice = configuredCreationPrice();
        if (creationPrice > 0.0) {
            if (!economy.available()) {
                messages.send(player, "errors.economy-unavailable");
                return;
            }
            if (!economy.has(player, creationPrice)) {
                messages.send(player, "errors.insufficient-funds",
                        Map.of("price", economy.format(creationPrice)));
                return;
            }
            EconomyTransaction withdrawal = economy.withdraw(player, creationPrice);
            if (!withdrawal.successful()) {
                plugin.getLogger().warning("Vault withdrawal failed: "
                        + withdrawal.errorMessage());
                messages.send(player, "errors.economy-transaction-failed");
                return;
            }
            messages.send(player, "general.creation-price-withdrawn",
                    Map.of("price", economy.format(creationPrice)));
        }
        handle(
                clanService.createClan(player.getUniqueId(), player.getName(), name, tag),
                result -> {
                    if (!result.successful()) {
                        refundCreationPrice(player, creationPrice);
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    Clan clan = result.value();
                    messages.send(player, "general.created", clanPlaceholders(clan));
                },
                player,
                () -> refundCreationPrice(player, creationPrice)
        );
    }

    private void invite(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "catclans.clan.invite")) {
            return;
        }
        if (args.length != 2) {
            usage(player, "/clan invite <Player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "errors.player-not-found", Map.of("player", args[1]));
            return;
        }
        clanGui.openInviteConfirmation(player, target);
    }

    private void accept(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "catclans.clan.join")) {
            return;
        }
        if (args.length < 2) {
            clanGui.openInvites(player);
            return;
        }
        String clanSearch = joinArguments(args, 1);
        handle(
                clanService.acceptInvite(player.getUniqueId(), player.getName(), clanSearch),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of("clan", clanSearch));
                        return;
                    }
                    messages.send(player, "general.invite-accepted",
                            Map.of("clan", result.value().name()));
                },
                player
        );
    }

    private void deny(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "catclans.clan.join")) {
            return;
        }
        if (args.length < 2) {
            clanGui.openInvites(player);
            return;
        }
        String clanSearch = joinArguments(args, 1);
        handle(
                clanService.denyInvite(player.getUniqueId(), player.getName(), clanSearch),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of("clan", clanSearch));
                        return;
                    }
                    messages.send(player, "general.invite-denied",
                            Map.of("clan", result.value().name()));
                },
                player
        );
    }

    private void join(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "catclans.clan.join")) {
            return;
        }
        if (args.length < 2) {
            clanGui.openClanList(player, 0);
            return;
        }
        String clanSearch = joinArguments(args, 1);
        handle(
                clanService.joinOpenClan(player.getUniqueId(), player.getName(), clanSearch),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of("clan", clanSearch));
                        return;
                    }
                    messages.send(player, "general.joined", Map.of("clan", result.value().name()));
                },
                player
        );
    }

    private void leave(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "catclans.clan.leave")) {
            return;
        }
        if (args.length == 1) {
            clanGui.openLeaveConfirmation(player);
            return;
        }
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "-";
        boolean wasOwner = clanService.findCachedClanForPlayer(player.getUniqueId())
                .map(clan -> clan.ownerId().equals(player.getUniqueId()))
                .orElse(false);
        handle(
                clanService.leaveClan(player.getUniqueId(), player.getName(), reason),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    messages.send(player, "general.left", Map.of("clan", result.value().name()));
                    if (wasOwner) {
                        Player successor = Bukkit.getPlayer(result.value().ownerId());
                        if (successor != null) {
                            messages.send(successor, "general.owner-received",
                                    Map.of("clan", result.value().name()));
                        }
                    }
                },
                player
        );
    }

    private void deleteOwnedClan(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "catclans.clan.delete")) {
            return;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("confirm")) {
            usage(player, "/clan delete confirm");
            return;
        }
        Optional<Clan> clan = clanService.findCachedClanForPlayer(player.getUniqueId());
        if (clan.isPresent() && clanGui.hasPendingStorageOperation(clan.get().id())) {
            messages.send(player, "errors.clan-busy");
            return;
        }
        handle(
                clanService.deleteOwnedClan(player.getUniqueId(), player.getName()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    notifyDeletedClanMembers(result.value());
                },
                player
        );
    }

    private void adminCommand(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("delete")) {
            adminDeleteClan(sender, args);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("war")) {
            adminEndWar(sender, args);
            return;
        }
        if (!canDeleteAnyClan(sender) && !canEndAnyWar(sender)) {
            messages.send(sender, "errors.no-permission");
            return;
        }
        usage(sender, "/clanadmin delete <Clan> confirm | /clanadmin war end <Clan1> <Clan2>");
    }

    private void adminDeleteClan(CommandSender sender, String[] args) {
        if (!canDeleteAnyClan(sender)) {
            messages.send(sender, "errors.no-permission");
            return;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("delete")
                || !args[args.length - 1].equalsIgnoreCase("confirm")) {
            usage(sender, "/clanadmin delete <Clan> confirm");
            return;
        }
        String clanSearch = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
        Optional<Clan> cachedClan = clanService.findCachedClan(clanSearch);
        if (cachedClan.isPresent()
                && clanGui.hasPendingStorageOperation(cachedClan.get().id())) {
            messages.send(sender, "errors.clan-busy");
            return;
        }
        UUID actorId = sender instanceof Player player
                ? player.getUniqueId()
                : CONSOLE_ACTOR_ID;
        handle(
                clanService.deleteClanAsAdmin(clanSearch, actorId, sender.getName()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(sender, result.code(), Map.of("clan", clanSearch));
                        return;
                    }
                    messages.send(sender, "admin.clan-deleted", clanPlaceholders(result.value()));
                    notifyDeletedClanMembers(result.value());
                },
                sender
        );
    }

    private void adminEndWar(CommandSender sender, String[] args) {
        if (!canEndAnyWar(sender)) {
            messages.send(sender, "errors.no-permission");
            return;
        }
        if (args.length != 4 || !args[0].equalsIgnoreCase("war")
                || !args[1].equalsIgnoreCase("end")) {
            usage(sender, "/clanadmin war end <Clan1-Tag> <Clan2-Tag>");
            return;
        }
        String firstClanSearch = args[2];
        String secondClanSearch = args[3];
        UUID actorId = sender instanceof Player player
                ? player.getUniqueId()
                : CONSOLE_ACTOR_ID;
        Map<String, String> placeholders = Map.of(
                "clan", firstClanSearch + " / " + secondClanSearch,
                "clan_one", firstClanSearch,
                "clan_two", secondClanSearch
        );
        handle(
                clanService.endWarAsAdmin(
                        firstClanSearch,
                        secondClanSearch,
                        actorId,
                        sender.getName()
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(sender, result.code(), placeholders);
                        return;
                    }
                    Map<String, String> resolved = warPlaceholders(result.value());
                    messages.send(sender, "admin.war-ended", resolved);
                    notifyWarEndedByAdmin(result.value(), resolved);
                },
                sender
        );
    }

    private void notifyWarEndedByAdmin(
            AdminWarEndResult result,
            Map<String, String> placeholders
    ) {
        java.util.Set<UUID> notified = new java.util.HashSet<>();
        for (Clan clan : List.of(result.firstClan(), result.secondClan())) {
            for (var member : clan.members()) {
                if (!notified.add(member.playerId())) {
                    continue;
                }
                Player online = Bukkit.getPlayer(member.playerId());
                if (online != null) {
                    messages.send(online, "general.war-ended-by-admin", placeholders);
                }
            }
        }
    }

    private void notifyDeletedClanMembers(Clan clan) {
        Map<String, String> placeholders = clanPlaceholders(clan);
        for (var member : clan.members()) {
            Player online = Bukkit.getPlayer(member.playerId());
            if (online != null) {
                messages.send(online, "general.clan-deleted", placeholders);
            }
        }
    }

    private void joinMode(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "catclans.clan.joinmode")) {
            return;
        }
        if (args.length != 2) {
            clanGui.openMain(player);
            return;
        }
        Optional<JoinMode> mode = JoinMode.fromCommand(args[1]);
        if (mode.isEmpty()) {
            usage(player, "/clan joinmode <invite|open>");
            return;
        }
        handle(
                clanService.setJoinMode(player.getUniqueId(), player.getName(), mode.get()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    messages.send(player, "general.join-mode-changed",
                            Map.of("mode", displayJoinMode(result.value().joinMode())));
                },
                player
        );
    }

    private void tag(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null
                || !requirePermission(player, "catclans.clan.tag.change")) {
            return;
        }
        if (args.length != 2) {
            usage(player, "/clan tag <Formatierter-Tag>");
            return;
        }
        handle(
                clanService.changeTag(
                        player.getUniqueId(),
                        player.getName(),
                        args[1]
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    messages.send(
                            player,
                            "general.tag-changed",
                            clanPlaceholders(result.value())
                    );
                },
                player
        );
    }

    private void profile(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "catclans.clan.profile")) {
            return;
        }
        CompletionStage<Optional<Clan>> lookup;
        String clanSearch = args.length >= 2 ? joinArguments(args, 1) : "";
        if (args.length >= 2) {
            lookup = clanService.findPublicClan(clanSearch);
        } else if (sender instanceof Player player) {
            lookup = clanService.findClanForPlayer(player.getUniqueId());
        } else {
            usage(sender, "/clan profile <Clan>");
            return;
        }
        handle(
                lookup,
                found -> {
                    if (found.isEmpty()) {
                        messages.send(sender, "errors.clan-not-found",
                                Map.of("clan", args.length >= 2 ? clanSearch : "-"));
                        return;
                    }
                    if (sender instanceof Player player) {
                        clanGui.openProfile(player, found.get());
                    } else {
                        sendProfile(sender, found.get());
                    }
                },
                sender
        );
    }

    private void members(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "catclans.clan.members")) {
            return;
        }
        if (!configs.main().getBoolean("features.gui.enabled", true)
                || !configs.gui().getBoolean("general.enabled", true)) {
            messages.send(player, "errors.feature-disabled");
            return;
        }
        String clanSearch = args.length >= 2 ? joinArguments(args, 1) : "";
        CompletionStage<Optional<Clan>> lookup = args.length >= 2
                ? clanService.findPublicClan(clanSearch)
                : clanService.findClanForPlayer(player.getUniqueId());
        handle(
                lookup,
                found -> {
                    if (found.isEmpty()) {
                        messages.send(player, args.length >= 2
                                        ? "errors.clan-not-found"
                                        : "errors.not-in-clan",
                                Map.of("clan", args.length >= 2 ? clanSearch : "-"));
                        return;
                    }
                    clanGui.openMembers(player, found.get());
                },
                player
        );
    }

    private void list(CommandSender sender) {
        if (!requirePermission(sender, "catclans.clan.list")) {
            return;
        }
        if (sender instanceof Player player) {
            clanGui.openClanList(player, 0);
            return;
        }
        handle(
                clanService.listClans(),
                clans -> {
                    messages.send(sender, "general.list-header",
                            Map.of("count", Integer.toString(clans.size())));
                    int maximumEntries = configs.performance().getInt(
                            "commands.maximum-list-entries",
                            100
                    );
                    for (int index = 0; index < Math.min(clans.size(), maximumEntries); index++) {
                        messages.send(sender, "general.list-entry",
                                clanPlaceholders(clans.get(index)));
                    }
                },
                sender
        );
    }

    private void top(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null
                || !requirePermission(player, "catclans.clan.ranking.view")) {
            return;
        }
        clanGui.openRanking(player, RankingCategory.TOTAL, 0);
    }

    private void sendProfile(CommandSender sender, Clan clan) {
        Map<String, String> values = new java.util.HashMap<>(clanPlaceholders(clan));
        values.put(
                "ranking_tier",
                clanService.cachedRankingPosition(clan.id())
                        .stream()
                        .mapToObj(position -> "#" + position)
                        .findFirst()
                        .orElseGet(() -> configs.messages().getString(
                                "profile.ranking-tier-unavailable",
                                "Unavailable"
                        ))
        );
        values.put("join_mode", displayJoinMode(clan.joinMode()));
        Map<String, String> placeholders = Map.copyOf(values);
        for (String path : List.of(
                "profile.header",
                "profile.tag",
                "profile.members",
                "profile.ranking-tier",
                "profile.join-mode"
        )) {
            messages.send(sender, path, placeholders);
        }
    }

    private String displayJoinMode(JoinMode mode) {
        return configs.messages().getString(
                mode == JoinMode.OPEN ? "values.join-mode.open" : "values.join-mode.invite-only",
                mode.name()
        );
    }

    private void sendFailure(
            CommandSender sender,
            OperationCode code,
            Map<String, String> providedPlaceholders
    ) {
        Map<String, String> placeholders = new java.util.HashMap<>(providedPlaceholders);
        placeholders.putIfAbsent("min", Integer.toString(
                code == OperationCode.INVALID_TAG
                        ? configs.main().getInt("clans.tags.min-length")
                        : configs.main().getInt("clans.names.min-length")
        ));
        placeholders.putIfAbsent("max", Integer.toString(
                code == OperationCode.INVALID_TAG
                        ? configs.main().getInt("clans.tags.max-length")
                        : configs.main().getInt("clans.names.max-length")
        ));

        String path = switch (code) {
            case ALREADY_IN_CLAN -> "errors.already-in-clan";
            case TARGET_ALREADY_IN_CLAN -> "errors.target-already-in-clan";
            case NOT_IN_CLAN -> "errors.not-in-clan";
            case CLAN_NOT_FOUND -> "errors.clan-not-found";
            case NAME_TAKEN -> "errors.name-taken";
            case TAG_TAKEN -> "errors.tag-taken";
            case INVALID_NAME -> "errors.invalid-name";
            case INVALID_TAG -> "errors.invalid-tag";
            case INVITE_REQUIRED -> "errors.invite-required";
            case INVITE_NOT_FOUND -> "errors.invite-not-found";
            case CLAN_FULL -> "errors.clan-full";
            case OWNER_CANNOT_LEAVE -> "errors.owner-cannot-leave";
            case CLAN_RIGHT_MISSING -> "errors.clan-right-missing";
            case OWNER_ONLY -> "errors.owner-only";
            case CANNOT_INVITE_SELF -> "errors.cannot-invite-self";
            case MEMBER_NOT_FOUND -> "errors.member-not-found";
            case CANNOT_KICK_SELF -> "errors.cannot-kick-self";
            case CANNOT_KICK_OWNER -> "errors.cannot-kick-owner";
            case RANK_TOO_LOW -> "errors.rank-too-low";
            case ROLE_NOT_FOUND -> "errors.role-not-found";
            case ROLE_LIMIT_REACHED -> "errors.role-limit-reached";
            case ROLE_NAME_TAKEN -> "errors.role-name-taken";
            case INVALID_ROLE_NAME -> "errors.invalid-role-name";
            case INVALID_ROLE_PRIORITY -> "errors.invalid-role-priority";
            case ROLE_PRIORITY_TAKEN -> "errors.role-priority-taken";
            case OWNER_ROLE_LOCKED -> "errors.owner-role-locked";
            case BATTLEPASS_DISABLED -> "errors.battlepass-disabled";
            case REWARD_NOT_AVAILABLE -> "errors.reward-not-available";
            case REWARD_ALREADY_CLAIMED -> "errors.reward-already-claimed";
            case REWARD_LIMIT_REACHED -> "errors.reward-limit-reached";
            case VAULT_DISABLED -> "errors.vault-disabled";
            case VAULT_PAGE_LOCKED -> "errors.vault-page-locked";
            case VAULT_ITEM_TOO_LARGE -> "errors.vault-item-too-large";
            case BANK_DISABLED -> "errors.bank-disabled";
            case INVALID_BANK_AMOUNT -> "errors.invalid-bank-amount";
            case BANK_INSUFFICIENT_FUNDS -> "errors.bank-insufficient-funds";
            case HOME_DISABLED -> "errors.home-disabled";
            case HOME_SLOT_LOCKED -> "errors.home-slot-locked";
            case HOME_NOT_SET -> "errors.home-not-set";
            case PVP_REWARD_COOLDOWN -> "errors.pvp-reward-cooldown";
            case DIPLOMACY_DISABLED -> "errors.diplomacy-disabled";
            case DIPLOMACY_SELF_TARGET -> "errors.diplomacy-self-target";
            case DIPLOMACY_REQUEST_NOT_FOUND -> "errors.diplomacy-request-not-found";
            case DIPLOMACY_REQUEST_PENDING -> "errors.diplomacy-request-pending";
            case DIPLOMACY_RELATION_EXISTS -> "errors.diplomacy-relation-exists";
            case DIPLOMACY_REQUEST_LIMIT -> "errors.diplomacy-request-limit";
            case INVALID_WAR_DURATION -> "errors.invalid-war-duration";
            case WAR_NOT_ACTIVE -> "errors.war-not-active";
            case SUCCESS -> throw new IllegalArgumentException("SUCCESS is not an error");
        };
        messages.send(sender, path, Map.copyOf(placeholders));
    }

    private <T> void handle(
            CompletionStage<T> stage,
            Consumer<T> success,
            CommandSender sender
    ) {
        handle(stage, success, sender, () -> {
        });
    }

    private <T> void handle(
            CompletionStage<T> stage,
            Consumer<T> success,
            CommandSender sender,
            Runnable onException
    ) {
        stage.whenComplete((value, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    onException.run();
                    plugin.getLogger().severe("Clan action failed: "
                            + rootMessage(error));
                    messages.send(sender, "errors.internal");
                    return;
                }
                success.accept(value);
            });
        });
    }

    private double configuredCreationPrice() {
        boolean enabled = configs.main().getBoolean("features.economy.enabled", true)
                && configs.economy().getBoolean("general.enabled", true)
                && configs.economy().getBoolean("clan-creation.charge-enabled", false);
        return enabled ? configs.economy().getDouble("clan-creation.price", 0.0) : 0.0;
    }

    private void refundCreationPrice(Player player, double amount) {
        if (amount <= 0.0
                || !configs.economy().getBoolean(
                "clan-creation.refund-on-create-failure",
                true
        )) {
            return;
        }
        EconomyTransaction refund = economy.deposit(player, amount);
        if (!refund.successful()) {
            plugin.getLogger().severe("Automatic refund failed for "
                    + player.getUniqueId() + ": " + refund.errorMessage());
            messages.send(player, "errors.economy-transaction-failed");
            return;
        }
        messages.send(player, "general.creation-price-refunded",
                Map.of("price", economy.format(amount)));
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "errors.players-only");
        return null;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages.send(sender, "errors.no-permission");
        return false;
    }

    private boolean canDeleteAnyClan(CommandSender sender) {
        return sender instanceof ConsoleCommandSender
                || sender.hasPermission("catclans.command.clanadmin")
                && sender.hasPermission("catclans.management.clan.delete");
    }

    private boolean canEndAnyWar(CommandSender sender) {
        return sender instanceof ConsoleCommandSender
                || sender.hasPermission("catclans.command.clanadmin")
                && sender.hasPermission("catclans.admin.war.end");
    }

    private List<String> adminTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (canDeleteAnyClan(sender)) {
                options.add("delete");
            }
            if (canEndAnyWar(sender)) {
                options.add("war");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("war")
                && canEndAnyWar(sender)) {
            return filter(List.of("end"), args[1]);
        }
        if ((args.length == 3 || args.length == 4)
                && args[0].equalsIgnoreCase("war")
                && args[1].equalsIgnoreCase("end")
                && canEndAnyWar(sender)) {
            String firstTag = args.length == 4 ? args[2] : "";
            return clanService.listClans().getNow(List.of()).stream()
                    .map(Clan::tag)
                    .filter(tag -> args.length == 3 || !tag.equalsIgnoreCase(firstTag))
                    .filter(tag -> startsWithIgnoreCase(tag, args[args.length - 1]))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("delete")
                && canDeleteAnyClan(sender)) {
            return filter(List.of("confirm"), args[args.length - 1]);
        }
        return List.of();
    }

    private void usage(CommandSender sender, String usage) {
        messages.send(sender, "errors.invalid-usage", Map.of("usage", usage));
    }

    private static Map<String, String> clanPlaceholders(Clan clan) {
        return Map.of(
                "clan", clan.name(),
                "tag", clan.tag(),
                "formatted_tag", clan.formattedTag(),
                "members", Integer.toString(clan.members().size()),
                "max_members", Integer.toString(clan.maxMembers())
        );
    }

    private static Map<String, String> warPlaceholders(AdminWarEndResult result) {
        boolean firstIsCanonicalFirst = result.result().firstClanId()
                .equals(result.firstClan().id());
        int firstDeaths = firstIsCanonicalFirst
                ? result.result().firstDeaths()
                : result.result().secondDeaths();
        int secondDeaths = firstIsCanonicalFirst
                ? result.result().secondDeaths()
                : result.result().firstDeaths();
        String winner = result.result().draw()
                ? "-"
                : result.result().winnerClanId().equals(result.firstClan().id())
                ? result.firstClan().name()
                : result.secondClan().name();
        String loser = result.result().draw()
                ? "-"
                : result.result().loserClanId().equals(result.firstClan().id())
                ? result.firstClan().name()
                : result.secondClan().name();
        return Map.of(
                "clan_one", result.firstClan().name(),
                "clan_one_tag", result.firstClan().tag(),
                "clan_one_deaths", Integer.toString(firstDeaths),
                "clan_two", result.secondClan().name(),
                "clan_two_tag", result.secondClan().tag(),
                "clan_two_deaths", Integer.toString(secondDeaths),
                "result", result.result().draw() ? "Unentschieden" : "Sieger: " + winner,
                "winner", winner,
                "loser", loser
        );
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (startsWithIgnoreCase(option, prefix)) {
                result.add(option);
            }
        }
        return List.copyOf(result);
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private static String joinArguments(String[] args, int startIndex) {
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length));
    }
}
