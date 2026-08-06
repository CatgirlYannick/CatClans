package dev.catgirlyannick.catclans.gui;

import dev.catgirlyannick.catclans.CatClansPlugin;
import dev.catgirlyannick.catclans.config.ConfigBundle;
import dev.catgirlyannick.catclans.config.RankPolicy;
import dev.catgirlyannick.catclans.message.MessageService;
import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.ClanRole;
import dev.catgirlyannick.catclans.model.ClanRoleOverview;
import dev.catgirlyannick.catclans.model.ClanRankingEntry;
import dev.catgirlyannick.catclans.model.DiplomacyRequest;
import dev.catgirlyannick.catclans.model.DiplomacyType;
import dev.catgirlyannick.catclans.model.DiplomacyView;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.MemberPermissionView;
import dev.catgirlyannick.catclans.model.PermissionOverride;
import dev.catgirlyannick.catclans.model.RolePermissionView;
import dev.catgirlyannick.catclans.model.RoleMoveDirection;
import dev.catgirlyannick.catclans.model.RankingCategory;
import dev.catgirlyannick.catclans.service.ClanService;
import dev.catgirlyannick.catclans.service.OperationCode;
import dev.catgirlyannick.catclans.service.OperationResult;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ClanGuiController implements Listener {

    private final CatClansPlugin plugin;
    private final ConfigBundle configs;
    private final ClanService clanService;
    private final MessageService messages;
    private final RankPolicy rankPolicy;
    private final DateTimeFormatter dateFormatter;
    private final NamespacedKey clanIdKey;
    private final NamespacedKey playerIdKey;
    private final NamespacedKey roleIdKey;
    private final NamespacedKey permissionKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey requestIdKey;
    private final NamespacedKey rankingCategoryKey;
    private final Map<String, Material> materialCache = new HashMap<>();
    private final Map<String, Integer> slotCache = new HashMap<>();
    private final Map<String, ItemStack> decorationCache = new HashMap<>();
    private final Map<String, String> permissionDisplayNames;
    private final GuiNavigationHistory navigation = new GuiNavigationHistory();
    private final Map<UUID, PendingRoleNameInput> pendingRoleNameInputs =
            new ConcurrentHashMap<>();
    private final Map<UUID, PendingRolePriorityInput> pendingRolePriorityInputs =
            new ConcurrentHashMap<>();
    private final Map<UUID, PendingClanEditInput> pendingClanEditInputs =
            new ConcurrentHashMap<>();
    private final PlainTextComponentSerializer plainText =
            PlainTextComponentSerializer.plainText();
    private ClanFeatureGuiController featureGui;

    public ClanGuiController(
            CatClansPlugin plugin,
            ConfigBundle configs,
            ClanService clanService,
            MessageService messages,
            RankPolicy rankPolicy
    ) {
        this.plugin = plugin;
        this.configs = configs;
        this.clanService = clanService;
        this.messages = messages;
        this.rankPolicy = rankPolicy;
        this.dateFormatter = DateTimeFormatter.ofPattern(
                configs.gui().getString("members-menu.date-time-pattern", "dd.MM.yyyy HH:mm")
        ).withZone(ZoneId.systemDefault());
        this.clanIdKey = new NamespacedKey(plugin, "gui_clan_id");
        this.playerIdKey = new NamespacedKey(plugin, "gui_player_id");
        this.roleIdKey = new NamespacedKey(plugin, "gui_role_id");
        this.permissionKey = new NamespacedKey(plugin, "gui_permission");
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.requestIdKey = new NamespacedKey(plugin, "gui_request_id");
        this.rankingCategoryKey = new NamespacedKey(plugin, "gui_ranking_category");
        this.permissionDisplayNames = loadPermissionDisplayNames(configs);
    }

    public void featureGui(ClanFeatureGuiController featureGui) {
        this.featureGui = featureGui;
    }

    public boolean hasPendingStorageOperation(UUID clanId) {
        return featureGui != null && featureGui.hasPendingStorageOperation(clanId);
    }

    public void openMain(Player player) {
        renderMain(player, true);
    }

    private void renderMain(Player player, boolean resetHistory) {
        Optional<Clan> ownClan = clanService.findCachedClanForPlayer(player.getUniqueId());
        ClanMenuHolder holder = holder(ClanMenuType.MAIN, ownClan.map(Clan::id).orElse(null));
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("main-menu.size", 45),
                "main-menu.title",
                ownClan.map(this::clanPlaceholders).orElse(Map.of())
        );
        fill(inventory);

        set(inventory, "main-menu.browse", Map.of());
        if (clanService.rankingsEnabled()) {
            set(inventory, "main-menu.rankings", Map.of());
        }
        set(inventory, "main-menu.invitations", Map.of());
        if (ownClan.isEmpty()) {
            set(inventory, "main-menu.create", Map.of());
        } else {
            Clan clan = ownClan.get();
            Map<String, String> placeholders = clanPlaceholders(clan);
            placeholders = with(placeholders, "mode", displayJoinMode(clan.joinMode()));
            set(inventory, "main-menu.profile", placeholders, clanHead(clan, "main-menu.profile", placeholders));
            set(inventory, "main-menu.members", placeholders);
            if (clan.ownerId().equals(player.getUniqueId())) {
                set(inventory, "main-menu.join-mode", placeholders);
            }
            if (!clan.ownerId().equals(player.getUniqueId())) {
                set(inventory, "main-menu.leave", placeholders);
            }
        }
        set(inventory, "main-menu.close", Map.of());
        if (player.hasPermission("catclans.admin.battlepass.rewards")) {
            set(inventory, "main-menu.admin-battlepass", Map.of());
        }
        if (resetHistory) {
            navigation.clear(player.getUniqueId());
            navigation.replaceNext(player.getUniqueId(), ClanMenuState.from(holder));
        }
        openMenu(player, inventory);
    }

    public void openClanList(Player player, int requestedPage) {
        List<Clan> clans = clanService.listClans().getNow(List.of());
        List<Integer> slots = configs.gui().getIntegerList("clan-list-menu.clan-slots");
        int pageSize = Math.max(1, slots.size());
        int pages = Math.max(1, (clans.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Map<String, String> pageValues = Map.of(
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(pages),
                "count", Integer.toString(clans.size())
        );
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.CLAN_LIST, null, null, page, null, null, null
        );
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("clan-list-menu.size", 54),
                "clan-list-menu.title",
                pageValues
        );
        fill(inventory);
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < clans.size(); index++) {
            Clan clan = clans.get(start + index);
            ItemStack head = clanHead(clan, "clan-list-menu.clan", clanPlaceholders(clan));
            inventory.setItem(slots.get(index), head);
        }
        if (page > 0) {
            set(inventory, "clan-list-menu.previous", pageValues);
        }
        if (page + 1 < pages) {
            set(inventory, "clan-list-menu.next", pageValues);
        }
        set(inventory, "clan-list-menu.back", pageValues);
        set(inventory, "clan-list-menu.close", pageValues);
        openMenu(player, inventory);
    }

    public void openRanking(
            Player player,
            RankingCategory category,
            int requestedPage
    ) {
        if (!player.hasPermission("catclans.clan.ranking.view")) {
            messages.send(player, "errors.no-permission");
            return;
        }
        if (!clanService.rankingsEnabled()
                || !configs.rankings().getBoolean(
                "categories." + category.configKey(),
                true
        )) {
            messages.send(player, "errors.feature-disabled");
            return;
        }
        handle(
                clanService.ranking(category),
                entries -> renderRanking(player, category, requestedPage, entries),
                player
        );
    }

    private void renderRanking(
            Player player,
            RankingCategory category,
            int requestedPage,
            List<ClanRankingEntry> entries
    ) {
        List<Integer> slots = configs.gui().getIntegerList("ranking-menu.entry-slots");
        int pageSize = Math.max(1, slots.size());
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Map<String, String> pageValues = Map.of(
                "category", rankingCategoryLabel(category),
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(pages),
                "count", Integer.toString(entries.size()),
                "money_status", clanService.rankingMoneyAvailable()
                        ? rankingValue("money-enabled", "Aktiv")
                        : rankingValue("money-disabled", "Disabled")
        );
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.RANKING,
                null,
                null,
                page,
                category.name(),
                null,
                null
        );
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("ranking-menu.size", 54),
                "ranking-menu.title",
                pageValues
        );
        fill(inventory);
        for (RankingCategory selectable : RankingCategory.values()) {
            if (!configs.rankings().getBoolean(
                    "categories." + selectable.configKey(),
                    true
            )) {
                continue;
            }
            String path = "ranking-menu.categories." + selectable.configKey();
            ItemStack item = configuredItem(path, pageValues, Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(
                    rankingCategoryKey,
                    PersistentDataType.STRING,
                    selectable.name()
            );
            item.setItemMeta(meta);
            inventory.setItem(slot(path, -1), item);
        }

        if (category == RankingCategory.MONEY
                && !clanService.rankingMoneyAvailable()) {
            set(inventory, "ranking-menu.money-disabled", pageValues);
        } else {
            int start = page * pageSize;
            for (int index = 0;
                 index < pageSize && start + index < entries.size();
                 index++) {
                ClanRankingEntry entry = entries.get(start + index);
                inventory.setItem(
                        slots.get(index),
                        clanHead(
                                entry.clan(),
                                "ranking-menu.entry",
                                rankingPlaceholders(entry, category)
                        )
                );
            }
        }
        if (page > 0) {
            set(inventory, "ranking-menu.previous", pageValues);
        }
        if (page + 1 < pages) {
            set(inventory, "ranking-menu.next", pageValues);
        }
        set(inventory, "ranking-menu.info", pageValues);
        set(inventory, "ranking-menu.back", pageValues);
        set(inventory, "ranking-menu.close", pageValues);
        openMenu(player, inventory);
    }

    public void openProfile(Player player, Clan clan) {
        openProfile(player, clan, false);
    }

    public void openMembers(Player player, Clan clan) {
        Map<String, String> values = clanPlaceholders(clan);
        ClanMenuHolder holder = holder(ClanMenuType.MEMBERS, clan.id());
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("members-menu.size", 54),
                "members-menu.title",
                values
        );
        fill(inventory);

        List<Integer> memberSlots = configs.gui().getIntegerList("members-menu.member-slots");
        int index = 0;
        for (ClanMember member : clan.members()) {
            if (index >= memberSlots.size()) {
                break;
            }
            inventory.setItem(memberSlots.get(index++), memberHead(member));
        }
        ItemStack locked = configuredItem(
                "members-menu.locked-slot",
                Map.of(),
                Material.RED_STAINED_GLASS_PANE
        );
        while (index < memberSlots.size()) {
            int slot = memberSlots.get(index);
            if (index >= clan.maxMembers()) {
                inventory.setItem(slot, locked);
            }
            index++;
        }
        inventory.setItem(
                configs.gui().getInt("members-menu.info.slot", 4),
                clanHead(clan, "members-menu.info", values)
        );
        set(inventory, "members-menu.back", values);
        set(inventory, "members-menu.close", values);
        openMenu(player, inventory);
    }

    public void openInvites(Player player) {
        handle(
                clanService.findPendingInvites(player.getUniqueId()),
                clans -> openInvites(player, clans),
                player
        );
    }

    public void openInviteConfirmation(Player player, Player target) {
        Optional<Clan> clan = clanService.findCachedClanForPlayer(player.getUniqueId());
        if (clan.isEmpty()) {
            messages.send(player, "errors.not-in-clan");
            return;
        }
        openConfirmation(
                player,
                ConfirmAction.INVITE,
                clan.get(),
                target.getUniqueId(),
                null,
                null
        );
    }

    public void openPermissionHome(Player player) {
        if (!requirePermission(player, "catclans.clan.permissions.manage")) {
            return;
        }
        Optional<Clan> clan = requireOwnedClan(player);
        if (clan.isEmpty()) {
            return;
        }
        ClanMenuHolder holder = holder(ClanMenuType.PERMISSION_HOME, clan.get().id());
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("permission-home-menu.size", 27),
                "permission-home-menu.title",
                clanPlaceholders(clan.get())
        );
        fill(inventory);
        set(inventory, "permission-home-menu.members", Map.of());
        set(inventory, "permission-home-menu.roles", Map.of());
        set(inventory, "permission-home-menu.back", Map.of());
        set(inventory, "permission-home-menu.close", Map.of());
        openMenu(player, inventory);
    }

    private void openRoleList(Player player) {
        handle(
                clanService.findRolesForOwner(player.getUniqueId()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    renderRoleList(player, result.value());
                },
                player
        );
    }

    private void renderRoleList(Player player, ClanRoleOverview overview) {
        Optional<Clan> clan = requireOwnedClan(player);
        if (clan.isEmpty()) {
            return;
        }
        Map<String, String> values = Map.of(
                "roles", Integer.toString(overview.roles().size()),
                "max_roles", Integer.toString(overview.maximumRoles())
        );
        ClanMenuHolder holder = holder(ClanMenuType.ROLE_LIST, clan.get().id());
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("role-list-menu.size", 54),
                "role-list-menu.title",
                values
        );
        fill(inventory);
        List<Integer> slots = configs.gui().getIntegerList("role-list-menu.role-slots");
        for (int index = 0; index < Math.min(slots.size(), overview.roles().size()); index++) {
            ClanRole role = overview.roles().get(index);
            inventory.setItem(slots.get(index), roleItem(role, "role-list-menu.role"));
        }
        if (overview.canCreateRole()) {
            ItemStack create = configuredItem(
                    "role-list-menu.create",
                    values,
                    Material.LIME_DYE
            );
            ItemMeta meta = create.getItemMeta();
            meta.getPersistentDataContainer().set(
                    actionKey,
                    PersistentDataType.STRING,
                    "create_role"
            );
            create.setItemMeta(meta);
            inventory.setItem(slot("role-list-menu.create", 49), create);
        } else {
            set(inventory, "role-list-menu.locked", values);
        }
        set(inventory, "role-list-menu.back", values);
        set(inventory, "role-list-menu.close", values);
        openMenu(player, inventory);
    }

    private void openMemberPermissionList(Player player) {
        Optional<Clan> clan = requireOwnedClan(player);
        if (clan.isEmpty()) {
            return;
        }
        ClanMenuHolder holder = holder(
                ClanMenuType.MEMBER_PERMISSION_LIST,
                clan.get().id()
        );
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("member-permission-list-menu.size", 54),
                "member-permission-list-menu.title",
                clanPlaceholders(clan.get())
        );
        fill(inventory);
        List<Integer> slots = configs.gui()
                .getIntegerList("member-permission-list-menu.member-slots");
        for (int index = 0; index < Math.min(slots.size(), clan.get().members().size()); index++) {
            ClanMember member = clan.get().members().get(index);
            Map<String, String> values = Map.of(
                    "player", member.lastKnownName(),
                    "role", clanService.displayRole(clan.get().id(), member)
            );
            inventory.setItem(
                    slots.get(index),
                    playerHead(
                            member.playerId(),
                            "member-permission-list-menu.member",
                            values
                    )
            );
        }
        set(inventory, "member-permission-list-menu.back", Map.of());
        set(inventory, "member-permission-list-menu.close", Map.of());
        openMenu(player, inventory);
    }

    private void openRolePermissions(Player player, String roleId) {
        handle(
                clanService.findRolePermissions(player.getUniqueId(), roleId),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    renderRolePermissions(player, result.value());
                },
                player
        );
    }

    private void renderRolePermissions(Player player, RolePermissionView view) {
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.ROLE_PERMISSIONS,
                view.role().clanId(),
                null,
                0,
                view.role().id(),
                null,
                null
        );
        Map<String, String> values = Map.of(
                "role", view.role().displayName(),
                "priority", Integer.toString(view.role().priority())
        );
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("role-permissions-menu.size", 54),
                "role-permissions-menu.title",
                values
        );
        fill(inventory);
        List<Integer> slots = configs.gui()
                .getIntegerList("role-permissions-menu.permission-slots");
        for (int index = 0; index < Math.min(slots.size(), view.permissions().size()); index++) {
            String permission = view.permissions().get(index);
            inventory.setItem(
                    slots.get(index),
                    permissionItem(
                            permission,
                            Boolean.TRUE.equals(view.values().get(permission))
                                    ? PermissionOverride.ALLOW
                                    : PermissionOverride.DENY
                    )
            );
        }
        set(inventory, "role-permissions-menu.rename", values);
        if (!"owner".equals(view.role().id())) {
            set(inventory, "role-permissions-menu.move-up", values);
            set(inventory, "role-permissions-menu.move-down", values);
        }
        set(inventory, "role-permissions-menu.priority", values);
        set(inventory, "role-permissions-menu.back", values);
        set(inventory, "role-permissions-menu.close", values);
        openMenu(player, inventory);
    }

    private void openMemberPermissions(Player player, UUID memberId) {
        handle(
                clanService.findMemberPermissions(player.getUniqueId(), memberId),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    renderMemberPermissions(player, result.value());
                },
                player
        );
    }

    private void renderMemberPermissions(Player player, MemberPermissionView view) {
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.MEMBER_PERMISSIONS,
                view.role().clanId(),
                view.member().playerId(),
                0,
                null,
                null,
                null
        );
        Map<String, String> values = Map.of(
                "player", view.member().lastKnownName(),
                "role", view.role().displayName()
        );
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("member-permissions-menu.size", 54),
                "member-permissions-menu.title",
                values
        );
        fill(inventory);
        List<Integer> slots = configs.gui()
                .getIntegerList("member-permissions-menu.permission-slots");
        for (int index = 0; index < Math.min(slots.size(), view.permissions().size()); index++) {
            String permission = view.permissions().get(index);
            inventory.setItem(
                    slots.get(index),
                    permissionItem(permission, view.state(permission))
            );
        }
        set(inventory, "member-permissions-menu.role", values);
        set(inventory, "member-permissions-menu.back", values);
        set(inventory, "member-permissions-menu.close", values);
        openMenu(player, inventory);
    }

    private void openRoleAssignment(Player player, UUID memberId) {
        handle(
                clanService.findRolesForOwner(player.getUniqueId()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    Optional<Clan> clan = requireOwnedClan(player);
                    if (clan.isEmpty()) {
                        return;
                    }
                    ClanMenuHolder holder = new ClanMenuHolder(
                            ClanMenuType.ROLE_ASSIGNMENT,
                            clan.get().id(),
                            memberId,
                            0,
                            null,
                            null,
                            null
                    );
                    Inventory inventory = createInventory(
                            holder,
                            configs.gui().getInt("role-assignment-menu.size", 54),
                            "role-assignment-menu.title",
                            Map.of()
                    );
                    fill(inventory);
                    List<Integer> slots = configs.gui()
                            .getIntegerList("role-assignment-menu.role-slots");
                    List<ClanRole> assignable = result.value().roles().stream()
                            .filter(role -> !"owner".equals(role.id()))
                            .toList();
                    for (int index = 0; index < Math.min(slots.size(), assignable.size()); index++) {
                        inventory.setItem(
                                slots.get(index),
                                roleItem(assignable.get(index), "role-assignment-menu.role")
                        );
                    }
                    set(inventory, "role-assignment-menu.back", Map.of());
                    set(inventory, "role-assignment-menu.close", Map.of());
                    openMenu(player, inventory);
                },
                player
        );
    }

    private void showCreateCommandHelp(Player player) {
        if (!player.hasPermission("catclans.clan.create")) {
            messages.send(player, "errors.no-permission");
            return;
        }
        if (clanService.findCachedClanForPlayer(player.getUniqueId()).isPresent()) {
            messages.send(player, "errors.already-in-clan");
            return;
        }
        player.closeInventory();
        messages.sendCreateCommandHelp(player);
    }

    public void openLeaveConfirmation(Player player) {
        Optional<Clan> clan = clanService.findCachedClanForPlayer(player.getUniqueId());
        if (clan.isEmpty()) {
            messages.send(player, "errors.not-in-clan");
            return;
        }
        if (clan.get().ownerId().equals(player.getUniqueId())) {
            messages.send(player, "errors.owner-cannot-leave");
            return;
        }
        openConfirmation(player, ConfirmAction.LEAVE, clan.get(), null, null, null);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClanMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        switch (holder.type()) {
            case MAIN -> handleMainClick(player, event.getRawSlot());
            case CLAN_LIST -> handleClanListClick(player, holder, event);
            case RANKING -> handleRankingClick(player, holder, event);
            case PROFILE -> handleProfileClick(player, holder, event);
            case CLAN_EDIT -> handleClanEditClick(player, holder, event.getRawSlot());
            case MEMBERS -> handleMembersClick(player, holder, event);
            case INVITES -> handleInvitesClick(player, event);
            case PERMISSION_HOME -> handlePermissionHomeClick(player, event.getRawSlot());
            case ROLE_LIST -> handleRoleListClick(player, event);
            case MEMBER_PERMISSION_LIST -> handleMemberPermissionListClick(player, event);
            case ROLE_PERMISSIONS -> handleRolePermissionsClick(player, holder, event);
            case MEMBER_PERMISSIONS -> handleMemberPermissionsClick(player, holder, event);
            case ROLE_ASSIGNMENT -> handleRoleAssignmentClick(player, holder, event);
            case ALLY_REQUESTS -> handleAllyRequestsClick(player, holder, event);
            case WAR_DURATION -> handleWarDurationClick(player, holder, event.getRawSlot());
            case DIPLOMACY_RESPONSE -> handleDiplomacyResponseClick(
                    player,
                    holder,
                    event.getRawSlot()
            );
            case CONFIRMATION -> handleConfirmationClick(player, holder, event.getRawSlot());
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClanMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingRoleNameInputs.remove(playerId);
        pendingRolePriorityInputs.remove(playerId);
        pendingClanEditInputs.remove(playerId);
        navigation.clear(playerId);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingRoleNameInput pendingName = pendingRoleNameInputs.get(player.getUniqueId());
        PendingRolePriorityInput pendingPriority = pendingRolePriorityInputs.get(
                player.getUniqueId()
        );
        PendingClanEditInput pendingClanEdit = pendingClanEditInputs.get(
                player.getUniqueId()
        );
        if (pendingName == null && pendingPriority == null && pendingClanEdit == null) {
            return;
        }
        event.setCancelled(true);
        String input = plainText.serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(
                plugin,
                () -> {
                    if (pendingClanEdit != null) {
                        handleClanEditChatInput(player, pendingClanEdit, input);
                    } else if (pendingName != null) {
                        handleRoleNameChatInput(player, pendingName, input);
                    } else {
                        handleRolePriorityChatInput(player, pendingPriority, input);
                    }
                }
        );
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == slot("main-menu.close", 44)) {
            player.closeInventory();
            return;
        }
        if (slot == slot("main-menu.admin-battlepass", 49)
                && featureGui != null
                && player.hasPermission("catclans.admin.battlepass.rewards")) {
            featureGui.openBattlepassAdmin(player, 0);
            return;
        }
        if (slot == slot("main-menu.browse", 28)) {
            if (requirePermission(player, "catclans.clan.list")) {
                openClanList(player, 0);
            }
            return;
        }
        if (slot == slot("main-menu.rankings", 31)) {
            openRanking(player, RankingCategory.TOTAL, 0);
            return;
        }
        if (slot == slot("main-menu.invitations", 32)) {
            if (requirePermission(player, "catclans.clan.join")) {
                openInvites(player);
            }
            return;
        }
        Optional<Clan> ownClan = clanService.findCachedClanForPlayer(player.getUniqueId());
        if (ownClan.isEmpty()) {
            if (slot == slot("main-menu.create", 22)) {
                showCreateCommandHelp(player);
            }
            return;
        }
        Clan clan = ownClan.get();
        if (slot == slot("main-menu.profile", 10)) {
            openProfile(player, clan);
        } else if (slot == slot("main-menu.members", 12)
                && requirePermission(player, "catclans.clan.members")) {
            openMembers(player, clan);
        } else if (slot == slot("main-menu.join-mode", 16)
                && clan.ownerId().equals(player.getUniqueId())
                && requirePermission(player, "catclans.clan.joinmode")) {
            ConfirmAction action = clan.joinMode() == JoinMode.OPEN
                    ? ConfirmAction.JOIN_MODE_INVITE
                    : ConfirmAction.JOIN_MODE_OPEN;
            openConfirmation(player, action, clan, null, null, null);
        } else if (slot == slot("main-menu.leave", 34)
                && !clan.ownerId().equals(player.getUniqueId())
                && requirePermission(player, "catclans.clan.leave")) {
            openLeaveConfirmation(player);
        }
    }

    private void handleClanListClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        int slot = event.getRawSlot();
        if (slot == slot("clan-list-menu.back", 45)) {
            navigateBack(player);
        } else if (slot == slot("clan-list-menu.previous", 48)) {
            replaceNext(player, menuState(ClanMenuType.CLAN_LIST, null, holder.page() - 1));
            openClanList(player, holder.page() - 1);
        } else if (slot == slot("clan-list-menu.next", 50)) {
            replaceNext(player, menuState(ClanMenuType.CLAN_LIST, null, holder.page() + 1));
            openClanList(player, holder.page() + 1);
        } else if (slot == slot("clan-list-menu.close", 53)) {
            player.closeInventory();
        } else {
            readUuid(event.getCurrentItem(), clanIdKey)
                    .flatMap(clanService::findCachedClan)
                    .ifPresent(clan -> openProfile(player, clan));
        }
    }

    private void handleRankingClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        int clickedSlot = event.getRawSlot();
        if (clickedSlot == slot("ranking-menu.back", 45)) {
            navigateBack(player);
            return;
        }
        if (clickedSlot == slot("ranking-menu.close", 53)) {
            player.closeInventory();
            return;
        }

        RankingCategory current = rankingCategory(holder.firstValue());
        if (clickedSlot == slot("ranking-menu.previous", 48)) {
            replaceNext(
                    player,
                    rankingMenuState(current, holder.page() - 1)
            );
            openRanking(player, current, holder.page() - 1);
            return;
        }
        if (clickedSlot == slot("ranking-menu.next", 50)) {
            replaceNext(
                    player,
                    rankingMenuState(current, holder.page() + 1)
            );
            openRanking(player, current, holder.page() + 1);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        String categoryName = clicked.getItemMeta()
                .getPersistentDataContainer()
                .get(rankingCategoryKey, PersistentDataType.STRING);
        if (categoryName != null) {
            RankingCategory selected = rankingCategory(categoryName);
            replaceNext(
                    player,
                    rankingMenuState(selected, 0)
            );
            openRanking(player, selected, 0);
            return;
        }
        readUuid(clicked, clanIdKey)
                .flatMap(clanService::findCachedClan)
                .ifPresent(clan -> openProfile(player, clan));
    }

    private void handleProfileClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        Optional<Clan> found = clanService.findCachedClan(holder.clanId());
        if (found.isEmpty()) {
            messages.send(player, "errors.clan-not-found", Map.of("clan", "-"));
            openClanList(player, 0);
            return;
        }
        Clan clan = found.get();
        boolean ownClan = clan.id().equals(clanService.findCachedClanForPlayer(
                player.getUniqueId()).map(Clan::id).orElse(null));
        int slot = event.getRawSlot();
        if (slot == slot("profile-menu.members", 11)
                && requirePermission(player, "catclans.clan.members")) {
            openMembers(player, clan);
        } else if (slot == slot("profile-menu.permissions", 10)
                && clan.ownerId().equals(player.getUniqueId())) {
            openPermissionHome(player);
        } else if (slot == slot("profile-menu.edit", 30)
                && ownClan
                && (player.hasPermission("catclans.clan.name.change")
                || player.hasPermission("catclans.clan.tag.change"))) {
            openClanEdit(player, clan);
        } else if (slot == slot("profile-menu.battlepass", 29)
                && featureGui != null
                && clan.id().equals(clanService.findCachedClanForPlayer(
                player.getUniqueId()).map(Clan::id).orElse(null))) {
            featureGui.openBattlepass(player, clan, 0);
        } else if (slot == slot("profile-menu.bank", 31)
                && featureGui != null
                && clan.id().equals(clanService.findCachedClanForPlayer(
                player.getUniqueId()).map(Clan::id).orElse(null))) {
            featureGui.openBank(player, clan);
        } else if (slot == slot("profile-menu.vault", 33)
                && featureGui != null
                && clan.id().equals(clanService.findCachedClanForPlayer(
                player.getUniqueId()).map(Clan::id).orElse(null))) {
            featureGui.openVault(player, clan, 1);
        } else if (slot == slot("profile-menu.homes", 32)
                && featureGui != null
                && ownClan) {
            featureGui.openHomes(player, clan, 0);
        } else if (slot == slot("profile-menu.primary-action", 15)
                && ownClan
                && clanService.alliancesEnabled()
                && requirePermission(player, "catclans.clan.diplomacy")) {
            openAllyRequests(player, 0);
        } else if (slot == slot("profile-menu.primary-action", 15)) {
            if ("invite".equals(holder.firstValue())) {
                openConfirmation(player, ConfirmAction.ACCEPT, clan, null, null, null);
            } else if (clan.joinMode() == JoinMode.OPEN
                    && clanService.findCachedClanForPlayer(player.getUniqueId()).isEmpty()
                    && requirePermission(player, "catclans.clan.join")) {
                openConfirmation(player, ConfirmAction.JOIN, clan, null, null, null);
            }
        } else if (slot == slot("profile-menu.deny", 16)
                && "invite".equals(holder.firstValue())) {
            openConfirmation(player, ConfirmAction.DENY, clan, null, null, null);
        } else if (slot == slot("profile-menu.ally-action", 21)
                && requirePermission(player, "catclans.clan.diplomacy")) {
            if (holder.firstValue() != null
                    && holder.firstValue().startsWith("ally:incoming:")) {
                openDiplomacyResponse(
                        player,
                        clan,
                        UUID.fromString(holder.firstValue().substring("ally:incoming:".length())),
                        DiplomacyType.ALLY,
                        0
                );
            } else if (holder.firstValue() == null) {
                openConfirmation(
                        player,
                        ConfirmAction.ALLY_REQUEST,
                        clan,
                        null,
                        null,
                        null
                );
            }
        } else if (slot == slot("profile-menu.war-action", 23)
                && requirePermission(player, "catclans.clan.diplomacy")) {
            if (holder.secondValue() != null
                    && holder.secondValue().startsWith("war:incoming:")) {
                String[] parts = holder.secondValue().split(":");
                openDiplomacyResponse(
                        player,
                        clan,
                        UUID.fromString(parts[2]),
                        DiplomacyType.WAR,
                        Integer.parseInt(parts[3])
                );
            } else if (holder.secondValue() == null) {
                openWarDuration(player, clan);
            }
        } else if (slot == slot("profile-menu.back", 18)) {
            navigateBack(player);
        } else if (slot == slot("profile-menu.close", 26)) {
            player.closeInventory();
        }
    }

    private void handleClanEditClick(
            Player player,
            ClanMenuHolder holder,
            int slot
    ) {
        Optional<Clan> clan = clanService.findCachedClanForPlayer(player.getUniqueId())
                .filter(current -> current.id().equals(holder.clanId()));
        if (clan.isEmpty()) {
            messages.send(player, "errors.not-in-clan");
            openMain(player);
            return;
        }
        if (slot == slot("clan-edit-menu.name", 11)
                && requirePermission(player, "catclans.clan.name.change")) {
            beginClanEditChatInput(player, clan.get(), ClanEditField.NAME);
        } else if (slot == slot("clan-edit-menu.tag", 15)
                && requirePermission(player, "catclans.clan.tag.change")) {
            beginClanEditChatInput(player, clan.get(), ClanEditField.TAG);
        } else if (slot == slot("clan-edit-menu.back", 18)) {
            navigateBack(player);
        } else if (slot == slot("clan-edit-menu.close", 26)) {
            player.closeInventory();
        }
    }

    private void handleMembersClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        if (event.getRawSlot() == slot("members-menu.back", 45)) {
            navigateBack(player);
            return;
        }
        if (event.getRawSlot() == slot("members-menu.close", 49)) {
            player.closeInventory();
            return;
        }
        Optional<UUID> targetId = readUuid(event.getCurrentItem(), playerIdKey);
        Optional<Clan> clan = clanService.findCachedClan(holder.clanId());
        if (targetId.isEmpty() || clan.isEmpty()
                || !clan.get().id().equals(
                clanService.findCachedClanForPlayer(player.getUniqueId())
                        .map(Clan::id)
                        .orElse(null)
        )) {
            return;
        }
        Optional<ClanMember> target = clan.get().member(targetId.get());
        if (target.isEmpty()
                || !player.hasPermission("catclans.clan.kick")
                || targetId.get().equals(player.getUniqueId())
                || clan.get().ownerId().equals(targetId.get())) {
            return;
        }
        openConfirmation(player, ConfirmAction.KICK, clan.get(), targetId.get(), null, null);
    }

    private void handleInvitesClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == slot("invites-menu.back", 45)) {
            navigateBack(player);
        } else if (slot == slot("invites-menu.close", 53)) {
            player.closeInventory();
        } else {
            readUuid(event.getCurrentItem(), clanIdKey)
                    .flatMap(clanService::findCachedClan)
                    .ifPresent(clan -> openProfile(player, clan, true));
        }
    }

    private void handlePermissionHomeClick(Player player, int slot) {
        if (slot == slot("permission-home-menu.members", 11)) {
            openMemberPermissionList(player);
        } else if (slot == slot("permission-home-menu.roles", 15)) {
            openRoleList(player);
        } else if (slot == slot("permission-home-menu.back", 18)) {
            navigateBack(player);
        } else if (slot == slot("permission-home-menu.close", 26)) {
            player.closeInventory();
        }
    }

    private void handleRoleListClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if ("create_role".equals(readString(
                event.getCurrentItem(),
                actionKey
        ).orElse(null))) {
            beginRoleNameChatInput(player, null);
        } else if (slot == slot("role-list-menu.back", 45)) {
            navigateBack(player);
        } else if (slot == slot("role-list-menu.close", 53)) {
            player.closeInventory();
        } else {
            readString(event.getCurrentItem(), roleIdKey)
                    .ifPresent(roleId -> openRolePermissions(player, roleId));
        }
    }

    private void handleMemberPermissionListClick(
            Player player,
            InventoryClickEvent event
    ) {
        int slot = event.getRawSlot();
        if (slot == slot("member-permission-list-menu.back", 45)) {
            navigateBack(player);
        } else if (slot == slot("member-permission-list-menu.close", 53)) {
            player.closeInventory();
        } else {
            readUuid(event.getCurrentItem(), playerIdKey)
                    .ifPresent(memberId -> openMemberPermissions(player, memberId));
        }
    }

    private void handleRolePermissionsClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        int slot = event.getRawSlot();
        if (slot == slot("role-permissions-menu.rename", 4)) {
            beginRoleNameChatInput(player, holder.firstValue());
        } else if (slot == slot("role-permissions-menu.move-up", 2)) {
            moveRole(player, holder.firstValue(), RoleMoveDirection.UP);
        } else if (slot == slot("role-permissions-menu.move-down", 6)) {
            moveRole(player, holder.firstValue(), RoleMoveDirection.DOWN);
        } else if (slot == slot("role-permissions-menu.priority", 49)) {
            beginRolePriorityChatInput(player, holder.firstValue());
        } else if (slot == slot("role-permissions-menu.back", 45)) {
            navigateBack(player);
        } else if (slot == slot("role-permissions-menu.close", 53)) {
            player.closeInventory();
        } else {
            readString(event.getCurrentItem(), permissionKey).ifPresent(permission ->
                    handle(
                            clanService.toggleRolePermission(
                                    player.getUniqueId(),
                                    player.getName(),
                                    holder.firstValue(),
                                    permission
                            ),
                            result -> {
                                if (!result.successful()) {
                                    sendFailure(player, result.code(), Map.of());
                                    return;
                                }
                                renderRolePermissions(player, result.value());
                            },
                            player
                    )
            );
        }
    }

    private void moveRole(
            Player player,
            String roleId,
            RoleMoveDirection direction
    ) {
        handle(
                clanService.moveRole(
                        player.getUniqueId(),
                        player.getName(),
                        roleId,
                        direction
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    renderRolePermissions(player, result.value());
                },
                player
        );
    }

    private void handleMemberPermissionsClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        int slot = event.getRawSlot();
        if (slot == slot("member-permissions-menu.role", 4)) {
            openRoleAssignment(player, holder.targetId());
        } else if (slot == slot("member-permissions-menu.back", 45)) {
            navigateBack(player);
        } else if (slot == slot("member-permissions-menu.close", 53)) {
            player.closeInventory();
        } else {
            readString(event.getCurrentItem(), permissionKey).ifPresent(permission ->
                    handle(
                            clanService.cycleMemberPermission(
                                    player.getUniqueId(),
                                    player.getName(),
                                    holder.targetId(),
                                    permission
                            ),
                            result -> {
                                if (!result.successful()) {
                                    sendFailure(player, result.code(), Map.of());
                                    return;
                                }
                                renderMemberPermissions(player, result.value());
                            },
                            player
                    )
            );
        }
    }

    private void handleRoleAssignmentClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        int slot = event.getRawSlot();
        if (slot == slot("role-assignment-menu.back", 45)) {
            navigateBack(player);
        } else if (slot == slot("role-assignment-menu.close", 53)) {
            player.closeInventory();
        } else {
            readString(event.getCurrentItem(), roleIdKey).ifPresent(roleId ->
                    handle(
                            clanService.assignRole(
                                    player.getUniqueId(),
                                    player.getName(),
                                    holder.targetId(),
                                    roleId
                            ),
                            result -> {
                                if (!result.successful()) {
                                    sendFailure(player, result.code(), Map.of());
                                    return;
                                }
                                openMemberPermissions(player, holder.targetId());
                            },
                            player
                    )
            );
        }
    }

    private void handleConfirmationClick(Player player, ClanMenuHolder holder, int slot) {
        if (slot == slot("confirmation-menu.confirm", 11)) {
            executeConfirmed(player, holder);
        } else if (slot == slot("confirmation-menu.cancel", 15)) {
            returnFromConfirmation(player, holder);
        }
    }

    private void handleWarDurationClick(
            Player player,
            ClanMenuHolder holder,
            int slot
    ) {
        Optional<Clan> target = clanService.findCachedClan(holder.clanId());
        if (target.isEmpty()) {
            messages.send(player, "errors.clan-not-found", Map.of("clan", "-"));
            openMain(player);
            return;
        }
        List<Integer> durations = configs.diplomacy().getIntegerList(
                "wars.allowed-duration-hours"
        );
        List<Integer> durationSlots = configs.gui().getIntegerList(
                "war-duration-menu.duration-slots"
        );
        for (int index = 0; index < Math.min(durations.size(), durationSlots.size()); index++) {
            int hours = durations.get(index);
            if (slot == durationSlots.get(index)) {
                openConfirmation(
                        player,
                        ConfirmAction.WAR_REQUEST,
                        target.get(),
                        null,
                        Integer.toString(hours),
                        null
                );
                return;
            }
        }
        if (slot == slot("war-duration-menu.back", 18)) {
            navigateBack(player);
        } else if (slot == slot("war-duration-menu.close", 26)) {
            player.closeInventory();
        }
    }

    private void handleDiplomacyResponseClick(
            Player player,
            ClanMenuHolder holder,
            int slot
    ) {
        if (slot == slot("diplomacy-response-menu.accept", 11)) {
            respondDiplomacy(player, holder, true);
        } else if (slot == slot("diplomacy-response-menu.deny", 15)) {
            respondDiplomacy(player, holder, false);
        } else if (slot == slot("diplomacy-response-menu.back", 18)) {
            navigateBack(player);
        } else if (slot == slot("diplomacy-response-menu.close", 26)) {
            player.closeInventory();
        }
    }

    private void handleAllyRequestsClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        int slot = event.getRawSlot();
        if (slot == slot("ally-requests-menu.back", 45)) {
            navigateBack(player);
            return;
        }
        if (slot == slot("ally-requests-menu.previous", 48)) {
            replaceNext(
                    player,
                    menuState(
                            ClanMenuType.ALLY_REQUESTS,
                            holder.clanId(),
                            holder.page() - 1
                    )
            );
            openAllyRequests(player, holder.page() - 1);
            return;
        }
        if (slot == slot("ally-requests-menu.next", 50)) {
            replaceNext(
                    player,
                    menuState(
                            ClanMenuType.ALLY_REQUESTS,
                            holder.clanId(),
                            holder.page() + 1
                    )
            );
            openAllyRequests(player, holder.page() + 1);
            return;
        }
        if (slot == slot("ally-requests-menu.close", 53)) {
            player.closeInventory();
            return;
        }
        Optional<UUID> requestId = readUuid(event.getCurrentItem(), requestIdKey);
        Optional<Clan> sourceClan = readUuid(event.getCurrentItem(), clanIdKey)
                .flatMap(clanService::findCachedClan);
        if (requestId.isPresent() && sourceClan.isPresent()) {
            openDiplomacyResponse(
                    player,
                    sourceClan.get(),
                    requestId.get(),
                    DiplomacyType.ALLY,
                    0
            );
        }
    }

    private void executeConfirmed(Player player, ClanMenuHolder holder) {
        switch (holder.action()) {
            case INVITE -> invitePlayer(player, holder.targetId());
            case KICK -> kickPlayer(player, holder.clanId(), holder.targetId());
            case JOIN -> joinClan(player, holder.clanId(), false);
            case ACCEPT -> joinClan(player, holder.clanId(), true);
            case DENY -> denyInvite(player, holder.clanId());
            case LEAVE -> leaveClan(player);
            case JOIN_MODE_OPEN -> changeJoinMode(player, JoinMode.OPEN);
            case JOIN_MODE_INVITE -> changeJoinMode(player, JoinMode.INVITE_ONLY);
            case ALLY_REQUEST -> sendDiplomacyRequest(
                    player,
                    holder.clanId(),
                    DiplomacyType.ALLY,
                    0
            );
            case WAR_REQUEST -> sendDiplomacyRequest(
                    player,
                    holder.clanId(),
                    DiplomacyType.WAR,
                    Integer.parseInt(holder.firstValue())
            );
        }
    }

    private void sendDiplomacyRequest(
            Player player,
            UUID targetClanId,
            DiplomacyType type,
            int warDurationHours
    ) {
        handle(
                clanService.sendDiplomacyRequest(
                        player.getUniqueId(),
                        player.getName(),
                        targetClanId,
                        type,
                        warDurationHours
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    Optional<Clan> target = clanService.findCachedClan(targetClanId);
                    if (target.isEmpty()) {
                        openMain(player);
                        return;
                    }
                    Map<String, String> values = Map.of(
                            "clan",
                            target.get().name(),
                            "hours",
                            Integer.toString(warDurationHours)
                    );
                    messages.send(
                            player,
                            type == DiplomacyType.ALLY
                                    ? "general.ally-request-sent"
                                    : "general.war-request-sent",
                            values
                    );
                    openProfile(player, target.get());
                },
                player
        );
    }

    private void respondDiplomacy(
            Player player,
            ClanMenuHolder holder,
            boolean accept
    ) {
        DiplomacyType type = DiplomacyType.valueOf(holder.firstValue());
        handle(
                clanService.respondDiplomacyRequest(
                        player.getUniqueId(),
                        player.getName(),
                        holder.targetId(),
                        accept
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    Optional<Clan> foreignClan = clanService.findCachedClan(holder.clanId());
                    if (foreignClan.isEmpty()) {
                        navigateBack(player);
                        return;
                    }
                    String path = switch (type) {
                        case ALLY -> accept
                                ? "general.ally-request-accepted"
                                : "general.ally-request-denied";
                        case WAR -> accept
                                ? "general.war-request-accepted"
                                : "general.war-request-denied";
                    };
                    messages.send(
                            player,
                            path,
                            Map.of("clan", foreignClan.get().name())
                    );
                    navigateBack(player);
                },
                player
        );
    }

    private void beginClanEditChatInput(
            Player player,
            Clan clan,
            ClanEditField field
    ) {
        PendingClanEditInput pending = new PendingClanEditInput(clan.id(), field);
        pendingClanEditInputs.put(player.getUniqueId(), pending);
        pendingRoleNameInputs.remove(player.getUniqueId());
        pendingRolePriorityInputs.remove(player.getUniqueId());
        player.closeInventory();
        sendClanEditPrompt(player, pending);
    }

    private void handleClanEditChatInput(
            Player player,
            PendingClanEditInput pending,
            String input
    ) {
        if (!pendingClanEditInputs.remove(player.getUniqueId(), pending)
                || !player.isOnline()) {
            return;
        }
        if (input.equalsIgnoreCase(clanEditCancelKeyword())) {
            messages.send(player, "general.clan-edit-input-cancelled");
            returnToClanEdit(player, pending.clanId());
            return;
        }
        if (input.isBlank()) {
            pendingClanEditInputs.put(player.getUniqueId(), pending);
            sendClanEditPrompt(player, pending);
            return;
        }
        if (pending.field() == ClanEditField.NAME) {
            changeClanName(player, pending, input);
        } else {
            changeClanTag(player, pending, input);
        }
    }

    private void changeClanName(
            Player player,
            PendingClanEditInput pending,
            String name
    ) {
        handle(
                clanService.changeName(player.getUniqueId(), player.getName(), name),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        if (result.code() == OperationCode.INVALID_NAME
                                || result.code() == OperationCode.NAME_TAKEN) {
                            pendingClanEditInputs.put(player.getUniqueId(), pending);
                            sendClanEditPrompt(player, pending);
                        } else {
                            returnToClanEdit(player, pending.clanId());
                        }
                        return;
                    }
                    messages.send(
                            player,
                            "general.name-changed",
                            clanPlaceholders(result.value())
                    );
                    openClanEdit(player, result.value());
                },
                player,
                () -> returnToClanEdit(player, pending.clanId())
        );
    }

    private void changeClanTag(
            Player player,
            PendingClanEditInput pending,
            String tag
    ) {
        handle(
                clanService.changeTag(player.getUniqueId(), player.getName(), tag),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        if (result.code() == OperationCode.INVALID_TAG
                                || result.code() == OperationCode.TAG_TAKEN) {
                            pendingClanEditInputs.put(player.getUniqueId(), pending);
                            sendClanEditPrompt(player, pending);
                        } else {
                            returnToClanEdit(player, pending.clanId());
                        }
                        return;
                    }
                    messages.send(
                            player,
                            "general.tag-changed",
                            clanPlaceholders(result.value())
                    );
                    openClanEdit(player, result.value());
                },
                player,
                () -> returnToClanEdit(player, pending.clanId())
        );
    }

    private void sendClanEditPrompt(Player player, PendingClanEditInput pending) {
        Map<String, String> values = Map.of(
                "min",
                Integer.toString(configs.main().getInt("clans.tags.min-length", 2)),
                "max",
                Integer.toString(pending.field() == ClanEditField.NAME
                        ? configs.main().getInt("clans.names.max-length", 20)
                        : configs.main().getInt("clans.tags.max-length", 6)),
                "format_max",
                Integer.toString(configs.main().getInt(
                        "clans.tags.maximum-format-length",
                        256
                )),
                "cancel",
                clanEditCancelKeyword()
        );
        messages.send(
                player,
                pending.field() == ClanEditField.NAME
                        ? "general.clan-name-edit-prompt"
                        : "general.clan-tag-edit-prompt",
                values
        );
    }

    private String clanEditCancelKeyword() {
        return configs.gui().getString(
                "input-menu.clan-edit.chat.cancel-keyword",
                "cancel"
        ).trim();
    }

    private void returnToClanEdit(Player player, UUID clanId) {
        clanService.findCachedClanForPlayer(player.getUniqueId())
                .filter(clan -> clan.id().equals(clanId))
                .ifPresentOrElse(
                        clan -> openClanEdit(player, clan),
                        () -> openMain(player)
                );
    }

    private void invitePlayer(Player player, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            messages.send(player, "errors.player-not-found", Map.of("player", "-"));
            openMain(player);
            return;
        }
        handle(
                clanService.invite(
                        player.getUniqueId(),
                        player.getName(),
                        targetId,
                        target.getName()
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of("player", target.getName()));
                        return;
                    }
                    messages.send(player, "general.invited", Map.of("player", target.getName()));
                    messages.send(target, "general.invite-received",
                            Map.of("clan", result.value().name()));
                    openMain(player);
                },
                player
        );
    }

    private void kickPlayer(Player player, UUID clanId, UUID targetId) {
        Optional<Clan> clan = clanService.findCachedClan(clanId);
        String targetName = clan.flatMap(value -> value.member(targetId))
                .map(ClanMember::lastKnownName)
                .orElse("-");
        handle(
                clanService.kickMember(
                        player.getUniqueId(),
                        player.getName(),
                        targetId,
                        "GUI"
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of("player", targetName));
                        return;
                    }
                    messages.send(player, "general.kicked", Map.of("player", targetName));
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null) {
                        messages.send(target, "general.you-were-kicked",
                                Map.of("clan", result.value().name()));
                    }
                    openMembers(player, result.value());
                },
                player
        );
    }

    private void joinClan(Player player, UUID clanId, boolean invited) {
        Optional<Clan> clan = clanService.findCachedClan(clanId);
        if (clan.isEmpty()) {
            messages.send(player, "errors.clan-not-found", Map.of("clan", "-"));
            return;
        }
        CompletionStage<OperationResult<Clan>> stage = invited
                ? clanService.acceptInvite(player.getUniqueId(), player.getName(), clan.get().name())
                : clanService.joinOpenClan(player.getUniqueId(), player.getName(), clan.get().name());
        handle(
                stage,
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of("clan", clan.get().name()));
                        return;
                    }
                    messages.send(
                            player,
                            invited ? "general.invite-accepted" : "general.joined",
                            Map.of("clan", result.value().name())
                    );
                    openMain(player);
                },
                player
        );
    }

    private void denyInvite(Player player, UUID clanId) {
        Optional<Clan> clan = clanService.findCachedClan(clanId);
        if (clan.isEmpty()) {
            messages.send(player, "errors.clan-not-found", Map.of("clan", "-"));
            return;
        }
        handle(
                clanService.denyInvite(player.getUniqueId(), player.getName(), clan.get().name()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of("clan", clan.get().name()));
                        return;
                    }
                    messages.send(player, "general.invite-denied",
                            Map.of("clan", result.value().name()));
                    openInvites(player);
                },
                player
        );
    }

    private void leaveClan(Player player) {
        boolean wasOwner = clanService.findCachedClanForPlayer(player.getUniqueId())
                .map(clan -> clan.ownerId().equals(player.getUniqueId()))
                .orElse(false);
        handle(
                clanService.leaveClan(player.getUniqueId(), player.getName(), "GUI"),
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
                    openMain(player);
                },
                player
        );
    }

    private void changeJoinMode(Player player, JoinMode mode) {
        handle(
                clanService.setJoinMode(player.getUniqueId(), player.getName(), mode),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    messages.send(player, "general.join-mode-changed",
                            Map.of("mode", displayJoinMode(result.value().joinMode())));
                    openMain(player);
                },
                player
        );
    }

    private void beginRoleNameChatInput(Player player, String roleId) {
        Optional<Clan> clan = requireOwnedClan(player);
        if (clan.isEmpty()) {
            return;
        }
        PendingRoleNameInput pending = new PendingRoleNameInput(
                clan.get().id(),
                roleId
        );
        pendingRoleNameInputs.put(player.getUniqueId(), pending);
        pendingRolePriorityInputs.remove(player.getUniqueId());
        pendingClanEditInputs.remove(player.getUniqueId());
        player.closeInventory();
        sendRoleNamePrompt(player, pending);
    }

    private void handleRoleNameChatInput(
            Player player,
            PendingRoleNameInput pending,
            String input
    ) {
        if (!pendingRoleNameInputs.remove(player.getUniqueId(), pending)
                || !player.isOnline()) {
            return;
        }
        if (input.equalsIgnoreCase(roleNameCancelKeyword())) {
            messages.send(player, "general.role-name-input-cancelled");
            returnToRoleList(player, pending);
            return;
        }
        saveRoleName(player, pending, input);
    }

    private void saveRoleName(
            Player player,
            PendingRoleNameInput pending,
            String name
    ) {
        Optional<Clan> currentClan = clanService.findCachedClanForPlayer(
                player.getUniqueId()
        );
        if (currentClan.isEmpty()
                || !currentClan.get().id().equals(pending.clanId())
                || !currentClan.get().ownerId().equals(player.getUniqueId())) {
            messages.send(player, "errors.owner-only");
            openMain(player);
            return;
        }
        CompletionStage<? extends OperationResult<ClanRole>> stage = pending.creating()
                ? clanService.createRole(player.getUniqueId(), player.getName(), name)
                : clanService.renameRole(
                        player.getUniqueId(),
                        player.getName(),
                        pending.roleId(),
                        name
                );
        handle(
                stage,
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        if (result.code() == OperationCode.INVALID_ROLE_NAME
                                || result.code() == OperationCode.ROLE_NAME_TAKEN) {
                            pendingRoleNameInputs.put(player.getUniqueId(), pending);
                            sendRoleNamePrompt(player, pending);
                        } else {
                            returnToRoleList(player, pending);
                        }
                        return;
                    }
                    messages.send(
                            player,
                            pending.creating()
                                    ? "general.role-created"
                                    : "general.role-renamed",
                            Map.of("role", result.value().displayName())
                    );
                    returnToRoleList(player, pending);
                },
                player,
                () -> returnToRoleList(player, pending)
        );
    }

    private void sendRoleNamePrompt(Player player, PendingRoleNameInput pending) {
        messages.send(
                player,
                pending.creating()
                        ? "general.role-name-create-prompt"
                        : "general.role-name-rename-prompt",
                Map.of(
                        "max",
                        Integer.toString(configs.main().getInt(
                                "clans.roles.name-max-length",
                                24
                        )),
                        "cancel",
                        roleNameCancelKeyword()
                )
        );
    }

    private String roleNameCancelKeyword() {
        return configs.gui().getString(
                "input-menu.role-name.chat.cancel-keyword",
                "cancel"
        ).trim();
    }

    private void beginRolePriorityChatInput(Player player, String roleId) {
        if ("owner".equals(roleId)) {
            sendFailure(player, OperationCode.OWNER_ROLE_LOCKED, Map.of());
            return;
        }
        Optional<Clan> clan = requireOwnedClan(player);
        if (clan.isEmpty()) {
            return;
        }
        PendingRolePriorityInput pending = new PendingRolePriorityInput(
                clan.get().id(),
                roleId
        );
        pendingRolePriorityInputs.put(player.getUniqueId(), pending);
        pendingRoleNameInputs.remove(player.getUniqueId());
        pendingClanEditInputs.remove(player.getUniqueId());
        player.closeInventory();
        sendRolePriorityPrompt(player);
    }

    private void handleRolePriorityChatInput(
            Player player,
            PendingRolePriorityInput pending,
            String input
    ) {
        if (!pendingRolePriorityInputs.remove(player.getUniqueId(), pending)
                || !player.isOnline()) {
            return;
        }
        if (input.equalsIgnoreCase(rolePriorityCancelKeyword())) {
            messages.send(player, "general.role-priority-input-cancelled");
            openRolePermissions(player, pending.roleId());
            return;
        }
        final int priority;
        try {
            priority = Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            messages.send(player, "errors.invalid-role-priority");
            pendingRolePriorityInputs.put(player.getUniqueId(), pending);
            sendRolePriorityPrompt(player);
            return;
        }
        handle(
                clanService.setRolePriority(
                        player.getUniqueId(),
                        player.getName(),
                        pending.roleId(),
                        priority
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        if (result.code() == OperationCode.INVALID_ROLE_PRIORITY
                                || result.code() == OperationCode.ROLE_PRIORITY_TAKEN) {
                            pendingRolePriorityInputs.put(player.getUniqueId(), pending);
                            sendRolePriorityPrompt(player);
                        } else {
                            openRolePermissions(player, pending.roleId());
                        }
                        return;
                    }
                    messages.send(player, "general.role-priority-changed", Map.of(
                            "role", result.value().role().displayName(),
                            "priority", Integer.toString(result.value().role().priority())
                    ));
                    renderRolePermissions(player, result.value());
                },
                player,
                () -> openRolePermissions(player, pending.roleId())
        );
    }

    private void sendRolePriorityPrompt(Player player) {
        messages.send(player, "general.role-priority-prompt", Map.of(
                "cancel", rolePriorityCancelKeyword()
        ));
    }

    private String rolePriorityCancelKeyword() {
        return configs.gui().getString(
                "input-menu.role-priority.chat.cancel-keyword",
                "cancel"
        ).trim();
    }

    private void returnToRoleList(Player player, PendingRoleNameInput pending) {
        if (pending.creating()) {
            openRoleList(player);
        } else {
            navigateBack(player);
        }
    }

    private void openProfile(Player player, Clan clan, boolean invitation) {
        Optional<Clan> ownClan = clanService.findCachedClanForPlayer(player.getUniqueId());
        boolean foreignClan = ownClan.isPresent()
                && !ownClan.get().id().equals(clan.id());
        if (!invitation && foreignClan) {
            handle(
                    clanService.findDiplomacyView(player.getUniqueId(), clan.id()),
                    result -> renderProfile(
                            player,
                            clan,
                            false,
                            result.successful()
                                    ? result.value()
                                    : DiplomacyView.empty()
                    ),
                    player
            );
            return;
        }
        renderProfile(player, clan, invitation, DiplomacyView.empty());
    }

    private void renderProfile(
            Player player,
            Clan clan,
            boolean invitation,
            DiplomacyView diplomacy
    ) {
        Map<String, String> values = with(
                clanPlaceholders(clan),
                "mode",
                displayJoinMode(clan.joinMode())
        );
        values = with(
                values,
                "ranking_position",
                clanService.cachedRankingPosition(clan.id())
                        .stream()
                        .mapToObj(position -> "#" + position)
                        .findFirst()
                        .orElse("-")
        );
        values = with(
                values,
                "ranking_points",
                formatPoints(clanService.cachedRankingPoints(clan.id()))
        );
        if (diplomacy.activeWar().isPresent()) {
            values = with(
                    values,
                    "war_ends",
                    dateFormatter.format(diplomacy.activeWar().get().endsAt())
            );
        }
        String allyState = invitation ? "invite" : diplomacy.incomingAllyRequest()
                .map(request -> "ally:incoming:" + request.id())
                .orElseGet(() -> diplomacy.outgoingAllyRequest().isPresent()
                        ? "ally:pending"
                        : diplomacy.allied() ? "ally:active" : null);
        String warState = diplomacy.incomingWarRequest()
                .map(request -> "war:incoming:" + request.id()
                        + ":" + request.warDurationHours())
                .orElseGet(() -> diplomacy.outgoingWarRequest().isPresent()
                        ? "war:pending"
                        : diplomacy.activeWar().isPresent() ? "war:active" : null);
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.PROFILE,
                clan.id(),
                null,
                0,
                allyState,
                warState,
                null
        );
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("profile-menu.size", 27),
                "profile-menu.title",
                values
        );
        fill(inventory);
        inventory.setItem(
                configs.gui().getInt("profile-menu.info.slot", 13),
                clanHead(clan, "profile-menu.info", values)
        );
        set(inventory, "profile-menu.members", values);
        boolean outsideClan = clanService.findCachedClanForPlayer(player.getUniqueId()).isEmpty();
        if (invitation || outsideClan && clan.joinMode() == JoinMode.OPEN) {
            String actionPath = invitation
                    ? "profile-menu.accept"
                    : "profile-menu.join";
            setAtConfiguredSlot(inventory, "profile-menu.primary-action", actionPath, values);
        }
        if (invitation) {
            set(inventory, "profile-menu.deny", values);
        }
        boolean ownClan = clan.id().equals(clanService
                .findCachedClanForPlayer(player.getUniqueId())
                .map(Clan::id)
                .orElse(null));
        if (ownClan) {
            set(inventory, "profile-menu.battlepass", values);
            set(inventory, "profile-menu.bank", values);
            set(inventory, "profile-menu.homes", values);
            set(inventory, "profile-menu.vault", values);
            if (player.hasPermission("catclans.clan.name.change")
                    || player.hasPermission("catclans.clan.tag.change")) {
                set(inventory, "profile-menu.edit", values);
            }
            if (clanService.alliancesEnabled()) {
                setAtConfiguredSlot(
                        inventory,
                        "profile-menu.primary-action",
                        "profile-menu.ally-requests",
                        values
                );
            }
        }
        if (ownClan
                && clan.ownerId().equals(player.getUniqueId())
                && player.hasPermission("catclans.clan.permissions.manage")) {
            set(inventory, "profile-menu.permissions", values);
        }
        boolean foreignClan = clanService.findCachedClanForPlayer(player.getUniqueId())
                .filter(own -> !own.id().equals(clan.id()))
                .isPresent();
        if (foreignClan) {
            if (clanService.alliancesEnabled() && diplomacy.allied()) {
                setAtConfiguredSlot(
                        inventory,
                        "profile-menu.ally-action",
                        "profile-menu.allied",
                        values
                );
            } else if (clanService.alliancesEnabled()
                    && diplomacy.activeWar().isEmpty()) {
                String allyPath = diplomacy.incomingAllyRequest().isPresent()
                        ? "profile-menu.ally-incoming"
                        : diplomacy.outgoingAllyRequest().isPresent()
                        ? "profile-menu.ally-pending"
                        : "profile-menu.ally-request";
                setAtConfiguredSlot(
                        inventory,
                        "profile-menu.ally-action",
                        allyPath,
                        values
                );
            }
            if (clanService.warsEnabled() && diplomacy.activeWar().isPresent()) {
                setAtConfiguredSlot(
                        inventory,
                        "profile-menu.war-action",
                        "profile-menu.war-active",
                        values
                );
            } else if (clanService.warsEnabled() && !diplomacy.allied()) {
                String warPath = diplomacy.incomingWarRequest().isPresent()
                        ? "profile-menu.war-incoming"
                        : diplomacy.outgoingWarRequest().isPresent()
                        ? "profile-menu.war-pending"
                        : "profile-menu.war-request";
                setAtConfiguredSlot(
                        inventory,
                        "profile-menu.war-action",
                        warPath,
                        values
                );
            }
        }
        set(inventory, "profile-menu.back", values);
        set(inventory, "profile-menu.close", values);
        openMenu(player, inventory);
    }

    private void openClanEdit(Player player, Clan clan) {
        Optional<Clan> ownClan = clanService.findCachedClanForPlayer(player.getUniqueId())
                .filter(current -> current.id().equals(clan.id()));
        if (ownClan.isEmpty()) {
            messages.send(player, "errors.not-in-clan");
            openMain(player);
            return;
        }
        Clan current = ownClan.get();
        Map<String, String> values = clanPlaceholders(current);
        ClanMenuHolder holder = holder(ClanMenuType.CLAN_EDIT, current.id());
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("clan-edit-menu.size", 27),
                "clan-edit-menu.title",
                values
        );
        fill(inventory);
        inventory.setItem(
                slot("clan-edit-menu.info", 13),
                clanHead(current, "clan-edit-menu.info", values)
        );
        if (player.hasPermission("catclans.clan.name.change")) {
            set(inventory, "clan-edit-menu.name", values);
        }
        if (player.hasPermission("catclans.clan.tag.change")) {
            set(inventory, "clan-edit-menu.tag", values);
        }
        set(inventory, "clan-edit-menu.back", values);
        set(inventory, "clan-edit-menu.close", values);
        openMenu(player, inventory);
    }

    private void openInvites(Player player, List<Clan> clans) {
        ClanMenuHolder holder = holder(ClanMenuType.INVITES, null);
        Map<String, String> values = Map.of("count", Integer.toString(clans.size()));
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("invites-menu.size", 54),
                "invites-menu.title",
                values
        );
        fill(inventory);
        List<Integer> slots = configs.gui().getIntegerList("invites-menu.invite-slots");
        for (int index = 0; index < Math.min(slots.size(), clans.size()); index++) {
            Clan clan = clans.get(index);
            inventory.setItem(
                    slots.get(index),
                    clanHead(clan, "invites-menu.invite", clanPlaceholders(clan))
            );
        }
        if (clans.isEmpty()) {
            set(inventory, "invites-menu.empty", values);
        }
        set(inventory, "invites-menu.back", values);
        set(inventory, "invites-menu.close", values);
        openMenu(player, inventory);
    }

    private void openWarDuration(Player player, Clan targetClan) {
        Map<String, String> values = clanPlaceholders(targetClan);
        ClanMenuHolder holder = holder(ClanMenuType.WAR_DURATION, targetClan.id());
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("war-duration-menu.size", 27),
                "war-duration-menu.title",
                values
        );
        fill(inventory);
        List<Integer> durations = configs.diplomacy().getIntegerList(
                "wars.allowed-duration-hours"
        );
        List<Integer> durationSlots = configs.gui().getIntegerList(
                "war-duration-menu.duration-slots"
        );
        for (int index = 0; index < Math.min(durations.size(), durationSlots.size()); index++) {
            Map<String, String> durationValues = with(
                    values,
                    "hours",
                    Integer.toString(durations.get(index))
            );
            inventory.setItem(
                    durationSlots.get(index),
                    configuredItem(
                            "war-duration-menu.duration",
                            durationValues,
                            Material.CLOCK
                    )
            );
        }
        set(inventory, "war-duration-menu.back", values);
        set(inventory, "war-duration-menu.close", values);
        openMenu(player, inventory);
    }

    private void openAllyRequests(Player player, int requestedPage) {
        if (!requirePermission(player, "catclans.clan.diplomacy")) {
            return;
        }
        handle(
                clanService.findIncomingAllyRequests(player.getUniqueId()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code(), Map.of());
                        return;
                    }
                    renderAllyRequests(player, result.value(), requestedPage);
                },
                player
        );
    }

    private void renderAllyRequests(
            Player player,
            List<DiplomacyRequest> requests,
            int requestedPage
    ) {
        Optional<Clan> ownClan = clanService.findCachedClanForPlayer(player.getUniqueId());
        if (ownClan.isEmpty()) {
            messages.send(player, "errors.not-in-clan");
            return;
        }
        List<Integer> slots = configs.gui().getIntegerList(
                "ally-requests-menu.request-slots"
        );
        int pageSize = Math.max(1, slots.size());
        int pages = Math.max(1, (requests.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Map<String, String> values = Map.of(
                "count", Integer.toString(requests.size()),
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(pages)
        );
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.ALLY_REQUESTS,
                ownClan.get().id(),
                null,
                page,
                null,
                null,
                null
        );
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("ally-requests-menu.size", 54),
                "ally-requests-menu.title",
                values
        );
        fill(inventory);
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < requests.size(); index++) {
            DiplomacyRequest request = requests.get(start + index);
            int requestSlot = slots.get(index);
            clanService.findCachedClan(request.sourceClanId()).ifPresent(sourceClan -> {
                Map<String, String> requestValues = with(
                        clanPlaceholders(sourceClan),
                        "expires_at",
                        dateFormatter.format(request.expiresAt())
                );
                ItemStack item = clanHead(
                        sourceClan,
                        "ally-requests-menu.request",
                        requestValues
                );
                ItemMeta meta = item.getItemMeta();
                meta.getPersistentDataContainer().set(
                        requestIdKey,
                        PersistentDataType.STRING,
                        request.id().toString()
                );
                item.setItemMeta(meta);
                inventory.setItem(requestSlot, item);
            });
        }
        if (requests.isEmpty()) {
            set(inventory, "ally-requests-menu.empty", values);
        }
        if (page > 0) {
            set(inventory, "ally-requests-menu.previous", values);
        }
        if (page + 1 < pages) {
            set(inventory, "ally-requests-menu.next", values);
        }
        set(inventory, "ally-requests-menu.back", values);
        set(inventory, "ally-requests-menu.close", values);
        openMenu(player, inventory);
    }

    private void openDiplomacyResponse(
            Player player,
            Clan foreignClan,
            UUID requestId,
            DiplomacyType type,
            int warDurationHours
    ) {
        Map<String, String> values = new HashMap<>(clanPlaceholders(foreignClan));
        values.put("hours", Integer.toString(warDurationHours));
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.DIPLOMACY_RESPONSE,
                foreignClan.id(),
                requestId,
                0,
                type.name(),
                Integer.toString(warDurationHours),
                null
        );
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("diplomacy-response-menu.size", 27),
                "diplomacy-response-menu.title",
                Map.copyOf(values)
        );
        fill(inventory);
        inventory.setItem(
                configs.gui().getInt("diplomacy-response-menu.subject-slot", 13),
                clanHead(
                        foreignClan,
                        type == DiplomacyType.ALLY
                                ? "diplomacy-response-menu.ally-subject"
                                : "diplomacy-response-menu.war-subject",
                        Map.copyOf(values)
                )
        );
        set(inventory, "diplomacy-response-menu.accept", Map.copyOf(values));
        set(inventory, "diplomacy-response-menu.deny", Map.copyOf(values));
        set(inventory, "diplomacy-response-menu.back", Map.copyOf(values));
        set(inventory, "diplomacy-response-menu.close", Map.copyOf(values));
        openMenu(player, inventory);
    }

    private void openConfirmation(
            Player player,
            ConfirmAction action,
            Clan clan,
            UUID targetId,
            String firstValue,
            String secondValue
    ) {
        Map<String, String> values = new HashMap<>();
        if (clan != null) {
            values.putAll(clanPlaceholders(clan));
            values.put("mode", displayJoinMode(clan.joinMode()));
        }
        if (targetId != null) {
            String playerName = Bukkit.getOfflinePlayer(targetId).getName();
            values.put("player", playerName == null ? targetId.toString() : playerName);
        }
        if (action == ConfirmAction.WAR_REQUEST && firstValue != null) {
            values.put("hours", firstValue);
        } else if (firstValue != null) {
            values.put("tag", firstValue);
            values.put("formatted_tag", firstValue);
        }
        if (secondValue != null) {
            values.put("clan", secondValue);
        }
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.CONFIRMATION,
                clan == null ? null : clan.id(),
                targetId,
                0,
                firstValue,
                secondValue,
                action
        );
        Inventory inventory = createInventory(
                holder,
                configs.gui().getInt("confirmation-menu.size", 27),
                "confirmation-menu.title",
                Map.copyOf(values)
        );
        fill(inventory);
        ItemStack subject;
        if (targetId != null) {
            subject = playerHead(
                    targetId,
                    "confirmation-menu.actions." + action.configKey(),
                    Map.copyOf(values)
            );
        } else if (clan != null) {
            subject = clanHead(
                    clan,
                    "confirmation-menu.actions." + action.configKey(),
                    Map.copyOf(values)
            );
        } else {
            subject = configuredItem(
                    "confirmation-menu.actions." + action.configKey(),
                    Map.copyOf(values),
                    Material.NAME_TAG
            );
        }
        inventory.setItem(configs.gui().getInt("confirmation-menu.subject-slot", 13), subject);
        set(inventory, "confirmation-menu.confirm", Map.copyOf(values));
        set(inventory, "confirmation-menu.cancel", Map.copyOf(values));
        openMenu(player, inventory);
    }

    private void returnFromConfirmation(Player player, ClanMenuHolder holder) {
        navigateBack(player);
    }

    InventoryView openMenu(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof ClanMenuHolder openedHolder)) {
            throw new IllegalArgumentException("CatClans menu holder is missing");
        }
        ClanMenuState current = null;
        if (player.getOpenInventory().getTopInventory().getHolder()
                instanceof ClanMenuHolder currentHolder) {
            current = ClanMenuState.from(currentHolder);
        }
        navigation.opened(
                player.getUniqueId(),
                current,
                ClanMenuState.from(openedHolder)
        );
        return player.openInventory(inventory);
    }

    void replaceNext(Player player, ClanMenuState target) {
        navigation.replaceNext(player.getUniqueId(), target);
    }

    void navigateBack(Player player) {
        navigation.back(player.getUniqueId())
                .ifPresentOrElse(
                        state -> reopen(player, state),
                        () -> openMain(player)
                );
    }

    private void reopen(Player player, ClanMenuState state) {
        switch (state.type()) {
            case MAIN -> renderMain(player, false);
            case CLAN_LIST -> openClanList(player, state.page());
            case RANKING -> openRanking(
                    player,
                    rankingCategory(state.firstValue()),
                    state.page()
            );
            case PROFILE -> cachedClanOrMain(player, state.clanId())
                    .ifPresent(clan -> openProfile(
                            player,
                            clan,
                            "invite".equals(state.firstValue())
                    ));
            case CLAN_EDIT -> cachedClanOrMain(player, state.clanId())
                    .ifPresent(clan -> openClanEdit(player, clan));
            case MEMBERS -> cachedClanOrMain(player, state.clanId())
                    .ifPresent(clan -> openMembers(player, clan));
            case INVITES -> openInvites(player);
            case PERMISSION_HOME -> openPermissionHome(player);
            case ROLE_LIST -> openRoleList(player);
            case MEMBER_PERMISSION_LIST -> openMemberPermissionList(player);
            case ROLE_PERMISSIONS -> openRolePermissions(player, state.firstValue());
            case MEMBER_PERMISSIONS -> openMemberPermissions(player, state.targetId());
            case ROLE_ASSIGNMENT -> openRoleAssignment(player, state.targetId());
            case ALLY_REQUESTS -> openAllyRequests(player, state.page());
            case WAR_DURATION -> cachedClanOrMain(player, state.clanId())
                    .ifPresent(clan -> openWarDuration(player, clan));
            case DIPLOMACY_RESPONSE -> cachedClanOrMain(player, state.clanId())
                    .ifPresent(clan -> openDiplomacyResponse(
                            player,
                            clan,
                            state.targetId(),
                            DiplomacyType.valueOf(state.firstValue()),
                            Integer.parseInt(state.secondValue())
                    ));
            case CONFIRMATION -> {
                Clan clan = state.clanId() == null
                        ? null
                        : clanService.findCachedClan(state.clanId()).orElse(null);
                if (state.clanId() != null && clan == null) {
                    openMain(player);
                    return;
                }
                openConfirmation(
                        player,
                        state.action(),
                        clan,
                        state.targetId(),
                        state.firstValue(),
                        state.secondValue()
                );
            }
            case BATTLEPASS, BATTLEPASS_REWARD_EDITOR, BANK, BANK_DEPOSIT,
                    BANK_WITHDRAW, BANK_LOG_MEMBERS, BANK_LOG_ENTRIES, HOMES,
                    HOME_CONFIRMATION, VAULT, VAULT_LOG_MEMBERS,
                    VAULT_LOG_ENTRIES -> {
                if (featureGui == null || !featureGui.reopen(player, state)) {
                    openMain(player);
                }
            }
        }
    }

    private Optional<Clan> cachedClanOrMain(Player player, UUID clanId) {
        Optional<Clan> clan = clanId == null
                ? Optional.empty()
                : clanService.findCachedClan(clanId);
        if (clan.isEmpty()) {
            messages.send(player, "errors.clan-not-found", Map.of("clan", "-"));
            openMain(player);
        }
        return clan;
    }

    private Inventory createInventory(
            ClanMenuHolder holder,
            int size,
            String titlePath,
            Map<String, String> placeholders
    ) {
        Component title = messages.renderConfig("gui", titlePath, placeholders);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory(inventory);
        return inventory;
    }

    private void fill(Inventory inventory) {
        if (configs.gui().getBoolean("common.filler.enabled", true)) {
            ItemStack filler = decorationItem(
                    "common.filler",
                    Material.BLACK_STAINED_GLASS_PANE
            );
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        if (!configs.gui().getBoolean("common.frame.enabled", true)) {
            return;
        }
        ItemStack frame = decorationItem(
                "common.frame",
                Material.PURPLE_STAINED_GLASS_PANE
        );
        if (configs.gui().getBoolean("common.frame.top-row", true)) {
            fillRow(inventory, 0, frame);
        }
        if (configs.gui().getBoolean("common.frame.bottom-row", true)) {
            fillRow(inventory, inventory.getSize() - 9, frame);
        }
    }

    private ItemStack decorationItem(String path, Material fallback) {
        return decorationCache.computeIfAbsent(path, ignored -> {
            ItemStack item = configuredItem(path, Map.of(), fallback);
            ItemMeta meta = item.getItemMeta();
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
            return item;
        }).clone();
    }

    private static void fillRow(Inventory inventory, int startSlot, ItemStack item) {
        for (int slot = startSlot; slot < startSlot + 9; slot++) {
            inventory.setItem(slot, item);
        }
    }

    private void set(Inventory inventory, String path, Map<String, String> placeholders) {
        inventory.setItem(
                slot(path, -1),
                configuredItem(path, placeholders, Material.BARRIER)
        );
    }

    private void set(
            Inventory inventory,
            String path,
            Map<String, String> placeholders,
            ItemStack item
    ) {
        inventory.setItem(slot(path, -1), item);
    }

    private void setAtConfiguredSlot(
            Inventory inventory,
            String slotPath,
            String itemPath,
            Map<String, String> placeholders
    ) {
        inventory.setItem(
                slot(slotPath, -1),
                configuredItem(itemPath, placeholders, Material.LIME_DYE)
        );
    }

    private ItemStack memberHead(ClanMember member) {
        Optional<Clan> clan = clanService.findCachedClanForPlayer(member.playerId());
        Map<String, String> values = Map.of(
                "player", member.lastKnownName(),
                "rank", clan.map(value -> clanService.displayRole(value.id(), member))
                        .orElseGet(() -> rankPolicy.displayName(member.rank())),
                "joined_at", dateFormatter.format(member.joinedAt())
        );
        return playerHead(member.playerId(), "members-menu.member", values);
    }

    private ItemStack roleItem(ClanRole role, String path) {
        ItemStack item = configuredItem(
                path,
                Map.of(
                        "role", role.displayName(),
                        "priority", Integer.toString(role.priority())
                ),
                role.standard() ? Material.NAME_TAG : Material.PAPER
        );
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                roleIdKey,
                PersistentDataType.STRING,
                role.id()
        );
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack permissionItem(
            String permission,
            PermissionOverride state
    ) {
        String stateKey = switch (state) {
            case INHERIT -> "inherit";
            case ALLOW -> "allow";
            case DENY -> "deny";
        };
        String path = "permission-items." + stateKey;
        ItemStack item = configuredItem(
                path,
                Map.of(
                        "permission", permissionDisplayName(permission),
                        "permission_id", permission,
                        "state", configs.gui().getString(
                                "permission-items.states." + stateKey,
                                state.name()
                        )
                ),
                switch (state) {
                    case INHERIT -> Material.GRAY_DYE;
                    case ALLOW -> Material.LIME_DYE;
                    case DENY -> Material.RED_DYE;
                }
        );
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                permissionKey,
                PersistentDataType.STRING,
                permission
        );
        item.setItemMeta(meta);
        return item;
    }

    private Map<String, String> rankingPlaceholders(
            ClanRankingEntry entry,
            RankingCategory category
    ) {
        Map<String, String> values = new HashMap<>(clanPlaceholders(entry.clan()));
        values.put("position", Integer.toString(entry.position()));
        values.put("category", rankingCategoryLabel(category));
        values.put("category_points", formatPoints(entry.points(category)));
        values.put("total_points", formatPoints(entry.totalPoints()));
        values.put("combat_points", formatPoints(entry.points(RankingCategory.COMBAT)));
        values.put("member_points", formatPoints(entry.points(RankingCategory.MEMBERS)));
        values.put("money_points", formatPoints(entry.points(RankingCategory.MONEY)));
        values.put("wars_won_points", formatPoints(entry.points(RankingCategory.WARS_WON)));
        values.put("wars_lost_points", formatPoints(entry.points(RankingCategory.WARS_LOST)));
        values.put("activity_points", formatPoints(entry.points(RankingCategory.ACTIVITY)));
        return Map.copyOf(values);
    }

    private String rankingCategoryLabel(RankingCategory category) {
        return configs.gui().getString(
                "ranking-menu.values.categories." + category.configKey(),
                category.name()
        );
    }

    private String rankingValue(String key, String fallback) {
        return configs.gui().getString("ranking-menu.values." + key, fallback);
    }

    private static String formatPoints(BigDecimal points) {
        return points.stripTrailingZeros().toPlainString();
    }

    private String permissionDisplayName(String permission) {
        return permissionDisplayNames.getOrDefault(permission, permission);
    }

    private ItemStack clanHead(Clan clan, String path, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyConfiguredDisplay(meta, path, placeholders);
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(clan.ownerId()));
        meta.getPersistentDataContainer().set(
                clanIdKey,
                PersistentDataType.STRING,
                clan.id().toString()
        );
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(UUID playerId, String path, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyConfiguredDisplay(meta, path, placeholders);
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
        meta.getPersistentDataContainer().set(
                playerIdKey,
                PersistentDataType.STRING,
                playerId.toString()
        );
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack configuredItem(
            String path,
            Map<String, String> placeholders,
            Material fallback
    ) {
        ItemStack item = new ItemStack(configuredMaterial(path, fallback));
        ItemMeta meta = item.getItemMeta();
        applyConfiguredDisplay(meta, path, placeholders);
        item.setItemMeta(meta);
        return item;
    }

    private void applyConfiguredDisplay(
            ItemMeta meta,
            String path,
            Map<String, String> placeholders
    ) {
        if (configs.gui().isString(path + ".display-name")) {
            meta.displayName(GuiTextStyle.nonItalic(messages.renderConfig(
                    "gui",
                    path + ".display-name",
                    placeholders
            )));
        }
        List<Component> lore = configs.gui().getStringList(path + ".lore").stream()
                .map(line -> messages.renderMenu(line, placeholders))
                .toList();
        if (!lore.isEmpty()) {
            meta.lore(GuiTextStyle.nonItalic(lore));
        }
    }

    private Optional<UUID> readUuid(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<String> readString(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer()
                        .get(key, PersistentDataType.STRING))
                .filter(value -> !value.isBlank());
    }

    private int slot(String path, int fallback) {
        return slotCache.computeIfAbsent(
                path,
                ignored -> configs.gui().getInt(path + ".slot", fallback)
        );
    }

    private Material configuredMaterial(String path, Material fallback) {
        return materialCache.computeIfAbsent(path, ignored -> {
            Material configured = Material.matchMaterial(
                    configs.gui().getString(path + ".material", fallback.name())
            );
            return configured == null ? fallback : configured;
        });
    }

    private static Map<String, String> loadPermissionDisplayNames(ConfigBundle configs) {
        var section = configs.permissions().getConfigurationSection("clan-rights");
        if (section == null) {
            return Map.of();
        }
        Map<String, String> displayNames = new HashMap<>();
        section.getValues(false).forEach((key, value) -> {
            String permission = String.valueOf(value);
            displayNames.put(
                    permission,
                    configs.gui().getString("permission-labels." + key, permission)
            );
        });
        return Map.copyOf(displayNames);
    }

    private Optional<Clan> requireOwnedClan(Player player) {
        Optional<Clan> clan = clanService.findCachedClanForPlayer(player.getUniqueId());
        if (clan.isEmpty()) {
            messages.send(player, "errors.not-in-clan");
            return Optional.empty();
        }
        if (!clan.get().ownerId().equals(player.getUniqueId())) {
            messages.send(player, "errors.owner-only");
            return Optional.empty();
        }
        return clan;
    }

    private boolean requirePermission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        messages.send(player, "errors.no-permission");
        return false;
    }

    private String displayJoinMode(JoinMode mode) {
        return configs.messages().getString(
                mode == JoinMode.OPEN ? "values.join-mode.open" : "values.join-mode.invite-only",
                mode.name()
        );
    }

    private void sendFailure(
            Player player,
            OperationCode code,
            Map<String, String> providedPlaceholders
    ) {
        Map<String, String> placeholders = new HashMap<>(providedPlaceholders);
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
        messages.send(player, path, Map.copyOf(placeholders));
    }

    private <T> void handle(
            CompletionStage<T> stage,
            Consumer<T> success,
            Player player
    ) {
        handle(stage, success, player, () -> {
        });
    }

    private <T> void handle(
            CompletionStage<T> stage,
            Consumer<T> success,
            Player player,
            Runnable onException
    ) {
        stage.whenComplete((value, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) {
                    return;
                }
                if (error != null) {
                    onException.run();
                    plugin.getLogger().severe("Clan-GUI-Aktion failed: "
                            + rootMessage(error));
                    messages.send(player, "errors.internal");
                    return;
                }
                success.accept(value);
            });
        });
    }

    private Map<String, String> clanPlaceholders(Clan clan) {
        return Map.of(
                "clan", clan.name(),
                "tag", clan.tag(),
                "formatted_tag", clan.formattedTag(),
                "members", Integer.toString(clan.members().size()),
                "max_members", Integer.toString(clan.maxMembers())
        );
    }

    private static Map<String, String> with(
            Map<String, String> source,
            String key,
            String value
    ) {
        Map<String, String> copy = new HashMap<>(source);
        copy.put(key, value);
        return Map.copyOf(copy);
    }

    private static ClanMenuHolder holder(ClanMenuType type, UUID clanId) {
        return new ClanMenuHolder(type, clanId, null, 0, null, null, null);
    }

    private static ClanMenuState menuState(
            ClanMenuType type,
            UUID clanId,
            int page
    ) {
        return new ClanMenuState(type, clanId, null, page, null, null, null);
    }

    private static ClanMenuState rankingMenuState(
            RankingCategory category,
            int page
    ) {
        return new ClanMenuState(
                ClanMenuType.RANKING,
                null,
                null,
                page,
                category.name(),
                null,
                null
        );
    }

    private static RankingCategory rankingCategory(String value) {
        return RankingCategory.fromConfigKey(value).orElse(RankingCategory.TOTAL);
    }

    private record PendingRoleNameInput(UUID clanId, String roleId) {

        private boolean creating() {
            return roleId == null;
        }
    }

    private record PendingRolePriorityInput(UUID clanId, String roleId) {
    }

    private record PendingClanEditInput(UUID clanId, ClanEditField field) {
    }

    private enum ClanEditField {
        NAME,
        TAG
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}
