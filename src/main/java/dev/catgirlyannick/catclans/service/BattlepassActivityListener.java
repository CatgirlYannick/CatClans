package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.CatClansPlugin;
import dev.catgirlyannick.catclans.message.MessageService;
import dev.catgirlyannick.catclans.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BattlepassActivityListener implements Listener {

    private final CatClansPlugin plugin;
    private final ClanService clanService;
    private final MessageService messages;
    private final boolean dailyLoginEnabled;
    private final boolean pvpEnabled;
    private final boolean onlineEnabled;
    private final int onlineIntervalMinutes;
    private BukkitTask onlineTask;

    public BattlepassActivityListener(
            CatClansPlugin plugin,
            ClanService clanService,
            MessageService messages,
            boolean dailyLoginEnabled,
            boolean pvpEnabled,
            boolean onlineEnabled,
            int onlineIntervalMinutes
    ) {
        this.plugin = plugin;
        this.clanService = clanService;
        this.messages = messages;
        this.dailyLoginEnabled = dailyLoginEnabled;
        this.pvpEnabled = pvpEnabled;
        this.onlineEnabled = onlineEnabled;
        this.onlineIntervalMinutes = onlineIntervalMinutes;
    }

    public void start() {
        if (!onlineEnabled) {
            return;
        }
        long intervalTicks = onlineIntervalMinutes * 60L * 20L;
        onlineTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::awardOnlineActivity,
                intervalTicks,
                intervalTicks
        );
    }

    public void stop() {
        if (onlineTask != null) {
            onlineTask.cancel();
            onlineTask = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!dailyLoginEnabled) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                clanService.registerDailyLogin(player.getUniqueId(), player.getName())
                        .whenComplete((result, error) -> {
                            if (error != null || !result.successful()
                                    || result.value().awardedXp().signum() <= 0) {
                                return;
                            }
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) {
                                    return;
                                }
                                messages.send(player, "battlepass.daily-login-xp", Map.of(
                                        "xp", decimal(result.value().awardedXp()),
                                        "streak", Integer.toString(result.value().streakDays()),
                                        "level", Integer.toString(result.value().progress().level())
                                ));
                                notifyLevelUp(player, result.value());
                            });
                        }), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!pvpEnabled && !clanService.rankingsEnabled()) {
            return;
        }
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        clanService.processPvpKill(
                killer.getUniqueId(),
                killer.getName(),
                victim.getUniqueId(),
                Instant.now(),
                pvpEnabled
        ).whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().severe("PvP processing failed: "
                        + rootMessage(error));
                return;
            }
            OperationResult<XpAwardResult> battlepass = result.battlepass();
            if (!battlepass.successful()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!killer.isOnline()) {
                    return;
                }
                messages.send(killer, "battlepass.pvp-kill-xp", Map.of(
                        "xp", decimal(battlepass.value().awardedXp()),
                        "level", Integer.toString(battlepass.value().progress().level())
                ));
                notifyLevelUp(killer, battlepass.value());
            });
        });
    }

    private void awardOnlineActivity() {
        Map<UUID, Integer> onlineByClan = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clanService.findCachedClanForPlayer(player.getUniqueId())
                    .map(Clan::id)
                    .ifPresent(clanId -> onlineByClan.merge(clanId, 1, Integer::sum));
        }
        onlineByClan.forEach((clanId, onlineMembers) ->
                clanService.awardOnlineXp(clanId, onlineMembers)
                        .whenComplete((result, error) -> {
                            if (error != null || !result.successful()
                                    || result.value().levelsGained() <= 0) {
                                return;
                            }
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    Bukkit.getOnlinePlayers().stream()
                                            .filter(player -> clanService
                                                    .findCachedClanForPlayer(player.getUniqueId())
                                                    .map(Clan::id)
                                                    .filter(clanId::equals)
                                                    .isPresent())
                                            .forEach(player -> notifyLevelUp(
                                                    player,
                                                    result.value()
                                            )));
                        }));
    }

    private void notifyLevelUp(Player player, XpAwardResult result) {
        if (result.levelsGained() <= 0) {
            return;
        }
        messages.send(player, "battlepass.level-up", Map.of(
                "level", Integer.toString(result.progress().level()),
                "levels", Integer.toString(result.levelsGained())
        ));
    }

    private static String decimal(java.math.BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}
