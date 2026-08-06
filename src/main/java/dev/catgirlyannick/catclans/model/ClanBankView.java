package dev.catgirlyannick.catclans.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ClanBankView(
        UUID clanId,
        BigDecimal balance,
        boolean canDeposit,
        boolean canWithdraw,
        boolean canViewLog,
        boolean canManagePermissions
) {

    public ClanBankView {
        Objects.requireNonNull(clanId, "clanId");
        Objects.requireNonNull(balance, "balance");
        if (balance.signum() < 0) {
            throw new IllegalArgumentException("Clan bank balance must not be negative");
        }
    }
}
