package dev.catgirlyannick.catclans.integration;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationRegistryTest {

    @Test
    void recognizesOfficialEzEconomyVaultProvider() {
        EconomyBridge provider = provider(
                "EzEconomy",
                "com.skyblockexp.ezeconomy.core.VaultEconomyImpl"
        );

        assertTrue(IntegrationRegistry.isEzEconomyProvider(provider));
    }

    @Test
    void rejectsUnrelatedVaultProvider() {
        EconomyBridge provider = provider(
                "OtherEconomy",
                "example.economy.OtherVaultProvider"
        );

        assertFalse(IntegrationRegistry.isEzEconomyProvider(provider));
    }

    private static EconomyBridge provider(String name, String implementation) {
        return new EconomyBridge() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String providerName() {
                return name;
            }

            @Override
            public String providerImplementationName() {
                return implementation;
            }

            @Override
            public boolean has(OfflinePlayer player, double amount) {
                return true;
            }

            @Override
            public EconomyTransaction withdraw(OfflinePlayer player, double amount) {
                return EconomyTransaction.success();
            }

            @Override
            public EconomyTransaction deposit(OfflinePlayer player, double amount) {
                return EconomyTransaction.success();
            }

            @Override
            public String format(double amount) {
                return Double.toString(amount);
            }
        };
    }
}
