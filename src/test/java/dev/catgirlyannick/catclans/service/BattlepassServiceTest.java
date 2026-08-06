package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.audit.TextAuditLogService;
import dev.catgirlyannick.catclans.config.RankPolicyTest;
import dev.catgirlyannick.catclans.model.BattlepassRewardType;
import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankId;
import dev.catgirlyannick.catclans.storage.SqliteClanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattlepassServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void awardsConfiguredActivityXpAndEnforcesPvpCooldown() throws Exception {
        Context context = context("activity.db");
        try (ClanService service = context.service()) {
            OperationResult<XpAwardResult> login = service.registerDailyLogin(
                    context.ownerId(),
                    "Owner"
            ).get(5, TimeUnit.SECONDS);
            assertTrue(login.successful());
            assertEquals(new BigDecimal("25.00"), login.value().awardedXp());
            assertEquals(1, login.value().streakDays());

            OperationResult<XpAwardResult> repeatedLogin = service.registerDailyLogin(
                    context.ownerId(),
                    "Owner"
            ).get(5, TimeUnit.SECONDS);
            assertEquals(BigDecimal.ZERO, repeatedLogin.value().awardedXp());

            PvpKillProcessingResult kill = service.processPvpKill(
                    context.ownerId(),
                    "Owner",
                    UUID.randomUUID(),
                    Instant.now(),
                    true
            ).get(5, TimeUnit.SECONDS);
            assertTrue(kill.battlepass().successful());
            assertFalse(kill.ranking().combatPointAwarded());
            assertEquals(
                    new BigDecimal("15"),
                    kill.battlepass().value().awardedXp()
            );

            UUID repeatedVictim = UUID.randomUUID();
            assertTrue(service.awardPvpKill(
                    context.ownerId(),
                    "Owner",
                    repeatedVictim
            ).get(5, TimeUnit.SECONDS).successful());
            assertEquals(
                    OperationCode.PVP_REWARD_COOLDOWN,
                    service.awardPvpKill(
                            context.secondMemberId(),
                            "Member",
                            repeatedVictim
                    ).get(5, TimeUnit.SECONDS).code()
            );

            service.awardOnlineXp(context.clanId(), 2).get(5, TimeUnit.SECONDS);
            assertEquals(1, service.findCachedBattlepass(context.clanId()).level());
            assertEquals(
                    new BigDecimal("55.00"),
                    service.findCachedBattlepass(context.clanId()).currentXp()
            );
        }
    }

    @Test
    void ownerClaimsPersistentClanUpgradesAndUsesUnlockedVaultPage() throws Exception {
        Context context = context("rewards.db");
        try (ClanService service = context.service()) {
            service.awardOnlineXp(context.clanId(), 2).get(5, TimeUnit.SECONDS);
            service.adjustBattlepassReward(
                    UUID.randomUUID(),
                    1,
                    BattlepassRewardType.MEMBER_SLOTS,
                    1
            ).get(5, TimeUnit.SECONDS);
            service.adjustBattlepassReward(
                    UUID.randomUUID(),
                    1,
                    BattlepassRewardType.VAULT_PAGES,
                    1
            ).get(5, TimeUnit.SECONDS);

            OperationResult<?> claim = service.claimBattlepassLevel(
                    context.ownerId(),
                    "Owner",
                    1
            ).get(5, TimeUnit.SECONDS);
            assertTrue(claim.successful());
            assertEquals(28, service.findCachedClan(context.clanId())
                    .orElseThrow()
                    .maxMembers());
            assertEquals(
                    OperationCode.REWARD_ALREADY_CLAIMED,
                    service.claimBattlepassLevel(
                            context.ownerId(),
                            "Owner",
                            1
                    ).get(5, TimeUnit.SECONDS).code()
            );

            OperationResult<?> page = service.openVault(
                    context.ownerId(),
                    2
            ).get(5, TimeUnit.SECONDS);
            assertTrue(page.successful());
            byte[] itemData = new byte[]{9, 8, 7};
            assertTrue(service.saveVaultSlot(
                    context.ownerId(),
                    "Owner",
                    2,
                    4,
                    itemData,
                    VaultMutationType.DEPOSIT,
                    "item=TEST amount=1"
            ).get(5, TimeUnit.SECONDS).successful());
            assertTrue(java.util.Arrays.equals(
                    itemData,
                    service.openVault(context.ownerId(), 2)
                            .get(5, TimeUnit.SECONDS)
                            .value()
                            .items()
                            .get(4)
            ));
            assertEquals(
                    2,
                    service.vaultLogMembers(context.ownerId())
                            .get(5, TimeUnit.SECONDS)
                            .value()
                            .size()
            );
            assertEquals(
                    "VAULT_DEPOSIT",
                    service.vaultLogEntries(
                            context.ownerId(),
                            context.ownerId(),
                            18
                    ).get(5, TimeUnit.SECONDS).value().getFirst().action()
            );
        }
    }

    private Context context(String databaseName) throws Exception {
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID secondMemberId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T00:00:00Z");
        Clan clan = new Clan(
                clanId,
                "Ashen",
                "ashen",
                "ASH",
                "ash",
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                now,
                List.of(
                        new ClanMember(ownerId, "Owner", RankId.OWNER, now),
                        new ClanMember(
                                secondMemberId,
                                "Member",
                                RankId.MEMBER,
                                now
                        )
                )
        );
        SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve(databaseName),
                true,
                5000
        );
        repository.initialize();
        repository.save(clan);
        ClanSnapshotCache cache = new ClanSnapshotCache(100);
        cache.preload(List.of(clan));
        TextAuditLogService audit = new TextAuditLogService(
                temporaryDirectory.resolve("logs-" + databaseName),
                true,
                14,
                "yyyy-MM-dd'.log'",
                "yyyy-MM-dd HH:mm:ss XXX",
                ignored -> {
                }
        );
        BattlepassSettings settings = new BattlepassSettings(
                true,
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
                ZoneId.systemDefault(),
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
        ClanService service = new ClanService(
                repository,
                audit,
                RankPolicyTest.policy(),
                new ClanRules(1, 20, "^[A-Za-z]+$", 2, 6, "^[A-Za-z0-9]+$"),
                JoinMode.INVITE_ONLY,
                27,
                Duration.ofHours(48),
                true,
                "Battlepass-Test",
                cache,
                128,
                5,
                5,
                10,
                List.of(
                        "vault.view",
                        "vault.deposit",
                        "vault.withdraw",
                        "vault.log"
                ),
                Map.of(),
                settings,
                new VaultSettings(true, 45),
                repository.findAllBattlepassProgress(),
                audit
        );
        return new Context(clanId, ownerId, secondMemberId, service);
    }

    private record Context(
            UUID clanId,
            UUID ownerId,
            UUID secondMemberId,
            ClanService service
    ) {
    }
}
