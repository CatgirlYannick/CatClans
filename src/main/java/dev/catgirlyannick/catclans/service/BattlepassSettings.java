package dev.catgirlyannick.catclans.service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import dev.catgirlyannick.catclans.model.BattlepassRewardType;

public record BattlepassSettings(
        boolean enabled,
        BattlepassCurve curve,
        LoginStreakCalculator loginStreak,
        ZoneId loginZone,
        BigDecimal onlineXp,
        int onlineIntervalMinutes,
        BigDecimal pvpKillXp,
        int pvpCooldownMinutes,
        boolean allowSameClanKills,
        int absoluteMaxMembers,
        int absoluteMaxRoles,
        int absoluteMaxVaultPages,
        int absoluteMaxBonusHomeSlots,
        Set<BattlepassRewardType> enabledRewardTypes
) {

    public BattlepassSettings {
        enabledRewardTypes = Set.copyOf(enabledRewardTypes);
    }

    public BattlepassSettings(
            boolean enabled,
            BattlepassCurve curve,
            LoginStreakCalculator loginStreak,
            ZoneId loginZone,
            BigDecimal onlineXp,
            int onlineIntervalMinutes,
            BigDecimal pvpKillXp,
            int pvpCooldownMinutes,
            boolean allowSameClanKills,
            int absoluteMaxMembers,
            int absoluteMaxRoles,
            int absoluteMaxVaultPages,
            int absoluteMaxBonusHomeSlots
    ) {
        this(
                enabled,
                curve,
                loginStreak,
                loginZone,
                onlineXp,
                onlineIntervalMinutes,
                pvpKillXp,
                pvpCooldownMinutes,
                allowSameClanKills,
                absoluteMaxMembers,
                absoluteMaxRoles,
                absoluteMaxVaultPages,
                absoluteMaxBonusHomeSlots,
                EnumSet.allOf(BattlepassRewardType.class)
        );
    }
}
