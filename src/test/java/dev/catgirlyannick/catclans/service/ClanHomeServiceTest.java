package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.audit.TextAuditLogService;
import dev.catgirlyannick.catclans.config.RankPolicyTest;
import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanHomeView;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankId;
import dev.catgirlyannick.catclans.storage.SqliteClanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanHomeServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsManagesAndLimitsClanHomes() throws Exception {
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-31T00:00:00Z");
        Clan clan = new Clan(
                clanId,
                "Ashen",
                "ashen",
                "ASH",
                "ash",
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                createdAt,
                List.of(new ClanMember(ownerId, "Owner", RankId.OWNER, createdAt))
        );
        Path database = temporaryDirectory.resolve("homes.db");
        SqliteClanRepository repository = new SqliteClanRepository(database, true, 5000);
        repository.initialize();
        repository.save(clan);
        ClanSnapshotCache cache = new ClanSnapshotCache(100);
        cache.preload(List.of(clan));
        TextAuditLogService audit = new TextAuditLogService(
                temporaryDirectory.resolve("audit"),
                true,
                14,
                "yyyy-MM-dd'.log'",
                "yyyy-MM-dd HH:mm:ss",
                ignored -> {
                }
        );

        try (ClanService service = new ClanService(
                repository,
                audit,
                RankPolicyTest.policy(),
                new ClanRules(1, 20, "^[A-Za-z ]+$", 2, 6, "^[A-Za-z0-9]+$"),
                JoinMode.INVITE_ONLY,
                27,
                Duration.ofHours(48),
                true,
                "ClanHomeServiceTest",
                cache,
                64,
                5,
                5,
                10,
                List.of("home.view", "home.teleport", "home.set", "home.delete"),
                Map.of(),
                battlepassSettings(),
                new VaultSettings(false, 45),
                Map.of(),
                audit,
                BankSettings.disabled(),
                audit,
                new HomeSettings(true, 3, 103, 0, true)
        )) {
            OperationResult<ClanHomeView> opened = service.openHomes(ownerId)
                    .get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, opened.code());
            assertEquals(3, opened.value().unlockedSlots());
            assertEquals(103, opened.value().maximumSlots());
            assertTrue(opened.value().homes().isEmpty());

            assertEquals(
                    OperationCode.HOME_SLOT_LOCKED,
                    service.setHome(
                            ownerId, "Owner", 4, worldId, "world",
                            0.5D, 65D, 0.5D, 0F, 0F
                    ).get(5, TimeUnit.SECONDS).code()
            );

            OperationResult<ClanHomeView> saved = service.setHome(
                    ownerId, "Owner", 1, worldId, "world",
                    10.5D, 70D, -4.5D, 90F, 10F
            ).get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, saved.code());
            assertEquals(10.5D, saved.value().home(1).orElseThrow().x());
            assertEquals(
                    OperationCode.SUCCESS,
                    service.homeForTeleport(ownerId, 1).get(5, TimeUnit.SECONDS).code()
            );

            assertEquals(
                    OperationCode.SUCCESS,
                    service.deleteHome(ownerId, "Owner", 1)
                            .get(5, TimeUnit.SECONDS).code()
            );
            assertEquals(
                    OperationCode.HOME_NOT_SET,
                    service.homeForTeleport(ownerId, 1).get(5, TimeUnit.SECONDS).code()
            );

            service.setHome(
                    ownerId, "Owner", 2, worldId, "world_nether",
                    4D, 80D, 8D, 180F, 0F
            ).get(5, TimeUnit.SECONDS);
        }
        repository.close();

        try (SqliteClanRepository reopened = new SqliteClanRepository(database, true, 5000)) {
            reopened.initialize();
            assertEquals("world_nether", reopened.findHome(clanId, 2)
                    .orElseThrow().worldName());
        }
    }

    private static BattlepassSettings battlepassSettings() {
        return new BattlepassSettings(
                false,
                new BattlepassCurve(
                        new BigDecimal("100"),
                        new BigDecimal("1.75"),
                        2,
                        RoundingMode.HALF_UP
                ),
                new LoginStreakCalculator(
                        new BigDecimal("25"),
                        new BigDecimal("1.3"),
                        10,
                        new BigDecimal("0.8"),
                        2,
                        RoundingMode.HALF_UP
                ),
                ZoneId.of("Europe/Berlin"),
                new BigDecimal("50"),
                30,
                new BigDecimal("15"),
                15,
                false,
                500,
                10,
                7,
                100
        );
    }
}
