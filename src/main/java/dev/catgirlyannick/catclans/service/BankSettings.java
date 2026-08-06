package dev.catgirlyannick.catclans.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public record BankSettings(
        boolean enabled,
        String currencyName,
        BigDecimal minimumDeposit,
        BigDecimal minimumWithdrawal,
        int decimalScale,
        RoundingMode roundingMode,
        List<BigDecimal> quickAmounts,
        boolean logDeposits,
        boolean logWithdrawals,
        int maximumLogEntries
) {

    public BankSettings {
        currencyName = Objects.requireNonNull(currencyName, "currencyName").trim();
        minimumDeposit = Objects.requireNonNull(minimumDeposit, "minimumDeposit");
        minimumWithdrawal = Objects.requireNonNull(minimumWithdrawal, "minimumWithdrawal");
        roundingMode = Objects.requireNonNull(roundingMode, "roundingMode");
        quickAmounts = List.copyOf(quickAmounts);
        if (currencyName.isEmpty()) {
            throw new IllegalArgumentException("Currency name must not be empty");
        }
        if (minimumDeposit.signum() <= 0 || minimumWithdrawal.signum() <= 0) {
            throw new IllegalArgumentException("Minimum bank amounts must be positive");
        }
        if (decimalScale < 0 || decimalScale > 8) {
            throw new IllegalArgumentException("Bank decimal scale must be between 0 and 8");
        }
        if (quickAmounts.isEmpty() || quickAmounts.size() > 7
                || quickAmounts.stream().anyMatch(amount -> amount.signum() <= 0)) {
            throw new IllegalArgumentException("Between 1 and 7 positive quick amounts are required");
        }
        if (maximumLogEntries < 1 || maximumLogEntries > 18) {
            throw new IllegalArgumentException("Bank log entries must be between 1 and 18");
        }
    }

    public static BankSettings disabled() {
        return new BankSettings(
                false,
                "Coins",
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                2,
                RoundingMode.HALF_UP,
                List.of(new BigDecimal("100")),
                true,
                true,
                18
        );
    }

    public BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(decimalScale, roundingMode).stripTrailingZeros();
    }
}
