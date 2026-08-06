package dev.catgirlyannick.catclans.gui;

import dev.catgirlyannick.catclans.CatClansPlugin;
import dev.catgirlyannick.catclans.audit.AuditLogEntry;
import dev.catgirlyannick.catclans.config.ConfigBundle;
import dev.catgirlyannick.catclans.integration.EconomyBridge;
import dev.catgirlyannick.catclans.integration.EconomyTransaction;
import dev.catgirlyannick.catclans.message.MessageService;
import dev.catgirlyannick.catclans.model.BattlepassProgress;
import dev.catgirlyannick.catclans.model.BattlepassReward;
import dev.catgirlyannick.catclans.model.BattlepassRewardType;
import dev.catgirlyannick.catclans.model.BattlepassView;
import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanBankView;
import dev.catgirlyannick.catclans.model.ClanHome;
import dev.catgirlyannick.catclans.model.ClanHomeView;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.VaultPageView;
import dev.catgirlyannick.catclans.service.ClanService;
import dev.catgirlyannick.catclans.service.OperationCode;
import dev.catgirlyannick.catclans.service.OperationResult;
import dev.catgirlyannick.catclans.service.VaultMutationType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ClanFeatureGuiController implements Listener {

    private static final UUID ADMIN_VIEW_ID = new UUID(0L, 0L);

    private final CatClansPlugin plugin;
    private final ConfigBundle configs;
    private final ClanService clanService;
    private final MessageService messages;
    private final EconomyBridge economy;
    private final ClanGuiController mainGui;
    private final NamespacedKey battlepassLevelKey;
    private final NamespacedKey rewardTypeKey;
    private final NamespacedKey vaultLogActorKey;
    private final NamespacedKey bankLogActorKey;
    private final DateTimeFormatter vaultLogDateFormatter;
    private final DateTimeFormatter bankLogDateFormatter;
    private final Set<Material> unsafeHomeMaterials;
    private final boolean requireSolidHomeGround;
    private final boolean allowCrossWorldHomes;
    private final int homeTeleportCooldownSeconds;
    private final Map<String, Material> materialCache = new HashMap<>();
    private final Map<String, Integer> slotCache = new HashMap<>();
    private final Map<String, ItemStack> decorationCache = new HashMap<>();
    private final Map<BattlepassRewardType, Material> rewardMaterialCache =
            new EnumMap<>(BattlepassRewardType.class);
    private final Map<UUID, UUID> openVaultByClan = new HashMap<>();
    private final Map<UUID, VaultSession> vaultSessions = new HashMap<>();
    private final Map<UUID, PendingVaultWrite> pendingVaultWrites = new HashMap<>();
    private final Map<UUID, ClanBankView> bankSessions = new HashMap<>();
    private final Map<UUID, PendingBankInput> pendingBankInputs = new ConcurrentHashMap<>();
    private final Map<UUID, PendingBankTransfer> pendingBankTransfers = new HashMap<>();
    private final Map<UUID, UUID> pendingBankClans = new HashMap<>();
    private final Map<UUID, ClanHomeView> homeSessions = new HashMap<>();
    private final Map<UUID, Long> homeCooldownUntilNanos = new HashMap<>();
    private final PlainTextComponentSerializer plainText =
            PlainTextComponentSerializer.plainText();
    private boolean shuttingDown;

    public ClanFeatureGuiController(
            CatClansPlugin plugin,
            ConfigBundle configs,
            ClanService clanService,
            MessageService messages,
            EconomyBridge economy,
            ClanGuiController mainGui
    ) {
        this.plugin = plugin;
        this.configs = configs;
        this.clanService = clanService;
        this.messages = messages;
        this.economy = economy;
        this.mainGui = mainGui;
        this.battlepassLevelKey = new NamespacedKey(plugin, "battlepass_level");
        this.rewardTypeKey = new NamespacedKey(plugin, "battlepass_reward_type");
        this.vaultLogActorKey = new NamespacedKey(plugin, "vault_log_actor");
        this.bankLogActorKey = new NamespacedKey(plugin, "bank_log_actor");
        this.vaultLogDateFormatter = DateTimeFormatter.ofPattern(
                configs.gui().getString(
                        "vault-log-entries-menu.date-time-pattern",
                        "dd.MM.yyyy HH:mm:ss"
                )
        ).withZone(ZoneId.systemDefault());
        this.bankLogDateFormatter = DateTimeFormatter.ofPattern(
                configs.gui().getString(
                        "bank-log-entries-menu.date-time-pattern",
                        "dd.MM.yyyy HH:mm:ss"
                )
        ).withZone(ZoneId.systemDefault());
        EnumSet<Material> unsafeMaterials = EnumSet.noneOf(Material.class);
        configs.homes().getStringList("safety.unsafe-materials").stream()
                .map(Material::matchMaterial)
                .filter(Objects::nonNull)
                .forEach(unsafeMaterials::add);
        this.unsafeHomeMaterials = Set.copyOf(unsafeMaterials);
        this.requireSolidHomeGround = configs.homes().getBoolean(
                "safety.require-solid-ground",
                true
        );
        this.allowCrossWorldHomes = configs.homes().getBoolean(
                "teleport.allow-cross-world",
                true
        );
        this.homeTeleportCooldownSeconds = configs.homes().getInt(
                "teleport.cooldown-seconds",
                0
        );
    }

    public boolean hasPendingStorageOperation(UUID clanId) {
        return pendingBankClans.containsKey(clanId)
                || pendingVaultWrites.values().stream()
                .anyMatch(write -> clanId.equals(write.holder().clanId()));
    }

    boolean reopen(Player player, ClanMenuState state) {
        return switch (state.type()) {
            case BATTLEPASS -> {
                if ("admin".equals(state.firstValue())) {
                    openBattlepassAdmin(player, state.page());
                    yield true;
                }
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openBattlepass(player, value, state.page()));
                yield clan.isPresent();
            }
            case BATTLEPASS_REWARD_EDITOR -> {
                openRewardEditor(
                        player,
                        state.page(),
                        Integer.parseInt(state.firstValue())
                );
                yield true;
            }
            case BANK -> {
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openBank(player, value));
                yield clan.isPresent();
            }
            case BANK_DEPOSIT, BANK_WITHDRAW -> {
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openBankAmountMenu(
                        player,
                        value,
                        state.type() == ClanMenuType.BANK_DEPOSIT
                ));
                yield clan.isPresent();
            }
            case BANK_LOG_MEMBERS -> {
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openBankLogMembers(player, value, state.page()));
                yield clan.isPresent();
            }
            case BANK_LOG_ENTRIES -> {
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openBankLogEntries(
                        player,
                        value,
                        state.targetId(),
                        state.firstValue()
                ));
                yield clan.isPresent();
            }
            case HOMES -> {
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openHomes(player, value, state.page()));
                yield clan.isPresent();
            }
            case HOME_CONFIRMATION -> {
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openHomeConfirmation(
                        player,
                        value,
                        state.page(),
                        Integer.parseInt(state.firstValue()),
                        state.secondValue()
                ));
                yield clan.isPresent();
            }
            case VAULT -> {
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openVault(player, value, state.page()));
                yield clan.isPresent();
            }
            case VAULT_LOG_MEMBERS -> {
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openVaultLogMembers(player, value, state.page()));
                yield clan.isPresent();
            }
            case VAULT_LOG_ENTRIES -> {
                Optional<Clan> clan = clanService.findCachedClan(state.clanId());
                clan.ifPresent(value -> openVaultLogEntries(
                        player,
                        value,
                        state.targetId(),
                        state.firstValue()
                ));
                yield clan.isPresent();
            }
            default -> false;
        };
    }

    public void openBattlepass(Player player, Clan clan, int page) {
        if (!player.hasPermission("catclans.clan.battlepass.view")) {
            messages.send(player, "errors.no-permission");
            return;
        }
        int levelsPerPage = configs.gui().getInt("battlepass-menu.levels-per-page", 28);
        int safePage = Math.max(0, page);
        int fromLevel = safePage * levelsPerPage + 1;
        int toLevel = fromLevel + levelsPerPage - 1;
        handle(
                clanService.battlepassView(clan.id(), fromLevel, toLevel),
                view -> renderBattlepass(player, clan, safePage, false, view),
                player
        );
    }

    public void shutdown() {
        shuttingDown = true;
        List<PendingBankTransfer> bankTransfers = List.copyOf(
                pendingBankTransfers.values()
        );
        CompletableFuture.allOf(bankTransfers.stream()
                .map(pending -> pending.future().handle((result, error) -> null))
                .toArray(CompletableFuture[]::new))
                .join();
        for (PendingBankTransfer pending : bankTransfers) {
            resolvePendingBankDuringShutdown(pending);
        }
        List<PendingVaultWrite> pendingWrites = List.copyOf(pendingVaultWrites.values());
        CompletableFuture.allOf(pendingWrites.stream()
                .map(pending -> pending.future().handle((result, error) -> null))
                .toArray(CompletableFuture[]::new))
                .join();
        for (PendingVaultWrite pending : pendingWrites) {
            resolvePendingDuringShutdown(pending);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder()
                    instanceof ClanMenuHolder) {
                player.closeInventory();
            }
        }
        vaultSessions.clear();
        openVaultByClan.clear();
        bankSessions.clear();
        pendingBankInputs.clear();
        pendingBankTransfers.clear();
        pendingBankClans.clear();
        homeSessions.clear();
        homeCooldownUntilNanos.clear();
    }

    private void resolvePendingBankDuringShutdown(PendingBankTransfer pending) {
        try {
            OperationResult<ClanBankView> result = pending.future().join();
            settleBankTransfer(pending, result, null, false);
        } catch (RuntimeException exception) {
            settleBankTransfer(pending, null, exception, false);
        }
    }

    private void resolvePendingDuringShutdown(PendingVaultWrite pending) {
        try {
            OperationResult<Void> result = pending.future().join();
            if (result.successful() && pending.unchanged()) {
                pending.apply();
            } else if (result.successful()) {
                restoreUntilSuccessful(pending);
            }
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Pending vault operation could not be resolved during shutdown: "
                            + rootMessage(exception)
            );
        } finally {
            pendingVaultWrites.remove(pending.player().getUniqueId());
        }
    }

    private void restoreUntilSuccessful(PendingVaultWrite pending) {
        while (true) {
            try {
                clanService.restoreVaultSlot(
                        pending.holder().clanId(),
                        pending.holder().page(),
                        pending.slot(),
                        serialize(pending.plan().beforeSlot()),
                        pending.player().getUniqueId(),
                        pending.player().getName()
                ).join();
                return;
            } catch (RuntimeException exception) {
                plugin.getLogger().severe(
                        "Vault-Rollback wird zum Schutz der Items wiederholt: "
                                + rootMessage(exception)
                );
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Vault rollback was interrupted",
                            interrupted
                    );
                }
            }
        }
    }

    public void openBattlepassAdmin(Player player, int page) {
        if (!player.hasPermission("catclans.admin.battlepass.rewards")) {
            messages.send(player, "errors.no-permission");
            return;
        }
        int levelsPerPage = configs.gui().getInt("battlepass-menu.levels-per-page", 28);
        int safePage = Math.max(0, page);
        int fromLevel = safePage * levelsPerPage + 1;
        int toLevel = fromLevel + levelsPerPage - 1;
        handle(
                clanService.battlepassRewards(fromLevel, toLevel),
                rewards -> {
                    BattlepassProgress progress = BattlepassProgress.initial(
                            ADMIN_VIEW_ID,
                            java.time.Instant.EPOCH
                    );
                    renderBattlepass(
                            player,
                            null,
                            safePage,
                            true,
                            new BattlepassView(
                                    ADMIN_VIEW_ID,
                                    progress,
                                    BigDecimal.ZERO,
                                    rewards,
                                    java.util.Set.of()
                            )
                    );
                },
                player
        );
    }

    public void openBank(Player player, Clan clan) {
        if (!player.hasPermission("catclans.clan.bank.view")) {
            messages.send(player, "errors.no-permission");
            return;
        }
        if (!economy.available()) {
            messages.send(player, "errors.economy-unavailable");
            return;
        }
        handle(
                clanService.openBank(player.getUniqueId()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    renderBank(player, clan, result.value());
                },
                player
        );
    }

    public void openHomes(Player player, Clan clan, int requestedPage) {
        if (!player.hasPermission("catclans.clan.homes.open")) {
            messages.send(player, "errors.no-permission");
            return;
        }
        handle(
                clanService.openHomes(player.getUniqueId()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    renderHomes(player, clan, requestedPage, result.value());
                },
                player
        );
    }

    private void renderHomes(
            Player player,
            Clan clan,
            int requestedPage,
            ClanHomeView view
    ) {
        List<Integer> slots = configs.gui().getIntegerList("home-menu.home-slots");
        int pages = Math.max(1, (view.unlockedSlots() + slots.size() - 1) / slots.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Map<String, String> values = homeOverviewValues(view, page, pages);
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.HOMES,
                clan.id(),
                null,
                page,
                null,
                null,
                null
        );
        Inventory inventory = createInventory(holder, "home-menu", values);
        fill(inventory);
        int firstHome = page * slots.size() + 1;
        for (int index = 0; index < slots.size(); index++) {
            int number = firstHome + index;
            if (number > view.unlockedSlots()) {
                break;
            }
            Optional<ClanHome> home = view.home(number);
            Map<String, String> itemValues = home
                    .map(value -> homeValues(value, values))
                    .orElseGet(() -> withHomeNumber(values, number));
            inventory.setItem(
                    slots.get(index),
                    item(
                            home.isPresent() ? "home-menu.home" : "home-menu.empty",
                            itemValues,
                            home.isPresent() ? Material.LIME_BED : Material.GRAY_BED
                    )
            );
        }
        if (page > 0) {
            set(inventory, "home-menu.previous", values);
        }
        if (page + 1 < pages) {
            set(inventory, "home-menu.next", values);
        }
        if (view.unlockedSlots() < view.maximumSlots()) {
            set(inventory, "home-menu.extensions", values);
        }
        set(inventory, "home-menu.info", values);
        set(inventory, "home-menu.back", values);
        set(inventory, "home-menu.close", values);
        homeSessions.put(player.getUniqueId(), view);
        mainGui.openMenu(player, inventory);
    }

    private void handleHomesClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        event.setCancelled(true);
        int clickedSlot = event.getRawSlot();
        if (clickedSlot < 0
                || clickedSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (clickedSlot == slot("home-menu.back")) {
            mainGui.navigateBack(player);
            return;
        }
        if (clickedSlot == slot("home-menu.close")) {
            player.closeInventory();
            return;
        }
        Optional<Clan> clan = clanService.findCachedClan(holder.clanId());
        if (clan.isEmpty()) {
            messages.send(player, "errors.clan-not-found", Map.of("clan", "-"));
            return;
        }
        if (clickedSlot == slot("home-menu.previous")) {
            int page = holder.page() - 1;
            mainGui.replaceNext(player, new ClanMenuState(
                    ClanMenuType.HOMES, holder.clanId(), null, page, null, null, null
            ));
            openHomes(player, clan.get(), page);
            return;
        }
        if (clickedSlot == slot("home-menu.next")) {
            int page = holder.page() + 1;
            mainGui.replaceNext(player, new ClanMenuState(
                    ClanMenuType.HOMES, holder.clanId(), null, page, null, null, null
            ));
            openHomes(player, clan.get(), page);
            return;
        }
        if (clickedSlot == slot("home-menu.extensions")) {
            openBattlepass(player, clan.get(), 0);
            return;
        }
        List<Integer> slots = configs.gui().getIntegerList("home-menu.home-slots");
        int index = slots.indexOf(clickedSlot);
        ClanHomeView view = homeSessions.get(player.getUniqueId());
        if (index < 0 || view == null || !holder.clanId().equals(view.clanId())) {
            return;
        }
        int number = holder.page() * slots.size() + index + 1;
        if (number > view.unlockedSlots()) {
            sendFailure(player, OperationCode.HOME_SLOT_LOCKED);
            return;
        }
        Optional<ClanHome> home = view.home(number);
        if (event.isLeftClick()) {
            if (home.isEmpty()) {
                sendFailure(player, OperationCode.HOME_NOT_SET);
            } else if (view.canTeleport()) {
                teleportHome(player, number);
            } else {
                sendFailure(player, OperationCode.CLAN_RIGHT_MISSING);
            }
            return;
        }
        if (!event.isRightClick()) {
            return;
        }
        if (event.isShiftClick()) {
            if (home.isEmpty()) {
                return;
            }
            if (!view.canDelete()) {
                sendFailure(player, OperationCode.CLAN_RIGHT_MISSING);
                return;
            }
            openHomeConfirmation(player, clan.get(), holder.page(), number, "delete");
        } else if (view.canSet()) {
            openHomeConfirmation(player, clan.get(), holder.page(), number, "set");
        } else {
            sendFailure(player, OperationCode.CLAN_RIGHT_MISSING);
        }
    }

    private void openHomeConfirmation(
            Player player,
            Clan clan,
            int page,
            int number,
            String action
    ) {
        if (!"set".equals(action) && !"delete".equals(action)) {
            return;
        }
        Map<String, String> values = Map.of(
                "home", Integer.toString(number),
                "action", configs.gui().getString(
                        "home-confirmation-menu.actions." + action,
                        action
                )
        );
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.HOME_CONFIRMATION,
                clan.id(),
                null,
                page,
                Integer.toString(number),
                action,
                null
        );
        Inventory inventory = createInventory(holder, "home-confirmation-menu", values);
        fill(inventory);
        set(inventory, "home-confirmation-menu.subject", values);
        set(inventory, "home-confirmation-menu.confirm", values);
        set(inventory, "home-confirmation-menu.cancel", values);
        mainGui.openMenu(player, inventory);
    }

    private void handleHomeConfirmationClick(
            Player player,
            ClanMenuHolder holder,
            int clickedSlot
    ) {
        if (clickedSlot == slot("home-confirmation-menu.cancel")) {
            mainGui.navigateBack(player);
            return;
        }
        if (clickedSlot != slot("home-confirmation-menu.confirm")) {
            return;
        }
        int number = Integer.parseInt(holder.firstValue());
        if ("set".equals(holder.secondValue())) {
            saveCurrentHome(player, number);
        } else if ("delete".equals(holder.secondValue())) {
            deleteHome(player, number);
        }
    }

    private void saveCurrentHome(Player player, int number) {
        Location location = player.getLocation().clone();
        if (!HomeSafety.isSafe(location, unsafeHomeMaterials, requireSolidHomeGround)) {
            messages.send(player, "errors.home-location-unsafe");
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            messages.send(player, "errors.home-world-unavailable");
            return;
        }
        handle(
                clanService.setHome(
                        player.getUniqueId(),
                        player.getName(),
                        number,
                        world.getUID(),
                        world.getName(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch()
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    messages.send(player, "general.home-set", Map.of(
                            "home", Integer.toString(number)
                    ));
                    mainGui.navigateBack(player);
                },
                player
        );
    }

    private void deleteHome(Player player, int number) {
        handle(
                clanService.deleteHome(player.getUniqueId(), player.getName(), number),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    messages.send(player, "general.home-deleted", Map.of(
                            "home", Integer.toString(number)
                    ));
                    mainGui.navigateBack(player);
                },
                player
        );
    }

    private void teleportHome(Player player, int number) {
        long now = System.nanoTime();
        long cooldownUntil = homeCooldownUntilNanos.getOrDefault(
                player.getUniqueId(),
                0L
        );
        if (cooldownUntil > now) {
            long seconds = Math.max(1L, (cooldownUntil - now + 999_999_999L) / 1_000_000_000L);
            messages.send(player, "errors.home-teleport-cooldown", Map.of(
                    "seconds", Long.toString(seconds)
            ));
            return;
        }
        handle(
                clanService.homeForTeleport(player.getUniqueId(), number),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    prepareHomeTeleport(player, result.value());
                },
                player
        );
    }

    private void prepareHomeTeleport(Player player, ClanHome home) {
        World world = Bukkit.getWorld(home.worldId());
        if (world == null) {
            world = Bukkit.getWorld(home.worldName());
        }
        if (world == null) {
            messages.send(player, "errors.home-world-unavailable");
            return;
        }
        if (!allowCrossWorldHomes && !world.equals(player.getWorld())) {
            messages.send(player, "errors.home-cross-world-disabled");
            return;
        }
        Location target = new Location(
                world,
                home.x(),
                home.y(),
                home.z(),
                home.yaw(),
                home.pitch()
        );
        World targetWorld = world;
        world.getChunkAtAsync(target.getBlockX() >> 4, target.getBlockZ() >> 4, true)
                .whenComplete((chunk, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || !player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        plugin.getLogger().warning("Clan home chunk could not be loaded: "
                                + rootMessage(error));
                        messages.send(player, "errors.home-world-unavailable");
                        return;
                    }
                    if (!targetWorld.equals(target.getWorld())
                            || !HomeSafety.isSafe(
                            target,
                            unsafeHomeMaterials,
                            requireSolidHomeGround
                    )) {
                        messages.send(player, "errors.home-location-unsafe");
                        return;
                    }
                    completeHomeTeleport(player, home, target);
                }));
    }

    private void completeHomeTeleport(Player player, ClanHome home, Location target) {
        player.teleportAsync(target).whenComplete((teleported, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || !player.isOnline()) {
                        return;
                    }
                    if (error != null || !Boolean.TRUE.equals(teleported)) {
                        if (error != null) {
                            plugin.getLogger().warning("Clan-Home-Teleport failed: "
                                    + rootMessage(error));
                        }
                        messages.send(player, "errors.home-teleport-failed");
                        return;
                    }
                    if (homeTeleportCooldownSeconds > 0) {
                        homeCooldownUntilNanos.put(
                                player.getUniqueId(),
                                System.nanoTime() + homeTeleportCooldownSeconds * 1_000_000_000L
                        );
                    }
                    messages.send(player, "general.home-teleported", Map.of(
                            "home", Integer.toString(home.number())
                    ));
                    clanService.recordHomeTeleport(
                            player.getUniqueId(),
                            player.getName(),
                            home.number()
                    ).exceptionally(errorValue -> {
                        plugin.getLogger().warning("Clan-Home-Teleportlog failed: "
                                + rootMessage(errorValue));
                        return null;
                    });
                })
        );
    }

    private static Map<String, String> homeOverviewValues(
            ClanHomeView view,
            int page,
            int pages
    ) {
        return Map.of(
                "set_homes", Integer.toString(view.homes().size()),
                "unlocked_homes", Integer.toString(view.unlockedSlots()),
                "maximum_homes", Integer.toString(view.maximumSlots()),
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(pages)
        );
    }

    private static Map<String, String> withHomeNumber(
            Map<String, String> values,
            int number
    ) {
        Map<String, String> result = new HashMap<>(values);
        result.put("home", Integer.toString(number));
        return Map.copyOf(result);
    }

    private static Map<String, String> homeValues(
            ClanHome home,
            Map<String, String> values
    ) {
        Map<String, String> result = new HashMap<>(withHomeNumber(values, home.number()));
        result.put("world", home.worldName());
        result.put("x", Integer.toString((int) Math.floor(home.x())));
        result.put("y", Integer.toString((int) Math.floor(home.y())));
        result.put("z", Integer.toString((int) Math.floor(home.z())));
        return Map.copyOf(result);
    }

    private void renderBank(Player player, Clan clan, ClanBankView view) {
        bankSessions.put(player.getUniqueId(), view);
        Map<String, String> values = bankValues(view);
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.BANK,
                clan.id(),
                null,
                0,
                null,
                null,
                null
        );
        Inventory inventory = createInventory(
                holder,
                "bank-menu",
                values
        );
        fill(inventory);
        set(inventory, "bank-menu.overview", values);
        set(inventory, "bank-menu.deposit", values);
        set(inventory, "bank-menu.withdraw", values);
        set(inventory, "bank-menu.log", values);
        if (view.canManagePermissions()) {
            set(inventory, "bank-menu.permissions", values);
        }
        set(inventory, "bank-menu.back", values);
        set(inventory, "bank-menu.close", values);
        mainGui.openMenu(player, inventory);
    }

    private void openBankAmountMenu(Player player, Clan clan, boolean deposit) {
        handle(
                clanService.openBank(player.getUniqueId()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    ClanBankView view = result.value();
                    if (deposit && !view.canDeposit()
                            || !deposit && !view.canWithdraw()) {
                        sendFailure(player, OperationCode.CLAN_RIGHT_MISSING);
                        return;
                    }
                    renderBankAmountMenu(player, clan, view, deposit);
                },
                player
        );
    }

    private void renderBankAmountMenu(
            Player player,
            Clan clan,
            ClanBankView view,
            boolean deposit
    ) {
        bankSessions.put(player.getUniqueId(), view);
        Map<String, String> values = new HashMap<>(bankValues(view));
        values.put("action", deposit ? "Deposit" : "Withdraw");
        ClanMenuHolder holder = new ClanMenuHolder(
                deposit ? ClanMenuType.BANK_DEPOSIT : ClanMenuType.BANK_WITHDRAW,
                clan.id(),
                null,
                0,
                null,
                null,
                null
        );
        Inventory inventory = createInventory(holder, "bank-amount-menu", values);
        fill(inventory);
        List<Integer> slots = configs.gui().getIntegerList(
                "bank-amount-menu.quick-amount-slots"
        );
        List<String> amounts = configs.economy().getStringList(
                "clan-bank.quick-amounts"
        );
        for (int index = 0; index < Math.min(slots.size(), amounts.size()); index++) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(amounts.get(index));
            } catch (NumberFormatException ignored) {
                continue;
            }
            Map<String, String> itemValues = new HashMap<>(values);
            itemValues.put("amount", formatBankAmount(amount));
            inventory.setItem(
                    slots.get(index),
                    item("bank-amount-menu.quick-amount", itemValues, Material.GOLD_NUGGET)
            );
        }
        set(inventory, "bank-amount-menu.info", values);
        set(inventory, "bank-amount-menu.custom", values);
        set(inventory, "bank-amount-menu.back", values);
        set(inventory, "bank-amount-menu.close", values);
        mainGui.openMenu(player, inventory);
    }

    public void openVault(Player player, Clan clan, int page) {
        if (!player.hasPermission("catclans.clan.vault.open")) {
            messages.send(player, "errors.no-permission");
            return;
        }
        UUID currentViewer = openVaultByClan.get(clan.id());
        if (currentViewer != null && !currentViewer.equals(player.getUniqueId())) {
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(currentViewer).getName())
                    .orElse(currentViewer.toString());
            messages.send(player, "errors.vault-in-use", Map.of("player", name));
            return;
        }
        handle(
                clanService.openVault(player.getUniqueId(), page),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    renderVault(player, result.value());
                },
                player
        );
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClanMenuHolder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (holder.type()) {
            case BATTLEPASS -> handleBattlepassClick(player, holder, event);
            case BATTLEPASS_REWARD_EDITOR ->
                    handleRewardEditorClick(player, holder, event);
            case BANK -> handleBankClick(player, holder, event);
            case BANK_DEPOSIT, BANK_WITHDRAW ->
                    handleBankAmountClick(player, holder, event.getRawSlot());
            case BANK_LOG_MEMBERS ->
                    handleBankLogMembersClick(player, holder, event);
            case BANK_LOG_ENTRIES ->
                    handleBankLogEntriesClick(player, event.getRawSlot());
            case HOMES -> handleHomesClick(player, holder, event);
            case HOME_CONFIRMATION ->
                    handleHomeConfirmationClick(player, holder, event.getRawSlot());
            case VAULT -> handleVaultClick(player, holder, event);
            case VAULT_LOG_MEMBERS ->
                    handleVaultLogMembersClick(player, holder, event);
            case VAULT_LOG_ENTRIES ->
                    handleVaultLogEntriesClick(player, event.getRawSlot());
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClanMenuHolder holder)
                || holder.type() != ClanMenuType.VAULT) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClanMenuHolder holder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (holder.type() != ClanMenuType.VAULT) {
            if (holder.type() == ClanMenuType.HOMES
                    || holder.type() == ClanMenuType.HOME_CONFIRMATION) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder()
                            instanceof ClanMenuHolder openHolder
                            && (openHolder.type() == ClanMenuType.HOMES
                            || openHolder.type() == ClanMenuType.HOME_CONFIRMATION)
                            && holder.clanId().equals(openHolder.clanId())) {
                        return;
                    }
                    homeSessions.remove(player.getUniqueId());
                });
            }
            if (isBankMenu(holder.type())) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder()
                            instanceof ClanMenuHolder openHolder
                            && isBankMenu(openHolder.type())
                            && holder.clanId().equals(openHolder.clanId())) {
                        return;
                    }
                    if (!pendingBankInputs.containsKey(player.getUniqueId())) {
                        bankSessions.remove(player.getUniqueId());
                    }
                });
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder()
                    instanceof ClanMenuHolder openHolder
                    && openHolder.type() == ClanMenuType.VAULT
                    && holder.clanId().equals(openHolder.clanId())) {
                return;
            }
            if (pendingVaultWrites.containsKey(player.getUniqueId())) {
                return;
            }
            vaultSessions.remove(player.getUniqueId());
            openVaultByClan.remove(holder.clanId(), player.getUniqueId());
        });
    }

    @EventHandler
    public void onBankChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingBankInput pending = pendingBankInputs.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String input = plainText.serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(
                plugin,
                () -> handleBankChatInput(player, pending, input)
        );
    }

    @EventHandler
    public void onBankQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingBankInputs.remove(playerId);
        bankSessions.remove(playerId);
        homeSessions.remove(playerId);
        homeCooldownUntilNanos.remove(playerId);
    }

    private void renderBattlepass(
            Player player,
            Clan clan,
            int page,
            boolean admin,
            BattlepassView view
    ) {
        Map<String, String> values = progressValues(view.progress(), view.requiredXp());
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.BATTLEPASS,
                clan == null ? null : clan.id(),
                null,
                page,
                admin ? "admin" : null,
                null,
                null
        );
        Inventory inventory = createInventory(holder, "battlepass-menu", values);
        fillBattlepass(inventory);
        set(inventory, "battlepass-menu.info", values);
        List<Integer> slots = configs.gui().getIntegerList("battlepass-menu.level-slots");
        int firstLevel = page * slots.size() + 1;
        Map<Integer, List<BattlepassReward>> rewardsByLevel = new HashMap<>();
        view.rewards().stream()
                .filter(reward -> rewardTypeEnabled(reward.type()))
                .forEach(reward -> rewardsByLevel
                .computeIfAbsent(reward.level(), ignored -> new ArrayList<>())
                .add(reward));
        for (int index = 0; index < slots.size(); index++) {
            int rewardLevel = firstLevel + index;
            List<BattlepassReward> rewards = rewardsByLevel.getOrDefault(
                    rewardLevel,
                    List.of()
            );
            String path;
            if (admin) {
                path = rewardLevel % 5 == 0
                        ? "battlepass-menu.level-admin-milestone"
                        : "battlepass-menu.level-admin";
            } else if (rewardLevel > view.progress().level()) {
                path = rewardLevel % 5 == 0
                        ? "battlepass-menu.level-milestone-locked"
                        : "battlepass-menu.level-locked";
            } else if (rewards.isEmpty()) {
                path = "battlepass-menu.level-unlocked";
            } else if (rewards.stream().allMatch(view::claimed)) {
                path = "battlepass-menu.level-claimed";
            } else {
                path = "battlepass-menu.level-claimable";
            }
            Map<String, String> levelValues = new HashMap<>(values);
            levelValues.put("reward_level", Integer.toString(rewardLevel));
            levelValues.put("reward_count", Integer.toString(rewards.size()));
            ItemStack item = item(path, Map.copyOf(levelValues), Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            List<Component> lore = meta.lore() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(meta.lore());
            if (rewards.isEmpty()) {
                lore.add(messages.renderConfig(
                        "gui",
                        "battlepass-menu.no-rewards-line",
                        Map.of()
                ));
            } else {
                rewards.forEach(reward -> lore.add(rewardLine(reward)));
            }
            meta.lore(GuiTextStyle.nonItalic(lore));
            meta.getPersistentDataContainer().set(
                    battlepassLevelKey,
                    PersistentDataType.INTEGER,
                    rewardLevel
            );
            item.setItemMeta(meta);
            inventory.setItem(slots.get(index), item);
        }
        if (page > 0) {
            set(inventory, "battlepass-menu.previous", values);
        }
        set(inventory, "battlepass-menu.next", values);
        set(inventory, "battlepass-menu.back", values);
        set(inventory, "battlepass-menu.close", values);
        mainGui.openMenu(player, inventory);
    }

    private void handleBattlepassClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == slot("battlepass-menu.back")) {
            mainGui.navigateBack(player);
            return;
        }
        if (slot == slot("battlepass-menu.close")) {
            player.closeInventory();
            return;
        }
        if (slot == slot("battlepass-menu.previous")) {
            mainGui.replaceNext(
                    player,
                    new ClanMenuState(
                            ClanMenuType.BATTLEPASS,
                            holder.clanId(),
                            null,
                            holder.page() - 1,
                            holder.firstValue(),
                            null,
                            null
                    )
            );
            reopenBattlepass(player, holder, holder.page() - 1);
            return;
        }
        if (slot == slot("battlepass-menu.next")) {
            mainGui.replaceNext(
                    player,
                    new ClanMenuState(
                            ClanMenuType.BATTLEPASS,
                            holder.clanId(),
                            null,
                            holder.page() + 1,
                            holder.firstValue(),
                            null,
                            null
                    )
            );
            reopenBattlepass(player, holder, holder.page() + 1);
            return;
        }
        Integer level = readInteger(event.getCurrentItem(), battlepassLevelKey);
        if (level == null) {
            return;
        }
        if ("admin".equals(holder.firstValue())) {
            if (!player.hasPermission("catclans.admin.battlepass.rewards")) {
                messages.send(player, "errors.no-permission");
                player.closeInventory();
                return;
            }
            openRewardEditor(player, holder.page(), level);
            return;
        }
        Optional<Clan> clan = clanService.findCachedClan(holder.clanId());
        if (clan.isEmpty() || !clan.get().ownerId().equals(player.getUniqueId())) {
            return;
        }
        handle(
                clanService.claimBattlepassLevel(
                        player.getUniqueId(),
                        player.getName(),
                        level
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    messages.send(player, "battlepass.reward-claimed", Map.of(
                            "level",
                            Integer.toString(level)
                    ));
                    openBattlepass(player, clan.get(), holder.page());
                },
                player
        );
    }

    private void reopenBattlepass(Player player, ClanMenuHolder holder, int page) {
        if ("admin".equals(holder.firstValue())) {
            openBattlepassAdmin(player, page);
            return;
        }
        clanService.findCachedClan(holder.clanId())
                .ifPresent(clan -> openBattlepass(player, clan, page));
    }

    private void openRewardEditor(Player player, int treePage, int level) {
        handle(
                clanService.battlepassRewards(level, level),
                rewards -> renderRewardEditor(player, treePage, level, rewards),
                player
        );
    }

    private void renderRewardEditor(
            Player player,
            int treePage,
            int level,
            List<BattlepassReward> rewards
    ) {
        Map<BattlepassRewardType, Integer> amounts = new EnumMap<>(
                BattlepassRewardType.class
        );
        rewards.forEach(reward -> amounts.put(reward.type(), reward.amount()));
        Map<String, String> values = Map.of("reward_level", Integer.toString(level));
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.BATTLEPASS_REWARD_EDITOR,
                null,
                null,
                treePage,
                Integer.toString(level),
                "admin",
                null
        );
        Inventory inventory = createInventory(
                holder,
                "battlepass-reward-editor-menu",
                values
        );
        fill(inventory);
        set(inventory, "battlepass-reward-editor-menu.instruction", values);
        for (BattlepassRewardType type : BattlepassRewardType.values()) {
            if (!rewardTypeEnabled(type)) {
                continue;
            }
            int slot = configs.gui().getInt(
                    "battlepass-reward-editor-menu.reward-slots." + type.configKey(),
                    -1
            );
            Map<String, String> itemValues = Map.of(
                    "reward_name", rewardDisplayName(type),
                    "amount", Integer.toString(amounts.getOrDefault(type, 0))
            );
            ItemStack item = item(
                    "battlepass-reward-editor-menu.reward",
                    itemValues,
                    rewardMaterial(type)
            );
            ItemMeta meta = item.getItemMeta();
            meta.displayName(GuiTextStyle.nonItalic(
                    messages.renderMenu(rewardDisplayName(type), Map.of())
            ));
            meta.getPersistentDataContainer().set(
                    rewardTypeKey,
                    PersistentDataType.STRING,
                    type.name()
            );
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
        set(inventory, "battlepass-reward-editor-menu.back", values);
        set(inventory, "battlepass-reward-editor-menu.close", values);
        mainGui.openMenu(player, inventory);
    }

    private void handleRewardEditorClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        event.setCancelled(true);
        if (!player.hasPermission("catclans.admin.battlepass.rewards")) {
            messages.send(player, "errors.no-permission");
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == slot("battlepass-reward-editor-menu.back")) {
            mainGui.navigateBack(player);
            return;
        }
        if (event.getRawSlot() == slot("battlepass-reward-editor-menu.close")) {
            player.closeInventory();
            return;
        }
        String storedType = readString(event.getCurrentItem(), rewardTypeKey);
        Optional<BattlepassRewardType> type = Optional.ofNullable(storedType)
                .flatMap(BattlepassRewardType::fromConfigKey);
        if (type.isEmpty() || !rewardTypeEnabled(type.get())) {
            return;
        }
        int delta = event.isRightClick() ? -1 : 1;
        int level = Integer.parseInt(holder.firstValue());
        handle(
                clanService.adjustBattlepassReward(
                        player.getUniqueId(),
                        level,
                        type.get(),
                        delta
                ),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    messages.send(player, "battlepass.reward-updated", Map.of(
                            "level",
                            Integer.toString(level)
                    ));
                    openRewardEditor(player, holder.page(), level);
                },
                player
        );
    }

    private void handleBankClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        int slot = event.getRawSlot();
        if (slot == slot("bank-menu.back")) {
            mainGui.navigateBack(player);
        } else if (slot == slot("bank-menu.close")) {
            player.closeInventory();
        } else if (slot == slot("bank-menu.deposit")) {
            clanService.findCachedClan(holder.clanId())
                    .ifPresent(clan -> openBankAmountMenu(player, clan, true));
        } else if (slot == slot("bank-menu.withdraw")) {
            clanService.findCachedClan(holder.clanId())
                    .ifPresent(clan -> openBankAmountMenu(player, clan, false));
        } else if (slot == slot("bank-menu.log")) {
            clanService.findCachedClan(holder.clanId())
                    .ifPresent(clan -> openBankLogMembers(player, clan, 0));
        } else if (slot == slot("bank-menu.permissions")) {
            ClanBankView view = bankSessions.get(player.getUniqueId());
            if (view != null && view.canManagePermissions()) {
                mainGui.openPermissionHome(player);
            }
        }
    }

    private void handleBankAmountClick(
            Player player,
            ClanMenuHolder holder,
            int slot
    ) {
        if (slot == slot("bank-amount-menu.back")) {
            mainGui.navigateBack(player);
            return;
        }
        if (slot == slot("bank-amount-menu.close")) {
            player.closeInventory();
            return;
        }
        boolean deposit = holder.type() == ClanMenuType.BANK_DEPOSIT;
        if (slot == slot("bank-amount-menu.custom")) {
            beginBankChatInput(player, holder.clanId(), deposit);
            return;
        }
        List<Integer> slots = configs.gui().getIntegerList(
                "bank-amount-menu.quick-amount-slots"
        );
        int index = slots.indexOf(slot);
        List<String> amounts = configs.economy().getStringList(
                "clan-bank.quick-amounts"
        );
        if (index < 0 || index >= amounts.size()) {
            return;
        }
        try {
            executeBankTransfer(
                    player,
                    holder.clanId(),
                    deposit,
                    new BigDecimal(amounts.get(index))
            );
        } catch (NumberFormatException exception) {
            messages.send(player, "errors.invalid-bank-amount");
        }
    }

    private void beginBankChatInput(Player player, UUID clanId, boolean deposit) {
        PendingBankInput pending = new PendingBankInput(clanId, deposit);
        pendingBankInputs.put(player.getUniqueId(), pending);
        player.closeInventory();
        sendBankInputPrompt(player, deposit);
    }

    private void handleBankChatInput(
            Player player,
            PendingBankInput pending,
            String input
    ) {
        if (!pendingBankInputs.remove(player.getUniqueId(), pending)
                || !player.isOnline()) {
            return;
        }
        if (input.equalsIgnoreCase(bankInputCancelKeyword())) {
            messages.send(player, "bank.input-cancelled");
            clanService.findCachedClan(pending.clanId()).ifPresent(clan ->
                    openBankAmountMenu(player, clan, pending.deposit()));
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(input.replace(',', '.'));
        } catch (NumberFormatException exception) {
            messages.send(player, "errors.invalid-bank-amount");
            pendingBankInputs.put(player.getUniqueId(), pending);
            sendBankInputPrompt(player, pending.deposit());
            return;
        }
        executeBankTransfer(
                player,
                pending.clanId(),
                pending.deposit(),
                amount
        );
    }

    private void sendBankInputPrompt(Player player, boolean deposit) {
        messages.send(
                player,
                deposit ? "bank.deposit-prompt" : "bank.withdraw-prompt",
                Map.of(
                        "currency", bankCurrency(),
                        "cancel", bankInputCancelKeyword()
                )
        );
    }

    private String bankInputCancelKeyword() {
        return configs.gui().getString(
                "input-menu.bank-amount.chat.cancel-keyword",
                "cancel"
        ).trim();
    }

    private void executeBankTransfer(
            Player player,
            UUID clanId,
            boolean deposit,
            BigDecimal amount
    ) {
        try {
            amount = amount.setScale(
                    configs.economy().getInt("clan-bank.decimal-scale", 2),
                    RoundingMode.valueOf(configs.economy().getString(
                            "clan-bank.rounding-mode",
                            "HALF_UP"
                    ))
            ).stripTrailingZeros();
        } catch (RuntimeException exception) {
            messages.send(player, "errors.invalid-bank-amount");
            return;
        }
        BigDecimal minimum = configuredBankDecimal(
                deposit
                        ? "clan-bank.minimum-deposit"
                        : "clan-bank.minimum-withdrawal"
        );
        double vaultAmount = amount.doubleValue();
        if (amount.signum() <= 0 || amount.compareTo(minimum) < 0
                || !Double.isFinite(vaultAmount)) {
            messages.send(player, "errors.invalid-bank-amount");
            return;
        }
        if (!economy.available()) {
            messages.send(player, "errors.economy-unavailable");
            return;
        }
        Optional<Clan> ownClan = clanService.findCachedClanForPlayer(player.getUniqueId());
        if (ownClan.isEmpty() || !ownClan.get().id().equals(clanId)) {
            messages.send(player, "errors.not-in-clan");
            return;
        }
        UUID current = pendingBankClans.putIfAbsent(clanId, player.getUniqueId());
        if (current != null) {
            messages.send(player, "errors.bank-busy");
            return;
        }
        if (deposit && !economy.has(player, vaultAmount)) {
            releaseBankTransfer(clanId, player.getUniqueId());
            messages.send(player, "errors.insufficient-funds", Map.of(
                    "price", formatBankAmount(amount)
            ));
            return;
        }
        if (deposit) {
            EconomyTransaction withdrawal = economy.withdraw(player, vaultAmount);
            if (!withdrawal.successful()) {
                releaseBankTransfer(clanId, player.getUniqueId());
                messages.send(player, "errors.economy-transaction-failed");
                return;
            }
        }
        CompletableFuture<OperationResult<ClanBankView>> future = deposit
                ? clanService.depositBank(player.getUniqueId(), player.getName(), amount)
                : clanService.withdrawBank(player.getUniqueId(), player.getName(), amount);
        PendingBankTransfer pending = new PendingBankTransfer(
                player,
                clanId,
                amount,
                deposit,
                future
        );
        pendingBankTransfers.put(player.getUniqueId(), pending);
        future.whenComplete((result, error) -> {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        settleBankTransfer(pending, result, error, true));
            }
        });
    }

    private void settleBankTransfer(
            PendingBankTransfer pending,
            OperationResult<ClanBankView> result,
            Throwable error,
            boolean notifyPlayer
    ) {
        if (!pendingBankTransfers.remove(pending.player().getUniqueId(), pending)) {
            return;
        }
        try {
            if (pending.deposit()) {
                settleDeposit(pending, result, error, notifyPlayer);
            } else {
                settleWithdrawal(pending, result, error, notifyPlayer);
            }
        } finally {
            releaseBankTransfer(pending.clanId(), pending.player().getUniqueId());
        }
    }

    private void settleDeposit(
            PendingBankTransfer pending,
            OperationResult<ClanBankView> result,
            Throwable error,
            boolean notifyPlayer
    ) {
        if (error != null || result == null || !result.successful()) {
            EconomyTransaction refund = economy.deposit(
                    pending.player(),
                    pending.amount().doubleValue()
            );
            if (!refund.successful()) {
                plugin.getLogger().severe("Economy refund failed: clan="
                        + pending.clanId() + " player=" + pending.player().getUniqueId()
                        + " amount=" + pending.amount());
                if (notifyPlayer && pending.player().isOnline()) {
                    messages.send(pending.player(), "errors.bank-compensation-failed");
                }
                return;
            }
            notifyBankFailure(pending, result, error, notifyPlayer);
            return;
        }
        notifyBankSuccess(pending, result.value(), "bank.deposited", notifyPlayer);
    }

    private void settleWithdrawal(
            PendingBankTransfer pending,
            OperationResult<ClanBankView> result,
            Throwable error,
            boolean notifyPlayer
    ) {
        if (error != null || result == null || !result.successful()) {
            notifyBankFailure(pending, result, error, notifyPlayer);
            return;
        }
        EconomyTransaction payout = economy.deposit(
                pending.player(),
                pending.amount().doubleValue()
        );
        if (!payout.successful()) {
            try {
                clanService.restoreBankWithdrawal(
                        pending.clanId(),
                        pending.amount(),
                        pending.player().getUniqueId(),
                        pending.player().getName()
                ).join();
            } catch (RuntimeException restoreFailure) {
                plugin.getLogger().severe("Clanbank-Rollback failed: clan="
                        + pending.clanId() + " player=" + pending.player().getUniqueId()
                        + " amount=" + pending.amount() + " error="
                        + rootMessage(restoreFailure));
                if (notifyPlayer && pending.player().isOnline()) {
                    messages.send(pending.player(), "errors.bank-compensation-failed");
                }
                return;
            }
            if (notifyPlayer && pending.player().isOnline()) {
                messages.send(pending.player(), "errors.economy-transaction-failed");
                reopenBank(pending.player(), pending.clanId());
            }
            return;
        }
        notifyBankSuccess(pending, result.value(), "bank.withdrawn", notifyPlayer);
    }

    private void notifyBankFailure(
            PendingBankTransfer pending,
            OperationResult<ClanBankView> result,
            Throwable error,
            boolean notifyPlayer
    ) {
        if (error != null) {
            plugin.getLogger().severe("Clanbank-Aktion failed: "
                    + rootMessage(error));
        }
        if (!notifyPlayer || !pending.player().isOnline()) {
            return;
        }
        sendFailure(
                pending.player(),
                result == null ? OperationCode.BANK_DISABLED : result.code()
        );
        reopenBank(pending.player(), pending.clanId());
    }

    private void notifyBankSuccess(
            PendingBankTransfer pending,
            ClanBankView view,
            String messagePath,
            boolean notifyPlayer
    ) {
        if (!notifyPlayer || !pending.player().isOnline()) {
            return;
        }
        messages.send(pending.player(), messagePath, Map.of(
                "amount", formatBankAmount(pending.amount()),
                "balance", formatBankAmount(view.balance()),
                "currency", bankCurrency()
        ));
        reopenBank(pending.player(), pending.clanId());
    }

    private void reopenBank(Player player, UUID clanId) {
        clanService.findCachedClan(clanId).ifPresent(clan -> openBank(player, clan));
    }

    private void releaseBankTransfer(UUID clanId, UUID playerId) {
        pendingBankClans.remove(clanId, playerId);
    }

    private void openBankLogMembers(Player player, Clan clan, int requestedPage) {
        handle(
                clanService.bankLogMembers(player.getUniqueId()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    renderBankLogMembers(
                            player,
                            clan,
                            result.value(),
                            Math.max(0, requestedPage)
                    );
                },
                player
        );
    }

    private void renderBankLogMembers(
            Player player,
            Clan clan,
            List<ClanMember> members,
            int requestedPage
    ) {
        List<Integer> slots = configs.gui().getIntegerList(
                "bank-log-members-menu.member-slots"
        );
        int pageCount = Math.max(1, (members.size() + slots.size() - 1) / slots.size());
        int page = Math.min(requestedPage, pageCount - 1);
        Map<String, String> values = Map.of(
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(pageCount),
                "count", Integer.toString(members.size())
        );
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.BANK_LOG_MEMBERS,
                clan.id(),
                null,
                page,
                null,
                null,
                null
        );
        Inventory inventory = createInventory(holder, "bank-log-members-menu", values);
        fill(inventory);
        int first = page * slots.size();
        for (int index = 0; index < slots.size() && first + index < members.size(); index++) {
            ClanMember member = members.get(first + index);
            Map<String, String> memberValues = Map.of(
                    "player", member.lastKnownName()
            );
            inventory.setItem(
                    slots.get(index),
                    bankLogMemberHead(member, memberValues)
            );
        }
        if (page > 0) {
            set(inventory, "bank-log-members-menu.previous", values);
        }
        set(inventory, "bank-log-members-menu.info", values);
        if (page + 1 < pageCount) {
            set(inventory, "bank-log-members-menu.next", values);
        }
        set(inventory, "bank-log-members-menu.back", values);
        set(inventory, "bank-log-members-menu.close", values);
        mainGui.openMenu(player, inventory);
    }

    private void openBankLogEntries(
            Player player,
            Clan clan,
            UUID actorId,
            String actorName
    ) {
        if (actorId == null) {
            openBankLogMembers(player, clan, 0);
            return;
        }
        handle(
                clanService.bankLogEntries(player.getUniqueId(), actorId),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    renderBankLogEntries(player, clan, actorId, actorName, result.value());
                },
                player
        );
    }

    private void renderBankLogEntries(
            Player player,
            Clan clan,
            UUID actorId,
            String actorName,
            List<AuditLogEntry> entries
    ) {
        String safeName = actorName == null || actorName.isBlank()
                ? actorId.toString()
                : actorName;
        Map<String, String> values = Map.of(
                "player", safeName,
                "count", Integer.toString(entries.size())
        );
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.BANK_LOG_ENTRIES,
                clan.id(),
                actorId,
                0,
                safeName,
                null,
                null
        );
        Inventory inventory = createInventory(holder, "bank-log-entries-menu", values);
        fill(inventory);
        List<Integer> slots = configs.gui().getIntegerList(
                "bank-log-entries-menu.entry-slots"
        );
        for (int index = 0; index < Math.min(slots.size(), entries.size()); index++) {
            AuditLogEntry entry = entries.get(index);
            Map<String, String> entryValues = Map.of(
                    "time", bankLogDateFormatter.format(entry.timestamp()),
                    "action", bankActionName(entry.action()),
                    "player", entry.actorName(),
                    "details", bankDetailsText(entry.details())
            );
            inventory.setItem(
                    slots.get(index),
                    item("bank-log-entries-menu.entry", entryValues, Material.PAPER)
            );
        }
        if (entries.isEmpty()) {
            set(inventory, "bank-log-entries-menu.empty", values);
        }
        set(inventory, "bank-log-entries-menu.info", values);
        set(inventory, "bank-log-entries-menu.back", values);
        set(inventory, "bank-log-entries-menu.close", values);
        mainGui.openMenu(player, inventory);
    }

    private void handleBankLogMembersClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        int slot = event.getRawSlot();
        if (slot == slot("bank-log-members-menu.back")) {
            mainGui.navigateBack(player);
            return;
        }
        if (slot == slot("bank-log-members-menu.close")) {
            player.closeInventory();
            return;
        }
        if (slot == slot("bank-log-members-menu.previous")) {
            clanService.findCachedClan(holder.clanId()).ifPresent(clan ->
                    openBankLogMembers(player, clan, holder.page() - 1));
            return;
        }
        if (slot == slot("bank-log-members-menu.next")) {
            clanService.findCachedClan(holder.clanId()).ifPresent(clan ->
                    openBankLogMembers(player, clan, holder.page() + 1));
            return;
        }
        UUID actorId = readUuid(event.getCurrentItem(), bankLogActorKey);
        if (actorId == null) {
            return;
        }
        String actorName = Optional.ofNullable(Bukkit.getOfflinePlayer(actorId).getName())
                .orElse(actorId.toString());
        clanService.findCachedClan(holder.clanId()).ifPresent(clan ->
                openBankLogEntries(player, clan, actorId, actorName));
    }

    private void handleBankLogEntriesClick(Player player, int slot) {
        if (slot == slot("bank-log-entries-menu.back")) {
            mainGui.navigateBack(player);
        } else if (slot == slot("bank-log-entries-menu.close")) {
            player.closeInventory();
        }
    }

    private void renderVault(Player player, VaultPageView view) {
        UUID previousViewer = openVaultByClan.putIfAbsent(
                view.clanId(),
                player.getUniqueId()
        );
        if (previousViewer != null && !previousViewer.equals(player.getUniqueId())) {
            messages.send(player, "errors.vault-in-use", Map.of(
                    "player",
                    Optional.ofNullable(Bukkit.getOfflinePlayer(previousViewer).getName())
                            .orElse(previousViewer.toString())
            ));
            return;
        }
        Map<String, String> values = Map.of(
                "page", Integer.toString(view.page()),
                "pages", Integer.toString(view.maximumPages()),
                "max_pages", Integer.toString(configs.vault().getInt(
                        "general.absolute-max-pages",
                        7
                )),
                "deposit_state", configs.gui().getString(
                        view.canDeposit() ? "vault-menu.allowed" : "vault-menu.denied",
                        "-"
                ),
                "withdraw_state", configs.gui().getString(
                        view.canWithdraw() ? "vault-menu.allowed" : "vault-menu.denied",
                        "-"
                )
        );
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.VAULT,
                view.clanId(),
                null,
                view.page(),
                null,
                null,
                null
        );
        Inventory inventory = createInventory(holder, "vault-menu", values);
        int storageSlots = storageSlots();
        view.items().forEach((slot, bytes) -> {
            if (slot < 0 || slot >= storageSlots) {
                return;
            }
            try {
                inventory.setItem(slot, ItemStack.deserializeBytes(bytes));
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Vault item could not be loaded: clan="
                        + view.clanId() + " page=" + view.page() + " slot=" + slot);
            }
        });
        ItemStack frame = decoration("common.frame", Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, frame);
        }
        if (view.page() > 1) {
            set(inventory, "vault-menu.previous", values);
        }
        if (view.page() < view.maximumPages()) {
            set(inventory, "vault-menu.next", values);
        }
        set(inventory, "vault-menu.info", values);
        if (view.canViewLog()) {
            set(inventory, "vault-menu.log", values);
        }
        if (view.canManageExtensions()) {
            set(inventory, "vault-menu.extensions", values);
        }
        set(inventory, "vault-menu.back", values);
        set(inventory, "vault-menu.close", values);
        vaultSessions.put(player.getUniqueId(), new VaultSession(view));
        mainGui.openMenu(player, inventory);
    }

    private void openVaultLogMembers(Player player, Clan clan, int requestedPage) {
        handle(
                clanService.vaultLogMembers(player.getUniqueId()),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    renderVaultLogMembers(
                            player,
                            clan,
                            result.value(),
                            requestedPage
                    );
                },
                player
        );
    }

    private void renderVaultLogMembers(
            Player player,
            Clan clan,
            List<ClanMember> members,
            int requestedPage
    ) {
        List<Integer> slots = configs.gui().getIntegerList(
                "vault-log-members-menu.member-slots"
        );
        int pageSize = Math.max(1, slots.size());
        int pages = Math.max(1, (members.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Map<String, String> values = Map.of(
                "clan", clan.name(),
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(pages),
                "count", Integer.toString(members.size())
        );
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.VAULT_LOG_MEMBERS,
                clan.id(),
                null,
                page,
                null,
                null,
                null
        );
        Inventory inventory = createInventory(
                holder,
                "vault-log-members-menu",
                values
        );
        fill(inventory);
        int offset = page * pageSize;
        for (int index = 0; index < slots.size() && offset + index < members.size(); index++) {
            ClanMember member = members.get(offset + index);
            Map<String, String> memberValues = Map.of(
                    "player", member.lastKnownName(),
                    "rank", member.rank().name()
            );
            inventory.setItem(
                    slots.get(index),
                    vaultLogMemberHead(member, memberValues)
            );
        }
        if (page > 0) {
            set(inventory, "vault-log-members-menu.previous", values);
        }
        if (page + 1 < pages) {
            set(inventory, "vault-log-members-menu.next", values);
        }
        set(inventory, "vault-log-members-menu.info", values);
        set(inventory, "vault-log-members-menu.back", values);
        set(inventory, "vault-log-members-menu.close", values);
        mainGui.openMenu(player, inventory);
    }

    private void openVaultLogEntries(
            Player player,
            Clan clan,
            UUID actorId,
            String actorName
    ) {
        if (actorId == null) {
            openVaultLogMembers(player, clan, 0);
            return;
        }
        handle(
                clanService.vaultLogEntries(player.getUniqueId(), actorId, 18),
                result -> {
                    if (!result.successful()) {
                        sendFailure(player, result.code());
                        return;
                    }
                    renderVaultLogEntries(
                            player,
                            clan,
                            actorId,
                            actorName,
                            result.value()
                    );
                },
                player
        );
    }

    private void renderVaultLogEntries(
            Player player,
            Clan clan,
            UUID actorId,
            String actorName,
            List<AuditLogEntry> entries
    ) {
        String safeActorName = actorName == null || actorName.isBlank()
                ? actorId.toString()
                : actorName;
        Map<String, String> values = Map.of(
                "clan", clan.name(),
                "player", safeActorName,
                "count", Integer.toString(entries.size())
        );
        ClanMenuHolder holder = new ClanMenuHolder(
                ClanMenuType.VAULT_LOG_ENTRIES,
                clan.id(),
                actorId,
                0,
                safeActorName,
                null,
                null
        );
        Inventory inventory = createInventory(
                holder,
                "vault-log-entries-menu",
                values
        );
        fill(inventory);
        List<Integer> slots = configs.gui().getIntegerList(
                "vault-log-entries-menu.entry-slots"
        );
        for (int index = 0; index < Math.min(slots.size(), entries.size()); index++) {
            AuditLogEntry entry = entries.get(index);
            Map<String, String> entryValues = Map.of(
                    "time", vaultLogDateFormatter.format(entry.timestamp()),
                    "action", vaultActionName(entry.action()),
                    "player", entry.actorName(),
                    "details", vaultDetailsText(entry.details())
            );
            inventory.setItem(
                    slots.get(index),
                    item("vault-log-entries-menu.entry", entryValues, Material.PAPER)
            );
        }
        if (entries.isEmpty()) {
            set(inventory, "vault-log-entries-menu.empty", values);
        }
        set(inventory, "vault-log-entries-menu.info", values);
        set(inventory, "vault-log-entries-menu.back", values);
        set(inventory, "vault-log-entries-menu.close", values);
        mainGui.openMenu(player, inventory);
    }

    private void handleVaultLogMembersClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == slot("vault-log-members-menu.back")) {
            mainGui.navigateBack(player);
            return;
        }
        if (slot == slot("vault-log-members-menu.close")) {
            player.closeInventory();
            return;
        }
        if (slot == slot("vault-log-members-menu.previous")) {
            reopenVaultLogMembersPage(player, holder, holder.page() - 1);
            return;
        }
        if (slot == slot("vault-log-members-menu.next")) {
            reopenVaultLogMembersPage(player, holder, holder.page() + 1);
            return;
        }
        UUID actorId = readUuid(event.getCurrentItem(), vaultLogActorKey);
        if (actorId == null) {
            return;
        }
        clanService.findCachedClan(holder.clanId()).ifPresent(clan -> {
            String actorName = clan.members().stream()
                    .filter(member -> member.playerId().equals(actorId))
                    .map(ClanMember::lastKnownName)
                    .findFirst()
                    .orElse(actorId.toString());
            openVaultLogEntries(player, clan, actorId, actorName);
        });
    }

    private void reopenVaultLogMembersPage(
            Player player,
            ClanMenuHolder holder,
            int page
    ) {
        mainGui.replaceNext(
                player,
                new ClanMenuState(
                        ClanMenuType.VAULT_LOG_MEMBERS,
                        holder.clanId(),
                        null,
                        page,
                        null,
                        null,
                        null
                )
        );
        clanService.findCachedClan(holder.clanId())
                .ifPresent(clan -> openVaultLogMembers(player, clan, page));
    }

    private void handleVaultLogEntriesClick(Player player, int slot) {
        if (slot == slot("vault-log-entries-menu.back")) {
            mainGui.navigateBack(player);
        } else if (slot == slot("vault-log-entries-menu.close")) {
            player.closeInventory();
        }
    }

    private void handleVaultClick(
            Player player,
            ClanMenuHolder holder,
            InventoryClickEvent event
    ) {
        VaultSession session = vaultSessions.get(player.getUniqueId());
        if (session == null || !session.view().clanId().equals(holder.clanId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (shuttingDown || pendingVaultWrites.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == slot("vault-menu.back")) {
            event.setCancelled(true);
            mainGui.navigateBack(player);
            return;
        }
        if (rawSlot == slot("vault-menu.close")) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (rawSlot == slot("vault-menu.log")) {
            event.setCancelled(true);
            if (session.view().canViewLog()) {
                clanService.findCachedClan(holder.clanId())
                        .ifPresent(clan -> openVaultLogMembers(player, clan, 0));
            }
            return;
        }
        if (rawSlot == slot("vault-menu.extensions")) {
            event.setCancelled(true);
            if (session.view().canManageExtensions()) {
                clanService.findCachedClan(holder.clanId())
                        .ifPresent(clan -> openBattlepass(player, clan, 0));
            }
            return;
        }
        if (rawSlot == slot("vault-menu.previous")) {
            event.setCancelled(true);
            mainGui.replaceNext(
                    player,
                    new ClanMenuState(
                            ClanMenuType.VAULT,
                            holder.clanId(),
                            null,
                            holder.page() - 1,
                            null,
                            null,
                            null
                    )
            );
            clanService.findCachedClan(holder.clanId())
                    .ifPresent(clan -> openVault(player, clan, holder.page() - 1));
            return;
        }
        if (rawSlot == slot("vault-menu.next")) {
            event.setCancelled(true);
            mainGui.replaceNext(
                    player,
                    new ClanMenuState(
                            ClanMenuType.VAULT,
                            holder.clanId(),
                            null,
                            holder.page() + 1,
                            null,
                            null,
                            null
                    )
            );
            clanService.findCachedClan(holder.clanId())
                    .ifPresent(clan -> openVault(player, clan, holder.page() + 1));
            return;
        }
        if (rawSlot >= storageSlots()
                && rawSlot < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            event.setCancelled(isUnsafeVaultClick(event));
            return;
        }
        event.setCancelled(true);
        if ((event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)
                || isUnsafeVaultClick(event)) {
            return;
        }
        Inventory inventory = event.getView().getTopInventory();
        VaultTransferPlan plan = VaultTransferPlan.create(
                event.getCurrentItem(),
                event.getCursor(),
                event.getClick()
        );
        if (plan == null || !allowed(session.view(), plan.mutation())) {
            return;
        }
        persistVaultPlan(
                player,
                holder,
                inventory,
                rawSlot,
                plan
        );
    }

    private void persistVaultPlan(
            Player player,
            ClanMenuHolder holder,
            Inventory inventory,
            int slot,
            VaultTransferPlan plan
    ) {
        byte[] bytes = serialize(plan.afterSlot());
        String details = vaultDetails(
                plan.mutation(),
                plan.beforeSlot(),
                plan.afterSlot()
        );
        CompletableFuture<OperationResult<Void>> future = clanService.saveVaultSlot(
                player.getUniqueId(),
                player.getName(),
                holder.page(),
                slot,
                bytes,
                plan.mutation(),
                details
        );
        PendingVaultWrite pending = new PendingVaultWrite(
                player,
                holder,
                inventory,
                slot,
                plan,
                future
        );
        pendingVaultWrites.put(player.getUniqueId(), pending);
        future.whenComplete((result, error) -> {
            if (shuttingDown) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error == null && result.successful() && pending.unchanged()) {
                    pending.apply();
                    finishVaultWrite(player, holder);
                    return;
                }
                if (error == null && result.successful()) {
                    rollbackVaultWrite(player, holder, slot, plan);
                    return;
                }
                finishVaultWrite(player, holder);
                if (player.isOnline()) {
                    if (error == null) {
                        sendFailure(player, result.code());
                    } else {
                        messages.send(player, "errors.internal");
                    }
                }
            });
        });
    }

    private void rollbackVaultWrite(
            Player player,
            ClanMenuHolder holder,
            int slot,
            VaultTransferPlan plan
    ) {
        clanService.restoreVaultSlot(
                holder.clanId(),
                holder.page(),
                slot,
                serialize(plan.beforeSlot()),
                player.getUniqueId(),
                player.getName()
        ).whenComplete((ignored, error) -> {
            if (shuttingDown) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().severe(
                        "Vault-Rollback failed: clan=" + holder.clanId()
                                + " page=" + holder.page() + " slot=" + slot
                );
                Bukkit.getScheduler().runTaskLater(
                        plugin,
                        () -> rollbackVaultWrite(player, holder, slot, plan),
                        20L
                );
                return;
            }
            finishVaultWrite(player, holder);
            });
        });
    }

    private void finishVaultWrite(Player player, ClanMenuHolder holder) {
        pendingVaultWrites.remove(player.getUniqueId());
        if (!player.isOnline()
                || !(player.getOpenInventory().getTopInventory().getHolder()
                instanceof ClanMenuHolder openHolder)
                || openHolder.type() != ClanMenuType.VAULT
                || !holder.clanId().equals(openHolder.clanId())) {
            vaultSessions.remove(player.getUniqueId());
            openVaultByClan.remove(holder.clanId(), player.getUniqueId());
        }
    }

    private static String vaultDetails(
            VaultMutationType mutation,
            ItemStack before,
            ItemStack after
    ) {
        int beforeAmount = usableAmount(before);
        int afterAmount = usableAmount(after);
        return switch (mutation) {
            case DEPOSIT -> "item=" + materialName(after)
                    + " amount=" + Math.max(0, afterAmount - beforeAmount);
            case WITHDRAW -> "item=" + materialName(before)
                    + " amount=" + Math.max(0, beforeAmount - afterAmount);
            case REPLACE -> "before=" + materialName(before) + "x" + beforeAmount
                    + " after=" + materialName(after) + "x" + afterAmount;
        };
    }

    private static int usableAmount(ItemStack item) {
        return item == null || item.getType().isAir() ? 0 : item.getAmount();
    }

    private static String materialName(ItemStack item) {
        return item == null || item.getType().isAir()
                ? "AIR"
                : item.getType().name();
    }

    private static boolean allowed(VaultPageView view, VaultMutationType mutation) {
        return switch (mutation) {
            case DEPOSIT -> view.canDeposit();
            case WITHDRAW -> view.canWithdraw();
            case REPLACE -> view.canDeposit() && view.canWithdraw();
        };
    }

    private static boolean isUnsafeVaultClick(InventoryClickEvent event) {
        return event.isShiftClick()
                || event.getClick() == ClickType.DOUBLE_CLICK
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD
                || event.getAction() == InventoryAction.HOTBAR_SWAP;
    }

    private int storageSlots() {
        return Math.min(
                configs.vault().getInt("general.storage-slots-per-page", 45),
                configs.gui().getInt("vault-menu.storage-slots", 45)
        );
    }

    private static byte[] serialize(ItemStack item) {
        return item == null || item.getType().isAir()
                ? null
                : item.serializeAsBytes();
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return Objects.equals(cloneOrNull(left), cloneOrNull(right));
    }

    private Inventory createInventory(
            ClanMenuHolder holder,
            String path,
            Map<String, String> placeholders
    ) {
        Inventory inventory = Bukkit.createInventory(
                holder,
                configs.gui().getInt(path + ".size", 54),
                messages.renderConfig("gui", path + ".title", placeholders)
        );
        holder.inventory(inventory);
        return inventory;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = decoration("common.filler", Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        ItemStack frame = decoration("common.frame", Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, frame);
        }
        for (int slot = inventory.getSize() - 9; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, frame);
        }
    }

    private void fillBattlepass(Inventory inventory) {
        fill(inventory);
        ItemStack filler = decoration(
                "battlepass-menu.filler",
                Material.GRAY_STAINED_GLASS_PANE
        );
        for (int slot = 9; slot < Math.min(45, inventory.getSize()); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack decoration(String path, Material fallback) {
        return decorationCache.computeIfAbsent(path, ignored -> {
            ItemStack item = item(path, Map.of(), fallback);
            ItemMeta meta = item.getItemMeta();
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
            return item;
        }).clone();
    }

    private void set(Inventory inventory, String path, Map<String, String> values) {
        inventory.setItem(slot(path), item(path, values, Material.BARRIER));
    }

    private int slot(String path) {
        return slotCache.computeIfAbsent(
                path,
                ignored -> configs.gui().getInt(path + ".slot", -1)
        );
    }

    private ItemStack item(
            String path,
            Map<String, String> placeholders,
            Material fallback
    ) {
        ItemStack item = new ItemStack(configuredMaterial(path, fallback));
        ItemMeta meta = item.getItemMeta();
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
        item.setItemMeta(meta);
        return item;
    }

    private Component rewardLine(BattlepassReward reward) {
        return messages.renderMenu(
                "<gray>• " + rewardDisplayName(reward.type())
                        + "<gray>: <white>+" + reward.amount(),
                Map.of()
        );
    }

    private String rewardDisplayName(BattlepassRewardType type) {
        return configs.battlepass().getString(
                "rewards.supported-types." + type.configKey() + ".display-name",
                type.name()
        );
    }

    private Material rewardMaterial(BattlepassRewardType type) {
        return rewardMaterialCache.computeIfAbsent(type, ignored -> {
            Material material = Material.matchMaterial(configs.battlepass().getString(
                    "rewards.supported-types." + type.configKey() + ".material",
                    "PAPER"
            ));
            return material == null ? Material.PAPER : material;
        });
    }

    private Material configuredMaterial(String path, Material fallback) {
        return materialCache.computeIfAbsent(path, ignored -> {
            Material configured = Material.matchMaterial(
                    configs.gui().getString(path + ".material", fallback.name())
            );
            return configured == null ? fallback : configured;
        });
    }

    private boolean rewardTypeEnabled(BattlepassRewardType type) {
        return configs.battlepass().getBoolean(
                "rewards.supported-types." + type.configKey() + ".enabled",
                true
        );
    }

    private static Map<String, String> progressValues(
            BattlepassProgress progress,
            BigDecimal requiredXp
    ) {
        BigDecimal percent = requiredXp.signum() == 0
                ? BigDecimal.ZERO
                : progress.currentXp()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(requiredXp, 1, RoundingMode.HALF_UP);
        return Map.of(
                "level", Integer.toString(progress.level()),
                "xp", decimal(progress.currentXp()),
                "required_xp", decimal(requiredXp),
                "progress_percent", decimal(percent)
        );
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private Map<String, String> bankValues(ClanBankView view) {
        return Map.of(
                "balance", formatBankAmount(view.balance()),
                "currency", bankCurrency(),
                "economy_provider", economy.providerName(),
                "deposit_state", bankPermissionState(view.canDeposit()),
                "withdraw_state", bankPermissionState(view.canWithdraw()),
                "log_state", bankPermissionState(view.canViewLog())
        );
    }

    private String bankPermissionState(boolean allowed) {
        return configs.gui().getString(
                allowed ? "bank-menu.allowed" : "bank-menu.denied",
                allowed ? "Erlaubt" : "Gesperrt"
        );
    }

    private String bankCurrency() {
        return configs.economy().getString("currency.display-name", "Coins");
    }

    private String formatBankAmount(BigDecimal amount) {
        int scale = configs.economy().getInt("clan-bank.decimal-scale", 2);
        RoundingMode mode = RoundingMode.valueOf(configs.economy().getString(
                "clan-bank.rounding-mode",
                "HALF_UP"
        ));
        String number = amount.setScale(scale, mode)
                .stripTrailingZeros()
                .toPlainString()
                .replace('.', ',');
        return number + " " + bankCurrency();
    }

    private BigDecimal configuredBankDecimal(String path) {
        Object value = configs.economy().get(path);
        return new BigDecimal(String.valueOf(value));
    }

    private static boolean isBankMenu(ClanMenuType type) {
        return type == ClanMenuType.BANK
                || type == ClanMenuType.BANK_DEPOSIT
                || type == ClanMenuType.BANK_WITHDRAW
                || type == ClanMenuType.BANK_LOG_MEMBERS
                || type == ClanMenuType.BANK_LOG_ENTRIES;
    }

    private static Integer readInteger(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.INTEGER);
    }

    private static String readString(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING);
    }

    private static UUID readUuid(ItemStack item, NamespacedKey key) {
        String value = readString(item, key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ItemStack vaultLogMemberHead(
            ClanMember member,
            Map<String, String> placeholders
    ) {
        ItemStack item = item(
                "vault-log-members-menu.member",
                placeholders,
                Material.PLAYER_HEAD
        );
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(member.playerId()));
        meta.getPersistentDataContainer().set(
                vaultLogActorKey,
                PersistentDataType.STRING,
                member.playerId().toString()
        );
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack bankLogMemberHead(
            ClanMember member,
            Map<String, String> placeholders
    ) {
        ItemStack item = item(
                "bank-log-members-menu.member",
                placeholders,
                Material.PLAYER_HEAD
        );
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(member.playerId()));
        meta.getPersistentDataContainer().set(
                bankLogActorKey,
                PersistentDataType.STRING,
                member.playerId().toString()
        );
        item.setItemMeta(meta);
        return item;
    }

    private static String bankActionName(String action) {
        return switch (action) {
            case "BANK_DEPOSIT" -> "Eingezahlt";
            case "BANK_WITHDRAW" -> "Ausgezahlt";
            case "BANK_WITHDRAW_ROLLBACK" -> "Withdrawal rolled back";
            default -> action;
        };
    }

    private String bankDetailsText(String details) {
        return details
                .replace("amount=", "Betrag ")
                .replace(" balance=", " " + bankCurrency() + " · Guthaben ")
                + " " + bankCurrency();
    }

    private static String vaultActionName(String action) {
        return switch (action) {
            case "VAULT_DEPOSIT" -> "Eingelagert";
            case "VAULT_WITHDRAW" -> "Entnommen";
            case "VAULT_REPLACE" -> "Ausgetauscht";
            case "VAULT_ROLLBACK" -> "Rolled back";
            default -> action;
        };
    }

    private static String vaultDetailsText(String details) {
        return details
                .replace("page=", "Seite ")
                .replace(" slot=", " · Slot ")
                .replace(" item=", " · Item ")
                .replace(" amount=", " ×")
                .replace(" before=", " · Vorher ")
                .replace(" after=", " · Danach ");
    }

    private void sendFailure(Player player, OperationCode code) {
        String path = switch (code) {
            case NOT_IN_CLAN -> "errors.not-in-clan";
            case CLAN_NOT_FOUND -> "errors.clan-not-found";
            case CLAN_RIGHT_MISSING -> "errors.clan-right-missing";
            case OWNER_ONLY -> "errors.owner-only";
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
            default -> "errors.internal";
        };
        messages.send(player, path, Map.of("clan", "-", "player", "-"));
    }

    private <T> void handle(
            CompletionStage<T> stage,
            Consumer<T> success,
            Player player
    ) {
        stage.whenComplete((value, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline()) {
                return;
            }
            if (error != null) {
                plugin.getLogger().severe("Feature-GUI-Aktion failed: "
                        + rootMessage(error));
                messages.send(player, "errors.internal");
                return;
            }
            success.accept(value);
        }));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private record VaultSession(VaultPageView view) {
    }

    private record PendingBankInput(UUID clanId, boolean deposit) {
    }

    private record PendingBankTransfer(
            Player player,
            UUID clanId,
            BigDecimal amount,
            boolean deposit,
            CompletableFuture<OperationResult<ClanBankView>> future
    ) {
    }

    private record PendingVaultWrite(
            Player player,
            ClanMenuHolder holder,
            Inventory inventory,
            int slot,
            VaultTransferPlan plan,
            CompletableFuture<OperationResult<Void>> future
    ) {

        private boolean unchanged() {
            return player.isOnline()
                    && player.getOpenInventory().getTopInventory() == inventory
                    && sameItem(inventory.getItem(slot), plan.beforeSlot())
                    && sameItem(player.getItemOnCursor(), plan.beforeCursor());
        }

        private void apply() {
            inventory.setItem(slot, cloneOrNull(plan.afterSlot()));
            player.setItemOnCursor(cloneOrNull(plan.afterCursor()));
        }
    }
}
