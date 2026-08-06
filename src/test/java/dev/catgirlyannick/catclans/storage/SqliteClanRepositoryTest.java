package dev.catgirlyannick.catclans.storage;

import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanHome;
import dev.catgirlyannick.catclans.model.ClanInvite;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.ClanRankingStats;
import dev.catgirlyannick.catclans.model.ClanRole;
import dev.catgirlyannick.catclans.model.ClanWarResult;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankId;
import dev.catgirlyannick.catclans.model.RankingKillResult;
import dev.catgirlyannick.catclans.model.BattlepassProgress;
import dev.catgirlyannick.catclans.model.BattlepassReward;
import dev.catgirlyannick.catclans.model.BattlepassRewardType;
import dev.catgirlyannick.catclans.model.DailyLoginState;
import dev.catgirlyannick.catclans.model.DiplomacyRequest;
import dev.catgirlyannick.catclans.model.DiplomacyType;
import dev.catgirlyannick.catclans.model.DiplomacyView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteClanRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void deletingClanCascadesAllClanOwnedData() throws Exception {
        Path database = temporaryDirectory.resolve("delete-cascade.db");
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        UUID ownerId = UUID.randomUUID();
        Clan clan = clan(UUID.randomUUID(), ownerId, "Delete", "DEL", now);

        try (SqliteClanRepository repository = new SqliteClanRepository(
                database,
                true,
                5000
        )) {
            repository.initialize();
            repository.save(clan);
            repository.saveRole(new ClanRole(
                    clan.id(),
                    RankId.OWNER.configKey(),
                    "Owner",
                    100,
                    true
            ));
            repository.saveVaultSlot(clan.id(), 1, 0, new byte[]{1, 2, 3});
            repository.depositBank(
                    clan.id(),
                    BigDecimal.TEN,
                    now
            );
            repository.saveHome(new ClanHome(
                    clan.id(), 1, UUID.randomUUID(), "world",
                    1D, 64D, 2D, 0F, 0F, ownerId, now
            ));

            assertTrue(repository.deleteClan(clan.id()));
            assertFalse(repository.deleteClan(clan.id()));
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            for (String table : List.of(
                    "clans",
                    "clan_members",
                    "clan_roles",
                    "clan_vault_items",
                    "clan_bank_accounts",
                    "clan_homes"
            )) {
                try (ResultSet result = statement.executeQuery(
                        "SELECT COUNT(*) AS total FROM " + table
                )) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt("total"), table);
                }
            }
        }
    }

    @Test
    void updatesClanBankAtomicallyWithoutBalanceCap() throws Exception {
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        UUID ownerId = UUID.randomUUID();
        Clan clan = clan(UUID.randomUUID(), ownerId, "Bank", "BNK", now);

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("bank.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(clan);

            assertEquals(BigDecimal.ZERO, repository.findBankBalance(clan.id()));
            assertEquals(
                    new BigDecimal("100.50"),
                    repository.depositBank(
                            clan.id(),
                            new BigDecimal("100.50"),
                            now
                    ).orElseThrow()
            );
            assertEquals(
                    0,
                    repository.depositBank(
                            clan.id(),
                            new BigDecimal("100"),
                            now.plusSeconds(1)
                    ).orElseThrow().compareTo(new BigDecimal("200.50"))
            );
            assertEquals(
                    new BigDecimal("160.25"),
                    repository.withdrawBank(
                            clan.id(),
                            new BigDecimal("40.25"),
                            now.plusSeconds(2)
                    ).orElseThrow()
            );
            assertTrue(repository.withdrawBank(
                    clan.id(),
                    new BigDecimal("250"),
                    now.plusSeconds(3)
            ).isEmpty());
            assertEquals(
                    0,
                    repository.restoreBankBalance(
                            clan.id(),
                            new BigDecimal("40.25"),
                            now.plusSeconds(4)
                    ).compareTo(new BigDecimal("200.50"))
            );
        }
    }

    @Test
    void persistsClansMembersAndInvites() throws Exception {
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invitedPlayerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T00:00:00Z");
        Clan clan = new Clan(
                clanId,
                "Wächter",
                "wächter",
                "W7",
                "w7",
                "<gradient:#FF0000:#00FFFF>W7",
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                now,
                List.of(new ClanMember(ownerId, "Yannick", RankId.OWNER, now))
        );

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("clans.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(clan);

            Clan loaded = repository.findByMember(ownerId).orElseThrow();
            assertEquals(clanId, loaded.id());
            assertEquals("Wächter", loaded.name());
            assertEquals("<gradient:#FF0000:#00FFFF>W7", loaded.formattedTag());
            assertEquals(RankId.OWNER, loaded.member(ownerId).orElseThrow().rank());
            assertTrue(repository.findByNameOrTag("w7").isPresent());
            assertEquals(1, repository.findAll().size());

            ClanInvite invite = new ClanInvite(
                    clanId,
                    invitedPlayerId,
                    ownerId,
                    now,
                    now.plusSeconds(3600)
            );
            repository.saveInvite(invite);
            assertEquals(invite, repository.findInvite(clanId, invitedPlayerId).orElseThrow());
            assertEquals(
                    List.of(invite),
                    repository.findInvitesForPlayer(invitedPlayerId, now.minusSeconds(1))
            );

            Clan joined = clan.withMember(new ClanMember(
                    invitedPlayerId,
                    "Member",
                    RankId.RECRUIT,
                    now.plusSeconds(60)
            ));
            repository.saveAndDeleteInvitesForPlayer(joined, invitedPlayerId);
            assertTrue(repository.findByMember(invitedPlayerId).isPresent());
            assertTrue(repository.findInvite(clanId, invitedPlayerId).isEmpty());
        }
    }

    @Test
    void removesExpiredInvitesWhenDatabaseStarts() throws Exception {
        Path database = temporaryDirectory.resolve("expired-invites.db");
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invitedPlayerId = UUID.randomUUID();
        Instant now = Instant.now();
        Clan clan = new Clan(
                clanId,
                "Alpha",
                "alpha",
                "AC",
                "ac",
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                now,
                List.of(new ClanMember(ownerId, "Owner", RankId.OWNER, now))
        );

        try (SqliteClanRepository repository =
                     new SqliteClanRepository(database, true, 5000)) {
            repository.initialize();
            repository.save(clan);
            repository.saveInvite(new ClanInvite(
                    clanId,
                    invitedPlayerId,
                    ownerId,
                    now.minusSeconds(7200),
                    now.minusSeconds(3600)
            ));
        }

        try (SqliteClanRepository repository =
                     new SqliteClanRepository(database, true, 5000)) {
            repository.initialize();
            assertTrue(repository.findInvite(clanId, invitedPlayerId).isEmpty());
        }
    }

    @Test
    void persistsAndAcceptsDirectedDiplomacyRequests() throws Exception {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        Clan first = clan(UUID.randomUUID(), firstOwner, "Alpha", "A1", now);
        Clan second = clan(UUID.randomUUID(), secondOwner, "Beta", "B2", now);

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("diplomacy.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(first);
            repository.save(second);
            DiplomacyRequest request = new DiplomacyRequest(
                    UUID.randomUUID(),
                    first.id(),
                    second.id(),
                    DiplomacyType.ALLY,
                    0,
                    firstOwner,
                    now,
                    now.plusSeconds(3600)
            );
            assertTrue(repository.saveDiplomacyRequest(request));
            assertFalse(repository.saveDiplomacyRequest(new DiplomacyRequest(
                    UUID.randomUUID(),
                    second.id(),
                    first.id(),
                    DiplomacyType.ALLY,
                    0,
                    secondOwner,
                    now.plusSeconds(1),
                    now.plusSeconds(3601)
            )));

            DiplomacyView outgoing = repository.findDiplomacyView(
                    first.id(),
                    second.id(),
                    now
            );
            DiplomacyView incoming = repository.findDiplomacyView(
                    second.id(),
                    first.id(),
                    now
            );
            assertEquals(request.id(), outgoing.outgoingAllyRequest().orElseThrow().id());
            assertEquals(request.id(), incoming.incomingAllyRequest().orElseThrow().id());
            assertEquals(1, repository.countPendingDiplomacyRequests(first.id(), now));
            assertEquals(
                    List.of(request),
                    repository.findIncomingDiplomacyRequests(
                            second.id(),
                            DiplomacyType.ALLY,
                            now
                    )
            );

            repository.acceptDiplomacyRequest(request, secondOwner, now.plusSeconds(30));

            DiplomacyView accepted = repository.findDiplomacyView(
                    first.id(),
                    second.id(),
                    now.plusSeconds(31)
            );
            assertTrue(accepted.allied());
            assertTrue(accepted.incomingAllyRequest().isEmpty());
            assertTrue(repository.findDiplomacyRequest(request.id()).isEmpty());
            assertTrue(repository.findIncomingDiplomacyRequests(
                    second.id(),
                    DiplomacyType.ALLY,
                    now.plusSeconds(31)
            ).isEmpty());
        }
    }

    @Test
    void activatesWarForRequestedDuration() throws Exception {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        Clan first = clan(UUID.randomUUID(), firstOwner, "Gamma", "G3", now);
        Clan second = clan(UUID.randomUUID(), secondOwner, "Delta", "D4", now);

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("war.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(first);
            repository.save(second);
            DiplomacyRequest request = new DiplomacyRequest(
                    UUID.randomUUID(),
                    first.id(),
                    second.id(),
                    DiplomacyType.WAR,
                    48,
                    firstOwner,
                    now,
                    now.plusSeconds(3600)
            );
            repository.saveDiplomacyRequest(request);
            repository.acceptDiplomacyRequest(request, secondOwner, now);

            assertTrue(repository.findDiplomacyView(
                    first.id(),
                    second.id(),
                    now.plusSeconds(48L * 3600L - 1)
            ).activeWar().isPresent());
            assertTrue(repository.findDiplomacyView(
                    first.id(),
                    second.id(),
                    now.plusSeconds(48L * 3600L)
            ).activeWar().isEmpty());
        }
    }

    @Test
    void recordsAtMostOnePermanentActivityPointPerClanAndDay() throws Exception {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        Clan clan = clan(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Aktiv",
                "AK",
                now
        );

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("ranking-activity.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(clan);

            assertTrue(repository.recordDailyRankingActivity(
                    clan.id(),
                    LocalDate.of(2026, 7, 31)
            ));
            assertFalse(repository.recordDailyRankingActivity(
                    clan.id(),
                    LocalDate.of(2026, 7, 31)
            ));
            assertTrue(repository.recordDailyRankingActivity(
                    clan.id(),
                    LocalDate.of(2026, 8, 1)
            ));

            ClanRankingStats stats = repository.findAllRankingStats().get(clan.id());
            assertEquals(2, stats.activeDays());
            assertEquals(LocalDate.of(2026, 8, 1), stats.lastActiveDate());
        }
    }

    @Test
    void awardsCombatPointsOnlyForNonAlliedClanKillsOutsideCooldown()
            throws Exception {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        Clan alpha = clan(UUID.randomUUID(), UUID.randomUUID(), "Alpha", "A", now);
        Clan beta = clan(UUID.randomUUID(), UUID.randomUUID(), "Beta", "B", now);
        Clan ally = clan(UUID.randomUUID(), UUID.randomUUID(), "Ally", "C", now);
        UUID betaVictim = beta.ownerId();

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("ranking-pvp.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(alpha);
            repository.save(beta);
            repository.save(ally);

            RankingKillResult first = repository.recordRankingKill(
                    alpha.id(),
                    beta.id(),
                    betaVictim,
                    now,
                    now.minusSeconds(900)
            );
            RankingKillResult repeated = repository.recordRankingKill(
                    alpha.id(),
                    beta.id(),
                    betaVictim,
                    now.plusSeconds(60),
                    now.minusSeconds(840)
            );
            RankingKillResult afterCooldown = repository.recordRankingKill(
                    alpha.id(),
                    beta.id(),
                    betaVictim,
                    now.plusSeconds(901),
                    now.plusSeconds(1)
            );

            DiplomacyRequest allyRequest = new DiplomacyRequest(
                    UUID.randomUUID(),
                    alpha.id(),
                    ally.id(),
                    DiplomacyType.ALLY,
                    0,
                    alpha.ownerId(),
                    now,
                    now.plusSeconds(3600)
            );
            assertTrue(repository.saveDiplomacyRequest(allyRequest));
            repository.acceptDiplomacyRequest(
                    allyRequest,
                    ally.ownerId(),
                    now.plusSeconds(10)
            );
            RankingKillResult allied = repository.recordRankingKill(
                    alpha.id(),
                    ally.id(),
                    ally.ownerId(),
                    now.plusSeconds(1000),
                    now.plusSeconds(100)
            );

            assertTrue(first.combatPointAwarded());
            assertFalse(repeated.combatPointAwarded());
            assertTrue(afterCooldown.combatPointAwarded());
            assertFalse(allied.combatPointAwarded());
            assertEquals(
                    2,
                    repository.findAllRankingStats().get(alpha.id()).combatKills()
            );
        }
    }

    @Test
    void countsOpponentDeathsAndFinalizesExpiredWarOnlyOnce() throws Exception {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        Clan alpha = clan(UUID.randomUUID(), UUID.randomUUID(), "Alpha", "A", now);
        Clan beta = clan(UUID.randomUUID(), UUID.randomUUID(), "Beta", "B", now);

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("ranking-war.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(alpha);
            repository.save(beta);
            DiplomacyRequest request = new DiplomacyRequest(
                    UUID.randomUUID(),
                    alpha.id(),
                    beta.id(),
                    DiplomacyType.WAR,
                    24,
                    alpha.ownerId(),
                    now,
                    now.plusSeconds(3600)
            );
            assertTrue(repository.saveDiplomacyRequest(request));
            repository.acceptDiplomacyRequest(request, beta.ownerId(), now);

            assertTrue(repository.recordRankingKill(
                    beta.id(),
                    alpha.id(),
                    alpha.ownerId(),
                    now.plusSeconds(1),
                    now.minusSeconds(899)
            ).warDeathRecorded());
            assertTrue(repository.recordRankingKill(
                    beta.id(),
                    alpha.id(),
                    alpha.ownerId(),
                    now.plusSeconds(2),
                    now.minusSeconds(898)
            ).warDeathRecorded());
            assertTrue(repository.recordRankingKill(
                    alpha.id(),
                    beta.id(),
                    beta.ownerId(),
                    now.plusSeconds(3),
                    now.minusSeconds(897)
            ).warDeathRecorded());

            List<ClanWarResult> results = repository.finalizeExpiredWars(
                    now.plusSeconds(24L * 3600L)
            );
            assertEquals(1, results.size());
            ClanWarResult result = results.getFirst();
            assertEquals(3, result.firstDeaths() + result.secondDeaths());
            assertEquals(beta.id(), result.winner().orElseThrow());
            assertEquals(alpha.id(), result.loser().orElseThrow());
            assertTrue(repository.finalizeExpiredWars(
                    now.plusSeconds(24L * 3600L + 1)
            ).isEmpty());

            Map<UUID, ClanRankingStats> stats = repository.findAllRankingStats();
            assertEquals(1, stats.get(beta.id()).warsWon());
            assertEquals(1, stats.get(alpha.id()).warsLost());
        }
    }

    @Test
    void finalizesEqualWarDeathsAsDrawWithoutRankingPoints() throws Exception {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        Clan alpha = clan(UUID.randomUUID(), UUID.randomUUID(), "Alpha", "A", now);
        Clan beta = clan(UUID.randomUUID(), UUID.randomUUID(), "Beta", "B", now);

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("ranking-war-draw.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(alpha);
            repository.save(beta);
            DiplomacyRequest request = new DiplomacyRequest(
                    UUID.randomUUID(),
                    alpha.id(),
                    beta.id(),
                    DiplomacyType.WAR,
                    24,
                    alpha.ownerId(),
                    now,
                    now.plusSeconds(3600)
            );
            repository.saveDiplomacyRequest(request);
            repository.acceptDiplomacyRequest(request, beta.ownerId(), now);

            ClanWarResult result = repository.finalizeExpiredWars(
                    now.plusSeconds(24L * 3600L)
            ).getFirst();
            assertTrue(result.draw());
            Map<UUID, ClanRankingStats> stats = repository.findAllRankingStats();
            assertEquals(0, stats.get(alpha.id()).warsWon());
            assertEquals(0, stats.get(alpha.id()).warsLost());
            assertEquals(0, stats.get(beta.id()).warsWon());
            assertEquals(0, stats.get(beta.id()).warsLost());
        }
    }

    @Test
    void endsActiveWarWithCurrentRankingResult() throws Exception {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        Clan alpha = clan(UUID.randomUUID(), UUID.randomUUID(), "Alpha", "A", now);
        Clan beta = clan(UUID.randomUUID(), UUID.randomUUID(), "Beta", "B", now);

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("admin-war-end.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(alpha);
            repository.save(beta);
            DiplomacyRequest request = new DiplomacyRequest(
                    UUID.randomUUID(),
                    alpha.id(),
                    beta.id(),
                    DiplomacyType.WAR,
                    24,
                    alpha.ownerId(),
                    now,
                    now.plusSeconds(3600)
            );
            assertTrue(repository.saveDiplomacyRequest(request));
            repository.acceptDiplomacyRequest(request, beta.ownerId(), now);

            assertTrue(repository.recordRankingKill(
                    beta.id(),
                    alpha.id(),
                    alpha.ownerId(),
                    now.plusSeconds(1),
                    now.minusSeconds(899)
            ).warDeathRecorded());

            ClanWarResult result = repository.endActiveWar(
                    alpha.id(),
                    beta.id(),
                    now.plusSeconds(10)
            ).orElseThrow();
            assertEquals(beta.id(), result.winner().orElseThrow());
            assertEquals(alpha.id(), result.loser().orElseThrow());
            assertEquals(1, result.firstDeaths() + result.secondDeaths());
            assertTrue(repository.findDiplomacyView(
                    alpha.id(),
                    beta.id(),
                    now.plusSeconds(11)
            ).activeWar().isEmpty());
            assertTrue(repository.endActiveWar(
                    alpha.id(),
                    beta.id(),
                    now.plusSeconds(12)
            ).isEmpty());
            assertTrue(repository.finalizeExpiredWars(
                    now.plusSeconds(24L * 3600L)
            ).isEmpty());

            Map<UUID, ClanRankingStats> stats = repository.findAllRankingStats();
            assertEquals(0, stats.get(alpha.id()).warsWon());
            assertEquals(1, stats.get(alpha.id()).warsLost());
            assertEquals(1, stats.get(beta.id()).warsWon());
            assertEquals(0, stats.get(beta.id()).warsLost());
        }
    }

    @Test
    void rejectsUnknownSchemaBeforeCreatingOrCleaningTables() throws Exception {
        Path database = temporaryDirectory.resolve("future-schema.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE schema_meta (
                        id INTEGER PRIMARY KEY,
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_meta(id, version) VALUES (1, 99)");
        }

        SqliteClanRepository repository = new SqliteClanRepository(database, true, 5000);
        try {
            assertThrows(Exception.class, repository::initialize);
        } finally {
            repository.close();
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.prepareStatement("""
                     SELECT 1 FROM sqlite_master
                     WHERE type = 'table' AND name = 'clan_invites'
                     """);
             ResultSet results = statement.executeQuery()) {
            assertFalse(results.next());
        }
    }

    @Test
    void migratesExactSchemaFiveWarTableToRankingSchemaSix() throws Exception {
        Path database = temporaryDirectory.resolve("schema-five.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE schema_meta (
                        id INTEGER PRIMARY KEY,
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_meta(id, version) VALUES (1, 5)");
            statement.execute("""
                    CREATE TABLE clans (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        normalized_name TEXT NOT NULL UNIQUE,
                        tag TEXT NOT NULL,
                        normalized_tag TEXT NOT NULL UNIQUE,
                        formatted_tag TEXT NOT NULL,
                        owner_uuid TEXT NOT NULL,
                        join_mode TEXT NOT NULL,
                        max_members INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE clan_wars (
                        id TEXT PRIMARY KEY,
                        first_clan_id TEXT NOT NULL,
                        second_clan_id TEXT NOT NULL,
                        duration_hours INTEGER NOT NULL,
                        started_at TEXT NOT NULL,
                        ends_at TEXT NOT NULL,
                        accepted_by_uuid TEXT NOT NULL,
                        UNIQUE (first_clan_id, second_clan_id)
                    )
                    """);
        }

        try (SqliteClanRepository repository =
                     new SqliteClanRepository(database, true, 5000)) {
            repository.initialize();
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery(
                    "SELECT version FROM schema_meta WHERE id = 1"
            )) {
                assertTrue(version.next());
                assertEquals(7, version.getInt("version"));
            }
            try (ResultSet columns = statement.executeQuery(
                    "PRAGMA table_info(clan_wars)"
            )) {
                java.util.Set<String> names = new java.util.HashSet<>();
                while (columns.next()) {
                    names.add(columns.getString("name"));
                }
                assertTrue(names.containsAll(List.of(
                        "first_deaths",
                        "second_deaths",
                        "result_processed",
                        "winner_clan_id",
                        "loser_clan_id",
                        "completed_at"
                )));
            }
            try (ResultSet rankingTable = statement.executeQuery("""
                    SELECT 1 FROM sqlite_master
                    WHERE type = 'table' AND name = 'clan_ranking_stats'
                    """)) {
                assertTrue(rankingTable.next());
            }
        }
    }

    private static Clan clan(
            UUID clanId,
            UUID ownerId,
            String name,
            String tag,
            Instant now
    ) {
        return new Clan(
                clanId,
                name,
                name.toLowerCase(),
                tag,
                tag.toLowerCase(),
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                now,
                List.of(new ClanMember(ownerId, "Owner", RankId.OWNER, now))
        );
    }

    @Test
    void migratesSchemaOneMembersToPersistentRoleIds() throws Exception {
        Path database = temporaryDirectory.resolve("schema-one.db");
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE schema_meta (
                        id INTEGER PRIMARY KEY,
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_meta(id, version) VALUES (1, 1)");
            statement.execute("""
                    CREATE TABLE clans (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        normalized_name TEXT NOT NULL UNIQUE,
                        tag TEXT NOT NULL,
                        normalized_tag TEXT NOT NULL UNIQUE,
                        owner_uuid TEXT NOT NULL,
                        join_mode TEXT NOT NULL,
                        max_members INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE clan_members (
                        clan_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL UNIQUE,
                        last_known_name TEXT NOT NULL,
                        rank_id TEXT NOT NULL,
                        joined_at INTEGER NOT NULL,
                        PRIMARY KEY (clan_id, player_uuid)
                    )
                    """);
            statement.execute("""
                    INSERT INTO clans(
                        id, name, normalized_name, tag, normalized_tag,
                        owner_uuid, join_mode, max_members, created_at
                    ) VALUES (
                        '%s', 'Ashen', 'ashen', 'ASH', 'ash',
                        '%s', 'INVITE_ONLY', 27, '2026-07-31T00:00:00Z'
                    )
                    """.formatted(clanId, ownerId));
            statement.execute("""
                    INSERT INTO clan_members(
                        clan_id, player_uuid, last_known_name, rank_id, joined_at
                    ) VALUES (
                        '%s', '%s', 'Owner', 'OWNER', '2026-07-31T00:00:00Z'
                    )
                    """.formatted(clanId, ownerId));
        }

        try (SqliteClanRepository repository =
                     new SqliteClanRepository(database, true, 5000)) {
            repository.initialize();
            Clan loaded = repository.findByMember(ownerId).orElseThrow();
            assertEquals("owner", loaded.member(ownerId).orElseThrow().roleId());
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT version FROM schema_meta WHERE id = 1"
             )) {
            assertTrue(result.next());
            assertEquals(7, result.getInt("version"));
        }
    }

    @Test
    void migratesSchemaTwoTagsToSafePlainFormatting() throws Exception {
        Path database = temporaryDirectory.resolve("schema-two.db");
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE schema_meta (
                        id INTEGER PRIMARY KEY,
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_meta(id, version) VALUES (1, 2)");
            statement.execute("""
                    CREATE TABLE clans (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        normalized_name TEXT NOT NULL UNIQUE,
                        tag TEXT NOT NULL,
                        normalized_tag TEXT NOT NULL UNIQUE,
                        owner_uuid TEXT NOT NULL,
                        join_mode TEXT NOT NULL,
                        max_members INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE clan_members (
                        clan_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL UNIQUE,
                        last_known_name TEXT NOT NULL,
                        rank_id TEXT NOT NULL,
                        role_id TEXT NOT NULL,
                        joined_at TEXT NOT NULL,
                        PRIMARY KEY (clan_id, player_uuid)
                    )
                    """);
            statement.execute("""
                    INSERT INTO clans(
                        id, name, normalized_name, tag, normalized_tag,
                        owner_uuid, join_mode, max_members, created_at
                    ) VALUES (
                        '%s', 'Ashen', 'ashen', 'ASH', 'ash',
                        '%s', 'INVITE_ONLY', 27, '2026-07-31T00:00:00Z'
                    )
                    """.formatted(clanId, ownerId));
            statement.execute("""
                    INSERT INTO clan_members(
                        clan_id, player_uuid, last_known_name, rank_id, role_id, joined_at
                    ) VALUES (
                        '%s', '%s', 'Owner', 'OWNER', 'owner',
                        '2026-07-31T00:00:00Z'
                    )
                    """.formatted(clanId, ownerId));
        }

        try (SqliteClanRepository repository =
                     new SqliteClanRepository(database, true, 5000)) {
            repository.initialize();
            Clan loaded = repository.findById(clanId).orElseThrow();
            assertEquals("ASH", loaded.tag());
            assertEquals("ASH", loaded.formattedTag());
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT version, formatted_tag FROM schema_meta, clans LIMIT 1"
             )) {
            assertTrue(result.next());
            assertEquals(7, result.getInt("version"));
            assertEquals("ASH", result.getString("formatted_tag"));
        }
    }

    @Test
    void persistsBattlepassClaimsStreaksCooldownsAndVaultItems() throws Exception {
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T00:00:00Z");
        Clan clan = new Clan(
                clanId,
                "Progress",
                "progress",
                "XP",
                "xp",
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                now,
                List.of(new ClanMember(ownerId, "Owner", RankId.OWNER, now))
        );

        try (SqliteClanRepository repository = new SqliteClanRepository(
                temporaryDirectory.resolve("progression.db"),
                true,
                5000
        )) {
            repository.initialize();
            repository.save(clan);
            repository.saveBattlepassProgress(new BattlepassProgress(
                    clanId,
                    3,
                    new BigDecimal("42.50"),
                    now
            ));
            assertEquals(
                    new BigDecimal("42.50"),
                    repository.findBattlepassProgress(clanId, now).currentXp()
            );

            DailyLoginState login = new DailyLoginState(
                    ownerId,
                    LocalDate.of(2026, 7, 31),
                    11
            );
            repository.saveDailyLoginState(login);
            assertEquals(login, repository.findDailyLoginState(ownerId).orElseThrow());

            repository.savePvpRewardAndBattlepass(
                    victimId,
                    now,
                    repository.findBattlepassProgress(clanId, now)
            );
            assertEquals(now, repository.findPvpRewardTime(victimId).orElseThrow());

            BattlepassReward reward = new BattlepassReward(
                    3,
                    BattlepassRewardType.MEMBER_SLOTS,
                    2,
                    ownerId,
                    now
            );
            repository.saveBattlepassReward(reward);
            assertEquals(List.of(reward), repository.findBattlepassRewards(3, 3));
            assertTrue(repository.claimBattlepassReward(
                    clanId,
                    ownerId,
                    reward,
                    500,
                    10,
                    7,
                    100,
                    5
            ).claimed());
            assertFalse(repository.claimBattlepassReward(
                    clanId,
                    ownerId,
                    reward,
                    500,
                    10,
                    7,
                    100,
                    5
            ).claimed());
            assertEquals(29, repository.findById(clanId).orElseThrow().maxMembers());
            assertTrue(repository.findClaimedRewardKeys(clanId, 3, 3)
                    .contains("3:MEMBER_SLOTS"));

            byte[] item = new byte[]{1, 2, 3, 4};
            repository.saveVaultSlot(clanId, 1, 7, item);
            assertTrue(java.util.Arrays.equals(
                    item,
                    repository.findVaultPage(clanId, 1).get(7)
            ));
            repository.saveVaultSlot(clanId, 1, 7, null);
            assertFalse(repository.findVaultPage(clanId, 1).containsKey(7));
        }
    }

    @Test
    void migratesSchemaThreeToFourWithoutLosingExistingClan() throws Exception {
        Path database = temporaryDirectory.resolve("schema-three.db");
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T00:00:00Z");
        Clan clan = new Clan(
                clanId,
                "Bestand",
                "bestand",
                "ALT",
                "alt",
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                now,
                List.of(new ClanMember(ownerId, "Owner", RankId.OWNER, now))
        );

        try (SqliteClanRepository repository =
                     new SqliteClanRepository(database, true, 5000)) {
            repository.initialize();
            repository.save(clan);
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            for (String table : List.of(
                    "clan_vault_items",
                    "clan_unlocks",
                    "battlepass_reward_claims",
                    "battlepass_rewards",
                    "battlepass_pvp_cooldowns",
                    "player_login_streaks",
                    "clan_battlepass"
            )) {
                statement.execute("DROP TABLE " + table);
            }
            statement.execute("UPDATE schema_meta SET version = 3 WHERE id = 1");
        }

        try (SqliteClanRepository repository =
                     new SqliteClanRepository(database, true, 5000)) {
            repository.initialize();
            assertEquals("Bestand", repository.findById(clanId).orElseThrow().name());
            assertEquals(0, repository.findBattlepassProgress(clanId, now).level());
            assertEquals(1, repository.findClanUnlocks(clanId).vaultPages());
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT version FROM schema_meta WHERE id = 1"
             )) {
            assertTrue(result.next());
            assertEquals(7, result.getInt("version"));
        }
    }
}
