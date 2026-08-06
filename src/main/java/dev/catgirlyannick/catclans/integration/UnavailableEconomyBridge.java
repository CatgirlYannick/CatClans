package dev.catgirlyannick.catclans.integration;

import org.bukkit.OfflinePlayer;

import java.text.DecimalFormat;

final class UnavailableEconomyBridge implements EconomyBridge {

    static final UnavailableEconomyBridge INSTANCE = new UnavailableEconomyBridge();
    private static final DecimalFormat FALLBACK_FORMAT = new DecimalFormat("#,##0.00");

    private UnavailableEconomyBridge() {
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String providerName() {
        return "none";
    }

    @Override
    public String providerImplementationName() {
        return "none";
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public EconomyTransaction withdraw(OfflinePlayer player, double amount) {
        return EconomyTransaction.failure("No economy provider is available");
    }

    @Override
    public EconomyTransaction deposit(OfflinePlayer player, double amount) {
        return EconomyTransaction.failure("No economy provider is available");
    }

    @Override
    public String format(double amount) {
        synchronized (FALLBACK_FORMAT) {
            return FALLBACK_FORMAT.format(amount);
        }
    }
}
