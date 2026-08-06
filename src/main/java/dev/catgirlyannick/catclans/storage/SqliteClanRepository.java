package dev.catgirlyannick.catclans.storage;

import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanInvite;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.ClanRole;
import dev.catgirlyannick.catclans.model.BattlepassProgress;
import dev.catgirlyannick.catclans.model.BattlepassReward;
import dev.catgirlyannick.catclans.model.BattlepassRewardType;
import dev.catgirlyannick.catclans.model.ClanUnlocks;
import dev.catgirlyannick.catclans.model.ClanRankingStats;
import dev.catgirlyannick.catclans.model.ClanHome;
import dev.catgirlyannick.catclans.model.ClanWarResult;
import dev.catgirlyannick.catclans.model.DailyLoginState;
import dev.catgirlyannick.catclans.model.DiplomacyRequest;
import dev.catgirlyannick.catclans.model.DiplomacyType;
import dev.catgirlyannick.catclans.model.DiplomacyView;
import dev.catgirlyannick.catclans.model.ClanWar;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankId;
import dev.catgirlyannick.catclans.model.RewardClaimResult;
import dev.catgirlyannick.catclans.model.RankingKillResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SqliteClanRepository implements ClanRepository {

    public static final int SCHEMA_VERSION = 7;

    private final Path databaseFile;
    private final boolean writeAheadLog;
    private final int busyTimeoutMilliseconds;
    private final String synchronousMode;
    private final int walAutoCheckpointPages;
    private final boolean optimizeOnClose;
    private Connection connection;

    protected SqliteClanRepository() {
        this.databaseFile = null;
        this.writeAheadLog = false;
        this.busyTimeoutMilliseconds = 5000;
        this.synchronousMode = "NORMAL";
        this.walAutoCheckpointPages = 1000;
        this.optimizeOnClose = false;
    }

    public SqliteClanRepository(Path databaseFile, boolean writeAheadLog, int busyTimeoutMilliseconds) {
        this(databaseFile, writeAheadLog, busyTimeoutMilliseconds, "NORMAL", 1000, true);
    }

    public SqliteClanRepository(
            Path databaseFile,
            boolean writeAheadLog,
            int busyTimeoutMilliseconds,
            String synchronousMode,
            int walAutoCheckpointPages,
            boolean optimizeOnClose
    ) {
        this.databaseFile = databaseFile;
        this.writeAheadLog = writeAheadLog;
        this.busyTimeoutMilliseconds = busyTimeoutMilliseconds;
        this.synchronousMode = synchronousMode;
        this.walAutoCheckpointPages = walAutoCheckpointPages;
        this.optimizeOnClose = optimizeOnClose;
    }

    @Override
    public void initialize() throws Exception {
        prepareStorage();
        connection = openConnection();
        configureConnection(connection);

        boolean existingSchema = tableExists("schema_meta");
        if (existingSchema) {
            int existingVersion = readSchemaVersion();
            if (existingVersion < 1 || existingVersion > SCHEMA_VERSION) {
                throw new SQLException("Unbekannte CatClans-Datenbankschema-Version");
            }
            if (existingVersion == 1) {
                migrateSchemaOneToTwo();
                existingVersion = 2;
            }
            if (existingVersion == 2) {
                migrateSchemaTwoToThree();
                existingVersion = 3;
            }
            if (existingVersion == 3) {
                migrateSchemaThreeToFour();
                existingVersion = 4;
            }
            if (existingVersion == 4) {
                migrateSchemaFourToFive();
                existingVersion = 5;
            }
            if (existingVersion == 5) {
                migrateSchemaFiveToSix();
                existingVersion = 6;
            }
            if (existingVersion == 6) {
                migrateSchemaSixToSeven();
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_meta (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO schema_meta(id, version)
                    VALUES (1, 7)
                    ON CONFLICT(id) DO NOTHING
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clans (
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
                    CREATE TABLE IF NOT EXISTS clan_members (
                        clan_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL UNIQUE,
                        last_known_name TEXT NOT NULL,
                        rank_id TEXT NOT NULL,
                        role_id TEXT NOT NULL,
                        joined_at TEXT NOT NULL,
                        PRIMARY KEY (clan_id, player_uuid),
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clan_invites (
                        clan_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        invited_by_uuid TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        expires_at TEXT NOT NULL,
                        PRIMARY KEY (clan_id, player_uuid),
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_clan_invites_player_expires
                    ON clan_invites(player_uuid, expires_at)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clan_roles (
                        clan_id TEXT NOT NULL,
                        role_id TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        priority INTEGER NOT NULL,
                        is_standard INTEGER NOT NULL CHECK (is_standard IN (0, 1)),
                        created_at TEXT NOT NULL,
                        PRIMARY KEY (clan_id, role_id),
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_clan_roles_name
                    ON clan_roles(clan_id, display_name COLLATE NOCASE)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clan_role_permissions (
                        clan_id TEXT NOT NULL,
                        role_id TEXT NOT NULL,
                        permission TEXT NOT NULL,
                        allowed INTEGER NOT NULL CHECK (allowed IN (0, 1)),
                        PRIMARY KEY (clan_id, role_id, permission),
                        FOREIGN KEY (clan_id, role_id)
                            REFERENCES clan_roles(clan_id, role_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clan_member_permissions (
                        clan_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        permission TEXT NOT NULL,
                        allowed INTEGER NOT NULL CHECK (allowed IN (0, 1)),
                        PRIMARY KEY (clan_id, player_uuid, permission),
                        FOREIGN KEY (clan_id, player_uuid)
                            REFERENCES clan_members(clan_id, player_uuid)
                            ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clan_role_limits (
                        clan_id TEXT PRIMARY KEY,
                        maximum_roles INTEGER NOT NULL,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            createProgressionAndVaultTables(statement);
            createDiplomacyTables(statement);
            createRankingTables(statement);
            createHomeTables(statement);
        }
        if (!existingSchema) {
            verifySchemaVersion();
        }
        try (PreparedStatement cleanup = connection.prepareStatement(
                "DELETE FROM clan_invites WHERE expires_at < ?")) {
            cleanup.setString(1, Instant.now().toString());
            cleanup.executeUpdate();
        }
        try (PreparedStatement cleanup = connection.prepareStatement(
                "DELETE FROM clan_diplomacy_requests WHERE expires_at < ?")) {
            cleanup.setString(1, Instant.now().toString());
            cleanup.executeUpdate();
        }
    }

    protected void prepareStorage() throws Exception {
        Files.createDirectories(databaseFile.getParent());
    }

    protected Connection openConnection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
    }

    protected void configureConnection(Connection configuredConnection) throws Exception {
        try (Statement statement = configuredConnection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = " + busyTimeoutMilliseconds);
            if (writeAheadLog) {
                statement.execute("PRAGMA journal_mode = WAL");
            }
            statement.execute("PRAGMA synchronous = " + synchronousMode);
            statement.execute("PRAGMA wal_autocheckpoint = " + walAutoCheckpointPages);
        }
    }

    protected Connection currentConnection() {
        return connection;
    }

    protected void lockClanPair(UUID firstClanId, UUID secondClanId) throws SQLException {
        // SQLite serialisiert Schreibtransaktionen bereits datenbankweit.
    }

    protected void lockBankAccount(UUID clanId) throws SQLException {
        // SQLite serialisiert Schreibtransaktionen bereits datenbankweit.
    }

    @Override
    public Optional<Clan> findById(UUID clanId) throws Exception {
        return findClanId("SELECT id FROM clans WHERE id = ?", clanId.toString());
    }

    @Override
    public Optional<Clan> findByMember(UUID playerId) throws Exception {
        return findClanId("SELECT clan_id FROM clan_members WHERE player_uuid = ?", playerId.toString());
    }

    @Override
    public Optional<Clan> findByNormalizedName(String normalizedName) throws Exception {
        return findClanId("SELECT id FROM clans WHERE normalized_name = ?", normalizedName);
    }

    @Override
    public Optional<Clan> findByNormalizedTag(String normalizedTag) throws Exception {
        return findClanId("SELECT id FROM clans WHERE normalized_tag = ?", normalizedTag);
    }

    @Override
    public Optional<Clan> findByNameOrTag(String normalizedSearch) throws Exception {
        return findClanId(
                "SELECT id FROM clans WHERE normalized_name = ? OR normalized_tag = ? LIMIT 1",
                normalizedSearch,
                normalizedSearch
        );
    }

    @Override
    public List<Clan> findAll() throws Exception {
        Map<UUID, List<ClanMember>> membersByClan = loadAllMembers();
        List<Clan> clans = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM clans ORDER BY normalized_name ASC");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                UUID clanId = UUID.fromString(results.getString("id"));
                clans.add(new Clan(
                        clanId,
                        results.getString("name"),
                        results.getString("normalized_name"),
                        results.getString("tag"),
                        results.getString("normalized_tag"),
                        results.getString("formatted_tag"),
                        UUID.fromString(results.getString("owner_uuid")),
                        JoinMode.valueOf(results.getString("join_mode")),
                        results.getInt("max_members"),
                        Instant.parse(results.getString("created_at")),
                        membersByClan.getOrDefault(clanId, List.of())
                ));
            }
        }
        return List.copyOf(clans);
    }

    @Override
    public void save(Clan clan) throws Exception {
        saveInternal(clan, null);
    }

    @Override
    public boolean deleteClan(UUID clanId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM clans WHERE id = ?")) {
            statement.setString(1, clanId.toString());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public void saveAndDeleteInvitesForPlayer(Clan clan, UUID playerId) throws Exception {
        saveInternal(clan, playerId);
    }

    private void saveInternal(Clan clan, UUID invitePlayerToClean) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int updatedClans;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE clans SET
                        name = ?,
                        normalized_name = ?,
                        tag = ?,
                        normalized_tag = ?,
                        formatted_tag = ?,
                        owner_uuid = ?,
                        join_mode = ?,
                        max_members = ?
                    WHERE id = ?
                    """)) {
                statement.setString(1, clan.name());
                statement.setString(2, clan.normalizedName());
                statement.setString(3, clan.tag());
                statement.setString(4, clan.normalizedTag());
                statement.setString(5, clan.formattedTag());
                statement.setString(6, clan.ownerId().toString());
                statement.setString(7, clan.joinMode().name());
                statement.setInt(8, clan.maxMembers());
                statement.setString(9, clan.id().toString());
                updatedClans = statement.executeUpdate();
            }
            if (updatedClans == 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO clans(
                            id, name, normalized_name, tag, normalized_tag, formatted_tag,
                            owner_uuid, join_mode, max_members, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setString(1, clan.id().toString());
                    statement.setString(2, clan.name());
                    statement.setString(3, clan.normalizedName());
                    statement.setString(4, clan.tag());
                    statement.setString(5, clan.normalizedTag());
                    statement.setString(6, clan.formattedTag());
                    statement.setString(7, clan.ownerId().toString());
                    statement.setString(8, clan.joinMode().name());
                    statement.setInt(9, clan.maxMembers());
                    statement.setString(10, clan.createdAt().toString());
                    statement.executeUpdate();
                }
            }

            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE clan_members SET
                        last_known_name = ?,
                        rank_id = ?,
                        role_id = ?,
                        joined_at = ?
                    WHERE clan_id = ? AND player_uuid = ?
                    """);
                 PreparedStatement insert = connection.prepareStatement("""
                         INSERT INTO clan_members(
                             clan_id, player_uuid, last_known_name, rank_id, role_id, joined_at
                         ) VALUES (?, ?, ?, ?, ?, ?)
                         """)) {
                for (ClanMember member : clan.members()) {
                    update.setString(1, member.lastKnownName());
                    update.setString(2, member.rank().name());
                    update.setString(3, member.roleId());
                    update.setString(4, member.joinedAt().toString());
                    update.setString(5, clan.id().toString());
                    update.setString(6, member.playerId().toString());
                    update.addBatch();
                }
                int[] updatedMembers = update.executeBatch();
                for (int index = 0; index < updatedMembers.length; index++) {
                    if (updatedMembers[index] != 0) {
                        continue;
                    }
                    ClanMember member = clan.members().get(index);
                    insert.setString(1, clan.id().toString());
                    insert.setString(2, member.playerId().toString());
                    insert.setString(3, member.lastKnownName());
                    insert.setString(4, member.rank().name());
                    insert.setString(5, member.roleId());
                    insert.setString(6, member.joinedAt().toString());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            String memberPlaceholders = String.join(
                    ", ",
                    java.util.Collections.nCopies(clan.members().size(), "?")
            );
            try (PreparedStatement deleteMissing = connection.prepareStatement(
                    "DELETE FROM clan_members WHERE clan_id = ? "
                            + "AND player_uuid NOT IN (" + memberPlaceholders + ")"
            )) {
                deleteMissing.setString(1, clan.id().toString());
                for (int index = 0; index < clan.members().size(); index++) {
                    deleteMissing.setString(
                            index + 2,
                            clan.members().get(index).playerId().toString()
                    );
                }
                deleteMissing.executeUpdate();
            }
            if (invitePlayerToClean != null) {
                try (PreparedStatement deleteInvites = connection.prepareStatement(
                        "DELETE FROM clan_invites WHERE player_uuid = ?")) {
                    deleteInvites.setString(1, invitePlayerToClean.toString());
                    deleteInvites.executeUpdate();
                }
            }
            ensureRankingStats(clan.id());
            connection.commit();
        } catch (Exception exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    @Override
    public Optional<ClanInvite> findInvite(UUID clanId, UUID playerId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT invited_by_uuid, created_at, expires_at
                FROM clan_invites
                WHERE clan_id = ? AND player_uuid = ?
                """)) {
            statement.setString(1, clanId.toString());
            statement.setString(2, playerId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ClanInvite(
                        clanId,
                        playerId,
                        UUID.fromString(results.getString("invited_by_uuid")),
                        Instant.parse(results.getString("created_at")),
                        Instant.parse(results.getString("expires_at"))
                ));
            }
        }
    }

    @Override
    public List<ClanInvite> findInvitesForPlayer(UUID playerId, Instant now) throws Exception {
        List<ClanInvite> invites = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT clan_id, invited_by_uuid, created_at, expires_at
                FROM clan_invites
                WHERE player_uuid = ? AND expires_at > ?
                ORDER BY created_at DESC
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, now.toString());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    invites.add(new ClanInvite(
                            UUID.fromString(results.getString("clan_id")),
                            playerId,
                            UUID.fromString(results.getString("invited_by_uuid")),
                            Instant.parse(results.getString("created_at")),
                            Instant.parse(results.getString("expires_at"))
                    ));
                }
            }
        }
        return List.copyOf(invites);
    }

    @Override
    public void saveInvite(ClanInvite invite) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_invites(
                    clan_id, player_uuid, invited_by_uuid, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(clan_id, player_uuid) DO UPDATE SET
                    invited_by_uuid = excluded.invited_by_uuid,
                    created_at = excluded.created_at,
                    expires_at = excluded.expires_at
                """)) {
            statement.setString(1, invite.clanId().toString());
            statement.setString(2, invite.playerId().toString());
            statement.setString(3, invite.invitedBy().toString());
            statement.setString(4, invite.createdAt().toString());
            statement.setString(5, invite.expiresAt().toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteInvite(UUID clanId, UUID playerId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM clan_invites WHERE clan_id = ? AND player_uuid = ?")) {
            statement.setString(1, clanId.toString());
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteInvitesForPlayer(UUID playerId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM clan_invites WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public Map<UUID, List<ClanRole>> findAllRoles() throws Exception {
        Map<UUID, List<ClanRole>> rolesByClan = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT clan_id, role_id, display_name, priority, is_standard
                FROM clan_roles
                ORDER BY clan_id, priority DESC, display_name COLLATE NOCASE
                """);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                UUID clanId = UUID.fromString(results.getString("clan_id"));
                rolesByClan.computeIfAbsent(clanId, ignored -> new ArrayList<>())
                        .add(mapRole(results, clanId));
            }
        }
        rolesByClan.replaceAll((ignored, roles) -> List.copyOf(roles));
        return Map.copyOf(rolesByClan);
    }

    @Override
    public List<ClanRole> findRoles(UUID clanId) throws Exception {
        List<ClanRole> roles = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT role_id, display_name, priority, is_standard
                FROM clan_roles
                WHERE clan_id = ?
                ORDER BY priority DESC, display_name COLLATE NOCASE
                """)) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    roles.add(mapRole(results, clanId));
                }
            }
        }
        return List.copyOf(roles);
    }

    @Override
    public void saveRole(ClanRole role) throws Exception {
        int updated;
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE clan_roles SET
                    display_name = ?,
                    priority = ?,
                    is_standard = ?
                WHERE clan_id = ? AND role_id = ?
                """)) {
            statement.setString(1, role.displayName());
            statement.setInt(2, role.priority());
            statement.setInt(3, role.standard() ? 1 : 0);
            statement.setString(4, role.clanId().toString());
            statement.setString(5, role.id());
            updated = statement.executeUpdate();
        }
        if (updated == 0) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO clan_roles(
                        clan_id, role_id, display_name, priority, is_standard, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, role.clanId().toString());
                statement.setString(2, role.id());
                statement.setString(3, role.displayName());
                statement.setInt(4, role.priority());
                statement.setInt(5, role.standard() ? 1 : 0);
                statement.setString(6, Instant.now().toString());
                statement.executeUpdate();
            }
        }
    }

    @Override
    public void deleteRole(UUID clanId, String roleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM clan_roles WHERE clan_id = ? AND role_id = ?")) {
            statement.setString(1, clanId.toString());
            statement.setString(2, roleId);
            statement.executeUpdate();
        }
    }

    @Override
    public Map<String, Boolean> findRolePermissions(UUID clanId, String roleId)
            throws Exception {
        return findPermissions(
                """
                SELECT permission, allowed
                FROM clan_role_permissions
                WHERE clan_id = ? AND role_id = ?
                """,
                clanId.toString(),
                roleId
        );
    }

    @Override
    public void setRolePermission(
            UUID clanId,
            String roleId,
            String permission,
            boolean allowed
    ) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_role_permissions(clan_id, role_id, permission, allowed)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(clan_id, role_id, permission) DO UPDATE SET
                    allowed = excluded.allowed
                """)) {
            statement.setString(1, clanId.toString());
            statement.setString(2, roleId);
            statement.setString(3, permission);
            statement.setInt(4, allowed ? 1 : 0);
            statement.executeUpdate();
        }
    }

    @Override
    public Map<String, Boolean> findMemberPermissions(UUID clanId, UUID playerId)
            throws Exception {
        return findPermissions(
                """
                SELECT permission, allowed
                FROM clan_member_permissions
                WHERE clan_id = ? AND player_uuid = ?
                """,
                clanId.toString(),
                playerId.toString()
        );
    }

    @Override
    public void setMemberPermission(
            UUID clanId,
            UUID playerId,
            String permission,
            Boolean allowed
    ) throws Exception {
        if (allowed == null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM clan_member_permissions
                    WHERE clan_id = ? AND player_uuid = ? AND permission = ?
                    """)) {
                statement.setString(1, clanId.toString());
                statement.setString(2, playerId.toString());
                statement.setString(3, permission);
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_member_permissions(
                    clan_id, player_uuid, permission, allowed
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT(clan_id, player_uuid, permission) DO UPDATE SET
                    allowed = excluded.allowed
                """)) {
            statement.setString(1, clanId.toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, permission);
            statement.setInt(4, allowed ? 1 : 0);
            statement.executeUpdate();
        }
    }

    @Override
    public int findRoleLimit(UUID clanId, int fallback) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT maximum_roles
                FROM clan_role_limits
                WHERE clan_id = ?
                """)) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getInt("maximum_roles") : fallback;
            }
        }
    }

    @Override
    public void saveRoleLimit(UUID clanId, int maximumRoles) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_role_limits(clan_id, maximum_roles)
                VALUES (?, ?)
                ON CONFLICT(clan_id) DO UPDATE SET maximum_roles = excluded.maximum_roles
                """)) {
            statement.setString(1, clanId.toString());
            statement.setInt(2, maximumRoles);
            statement.executeUpdate();
        }
    }

    @Override
    public Map<UUID, BattlepassProgress> findAllBattlepassProgress() throws Exception {
        Map<UUID, BattlepassProgress> progress = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT clan_id, level, current_xp, updated_at
                FROM clan_battlepass
                """);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                BattlepassProgress value = mapBattlepassProgress(results);
                progress.put(value.clanId(), value);
            }
        }
        return Map.copyOf(progress);
    }

    @Override
    public BattlepassProgress findBattlepassProgress(UUID clanId, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT clan_id, level, current_xp, updated_at
                FROM clan_battlepass
                WHERE clan_id = ?
                """)) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? mapBattlepassProgress(results)
                        : BattlepassProgress.initial(clanId, now);
            }
        }
    }

    @Override
    public void saveBattlepassProgress(BattlepassProgress progress) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_battlepass(clan_id, level, current_xp, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(clan_id) DO UPDATE SET
                    level = excluded.level,
                    current_xp = excluded.current_xp,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, progress.clanId().toString());
            statement.setInt(2, progress.level());
            statement.setString(3, progress.currentXp().toPlainString());
            statement.setString(4, progress.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<DailyLoginState> findDailyLoginState(UUID playerId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT last_login_date, streak_days
                FROM player_login_streaks
                WHERE player_uuid = ?
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(new DailyLoginState(
                        playerId,
                        LocalDate.parse(results.getString("last_login_date")),
                        results.getInt("streak_days")
                ));
            }
        }
    }

    @Override
    public void saveDailyLoginState(DailyLoginState state) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_login_streaks(player_uuid, last_login_date, streak_days)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    last_login_date = excluded.last_login_date,
                    streak_days = excluded.streak_days
                """)) {
            statement.setString(1, state.playerId().toString());
            statement.setString(2, state.lastLoginDate().toString());
            statement.setInt(3, state.streakDays());
            statement.executeUpdate();
        }
    }

    @Override
    public void saveDailyLoginAndBattlepass(
            DailyLoginState state,
            BattlepassProgress progress
    ) throws Exception {
        inTransaction(() -> {
            saveDailyLoginState(state);
            saveBattlepassProgress(progress);
        });
    }

    @Override
    public Optional<Instant> findPvpRewardTime(UUID victimId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT last_reward_at
                FROM battlepass_pvp_cooldowns
                WHERE victim_uuid = ?
                """)) {
            statement.setString(1, victimId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? Optional.of(Instant.parse(results.getString("last_reward_at")))
                        : Optional.empty();
            }
        }
    }

    @Override
    public void savePvpRewardAndBattlepass(
            UUID victimId,
            Instant rewardedAt,
            BattlepassProgress progress
    )
            throws Exception {
        inTransaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO battlepass_pvp_cooldowns(victim_uuid, last_reward_at)
                    VALUES (?, ?)
                    ON CONFLICT(victim_uuid) DO UPDATE SET
                        last_reward_at = excluded.last_reward_at
                    """)) {
                statement.setString(1, victimId.toString());
                statement.setString(2, rewardedAt.toString());
                statement.executeUpdate();
            }
            saveBattlepassProgress(progress);
        });
    }

    @Override
    public List<BattlepassReward> findBattlepassRewards(int fromLevel, int toLevel)
            throws Exception {
        List<BattlepassReward> rewards = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT level, reward_type, amount, created_by_uuid, created_at
                FROM battlepass_rewards
                WHERE level BETWEEN ? AND ?
                ORDER BY level, reward_type
                """)) {
            statement.setInt(1, fromLevel);
            statement.setInt(2, toLevel);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    rewards.add(new BattlepassReward(
                            results.getInt("level"),
                            BattlepassRewardType.valueOf(results.getString("reward_type")),
                            results.getInt("amount"),
                            UUID.fromString(results.getString("created_by_uuid")),
                            Instant.parse(results.getString("created_at"))
                    ));
                }
            }
        }
        return List.copyOf(rewards);
    }

    @Override
    public void saveBattlepassReward(BattlepassReward reward) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO battlepass_rewards(
                    level, reward_type, amount, created_by_uuid, created_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(level, reward_type) DO UPDATE SET
                    amount = excluded.amount,
                    created_by_uuid = excluded.created_by_uuid,
                    created_at = excluded.created_at
                """)) {
            statement.setInt(1, reward.level());
            statement.setString(2, reward.type().name());
            statement.setInt(3, reward.amount());
            statement.setString(4, reward.createdBy().toString());
            statement.setString(5, reward.createdAt().toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteBattlepassReward(int level, BattlepassRewardType type)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM battlepass_rewards
                WHERE level = ? AND reward_type = ?
                """)) {
            statement.setInt(1, level);
            statement.setString(2, type.name());
            statement.executeUpdate();
        }
    }

    @Override
    public Set<String> findClaimedRewardKeys(UUID clanId, int fromLevel, int toLevel)
            throws Exception {
        Set<String> claimed = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT level, reward_type
                FROM battlepass_reward_claims
                WHERE clan_id = ? AND level BETWEEN ? AND ?
                """)) {
            statement.setString(1, clanId.toString());
            statement.setInt(2, fromLevel);
            statement.setInt(3, toLevel);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    claimed.add(results.getInt("level") + ":"
                            + results.getString("reward_type"));
                }
            }
        }
        return Set.copyOf(claimed);
    }

    @Override
    public RewardClaimResult claimBattlepassReward(
            UUID clanId,
            UUID ownerId,
            BattlepassReward reward,
            int absoluteMaxMembers,
            int absoluteMaxRoles,
            int absoluteMaxVaultPages,
            int absoluteMaxBonusHomeSlots,
            int defaultMaxRoles
    ) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int maximumMembers = findMaximumMembers(clanId);
            int maximumRoles = findRoleLimit(clanId, defaultMaxRoles);
            ClanUnlocks unlocks = findClanUnlocks(clanId);

            switch (reward.type()) {
                case MEMBER_SLOTS -> ensureWithinLimit(
                        maximumMembers, reward.amount(), absoluteMaxMembers
                );
                case ROLE_SLOTS -> ensureWithinLimit(
                        maximumRoles, reward.amount(), absoluteMaxRoles
                );
                case VAULT_PAGES -> ensureWithinLimit(
                        unlocks.vaultPages(), reward.amount(), absoluteMaxVaultPages
                );
                case HOME_SLOTS -> ensureWithinLimit(
                        unlocks.bonusHomeSlots(),
                        reward.amount(),
                        absoluteMaxBonusHomeSlots
                );
            }

            if (!insertRewardClaim(clanId, ownerId, reward)) {
                connection.rollback();
                return new RewardClaimResult(false, maximumMembers, maximumRoles, unlocks);
            }

            switch (reward.type()) {
                case MEMBER_SLOTS -> {
                    maximumMembers += reward.amount();
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE clans SET max_members = ? WHERE id = ?
                            """)) {
                        statement.setInt(1, maximumMembers);
                        statement.setString(2, clanId.toString());
                        statement.executeUpdate();
                    }
                }
                case ROLE_SLOTS -> {
                    maximumRoles += reward.amount();
                    saveRoleLimit(clanId, maximumRoles);
                }
                case VAULT_PAGES -> {
                    unlocks = new ClanUnlocks(
                            unlocks.bonusHomeSlots(),
                            unlocks.vaultPages() + reward.amount()
                    );
                    saveClanUnlocks(clanId, unlocks);
                }
                case HOME_SLOTS -> {
                    unlocks = new ClanUnlocks(
                            unlocks.bonusHomeSlots() + reward.amount(),
                            unlocks.vaultPages()
                    );
                    saveClanUnlocks(clanId, unlocks);
                }
            }
            connection.commit();
            return new RewardClaimResult(true, maximumMembers, maximumRoles, unlocks);
        } catch (Exception exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    @Override
    public ClanUnlocks findClanUnlocks(UUID clanId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bonus_home_slots, vault_pages
                FROM clan_unlocks
                WHERE clan_id = ?
                """)) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? new ClanUnlocks(
                                results.getInt("bonus_home_slots"),
                                results.getInt("vault_pages")
                        )
                        : new ClanUnlocks(0, 1);
            }
        }
    }

    @Override
    public Map<UUID, ClanRankingStats> findAllRankingStats() throws Exception {
        Map<UUID, ClanRankingStats> stats = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    ranking.clan_id,
                    ranking.combat_kills,
                    ranking.wars_won,
                    ranking.wars_lost,
                    ranking.active_days,
                    ranking.last_active_date,
                    bank.balance
                FROM clan_ranking_stats ranking
                LEFT JOIN clan_bank_accounts bank ON bank.clan_id = ranking.clan_id
                """);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                UUID clanId = UUID.fromString(results.getString("clan_id"));
                String lastActiveDate = results.getString("last_active_date");
                String balance = results.getString("balance");
                stats.put(clanId, new ClanRankingStats(
                        clanId,
                        results.getLong("combat_kills"),
                        results.getInt("wars_won"),
                        results.getInt("wars_lost"),
                        results.getLong("active_days"),
                        lastActiveDate == null ? null : LocalDate.parse(lastActiveDate),
                        balance == null ? BigDecimal.ZERO : new BigDecimal(balance)
                ));
            }
        }
        return Map.copyOf(stats);
    }

    @Override
    public boolean recordDailyRankingActivity(UUID clanId, LocalDate activityDate)
            throws Exception {
        ensureRankingStats(clanId);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE clan_ranking_stats
                SET active_days = active_days + 1,
                    last_active_date = ?
                WHERE clan_id = ?
                  AND (last_active_date IS NULL OR last_active_date < ?)
                """)) {
            statement.setString(1, activityDate.toString());
            statement.setString(2, clanId.toString());
            statement.setString(3, activityDate.toString());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public RankingKillResult recordRankingKill(
            UUID killerClanId,
            UUID victimClanId,
            UUID victimId,
            Instant occurredAt,
            Instant cooldownCutoff
    ) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String first = canonicalFirst(killerClanId, victimClanId);
            String second = canonicalSecond(killerClanId, victimClanId);
            boolean allied;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT 1
                    FROM clan_alliances
                    WHERE first_clan_id = ? AND second_clan_id = ?
                    """)) {
                statement.setString(1, first);
                statement.setString(2, second);
                try (ResultSet results = statement.executeQuery()) {
                    allied = results.next();
                }
            }

            boolean warDeathRecorded = false;
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT id, first_clan_id
                    FROM clan_wars
                    WHERE first_clan_id = ?
                      AND second_clan_id = ?
                      AND ends_at > ?
                      AND result_processed = 0
                    LIMIT 1
                    """)) {
                select.setString(1, first);
                select.setString(2, second);
                select.setString(3, occurredAt.toString());
                try (ResultSet results = select.executeQuery()) {
                    if (results.next()) {
                        boolean victimIsFirst = victimClanId.toString()
                                .equals(results.getString("first_clan_id"));
                        String column = victimIsFirst ? "first_deaths" : "second_deaths";
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE clan_wars SET " + column + " = " + column
                                        + " + 1 WHERE id = ? AND result_processed = 0"
                        )) {
                            update.setString(1, results.getString("id"));
                            warDeathRecorded = update.executeUpdate() == 1;
                        }
                    }
                }
            }

            boolean combatPointAwarded = false;
            if (!allied) {
                int claimed;
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE ranking_pvp_cooldowns
                        SET last_reward_at = ?
                        WHERE victim_uuid = ? AND last_reward_at <= ?
                        """)) {
                    update.setString(1, occurredAt.toString());
                    update.setString(2, victimId.toString());
                    update.setString(3, cooldownCutoff.toString());
                    claimed = update.executeUpdate();
                }
                if (claimed == 0) {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO ranking_pvp_cooldowns(victim_uuid, last_reward_at)
                            VALUES (?, ?)
                            ON CONFLICT(victim_uuid) DO NOTHING
                            """)) {
                        insert.setString(1, victimId.toString());
                        insert.setString(2, occurredAt.toString());
                        claimed = insert.executeUpdate();
                    }
                }
                if (claimed == 1) {
                    ensureRankingStats(killerClanId);
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE clan_ranking_stats
                            SET combat_kills = combat_kills + 1
                            WHERE clan_id = ?
                            """)) {
                        update.setString(1, killerClanId.toString());
                        update.executeUpdate();
                    }
                    combatPointAwarded = true;
                }
            }
            connection.commit();
            return new RankingKillResult(combatPointAwarded, warDeathRecorded);
        } catch (Exception exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    @Override
    public List<ClanWarResult> finalizeExpiredWars(Instant now) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        List<ClanWarResult> finalized = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT
                    id,
                    first_clan_id,
                    second_clan_id,
                    first_deaths,
                    second_deaths
                FROM clan_wars
                WHERE ends_at <= ? AND result_processed = 0
                ORDER BY ends_at
                """)) {
            select.setString(1, now.toString());
            try (ResultSet results = select.executeQuery()) {
                while (results.next()) {
                    UUID warId = UUID.fromString(results.getString("id"));
                    UUID firstClanId = UUID.fromString(results.getString("first_clan_id"));
                    UUID secondClanId = UUID.fromString(results.getString("second_clan_id"));
                    int firstDeaths = results.getInt("first_deaths");
                    int secondDeaths = results.getInt("second_deaths");
                    UUID winner = null;
                    UUID loser = null;
                    if (firstDeaths < secondDeaths) {
                        winner = firstClanId;
                        loser = secondClanId;
                    } else if (secondDeaths < firstDeaths) {
                        winner = secondClanId;
                        loser = firstClanId;
                    }
                    int updated;
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE clan_wars
                            SET result_processed = 1,
                                winner_clan_id = ?,
                                loser_clan_id = ?,
                                completed_at = ?
                            WHERE id = ? AND result_processed = 0 AND ends_at <= ?
                            """)) {
                        update.setString(1, winner == null ? null : winner.toString());
                        update.setString(2, loser == null ? null : loser.toString());
                        update.setString(3, now.toString());
                        update.setString(4, warId.toString());
                        update.setString(5, now.toString());
                        updated = update.executeUpdate();
                    }
                    if (updated != 1) {
                        continue;
                    }
                    if (winner != null) {
                        ensureRankingStats(winner);
                        ensureRankingStats(loser);
                        incrementWarResult(winner, "wars_won");
                        incrementWarResult(loser, "wars_lost");
                    }
                    finalized.add(new ClanWarResult(
                            warId,
                            firstClanId,
                            secondClanId,
                            firstDeaths,
                            secondDeaths,
                            winner,
                            loser
                    ));
                }
            }
            connection.commit();
            return List.copyOf(finalized);
        } catch (Exception exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    @Override
    public Optional<ClanWarResult> endActiveWar(
            UUID firstClanId,
            UUID secondClanId,
            Instant endedAt
    ) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            lockClanPair(firstClanId, secondClanId);
            String first = canonicalFirst(firstClanId, secondClanId);
            String second = canonicalSecond(firstClanId, secondClanId);
            ClanWarResult result = null;
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT id, first_deaths, second_deaths
                    FROM clan_wars
                    WHERE first_clan_id = ?
                      AND second_clan_id = ?
                      AND ends_at > ?
                      AND result_processed = 0
                    LIMIT 1
                    """)) {
                select.setString(1, first);
                select.setString(2, second);
                select.setString(3, endedAt.toString());
                try (ResultSet results = select.executeQuery()) {
                    if (results.next()) {
                        UUID canonicalFirst = UUID.fromString(first);
                        UUID canonicalSecond = UUID.fromString(second);
                        int firstDeaths = results.getInt("first_deaths");
                        int secondDeaths = results.getInt("second_deaths");
                        UUID winner = null;
                        UUID loser = null;
                        if (firstDeaths < secondDeaths) {
                            winner = canonicalFirst;
                            loser = canonicalSecond;
                        } else if (secondDeaths < firstDeaths) {
                            winner = canonicalSecond;
                            loser = canonicalFirst;
                        }
                        result = new ClanWarResult(
                                UUID.fromString(results.getString("id")),
                                canonicalFirst,
                                canonicalSecond,
                                firstDeaths,
                                secondDeaths,
                                winner,
                                loser
                        );
                    }
                }
            }
            if (result == null) {
                connection.commit();
                return Optional.empty();
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE clan_wars
                    SET ends_at = ?,
                        result_processed = 1,
                        winner_clan_id = ?,
                        loser_clan_id = ?,
                        completed_at = ?
                    WHERE id = ?
                      AND result_processed = 0
                      AND ends_at > ?
                    """)) {
                update.setString(1, endedAt.toString());
                update.setString(2, result.winnerClanId() == null
                        ? null : result.winnerClanId().toString());
                update.setString(3, result.loserClanId() == null
                        ? null : result.loserClanId().toString());
                update.setString(4, endedAt.toString());
                update.setString(5, result.warId().toString());
                update.setString(6, endedAt.toString());
                if (update.executeUpdate() != 1) {
                    connection.commit();
                    return Optional.empty();
                }
            }
            if (!result.draw()) {
                ensureRankingStats(result.winnerClanId());
                ensureRankingStats(result.loserClanId());
                incrementWarResult(result.winnerClanId(), "wars_won");
                incrementWarResult(result.loserClanId(), "wars_lost");
            }
            connection.commit();
            return Optional.of(result);
        } catch (Exception exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    @Override
    public Map<Integer, byte[]> findVaultPage(UUID clanId, int page) throws Exception {
        Map<Integer, byte[]> items = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT slot, item_data
                FROM clan_vault_items
                WHERE clan_id = ? AND page_number = ?
                ORDER BY slot
                """)) {
            statement.setString(1, clanId.toString());
            statement.setInt(2, page);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    items.put(results.getInt("slot"), results.getBytes("item_data"));
                }
            }
        }
        return Map.copyOf(items);
    }

    @Override
    public void saveVaultSlot(UUID clanId, int page, int slot, byte[] itemData)
            throws Exception {
        if (page < 1 || slot < 0 || slot >= 45) {
            throw new IllegalArgumentException("Invalid vault page or slot");
        }
        if (itemData == null || itemData.length == 0) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM clan_vault_items
                    WHERE clan_id = ? AND page_number = ? AND slot = ?
                    """)) {
                statement.setString(1, clanId.toString());
                statement.setInt(2, page);
                statement.setInt(3, slot);
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_vault_items(
                    clan_id, page_number, slot, item_data, updated_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(clan_id, page_number, slot) DO UPDATE SET
                    item_data = excluded.item_data,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, clanId.toString());
            statement.setInt(2, page);
            statement.setInt(3, slot);
            statement.setBytes(4, itemData);
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    @Override
    public BigDecimal findBankBalance(UUID clanId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT balance
                FROM clan_bank_accounts
                WHERE clan_id = ?
                """)) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? new BigDecimal(results.getString("balance"))
                        : BigDecimal.ZERO;
            }
        }
    }

    @Override
    public Optional<BigDecimal> depositBank(
            UUID clanId,
            BigDecimal amount,
            Instant updatedAt
    ) throws Exception {
        return changeBankBalance(clanId, amount, updatedAt);
    }

    @Override
    public Optional<BigDecimal> withdrawBank(
            UUID clanId,
            BigDecimal amount,
            Instant updatedAt
    ) throws Exception {
        return changeBankBalance(clanId, amount.negate(), updatedAt);
    }

    @Override
    public BigDecimal restoreBankBalance(
            UUID clanId,
            BigDecimal amount,
            Instant updatedAt
    ) throws Exception {
        return changeBankBalance(clanId, amount, updatedAt).orElseThrow();
    }

    @Override
    public List<ClanHome> findHomes(UUID clanId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT clan_id, home_number, world_uuid, world_name,
                       x, y, z, yaw, pitch, updated_by_uuid, updated_at
                FROM clan_homes
                WHERE clan_id = ?
                ORDER BY home_number
                """)) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                List<ClanHome> homes = new ArrayList<>();
                while (results.next()) {
                    homes.add(mapClanHome(results));
                }
                return List.copyOf(homes);
            }
        }
    }

    @Override
    public Optional<ClanHome> findHome(UUID clanId, int number) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT clan_id, home_number, world_uuid, world_name,
                       x, y, z, yaw, pitch, updated_by_uuid, updated_at
                FROM clan_homes
                WHERE clan_id = ? AND home_number = ?
                """)) {
            statement.setString(1, clanId.toString());
            statement.setInt(2, number);
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? Optional.of(mapClanHome(results))
                        : Optional.empty();
            }
        }
    }

    @Override
    public void saveHome(ClanHome home) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_homes(
                    clan_id, home_number, world_uuid, world_name,
                    x, y, z, yaw, pitch, updated_by_uuid, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(clan_id, home_number) DO UPDATE SET
                    world_uuid = excluded.world_uuid,
                    world_name = excluded.world_name,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    yaw = excluded.yaw,
                    pitch = excluded.pitch,
                    updated_by_uuid = excluded.updated_by_uuid,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, home.clanId().toString());
            statement.setInt(2, home.number());
            statement.setString(3, home.worldId().toString());
            statement.setString(4, home.worldName());
            statement.setString(5, Double.toString(home.x()));
            statement.setString(6, Double.toString(home.y()));
            statement.setString(7, Double.toString(home.z()));
            statement.setString(8, Float.toString(home.yaw()));
            statement.setString(9, Float.toString(home.pitch()));
            statement.setString(10, home.updatedBy().toString());
            statement.setString(11, home.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    @Override
    public boolean deleteHome(UUID clanId, int number) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM clan_homes
                WHERE clan_id = ? AND home_number = ?
                """)) {
            statement.setString(1, clanId.toString());
            statement.setInt(2, number);
            return statement.executeUpdate() == 1;
        }
    }

    private Optional<BigDecimal> changeBankBalance(
            UUID clanId,
            BigDecimal change,
            Instant updatedAt
    ) throws Exception {
        if (change.signum() == 0) {
            throw new IllegalArgumentException("Bank mutation must not be null");
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            ensureBankAccount(clanId, updatedAt);
            lockBankAccount(clanId);
            BigDecimal current = findBankBalance(clanId);
            BigDecimal updated = current.add(change);
            if (updated.signum() < 0) {
                connection.rollback();
                return Optional.empty();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE clan_bank_accounts
                    SET balance = ?, updated_at = ?
                    WHERE clan_id = ?
                    """)) {
                statement.setString(1, updated.stripTrailingZeros().toPlainString());
                statement.setString(2, updatedAt.toString());
                statement.setString(3, clanId.toString());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Clan bank account could not be updated");
                }
            }
            connection.commit();
            return Optional.of(updated);
        } catch (Exception exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static ClanHome mapClanHome(ResultSet results) throws SQLException {
        return new ClanHome(
                UUID.fromString(results.getString("clan_id")),
                results.getInt("home_number"),
                UUID.fromString(results.getString("world_uuid")),
                results.getString("world_name"),
                Double.parseDouble(results.getString("x")),
                Double.parseDouble(results.getString("y")),
                Double.parseDouble(results.getString("z")),
                Float.parseFloat(results.getString("yaw")),
                Float.parseFloat(results.getString("pitch")),
                UUID.fromString(results.getString("updated_by_uuid")),
                Instant.parse(results.getString("updated_at"))
        );
    }

    private void ensureBankAccount(UUID clanId, Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_bank_accounts(clan_id, balance, updated_at)
                VALUES (?, '0', ?)
                ON CONFLICT(clan_id) DO NOTHING
                """)) {
            statement.setString(1, clanId.toString());
            statement.setString(2, updatedAt.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public DiplomacyView findDiplomacyView(
            UUID viewerClanId,
            UUID targetClanId,
            Instant now
    ) throws Exception {
        String first = canonicalFirst(viewerClanId, targetClanId);
        String second = canonicalSecond(viewerClanId, targetClanId);
        boolean allied;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM clan_alliances
                WHERE first_clan_id = ? AND second_clan_id = ?
                """)) {
            statement.setString(1, first);
            statement.setString(2, second);
            try (ResultSet results = statement.executeQuery()) {
                allied = results.next();
            }
        }

        Optional<ClanWar> activeWar = Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, duration_hours, started_at, ends_at, accepted_by_uuid
                FROM clan_wars
                WHERE first_clan_id = ?
                  AND second_clan_id = ?
                  AND ends_at > ?
                  AND result_processed = 0
                ORDER BY ends_at DESC
                LIMIT 1
                """)) {
            statement.setString(1, first);
            statement.setString(2, second);
            statement.setString(3, now.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    activeWar = Optional.of(new ClanWar(
                            UUID.fromString(results.getString("id")),
                            UUID.fromString(first),
                            UUID.fromString(second),
                            results.getInt("duration_hours"),
                            Instant.parse(results.getString("started_at")),
                            Instant.parse(results.getString("ends_at")),
                            UUID.fromString(results.getString("accepted_by_uuid"))
                    ));
                }
            }
        }

        Optional<DiplomacyRequest> incomingAlly = Optional.empty();
        Optional<DiplomacyRequest> outgoingAlly = Optional.empty();
        Optional<DiplomacyRequest> incomingWar = Optional.empty();
        Optional<DiplomacyRequest> outgoingWar = Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM clan_diplomacy_requests
                WHERE expires_at > ?
                  AND (
                    (source_clan_id = ? AND target_clan_id = ?)
                    OR (source_clan_id = ? AND target_clan_id = ?)
                  )
                ORDER BY created_at DESC
                """)) {
            statement.setString(1, now.toString());
            statement.setString(2, viewerClanId.toString());
            statement.setString(3, targetClanId.toString());
            statement.setString(4, targetClanId.toString());
            statement.setString(5, viewerClanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    DiplomacyRequest request = mapDiplomacyRequest(results);
                    boolean incoming = request.targetClanId().equals(viewerClanId);
                    if (request.type() == DiplomacyType.ALLY) {
                        if (incoming && incomingAlly.isEmpty()) {
                            incomingAlly = Optional.of(request);
                        } else if (!incoming && outgoingAlly.isEmpty()) {
                            outgoingAlly = Optional.of(request);
                        }
                    } else if (incoming && incomingWar.isEmpty()) {
                        incomingWar = Optional.of(request);
                    } else if (!incoming && outgoingWar.isEmpty()) {
                        outgoingWar = Optional.of(request);
                    }
                }
            }
        }
        return new DiplomacyView(
                allied,
                activeWar,
                incomingAlly,
                outgoingAlly,
                incomingWar,
                outgoingWar
        );
    }

    @Override
    public Optional<DiplomacyRequest> findDiplomacyRequest(UUID requestId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM clan_diplomacy_requests WHERE id = ?
                """)) {
            statement.setString(1, requestId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? Optional.of(mapDiplomacyRequest(results))
                        : Optional.empty();
            }
        }
    }

    @Override
    public List<DiplomacyRequest> findIncomingDiplomacyRequests(
            UUID targetClanId,
            DiplomacyType type,
            Instant now
    ) throws Exception {
        List<DiplomacyRequest> requests = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM clan_diplomacy_requests
                WHERE target_clan_id = ?
                  AND request_type = ?
                  AND expires_at > ?
                ORDER BY created_at DESC
                """)) {
            statement.setString(1, targetClanId.toString());
            statement.setString(2, type.name());
            statement.setString(3, now.toString());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    requests.add(mapDiplomacyRequest(results));
                }
            }
        }
        return List.copyOf(requests);
    }

    @Override
    public int countPendingDiplomacyRequests(UUID clanId, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM clan_diplomacy_requests
                WHERE source_clan_id = ? AND expires_at > ?
                """)) {
            statement.setString(1, clanId.toString());
            statement.setString(2, now.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getInt(1) : 0;
            }
        }
    }

    @Override
    public boolean saveDiplomacyRequest(DiplomacyRequest request) throws Exception {
        String first = canonicalFirst(request.sourceClanId(), request.targetClanId());
        String second = canonicalSecond(request.sourceClanId(), request.targetClanId());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_diplomacy_requests(
                    id, source_clan_id, target_clan_id, first_clan_id, second_clan_id,
                    request_type,
                    war_duration_hours, requested_by_uuid, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(first_clan_id, second_clan_id, request_type) DO NOTHING
                """)) {
            statement.setString(1, request.id().toString());
            statement.setString(2, request.sourceClanId().toString());
            statement.setString(3, request.targetClanId().toString());
            statement.setString(4, first);
            statement.setString(5, second);
            statement.setString(6, request.type().name());
            statement.setInt(7, request.warDurationHours());
            statement.setString(8, request.requestedBy().toString());
            statement.setString(9, request.createdAt().toString());
            statement.setString(10, request.expiresAt().toString());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public void declineDiplomacyRequest(UUID requestId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM clan_diplomacy_requests WHERE id = ?")) {
            statement.setString(1, requestId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void acceptDiplomacyRequest(
            DiplomacyRequest request,
            UUID acceptedBy,
            Instant acceptedAt
    ) throws Exception {
        inTransaction(() -> {
            String first = canonicalFirst(request.sourceClanId(), request.targetClanId());
            String second = canonicalSecond(request.sourceClanId(), request.targetClanId());
            lockClanPair(request.sourceClanId(), request.targetClanId());
            DiplomacyView current = findDiplomacyView(
                    request.targetClanId(),
                    request.sourceClanId(),
                    acceptedAt
            );
            if (current.allied() || current.activeWar().isPresent()) {
                throw new SQLException("An active relation already exists between the clans");
            }
            if (request.type() == DiplomacyType.ALLY) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO clan_alliances(
                            first_clan_id, second_clan_id, established_at, accepted_by_uuid
                        ) VALUES (?, ?, ?, ?)
                        ON CONFLICT(first_clan_id, second_clan_id) DO UPDATE SET
                            established_at = excluded.established_at,
                            accepted_by_uuid = excluded.accepted_by_uuid
                        """)) {
                    statement.setString(1, first);
                    statement.setString(2, second);
                    statement.setString(3, acceptedAt.toString());
                    statement.setString(4, acceptedBy.toString());
                    statement.executeUpdate();
                }
            } else {
                String endsAt = acceptedAt
                        .plusSeconds(request.warDurationHours() * 3600L)
                        .toString();
                int updated;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE clan_wars SET
                            id = ?,
                            duration_hours = ?,
                            started_at = ?,
                            ends_at = ?,
                            accepted_by_uuid = ?,
                            first_deaths = 0,
                            second_deaths = 0,
                            result_processed = 0,
                            winner_clan_id = NULL,
                            loser_clan_id = NULL,
                            completed_at = NULL
                        WHERE first_clan_id = ? AND second_clan_id = ?
                        """)) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setInt(2, request.warDurationHours());
                    statement.setString(3, acceptedAt.toString());
                    statement.setString(4, endsAt);
                    statement.setString(5, acceptedBy.toString());
                    statement.setString(6, first);
                    statement.setString(7, second);
                    updated = statement.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO clan_wars(
                                id, first_clan_id, second_clan_id, duration_hours,
                                started_at, ends_at, accepted_by_uuid
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        statement.setString(1, UUID.randomUUID().toString());
                        statement.setString(2, first);
                        statement.setString(3, second);
                        statement.setInt(4, request.warDurationHours());
                        statement.setString(5, acceptedAt.toString());
                        statement.setString(6, endsAt);
                        statement.setString(7, acceptedBy.toString());
                        statement.executeUpdate();
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM clan_diplomacy_requests
                    WHERE request_type = ?
                      AND (
                        (source_clan_id = ? AND target_clan_id = ?)
                        OR (source_clan_id = ? AND target_clan_id = ?)
                      )
                    """)) {
                statement.setString(1, request.type().name());
                statement.setString(2, request.sourceClanId().toString());
                statement.setString(3, request.targetClanId().toString());
                statement.setString(4, request.targetClanId().toString());
                statement.setString(5, request.sourceClanId().toString());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            if (optimizeOnClose) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA optimize");
                }
            }
            connection.close();
        }
    }

    private Optional<Clan> findClanId(String sql, String... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return loadClan(UUID.fromString(results.getString(1)));
            }
        }
    }

    private Optional<Clan> loadClan(UUID clanId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM clans WHERE id = ?")) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Clan(
                        clanId,
                        results.getString("name"),
                        results.getString("normalized_name"),
                        results.getString("tag"),
                        results.getString("normalized_tag"),
                        results.getString("formatted_tag"),
                        UUID.fromString(results.getString("owner_uuid")),
                        JoinMode.valueOf(results.getString("join_mode")),
                        results.getInt("max_members"),
                        Instant.parse(results.getString("created_at")),
                        loadMembers(clanId)
                ));
            }
        }
    }

    private List<ClanMember> loadMembers(UUID clanId) throws Exception {
        List<ClanMember> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid, last_known_name, rank_id, role_id, joined_at
                FROM clan_members
                WHERE clan_id = ?
                ORDER BY
                    CASE rank_id
                        WHEN 'OWNER' THEN 1
                        WHEN 'CO_OWNER' THEN 2
                        WHEN 'MODERATOR' THEN 3
                        WHEN 'MEMBER' THEN 4
                        ELSE 5
                    END,
                    joined_at ASC
                """)) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    members.add(new ClanMember(
                            UUID.fromString(results.getString("player_uuid")),
                            results.getString("last_known_name"),
                            RankId.fromStorage(results.getString("rank_id")),
                            results.getString("role_id"),
                            Instant.parse(results.getString("joined_at"))
                    ));
                }
            }
        }
        return List.copyOf(members);
    }

    private Map<UUID, List<ClanMember>> loadAllMembers() throws Exception {
        Map<UUID, List<ClanMember>> membersByClan = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT clan_id, player_uuid, last_known_name, rank_id, role_id, joined_at
                FROM clan_members
                ORDER BY
                    clan_id,
                    CASE rank_id
                        WHEN 'OWNER' THEN 1
                        WHEN 'CO_OWNER' THEN 2
                        WHEN 'MODERATOR' THEN 3
                        WHEN 'MEMBER' THEN 4
                        ELSE 5
                    END,
                    joined_at ASC
                """);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                UUID clanId = UUID.fromString(results.getString("clan_id"));
                membersByClan.computeIfAbsent(clanId, ignored -> new ArrayList<>())
                        .add(new ClanMember(
                                UUID.fromString(results.getString("player_uuid")),
                                results.getString("last_known_name"),
                                RankId.fromStorage(results.getString("rank_id")),
                                results.getString("role_id"),
                                Instant.parse(results.getString("joined_at"))
                        ));
            }
        }
        membersByClan.replaceAll((ignored, members) -> List.copyOf(members));
        return membersByClan;
    }

    private int readSchemaVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT version FROM schema_meta WHERE id = 1")) {
            if (!results.next()) {
                throw new SQLException("Unbekannte CatClans-Datenbankschema-Version");
            }
            return results.getInt("version");
        }
    }

    private void verifySchemaVersion() throws SQLException {
        if (readSchemaVersion() != SCHEMA_VERSION) {
            throw new SQLException("Unbekannte CatClans-Datenbankschema-Version");
        }
    }

    private void migrateSchemaOneToTwo() throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE clan_members
                    ADD COLUMN role_id TEXT NOT NULL DEFAULT 'recruit'
                    """);
            statement.execute("""
                    UPDATE clan_members
                    SET role_id = CASE rank_id
                        WHEN 'OWNER' THEN 'owner'
                        WHEN 'CO_OWNER' THEN 'co-owner'
                        WHEN 'MODERATOR' THEN 'moderator'
                        WHEN 'MEMBER' THEN 'member'
                        ELSE 'recruit'
                    END
                    """);
            statement.execute("UPDATE schema_meta SET version = 2 WHERE id = 1");
            connection.commit();
        } catch (SQLException exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void migrateSchemaTwoToThree() throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE clans
                    ADD COLUMN formatted_tag TEXT NOT NULL DEFAULT ''
                    """);
            statement.execute("""
                    UPDATE clans
                    SET formatted_tag = tag
                    WHERE formatted_tag = ''
                    """);
            statement.execute("UPDATE schema_meta SET version = 3 WHERE id = 1");
            connection.commit();
        } catch (SQLException exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void migrateSchemaThreeToFour() throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            createProgressionAndVaultTables(statement);
            statement.execute("UPDATE schema_meta SET version = 4 WHERE id = 1");
            connection.commit();
        } catch (SQLException exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void migrateSchemaFourToFive() throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            createDiplomacyTables(statement);
            statement.execute("UPDATE schema_meta SET version = 5 WHERE id = 1");
            connection.commit();
        } catch (SQLException exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void migrateSchemaFiveToSix() throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            addColumnIfMissing(
                    statement,
                    "clan_wars",
                    "first_deaths",
                    "INTEGER NOT NULL DEFAULT 0"
            );
            addColumnIfMissing(
                    statement,
                    "clan_wars",
                    "second_deaths",
                    "INTEGER NOT NULL DEFAULT 0"
            );
            addColumnIfMissing(
                    statement,
                    "clan_wars",
                    "result_processed",
                    "INTEGER NOT NULL DEFAULT 0"
            );
            addColumnIfMissing(statement, "clan_wars", "winner_clan_id", "TEXT");
            addColumnIfMissing(statement, "clan_wars", "loser_clan_id", "TEXT");
            addColumnIfMissing(statement, "clan_wars", "completed_at", "TEXT");
            createRankingTables(statement);
            statement.execute("UPDATE schema_meta SET version = 6 WHERE id = 1");
            connection.commit();
        } catch (SQLException exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void migrateSchemaSixToSeven() throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            createHomeTables(statement);
            statement.execute("UPDATE schema_meta SET version = 7 WHERE id = 1");
            connection.commit();
        } catch (SQLException exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void createProgressionAndVaultTables(Statement statement)
            throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS clan_battlepass (
                    clan_id TEXT PRIMARY KEY,
                    level INTEGER NOT NULL CHECK (level >= 0),
                    current_xp TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS player_login_streaks (
                    player_uuid TEXT PRIMARY KEY,
                    last_login_date TEXT NOT NULL,
                    streak_days INTEGER NOT NULL CHECK (streak_days >= 1)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS battlepass_pvp_cooldowns (
                    victim_uuid TEXT PRIMARY KEY,
                    last_reward_at TEXT NOT NULL,
                    CHECK (length(victim_uuid) > 0)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS battlepass_rewards (
                    level INTEGER NOT NULL CHECK (level >= 1),
                    reward_type TEXT NOT NULL,
                    amount INTEGER NOT NULL CHECK (amount >= 1),
                    created_by_uuid TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY (level, reward_type)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS battlepass_reward_claims (
                    clan_id TEXT NOT NULL,
                    level INTEGER NOT NULL,
                    reward_type TEXT NOT NULL,
                    claimed_by_uuid TEXT NOT NULL,
                    claimed_at TEXT NOT NULL,
                    PRIMARY KEY (clan_id, level, reward_type),
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS clan_unlocks (
                    clan_id TEXT PRIMARY KEY,
                    bonus_home_slots INTEGER NOT NULL DEFAULT 0,
                    vault_pages INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS clan_vault_items (
                    clan_id TEXT NOT NULL,
                    page_number INTEGER NOT NULL CHECK (page_number >= 1),
                    slot INTEGER NOT NULL CHECK (slot BETWEEN 0 AND 44),
                    item_data BLOB NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (clan_id, page_number, slot),
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
    }

    private static void createDiplomacyTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS clan_diplomacy_requests (
                    id TEXT PRIMARY KEY,
                    source_clan_id TEXT NOT NULL,
                    target_clan_id TEXT NOT NULL,
                    first_clan_id TEXT NOT NULL,
                    second_clan_id TEXT NOT NULL,
                    request_type TEXT NOT NULL,
                    war_duration_hours INTEGER NOT NULL DEFAULT 0,
                    requested_by_uuid TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    UNIQUE (first_clan_id, second_clan_id, request_type),
                    FOREIGN KEY (source_clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                    FOREIGN KEY (target_clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_diplomacy_target_expiry
                ON clan_diplomacy_requests(target_clan_id, expires_at)
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS clan_alliances (
                    first_clan_id TEXT NOT NULL,
                    second_clan_id TEXT NOT NULL,
                    established_at TEXT NOT NULL,
                    accepted_by_uuid TEXT NOT NULL,
                    PRIMARY KEY (first_clan_id, second_clan_id),
                    FOREIGN KEY (first_clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                    FOREIGN KEY (second_clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS clan_wars (
                    id TEXT PRIMARY KEY,
                    first_clan_id TEXT NOT NULL,
                    second_clan_id TEXT NOT NULL,
                    duration_hours INTEGER NOT NULL,
                    started_at TEXT NOT NULL,
                    ends_at TEXT NOT NULL,
                    accepted_by_uuid TEXT NOT NULL,
                    first_deaths INTEGER NOT NULL DEFAULT 0,
                    second_deaths INTEGER NOT NULL DEFAULT 0,
                    result_processed INTEGER NOT NULL DEFAULT 0,
                    winner_clan_id TEXT,
                    loser_clan_id TEXT,
                    completed_at TEXT,
                    UNIQUE (first_clan_id, second_clan_id),
                    FOREIGN KEY (first_clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                    FOREIGN KEY (second_clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_clan_wars_pair_end
                ON clan_wars(first_clan_id, second_clan_id, ends_at)
                """);
    }

    private static void createRankingTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS clan_ranking_stats (
                    clan_id TEXT PRIMARY KEY,
                    combat_kills INTEGER NOT NULL DEFAULT 0,
                    wars_won INTEGER NOT NULL DEFAULT 0,
                    wars_lost INTEGER NOT NULL DEFAULT 0,
                    active_days INTEGER NOT NULL DEFAULT 0,
                    last_active_date TEXT,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
        statement.execute("""
                INSERT INTO clan_ranking_stats(clan_id)
                SELECT id FROM clans
                WHERE id NOT IN (SELECT clan_id FROM clan_ranking_stats)
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS clan_bank_accounts (
                    clan_id TEXT PRIMARY KEY,
                    balance TEXT NOT NULL DEFAULT '0',
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS ranking_pvp_cooldowns (
                    victim_uuid TEXT PRIMARY KEY,
                    last_reward_at TEXT NOT NULL
                )
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_clan_wars_pending_end
                ON clan_wars(result_processed, ends_at)
                """);
    }

    private static void createHomeTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS clan_homes (
                    clan_id TEXT NOT NULL,
                    home_number INTEGER NOT NULL CHECK (home_number >= 1),
                    world_uuid TEXT NOT NULL,
                    world_name TEXT NOT NULL,
                    x TEXT NOT NULL,
                    y TEXT NOT NULL,
                    z TEXT NOT NULL,
                    yaw TEXT NOT NULL,
                    pitch TEXT NOT NULL,
                    updated_by_uuid TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (clan_id, home_number),
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
    }

    private BattlepassProgress mapBattlepassProgress(ResultSet results)
            throws SQLException {
        return new BattlepassProgress(
                UUID.fromString(results.getString("clan_id")),
                results.getInt("level"),
                new BigDecimal(results.getString("current_xp")),
                Instant.parse(results.getString("updated_at"))
        );
    }

    private int findMaximumMembers(UUID clanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT max_members FROM clans WHERE id = ?")) {
            statement.setString(1, clanId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new SQLException("Clan for reward claim was not found");
                }
                return results.getInt("max_members");
            }
        }
    }

    private static void ensureWithinLimit(int current, int amount, int maximum) {
        if ((long) current + amount > maximum) {
            throw new IllegalStateException("Unlock exceeds the configured limit");
        }
    }

    private boolean insertRewardClaim(
            UUID clanId,
            UUID ownerId,
            BattlepassReward reward
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO battlepass_reward_claims(
                    clan_id, level, reward_type, claimed_by_uuid, claimed_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(clan_id, level, reward_type) DO NOTHING
                """)) {
            statement.setString(1, clanId.toString());
            statement.setInt(2, reward.level());
            statement.setString(3, reward.type().name());
            statement.setString(4, ownerId.toString());
            statement.setString(5, Instant.now().toString());
            return statement.executeUpdate() == 1;
        }
    }

    private void saveClanUnlocks(UUID clanId, ClanUnlocks unlocks) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_unlocks(clan_id, bonus_home_slots, vault_pages)
                VALUES (?, ?, ?)
                ON CONFLICT(clan_id) DO UPDATE SET
                    bonus_home_slots = excluded.bonus_home_slots,
                    vault_pages = excluded.vault_pages
                """)) {
            statement.setString(1, clanId.toString());
            statement.setInt(2, unlocks.bonusHomeSlots());
            statement.setInt(3, unlocks.vaultPages());
            statement.executeUpdate();
        }
    }

    private void ensureRankingStats(UUID clanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO clan_ranking_stats(clan_id)
                VALUES (?)
                ON CONFLICT(clan_id) DO NOTHING
                """)) {
            statement.setString(1, clanId.toString());
            statement.executeUpdate();
        }
    }

    private void incrementWarResult(UUID clanId, String column) throws SQLException {
        if (!"wars_won".equals(column) && !"wars_lost".equals(column)) {
            throw new IllegalArgumentException("Invalid war result counter");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE clan_ranking_stats SET " + column + " = " + column
                        + " + 1 WHERE clan_id = ?"
        )) {
            statement.setString(1, clanId.toString());
            statement.executeUpdate();
        }
    }

    private void inTransaction(CheckedSqlRunnable action) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            action.run();
            connection.commit();
        } catch (Exception exception) {
            rollbackAfter(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void rollbackAfter(Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private Map<String, Boolean> findPermissions(String sql, String first, String second)
            throws SQLException {
        Map<String, Boolean> permissions = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, first);
            statement.setString(2, second);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    permissions.put(
                            results.getString("permission"),
                            results.getInt("allowed") == 1
                    );
                }
            }
        }
        return Map.copyOf(permissions);
    }

    private ClanRole mapRole(ResultSet results, UUID clanId) throws SQLException {
        return new ClanRole(
                clanId,
                results.getString("role_id"),
                results.getString("display_name"),
                results.getInt("priority"),
                results.getInt("is_standard") == 1
        );
    }

    private DiplomacyRequest mapDiplomacyRequest(ResultSet results) throws SQLException {
        return new DiplomacyRequest(
                UUID.fromString(results.getString("id")),
                UUID.fromString(results.getString("source_clan_id")),
                UUID.fromString(results.getString("target_clan_id")),
                DiplomacyType.valueOf(results.getString("request_type")),
                results.getInt("war_duration_hours"),
                UUID.fromString(results.getString("requested_by_uuid")),
                Instant.parse(results.getString("created_at")),
                Instant.parse(results.getString("expires_at"))
        );
    }

    private static String canonicalFirst(UUID first, UUID second) {
        String left = first.toString();
        String right = second.toString();
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static String canonicalSecond(UUID first, UUID second) {
        String left = first.toString();
        String right = second.toString();
        return left.compareTo(right) <= 0 ? right : left;
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(),
                null,
                "%",
                new String[]{"TABLE"}
        )) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addColumnIfMissing(
            Statement statement,
            String tableName,
            String columnName,
            String definition
    ) throws SQLException {
        if (columnExists(tableName, columnName)) {
            return;
        }
        try {
            statement.execute(
                    "ALTER TABLE " + tableName + " ADD COLUMN "
                            + columnName + " " + definition
            );
        } catch (SQLException exception) {
            if (!columnExists(tableName, columnName)) {
                throw exception;
            }
        }
    }

    private boolean columnExists(String tableName, String columnName)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(),
                null,
                tableName,
                null
        )) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface CheckedSqlRunnable {
        void run() throws Exception;
    }
}
