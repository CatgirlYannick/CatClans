package dev.catgirlyannick.catclans.integration;

import org.bukkit.OfflinePlayer;

public interface EconomyBridge {

    boolean available();

    String providerName();

    String providerImplementationName();

    boolean has(OfflinePlayer player, double amount);

    EconomyTransaction withdraw(OfflinePlayer player, double amount);

    EconomyTransaction deposit(OfflinePlayer player, double amount);

    String format(double amount);

    static EconomyBridge unavailable() {
        return UnavailableEconomyBridge.INSTANCE;
    }
}
