package dev.catgirlyannick.catclans.service;

public record VaultSettings(
        boolean enabled,
        int slotsPerPage,
        boolean logDeposits,
        boolean logWithdrawals,
        boolean logReplacements,
        int maxSerializedItemBytes
) {

    public VaultSettings(boolean enabled, int slotsPerPage) {
        this(enabled, slotsPerPage, true, true, true, 1_048_576);
    }

    public VaultSettings(
            boolean enabled,
            int slotsPerPage,
            boolean logDeposits,
            boolean logWithdrawals,
            boolean logReplacements
    ) {
        this(
                enabled,
                slotsPerPage,
                logDeposits,
                logWithdrawals,
                logReplacements,
                1_048_576
        );
    }

    public VaultSettings {
        if (slotsPerPage < 1 || slotsPerPage > 45) {
            throw new IllegalArgumentException("Vault slots per page must be between 1 and 45");
        }
        if (maxSerializedItemBytes < 1024 || maxSerializedItemBytes > 8_388_608) {
            throw new IllegalArgumentException(
                    "Vault item bytes must be between 1024 and 8388608"
            );
        }
    }

    public boolean logs(VaultMutationType mutation) {
        return switch (mutation) {
            case DEPOSIT -> logDeposits;
            case WITHDRAW -> logWithdrawals;
            case REPLACE -> logReplacements;
        };
    }
}
