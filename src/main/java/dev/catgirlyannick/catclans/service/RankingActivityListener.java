package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.CatClansPlugin;
import dev.catgirlyannick.catclans.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class RankingActivityListener implements Listener {

    private final CatClansPlugin plugin;
    private final ClanService clanService;
    private final int maintenanceIntervalSeconds;
    private BukkitTask maintenanceTask;

    public RankingActivityListener(
            CatClansPlugin plugin,
            ClanService clanService,
            int maintenanceIntervalSeconds
    ) {
        this.plugin = plugin;
        this.clanService = clanService;
        this.maintenanceIntervalSeconds = maintenanceIntervalSeconds;
    }

    public void start() {
        long intervalTicks = maintenanceIntervalSeconds * 20L;
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::runMaintenance,
                20L,
                intervalTicks
        );
    }

    public void stop() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> clanService.registerRankingActivity(playerId)
                        .exceptionally(error -> {
                            logFailure("activity point", error);
                            return false;
                        }),
                1L
        );
    }

    private void runMaintenance() {
        Set<UUID> onlineClanIds = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clanService.findCachedClanForPlayer(player.getUniqueId())
                    .map(Clan::id)
                    .ifPresent(onlineClanIds::add);
        }
        clanService.performRankingMaintenance(onlineClanIds)
                .exceptionally(error -> {
                    logFailure("Ranglisten-Wartung", error);
                    return null;
                });
    }

    private void logFailure(String action, Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        plugin.getLogger().severe(action + " failed: "
                + current.getClass().getSimpleName() + ": " + current.getMessage());
    }
}
