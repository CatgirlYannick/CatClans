package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.model.BattlepassProgress;

import java.math.BigDecimal;

public record XpAwardResult(
        BattlepassProgress progress,
        BigDecimal awardedXp,
        int levelsGained,
        int streakDays
) {
}
