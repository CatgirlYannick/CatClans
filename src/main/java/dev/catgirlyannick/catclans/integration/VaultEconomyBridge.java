package dev.catgirlyannick.catclans.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

final class VaultEconomyBridge implements EconomyBridge {

    private final Economy economy;

    private VaultEconomyBridge(Economy economy) {
        this.economy = economy;
    }

    static Optional<EconomyBridge> create(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> registration =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            return Optional.empty();
        }
        return Optional.of(new VaultEconomyBridge(registration.getProvider()));
    }

    @Override
    public boolean available() {
        return economy.isEnabled();
    }

    @Override
    public String providerName() {
        return economy.getName();
    }

    @Override
    public String providerImplementationName() {
        return economy.getClass().getName();
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return amount <= 0.0 || economy.has(player, amount);
    }

    @Override
    public EconomyTransaction withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0.0) {
            return EconomyTransaction.success();
        }
        return convert(economy.withdrawPlayer(player, amount));
    }

    @Override
    public EconomyTransaction deposit(OfflinePlayer player, double amount) {
        if (amount <= 0.0) {
            return EconomyTransaction.success();
        }
        return convert(economy.depositPlayer(player, amount));
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }

    private static EconomyTransaction convert(EconomyResponse response) {
        return response.transactionSuccess()
                ? EconomyTransaction.success()
                : EconomyTransaction.failure(response.errorMessage);
    }
}
