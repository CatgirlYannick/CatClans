package dev.catgirlyannick.catclans.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record DailyLoginState(UUID playerId, LocalDate lastLoginDate, int streakDays) {

    public DailyLoginState {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastLoginDate, "lastLoginDate");
        if (streakDays < 1) {
            throw new IllegalArgumentException("Login streak must be at least 1");
        }
    }
}
