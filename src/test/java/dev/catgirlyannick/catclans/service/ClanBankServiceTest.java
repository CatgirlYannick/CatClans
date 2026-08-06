package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.audit.AuditLogEntry;
import dev.catgirlyannick.catclans.audit.TextAuditLogService;
import dev.catgirlyannick.catclans.config.RankPolicyTest;
import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanBankView;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanBankServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsBankTransfersLimitsRollbacksAndTextLogs() throws Exception {
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
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
        SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("bank.db"),
                true,
                5000
        );
        repository.initialize();
        repository.save(clan);
        ClanSnapshotCache cache = new ClanSnapshotCache(100);
        cache.preload(List.of(clan));
        TextAuditLogService audit = audit("audit", false);
        TextAuditLogService bankAudit = audit("bank", true);

        try (ClanService service = new ClanService(
                repository,
                audit,
                RankPolicyTest.policy(),
                new ClanRules(1, 20, "^[A-Za-z ]+$", 2, 6, "^[A-Za-z0-9]+$"),
                JoinMode.INVITE_ONLY,
                27,
                Duration.ofHours(48),
                true,
                "ClanBankServiceTest",
                cache,
                64,
                5,
                5,
                10,
                List.of(
                        "bank.view",
                        "bank.deposit",
                        "bank.withdraw",
                        "bank.log"
                ),
                Map.of(),
                battlepassSettings(),
                new VaultSettings(false, 45),
                Map.of(),
                audit,
                bankSettings(),
                bankAudit
        )) {
            OperationResult<ClanBankView> opened = service.openBank(ownerId)
                    .get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, opened.code());
            assertEquals(0, opened.value().balance().compareTo(BigDecimal.ZERO));

            OperationResult<ClanBankView> deposited = service.depositBank(
                    ownerId,
                    "Owner",
                    new BigDecimal("100.50")
            ).get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, deposited.code());
            assertEquals(0, deposited.value().balance().compareTo(new BigDecimal("100.50")));

            OperationResult<ClanBankView> secondDeposit = service.depositBank(
                    ownerId,
                    "Owner",
                    new BigDecimal("100")
            ).get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, secondDeposit.code());
            assertEquals(0, secondDeposit.value().balance()
                    .compareTo(new BigDecimal("200.50")));
            assertEquals(
                    OperationCode.BANK_INSUFFICIENT_FUNDS,
                    service.withdrawBank(ownerId, "Owner", new BigDecimal("250"))
                            .get(5, TimeUnit.SECONDS)
                            .code()
            );

            OperationResult<ClanBankView> withdrawn = service.withdrawBank(
                    ownerId,
                    "Owner",
                    new BigDecimal("40.25")
            ).get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, withdrawn.code());
            assertEquals(0, withdrawn.value().balance().compareTo(new BigDecimal("160.25")));
            assertEquals(0, service.cachedBankBalance(clanId)
                    .compareTo(new BigDecimal("160.25")));

            service.restoreBankWithdrawal(
                    clanId,
                    new BigDecimal("40.25"),
                    ownerId,
                    "Owner"
            ).get(5, TimeUnit.SECONDS);
            assertEquals(0, service.cachedBankBalance(clanId)
                    .compareTo(new BigDecimal("200.50")));

            OperationResult<List<AuditLogEntry>> entries = service.bankLogEntries(
                    ownerId,
                    ownerId
            ).get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, entries.code());
            assertEquals(4, entries.value().size());
            assertTrue(entries.value().stream()
                    .anyMatch(entry -> entry.action().equals("BANK_DEPOSIT")));
            assertTrue(entries.value().stream()
                    .anyMatch(entry -> entry.action().equals("BANK_WITHDRAW")));
            assertTrue(entries.value().stream()
                    .anyMatch(entry -> entry.action().equals("BANK_WITHDRAW_ROLLBACK")));
        }

        try (SqliteClanRepository reopened = new SqliteClanRepository(
                temporaryDirectory.resolve("bank.db"),
                true,
                5000
        )) {
            reopened.initialize();
            assertEquals(0, reopened.findBankBalance(clanId)
                    .compareTo(new BigDecimal("200.50")));
        }
    }

    private TextAuditLogService audit(String directory, boolean enabled) {
        return new TextAuditLogService(
                temporaryDirectory.resolve(directory),
                enabled,
                14,
                "yyyy-MM-dd'.log'",
                "yyyy-MM-dd HH:mm:ss",
                ignored -> {
                }
        );
    }

    private static BankSettings bankSettings() {
        return new BankSettings(
                true,
                "AshenCoins",
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                2,
                RoundingMode.HALF_UP,
                List.of(
                        new BigDecimal("10"),
                        new BigDecimal("100")
                ),
                true,
                true,
                18
        );
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
