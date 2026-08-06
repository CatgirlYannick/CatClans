package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.model.RankingKillResult;

public record PvpKillProcessingResult(
        OperationResult<XpAwardResult> battlepass,
        RankingKillResult ranking
) {
}
