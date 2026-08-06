package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.ClanRankingStats;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankId;
import dev.catgirlyannick.catclans.model.RankingCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingCalculatorTest {

    @Test
    void weightsEachLostWarAsMinusTwoPointFive() {
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Clan clan = new Clan(
                clanId,
                "Alpha",
                "alpha",
                "A",
                "a",
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                Instant.EPOCH,
                List.of(new ClanMember(
                        ownerId,
                        "Owner",
                        RankId.OWNER,
                        Instant.EPOCH
                ))
        );
        ClanRankingStats stats = new ClanRankingStats(
                clanId,
                0,
                0,
                1,
                0,
                LocalDate.of(2026, 7, 31),
                BigDecimal.ZERO
        );
        RankingSettings settings = new RankingSettings(
                true,
                ZoneId.of("Europe/Berlin"),
                Duration.ofMinutes(1),
                Duration.ofMinutes(15),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.valueOf(10_000),
                BigDecimal.TEN,
                BigDecimal.valueOf(-5),
                new BigDecimal("0.5"),
                BigDecimal.ONE,
                false,
                false
        );

        Map<RankingCategory, BigDecimal> points =
                RankingCalculator.categoryPoints(clan, stats, settings);

        assertEquals(
                0,
                new BigDecimal("-2.5").compareTo(points.get(RankingCategory.WARS_LOST))
        );
        assertEquals(
                0,
                new BigDecimal("-1.5").compareTo(RankingCalculator.total(points))
        );
    }

    @Test
    void breaksEqualPointsAlphabeticallyByPlainClanTag() {
        Clan arsch = clan("Arsch");
        Clan baum = clan("Baum");

        assertEquals(
                -1,
                Integer.signum(RankingCalculator.compare(
                        arsch,
                        BigDecimal.TEN,
                        baum,
                        BigDecimal.TEN
                ))
        );
    }

    private static Clan clan(String tag) {
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        return new Clan(
                clanId,
                tag,
                tag.toLowerCase(),
                tag,
                tag.toLowerCase(),
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                Instant.EPOCH,
                List.of(new ClanMember(
                        ownerId,
                        "Owner",
                        RankId.OWNER,
                        Instant.EPOCH
                ))
        );
    }
}
