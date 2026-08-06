package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.audit.AuditLogEntry;
import dev.catgirlyannick.catclans.audit.TextAuditLogService;
import dev.catgirlyannick.catclans.config.RankPolicy;
import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanBankView;
import dev.catgirlyannick.catclans.model.ClanHome;
import dev.catgirlyannick.catclans.model.ClanHomeView;
import dev.catgirlyannick.catclans.model.ClanInvite;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.ClanRole;
import dev.catgirlyannick.catclans.model.BattlepassProgress;
import dev.catgirlyannick.catclans.model.BattlepassReward;
import dev.catgirlyannick.catclans.model.BattlepassRewardType;
import dev.catgirlyannick.catclans.model.BattlepassView;
import dev.catgirlyannick.catclans.model.ClanUnlocks;
import dev.catgirlyannick.catclans.model.ClanRankingEntry;
import dev.catgirlyannick.catclans.model.ClanRankingStats;
import dev.catgirlyannick.catclans.model.ClanWarResult;
import dev.catgirlyannick.catclans.model.DailyLoginState;
import dev.catgirlyannick.catclans.model.ClanRoleOverview;
import dev.catgirlyannick.catclans.model.DiplomacyRequest;
import dev.catgirlyannick.catclans.model.DiplomacyType;
import dev.catgirlyannick.catclans.model.DiplomacyView;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.MemberPermissionView;
import dev.catgirlyannick.catclans.model.PermissionOverride;
import dev.catgirlyannick.catclans.model.RankId;
import dev.catgirlyannick.catclans.model.RolePermissionView;
import dev.catgirlyannick.catclans.model.RoleMoveDirection;
import dev.catgirlyannick.catclans.model.RewardClaimResult;
import dev.catgirlyannick.catclans.model.RankingCategory;
import dev.catgirlyannick.catclans.model.RankingKillResult;
import dev.catgirlyannick.catclans.model.VaultPageView;
import dev.catgirlyannick.catclans.storage.ClanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.OptionalInt;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanService implements AutoCloseable {

    private final ClanRepository repository;
    private final TextAuditLogService audit;
    private final RankPolicy rankPolicy;
    private final ClanRules rules;
    private final JoinMode defaultJoinMode;
    private final int defaultMaxMembers;
    private final Duration inviteDuration;
    private final boolean preventOwnerLeaving;
    private final ClanSnapshotCache cache;
    private final int shutdownTimeoutSeconds;
    private final int defaultMaxRoles;
    private final int absoluteMaxRoles;
    private final List<String> knownRights;
    private final Map<UUID, RoleSnapshot> roleCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Map<String, Boolean>>> rolePermissionCache =
            new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Map<String, Boolean>>> memberPermissionCache =
            new ConcurrentHashMap<>();
    private final Map<UUID, BattlepassProgress> battlepassCache = new ConcurrentHashMap<>();
    private final BattlepassSettings battlepassSettings;
    private final VaultSettings vaultSettings;
    private final TextAuditLogService vaultAudit;
    private final DiplomacySettings diplomacySettings;
    private final RankingSettings rankingSettings;
    private final BankSettings bankSettings;
    private final TextAuditLogService bankAudit;
    private final HomeSettings homeSettings;
    private final Map<UUID, ClanRankingStats> rankingStatsCache =
            new ConcurrentHashMap<>();
    private volatile RankingSnapshot rankingSnapshot = RankingSnapshot.empty();
    private volatile boolean rankingDirty;
    private final ThreadPoolExecutor worker;
    private final Semaphore normalOperationSlots;

    public ClanService(
            ClanRepository repository,
            TextAuditLogService audit,
            RankPolicy rankPolicy,
            ClanRules rules,
            JoinMode defaultJoinMode,
            int defaultMaxMembers,
            Duration inviteDuration,
            boolean preventOwnerLeaving,
            String workerName,
            ClanSnapshotCache cache,
            int maximumQueuedOperations,
            int shutdownTimeoutSeconds,
            int defaultMaxRoles,
            int absoluteMaxRoles,
            List<String> knownRights,
            Map<UUID, List<ClanRole>> preloadedRoles,
            BattlepassSettings battlepassSettings,
            VaultSettings vaultSettings,
            Map<UUID, BattlepassProgress> preloadedBattlepass,
            TextAuditLogService vaultAudit
    ) {
        this(
                repository,
                audit,
                rankPolicy,
                rules,
                defaultJoinMode,
                defaultMaxMembers,
                inviteDuration,
                preventOwnerLeaving,
                workerName,
                cache,
                maximumQueuedOperations,
                shutdownTimeoutSeconds,
                defaultMaxRoles,
                absoluteMaxRoles,
                knownRights,
                preloadedRoles,
                battlepassSettings,
                vaultSettings,
                preloadedBattlepass,
                vaultAudit,
                new DiplomacySettings(
                        false,
                        false,
                        Duration.ofHours(24),
                        Set.of(24, 48, 72),
                        25
                ),
                disabledRankingSettings(),
                Map.of(),
                BankSettings.disabled(),
                audit
        );
    }

    public ClanService(
            ClanRepository repository,
            TextAuditLogService audit,
            RankPolicy rankPolicy,
            ClanRules rules,
            JoinMode defaultJoinMode,
            int defaultMaxMembers,
            Duration inviteDuration,
            boolean preventOwnerLeaving,
            String workerName,
            ClanSnapshotCache cache,
            int maximumQueuedOperations,
            int shutdownTimeoutSeconds,
            int defaultMaxRoles,
            int absoluteMaxRoles,
            List<String> knownRights,
            Map<UUID, List<ClanRole>> preloadedRoles,
            BattlepassSettings battlepassSettings,
            VaultSettings vaultSettings,
            Map<UUID, BattlepassProgress> preloadedBattlepass,
            TextAuditLogService vaultAudit,
            BankSettings bankSettings,
            TextAuditLogService bankAudit
    ) {
        this(
                repository,
                audit,
                rankPolicy,
                rules,
                defaultJoinMode,
                defaultMaxMembers,
                inviteDuration,
                preventOwnerLeaving,
                workerName,
                cache,
                maximumQueuedOperations,
                shutdownTimeoutSeconds,
                defaultMaxRoles,
                absoluteMaxRoles,
                knownRights,
                preloadedRoles,
                battlepassSettings,
                vaultSettings,
                preloadedBattlepass,
                vaultAudit,
                bankSettings,
                bankAudit,
                HomeSettings.disabled()
        );
    }

    public ClanService(
            ClanRepository repository,
            TextAuditLogService audit,
            RankPolicy rankPolicy,
            ClanRules rules,
            JoinMode defaultJoinMode,
            int defaultMaxMembers,
            Duration inviteDuration,
            boolean preventOwnerLeaving,
            String workerName,
            ClanSnapshotCache cache,
            int maximumQueuedOperations,
            int shutdownTimeoutSeconds,
            int defaultMaxRoles,
            int absoluteMaxRoles,
            List<String> knownRights,
            Map<UUID, List<ClanRole>> preloadedRoles,
            BattlepassSettings battlepassSettings,
            VaultSettings vaultSettings,
            Map<UUID, BattlepassProgress> preloadedBattlepass,
            TextAuditLogService vaultAudit,
            BankSettings bankSettings,
            TextAuditLogService bankAudit,
            HomeSettings homeSettings
    ) {
        this(
                repository,
                audit,
                rankPolicy,
                rules,
                defaultJoinMode,
                defaultMaxMembers,
                inviteDuration,
                preventOwnerLeaving,
                workerName,
                cache,
                maximumQueuedOperations,
                shutdownTimeoutSeconds,
                defaultMaxRoles,
                absoluteMaxRoles,
                knownRights,
                preloadedRoles,
                battlepassSettings,
                vaultSettings,
                preloadedBattlepass,
                vaultAudit,
                new DiplomacySettings(
                        false,
                        false,
                        Duration.ofHours(24),
                        Set.of(24, 48, 72),
                        25
                ),
                disabledRankingSettings(),
                Map.of(),
                bankSettings,
                bankAudit,
                homeSettings
        );
    }

    public ClanService(
            ClanRepository repository,
            TextAuditLogService audit,
            RankPolicy rankPolicy,
            ClanRules rules,
            JoinMode defaultJoinMode,
            int defaultMaxMembers,
            Duration inviteDuration,
            boolean preventOwnerLeaving,
            String workerName,
            ClanSnapshotCache cache,
            int maximumQueuedOperations,
            int shutdownTimeoutSeconds,
            int defaultMaxRoles,
            int absoluteMaxRoles,
            List<String> knownRights,
            Map<UUID, List<ClanRole>> preloadedRoles
    ) {
        this(
                repository,
                audit,
                rankPolicy,
                rules,
                defaultJoinMode,
                defaultMaxMembers,
                inviteDuration,
                preventOwnerLeaving,
                workerName,
                cache,
                maximumQueuedOperations,
                shutdownTimeoutSeconds,
                defaultMaxRoles,
                absoluteMaxRoles,
                knownRights,
                preloadedRoles,
                new BattlepassSettings(
                        false,
                        new BattlepassCurve(
                                BigDecimal.valueOf(100),
                                BigDecimal.valueOf(1.75),
                                2,
                                RoundingMode.HALF_UP
                        ),
                        new LoginStreakCalculator(
                                BigDecimal.valueOf(25),
                                BigDecimal.valueOf(1.3),
                                10,
                                BigDecimal.valueOf(0.8),
                                2,
                                RoundingMode.HALF_UP
                        ),
                        java.time.ZoneId.of("Europe/Berlin"),
                        BigDecimal.valueOf(50),
                        30,
                        BigDecimal.valueOf(15),
                        15,
                        false,
                        500,
                        absoluteMaxRoles,
                        7,
                        100
                ),
                new VaultSettings(false, 45),
                Map.of(),
                audit,
                new DiplomacySettings(
                        false,
                        false,
                        Duration.ofHours(24),
                        Set.of(24, 48, 72),
                        25
                ),
                disabledRankingSettings(),
                Map.of(),
                BankSettings.disabled(),
                audit
        );
    }

    public ClanService(
            ClanRepository repository,
            TextAuditLogService audit,
            RankPolicy rankPolicy,
            ClanRules rules,
            JoinMode defaultJoinMode,
            int defaultMaxMembers,
            Duration inviteDuration,
            boolean preventOwnerLeaving,
            String workerName,
            ClanSnapshotCache cache,
            int maximumQueuedOperations,
            int shutdownTimeoutSeconds,
            int defaultMaxRoles,
            int absoluteMaxRoles,
            List<String> knownRights,
            Map<UUID, List<ClanRole>> preloadedRoles,
            BattlepassSettings battlepassSettings,
            VaultSettings vaultSettings,
            Map<UUID, BattlepassProgress> preloadedBattlepass,
            TextAuditLogService vaultAudit,
            DiplomacySettings diplomacySettings,
            RankingSettings rankingSettings,
            Map<UUID, ClanRankingStats> preloadedRankingStats,
            BankSettings bankSettings,
            TextAuditLogService bankAudit
    ) {
        this(
                repository,
                audit,
                rankPolicy,
                rules,
                defaultJoinMode,
                defaultMaxMembers,
                inviteDuration,
                preventOwnerLeaving,
                workerName,
                cache,
                maximumQueuedOperations,
                shutdownTimeoutSeconds,
                defaultMaxRoles,
                absoluteMaxRoles,
                knownRights,
                preloadedRoles,
                battlepassSettings,
                vaultSettings,
                preloadedBattlepass,
                vaultAudit,
                diplomacySettings,
                rankingSettings,
                preloadedRankingStats,
                bankSettings,
                bankAudit,
                HomeSettings.disabled()
        );
    }

    public ClanService(
            ClanRepository repository,
            TextAuditLogService audit,
            RankPolicy rankPolicy,
            ClanRules rules,
            JoinMode defaultJoinMode,
            int defaultMaxMembers,
            Duration inviteDuration,
            boolean preventOwnerLeaving,
            String workerName,
            ClanSnapshotCache cache,
            int maximumQueuedOperations,
            int shutdownTimeoutSeconds,
            int defaultMaxRoles,
            int absoluteMaxRoles,
            List<String> knownRights,
            Map<UUID, List<ClanRole>> preloadedRoles,
            BattlepassSettings battlepassSettings,
            VaultSettings vaultSettings,
            Map<UUID, BattlepassProgress> preloadedBattlepass,
            TextAuditLogService vaultAudit,
            DiplomacySettings diplomacySettings,
            RankingSettings rankingSettings,
            Map<UUID, ClanRankingStats> preloadedRankingStats,
            BankSettings bankSettings,
            TextAuditLogService bankAudit,
            HomeSettings homeSettings
    ) {
        this.repository = repository;
        this.audit = audit;
        this.rankPolicy = rankPolicy;
        this.rules = rules;
        this.defaultJoinMode = defaultJoinMode;
        this.defaultMaxMembers = defaultMaxMembers;
        this.inviteDuration = inviteDuration;
        this.preventOwnerLeaving = preventOwnerLeaving;
        this.cache = cache;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        this.defaultMaxRoles = defaultMaxRoles;
        this.absoluteMaxRoles = absoluteMaxRoles;
        this.knownRights = List.copyOf(knownRights);
        this.battlepassSettings = battlepassSettings;
        this.vaultSettings = vaultSettings;
        this.vaultAudit = vaultAudit;
        this.diplomacySettings = diplomacySettings;
        this.rankingSettings = rankingSettings;
        this.bankSettings = bankSettings;
        this.bankAudit = bankAudit;
        this.homeSettings = homeSettings;
        preloadedRoles.forEach((clanId, roles) ->
                roleCache.put(clanId, createRoleSnapshot(clanId, roles)));
        battlepassCache.putAll(preloadedBattlepass);
        cache.list().forEach(clan -> rankingStatsCache.put(
                clan.id(),
                preloadedRankingStats.getOrDefault(
                        clan.id(),
                        ClanRankingStats.empty(clan.id())
                )
        ));
        rebuildRankingSnapshot();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, workerName);
            thread.setDaemon(true);
            return thread;
        };
        this.normalOperationSlots = new Semaphore(maximumQueuedOperations);
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maximumQueuedOperations * 2),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public CompletableFuture<OperationResult<Clan>> createClan(
            UUID ownerId,
            String ownerName,
            String requestedName,
            String requestedTag
    ) {
        return submit(() -> {
            if (cache.findByPlayer(ownerId).isPresent()) {
                return OperationResult.failure(OperationCode.ALREADY_IN_CLAN);
            }

            String name = rules.cleanDisplay(requestedName);
            if (!rules.validName(name)) {
                return OperationResult.failure(OperationCode.INVALID_NAME);
            }
            Optional<ClanTagFormatter.ParsedTag> parsedTag = rules.parseTag(requestedTag);
            if (parsedTag.isEmpty()) {
                return OperationResult.failure(OperationCode.INVALID_TAG);
            }
            String tag = parsedTag.get().plain();

            String normalizedName = rules.normalizeKey(name);
            String normalizedTag = rules.normalizeKey(tag);
            if (cache.findByNameOrTag(normalizedName).isPresent()) {
                return OperationResult.failure(OperationCode.NAME_TAKEN);
            }
            if (cache.findByNameOrTag(normalizedTag).isPresent()) {
                return OperationResult.failure(OperationCode.TAG_TAKEN);
            }

            Instant now = Instant.now();
            Clan clan = new Clan(
                    UUID.randomUUID(),
                    name,
                    normalizedName,
                    tag,
                    normalizedTag,
                    parsedTag.get().formatted(),
                    ownerId,
                    defaultJoinMode,
                    defaultMaxMembers,
                    now,
                    List.of(new ClanMember(ownerId, ownerName, RankId.OWNER, now))
            );
            cache.ensureCapacityForNewClan();
            repository.save(clan);
            cache.put(clan);
            rankingStatsCache.put(clan.id(), ClanRankingStats.empty(clan.id()));
            rankingDirty = true;
            if (rankingSettings.enabled()) {
                recordCurrentRankingDay(clan.id());
            }
            audit.log(clan, "CLAN_CREATED", ownerId, ownerName,
                    "tag=" + tag + " maxMembers=" + defaultMaxMembers);
            return OperationResult.success(clan);
        });
    }

    public CompletableFuture<OperationResult<Clan>> changeTag(
            UUID actorId,
            String actorName,
            String requestedTag
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(actorId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            Clan clan = found.get();
            PermissionDecision decision = permissionDecision(clan, actorId, "tag.change");
            if (!decision.allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            Optional<ClanTagFormatter.ParsedTag> parsedTag = rules.parseTag(requestedTag);
            if (parsedTag.isEmpty()) {
                return OperationResult.failure(OperationCode.INVALID_TAG);
            }
            String tag = parsedTag.get().plain();
            String normalizedTag = rules.normalizeKey(tag);
            Optional<Clan> collision = cache.findByNameOrTag(normalizedTag);
            if (collision.isPresent() && !collision.get().id().equals(clan.id())) {
                return OperationResult.failure(OperationCode.TAG_TAKEN);
            }

            Clan updated = clan.withTag(
                    tag,
                    normalizedTag,
                    parsedTag.get().formatted()
            );
            repository.save(updated);
            cache.put(updated);
            rankingDirty = true;
            audit.log(updated, "CLAN_TAG_CHANGED", actorId, actorName,
                    "previous=" + clan.tag() + " tag=" + tag
                            + " formatted=" + parsedTag.get().formatted());
            return OperationResult.success(updated);
        });
    }

    public CompletableFuture<OperationResult<Clan>> changeName(
            UUID actorId,
            String actorName,
            String requestedName
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(actorId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            Clan clan = found.get();
            PermissionDecision decision = permissionDecision(clan, actorId, "name.change");
            if (!decision.allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            String name = rules.cleanDisplay(requestedName);
            if (!rules.validName(name)) {
                return OperationResult.failure(OperationCode.INVALID_NAME);
            }
            String normalizedName = rules.normalizeKey(name);
            Optional<Clan> collision = cache.findByNameOrTag(normalizedName);
            if (collision.isPresent() && !collision.get().id().equals(clan.id())) {
                return OperationResult.failure(OperationCode.NAME_TAKEN);
            }

            Clan updated = clan.withName(name, normalizedName);
            repository.save(updated);
            cache.put(updated);
            rankingDirty = true;
            audit.log(updated, "CLAN_NAME_CHANGED", actorId, actorName,
                    "previous=" + clan.name() + " name=" + name);
            return OperationResult.success(updated);
        });
    }

    public CompletableFuture<OperationResult<Clan>> invite(
            UUID actorId,
            String actorName,
            UUID targetId,
            String targetName
    ) {
        return submit(() -> {
            Optional<Clan> actorClan = cache.findByPlayer(actorId);
            if (actorClan.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (actorId.equals(targetId)) {
                return OperationResult.failure(OperationCode.CANNOT_INVITE_SELF);
            }
            Clan clan = actorClan.get();
            PermissionDecision decision = permissionDecision(clan, actorId, "invite");
            if (!decision.allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            if (cache.findByPlayer(targetId).isPresent()) {
                return OperationResult.failure(OperationCode.TARGET_ALREADY_IN_CLAN);
            }
            if (clan.isFull()) {
                return OperationResult.failure(OperationCode.CLAN_FULL);
            }

            Instant now = Instant.now();
            repository.saveInvite(new ClanInvite(
                    clan.id(),
                    targetId,
                    actorId,
                    now,
                    now.plus(inviteDuration)
            ));
            audit.log(clan, "MEMBER_INVITED", actorId, actorName,
                    "target=" + targetId + "/" + targetName);
            return OperationResult.success(clan);
        });
    }

    public CompletableFuture<OperationResult<Clan>> acceptInvite(
            UUID playerId,
            String playerName,
            String clanSearch
    ) {
        return submit(() -> {
            if (cache.findByPlayer(playerId).isPresent()) {
                return OperationResult.failure(OperationCode.ALREADY_IN_CLAN);
            }
            Optional<Clan> found = cache.findByNameOrTag(rules.normalizeKey(clanSearch));
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
            }
            Clan clan = found.get();
            Optional<ClanInvite> invite = repository.findInvite(clan.id(), playerId);
            if (invite.isEmpty() || invite.get().isExpired(Instant.now())) {
                invite.ifPresent(value -> deleteInviteUnchecked(value.clanId(), value.playerId()));
                return OperationResult.failure(OperationCode.INVITE_NOT_FOUND);
            }
            return addMember(clan, playerId, playerName, "INVITE_ACCEPTED");
        });
    }

    public CompletableFuture<OperationResult<Clan>> denyInvite(
            UUID playerId,
            String playerName,
            String clanSearch
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByNameOrTag(rules.normalizeKey(clanSearch));
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
            }
            Clan clan = found.get();
            Optional<ClanInvite> invite = repository.findInvite(clan.id(), playerId);
            if (invite.isEmpty() || invite.get().isExpired(Instant.now())) {
                invite.ifPresent(value -> deleteInviteUnchecked(value.clanId(), value.playerId()));
                return OperationResult.failure(OperationCode.INVITE_NOT_FOUND);
            }
            repository.deleteInvite(clan.id(), playerId);
            audit.log(clan, "INVITE_DENIED", playerId, playerName, "invite denied");
            return OperationResult.success(clan);
        });
    }

    public CompletableFuture<OperationResult<Clan>> joinOpenClan(
            UUID playerId,
            String playerName,
            String clanSearch
    ) {
        return submit(() -> {
            if (cache.findByPlayer(playerId).isPresent()) {
                return OperationResult.failure(OperationCode.ALREADY_IN_CLAN);
            }
            Optional<Clan> found = cache.findByNameOrTag(rules.normalizeKey(clanSearch));
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
            }
            Clan clan = found.get();
            if (clan.joinMode() != JoinMode.OPEN) {
                return OperationResult.failure(OperationCode.INVITE_REQUIRED);
            }
            return addMember(clan, playerId, playerName, "OPEN_JOIN");
        });
    }

    public CompletableFuture<OperationResult<Clan>> leaveClan(
            UUID playerId,
            String playerName,
            String reason
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            Clan clan = found.get();
            if (clan.ownerId().equals(playerId)) {
                if (preventOwnerLeaving) {
                    return OperationResult.failure(OperationCode.OWNER_CANNOT_LEAVE);
                }
                Optional<ClanMember> successor = selectOwnershipSuccessor(clan, playerId);
                if (successor.isEmpty()) {
                    return OperationResult.failure(OperationCode.OWNER_CANNOT_LEAVE);
                }
                Clan updated = clan.transferOwnershipAndRemoveOwner(
                        playerId,
                        successor.get().playerId()
                );
                repository.save(updated);
                cache.put(updated);
                evictMemberPermissions(clan.id(), playerId);
                rankingDirty = true;
                audit.log(updated, "OWNERSHIP_TRANSFERRED_ON_LEAVE", playerId, playerName,
                        "reason=" + reason
                                + " successor=" + successor.get().playerId()
                                + " successorName=" + successor.get().lastKnownName());
                return OperationResult.success(updated);
            }
            Clan updated = clan.withoutMember(playerId);
            repository.save(updated);
            cache.put(updated);
            evictMemberPermissions(clan.id(), playerId);
            rankingDirty = true;
            audit.log(updated, "MEMBER_LEFT", playerId, playerName, "reason=" + reason);
            return OperationResult.success(updated);
        });
    }

    public CompletableFuture<OperationResult<Clan>> deleteOwnedClan(
            UUID ownerId,
            String ownerName
    ) {
        return submitCritical(() -> {
            Optional<Clan> found = cache.findByPlayer(ownerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            Clan clan = found.get();
            if (!clan.ownerId().equals(ownerId)) {
                return OperationResult.failure(OperationCode.OWNER_ONLY);
            }
            return deleteClan(clan, ownerId, ownerName, "OWNER_DELETE");
        });
    }

    public CompletableFuture<OperationResult<Clan>> deleteClanAsAdmin(
            String clanSearch,
            UUID actorId,
            String actorName
    ) {
        return submitCritical(() -> {
            Optional<Clan> found = cache.findByNameOrTag(rules.normalizeKey(clanSearch));
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
            }
            return deleteClan(found.get(), actorId, actorName, "ADMIN_DELETE");
        });
    }

    public CompletableFuture<OperationResult<Clan>> setJoinMode(
            UUID actorId,
            String actorName,
            JoinMode joinMode
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(actorId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            Clan clan = found.get();
            if (!clan.ownerId().equals(actorId)) {
                return OperationResult.failure(OperationCode.OWNER_ONLY);
            }
            Clan updated = clan.withJoinMode(joinMode);
            repository.save(updated);
            cache.put(updated);
            rankingDirty = true;
            audit.log(updated, "JOIN_MODE_CHANGED", actorId, actorName,
                    clan.joinMode() + " -> " + joinMode);
            return OperationResult.success(updated);
        });
    }

    public CompletableFuture<OperationResult<Clan>> kickMember(
            UUID actorId,
            String actorName,
            UUID targetId,
            String reason
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(actorId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            Clan clan = found.get();
            if (actorId.equals(targetId)) {
                return OperationResult.failure(OperationCode.CANNOT_KICK_SELF);
            }
            Optional<ClanMember> targetMember = clan.member(targetId);
            if (targetMember.isEmpty()) {
                return OperationResult.failure(OperationCode.MEMBER_NOT_FOUND);
            }
            if (clan.ownerId().equals(targetId)) {
                return OperationResult.failure(OperationCode.CANNOT_KICK_OWNER);
            }

            PermissionDecision decision = permissionDecision(clan, actorId, "kick");
            if (!decision.allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            ClanRole actorRole = findRole(clan.id(), clan.member(actorId).orElseThrow().roleId())
                    .orElseThrow();
            ClanRole targetRole = findRole(clan.id(), targetMember.get().roleId())
                    .orElseThrow();
            if (!decision.explicitMemberAllow()
                    && !clan.ownerId().equals(actorId)
                    && actorRole.priority() <= targetRole.priority()) {
                return OperationResult.failure(OperationCode.RANK_TOO_LOW);
            }

            Clan updated = clan.withoutMember(targetId);
            repository.save(updated);
            cache.put(updated);
            evictMemberPermissions(clan.id(), targetId);
            rankingDirty = true;
            audit.log(updated, "MEMBER_KICKED", actorId, actorName,
                    "target=" + targetId + "/" + targetMember.get().lastKnownName()
                            + " reason=" + reason);
            return OperationResult.success(updated);
        });
    }

    public CompletableFuture<List<Clan>> findPendingInvites(UUID playerId) {
        return submit(() -> repository.findInvitesForPlayer(playerId, Instant.now()).stream()
                .map(ClanInvite::clanId)
                .map(cache::findById)
                .flatMap(Optional::stream)
                .toList());
    }

    public CompletableFuture<OperationResult<ClanRoleOverview>> findRolesForOwner(
            UUID ownerId
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            int maximumRoles = Math.min(
                    absoluteMaxRoles,
                    Math.max(
                            RankId.values().length,
                            repository.findRoleLimit(clan.get().id(), defaultMaxRoles)
                    )
            );
            return OperationResult.success(new ClanRoleOverview(
                    rolesForClan(clan.get().id()),
                    maximumRoles
            ));
        });
    }

    public CompletableFuture<OperationResult<RolePermissionView>> findRolePermissions(
            UUID ownerId,
            String roleId
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            Optional<ClanRole> role = findRole(clan.get().id(), roleId);
            if (role.isEmpty()) {
                return OperationResult.failure(OperationCode.ROLE_NOT_FOUND);
            }
            return OperationResult.success(rolePermissionView(role.get()));
        });
    }

    public CompletableFuture<OperationResult<MemberPermissionView>> findMemberPermissions(
            UUID ownerId,
            UUID memberId
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            Optional<ClanMember> member = clan.get().member(memberId);
            if (member.isEmpty()) {
                return OperationResult.failure(OperationCode.MEMBER_NOT_FOUND);
            }
            Optional<ClanRole> role = findRole(clan.get().id(), member.get().roleId());
            if (role.isEmpty()) {
                return OperationResult.failure(OperationCode.ROLE_NOT_FOUND);
            }
            return OperationResult.success(new MemberPermissionView(
                    member.get(),
                    role.get(),
                    knownRights,
                    memberPermissions(clan.get().id(), memberId)
            ));
        });
    }

    public CompletableFuture<OperationResult<ClanRole>> createRole(
            UUID ownerId,
            String ownerName,
            String requestedName
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            String displayName = rules.cleanDisplay(requestedName);
            if (!rules.validRoleName(displayName)) {
                return OperationResult.failure(OperationCode.INVALID_ROLE_NAME);
            }
            List<ClanRole> roles = rolesForClan(clan.get().id());
            if (roles.stream().anyMatch(role ->
                    role.displayName().equalsIgnoreCase(displayName))) {
                return OperationResult.failure(OperationCode.ROLE_NAME_TAKEN);
            }
            int maximumRoles = Math.min(
                    absoluteMaxRoles,
                    Math.max(
                            RankId.values().length,
                            repository.findRoleLimit(clan.get().id(), defaultMaxRoles)
                    )
            );
            if (roles.size() >= maximumRoles) {
                return OperationResult.failure(OperationCode.ROLE_LIMIT_REACHED);
            }
            ClanRole role = new ClanRole(
                    clan.get().id(),
                    "custom-" + UUID.randomUUID(),
                    displayName,
                    0,
                    false
            );
            repository.saveRole(role);
            cacheRole(role);
            audit.log(clan.get(), "ROLE_CREATED", ownerId, ownerName,
                    "role=" + role.id() + "/" + role.displayName());
            return OperationResult.success(role);
        });
    }

    public CompletableFuture<OperationResult<ClanRole>> renameRole(
            UUID ownerId,
            String ownerName,
            String roleId,
            String requestedName
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            String displayName = rules.cleanDisplay(requestedName);
            if (!rules.validRoleName(displayName)) {
                return OperationResult.failure(OperationCode.INVALID_ROLE_NAME);
            }
            List<ClanRole> roles = rolesForClan(clan.get().id());
            Optional<ClanRole> found = roles.stream()
                    .filter(role -> role.id().equals(roleId))
                    .findFirst();
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.ROLE_NOT_FOUND);
            }
            if (roles.stream().anyMatch(role ->
                    !role.id().equals(roleId)
                            && role.displayName().equalsIgnoreCase(displayName))) {
                return OperationResult.failure(OperationCode.ROLE_NAME_TAKEN);
            }
            ClanRole updated = new ClanRole(
                    found.get().clanId(),
                    found.get().id(),
                    displayName,
                    found.get().priority(),
                    found.get().standard()
            );
            repository.saveRole(updated);
            cacheRole(updated);
            audit.log(clan.get(), "ROLE_RENAMED", ownerId, ownerName,
                    "role=" + roleId + " name=" + displayName);
            return OperationResult.success(updated);
        });
    }

    public CompletableFuture<OperationResult<RolePermissionView>> toggleRolePermission(
            UUID ownerId,
            String ownerName,
            String roleId,
            String permission
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            if (!knownRights.contains(permission)) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            Optional<ClanRole> role = findRole(clan.get().id(), roleId);
            if (role.isEmpty()) {
                return OperationResult.failure(OperationCode.ROLE_NOT_FOUND);
            }
            if ("owner".equals(roleId)) {
                return OperationResult.failure(OperationCode.OWNER_ROLE_LOCKED);
            }
            repository.saveRole(role.get());
            Map<String, Boolean> currentPermissions = rolePermissions(role.get());
            boolean newValue = !rolePermissionAllowed(
                    role.get(),
                    permission,
                    currentPermissions
            );
            repository.setRolePermission(clan.get().id(), roleId, permission, newValue);
            Map<String, Boolean> updatedPermissions = withPermission(
                    currentPermissions,
                    permission,
                    newValue
            );
            rolePermissionCache.put(
                    clan.get().id(),
                    updatedPermissionCache(
                            rolePermissionCache.get(clan.get().id()),
                            roleId,
                            updatedPermissions
                    )
            );
            audit.log(clan.get(), "ROLE_PERMISSION_CHANGED", ownerId, ownerName,
                    "role=" + roleId + " permission=" + permission + " allowed=" + newValue);
            return OperationResult.success(rolePermissionView(
                    role.get(),
                    updatedPermissions
            ));
        });
    }

    public CompletableFuture<OperationResult<RolePermissionView>> moveRole(
            UUID ownerId,
            String ownerName,
            String roleId,
            RoleMoveDirection direction
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            if ("owner".equals(roleId)) {
                return OperationResult.failure(OperationCode.OWNER_ROLE_LOCKED);
            }
            List<ClanRole> roles = rolesForClan(clan.get().id());
            int currentIndex = -1;
            for (int index = 0; index < roles.size(); index++) {
                if (roles.get(index).id().equals(roleId)) {
                    currentIndex = index;
                    break;
                }
            }
            if (currentIndex < 0) {
                return OperationResult.failure(OperationCode.ROLE_NOT_FOUND);
            }
            int targetIndex = direction == RoleMoveDirection.UP
                    ? currentIndex - 1
                    : currentIndex + 1;
            if (targetIndex < 1 || targetIndex >= roles.size()) {
                return OperationResult.success(rolePermissionView(roles.get(currentIndex)));
            }
            ClanRole current = roles.get(currentIndex);
            ClanRole target = roles.get(targetIndex);
            ClanRole moved = new ClanRole(
                    current.clanId(),
                    current.id(),
                    current.displayName(),
                    target.priority(),
                    current.standard()
            );
            ClanRole swapped = new ClanRole(
                    target.clanId(),
                    target.id(),
                    target.displayName(),
                    current.priority(),
                    target.standard()
            );
            repository.saveRole(moved);
            repository.saveRole(swapped);
            cacheRole(moved);
            cacheRole(swapped);
            audit.log(clan.get(), "ROLE_PRIORITY_CHANGED", ownerId, ownerName,
                    "role=" + roleId + " direction=" + direction
                            + " priority=" + moved.priority());
            return OperationResult.success(rolePermissionView(moved));
        });
    }

    public CompletableFuture<OperationResult<RolePermissionView>> setRolePriority(
            UUID ownerId,
            String ownerName,
            String roleId,
            int priority
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            if (priority < 0 || priority > 100) {
                return OperationResult.failure(OperationCode.INVALID_ROLE_PRIORITY);
            }
            if ("owner".equals(roleId)) {
                return OperationResult.failure(OperationCode.OWNER_ROLE_LOCKED);
            }
            List<ClanRole> roles = rolesForClan(clan.get().id());
            Optional<ClanRole> found = roles.stream()
                    .filter(role -> role.id().equals(roleId))
                    .findFirst();
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.ROLE_NOT_FOUND);
            }
            if (priority != 0 && roles.stream().anyMatch(role ->
                    !role.id().equals(roleId) && role.priority() == priority)) {
                return OperationResult.failure(OperationCode.ROLE_PRIORITY_TAKEN);
            }
            ClanRole updated = new ClanRole(
                    found.get().clanId(),
                    found.get().id(),
                    found.get().displayName(),
                    priority,
                    found.get().standard()
            );
            repository.saveRole(updated);
            cacheRole(updated);
            audit.log(clan.get(), "ROLE_PRIORITY_CHANGED", ownerId, ownerName,
                    "role=" + roleId + " priority=" + priority);
            return OperationResult.success(rolePermissionView(updated));
        });
    }

    public CompletableFuture<OperationResult<MemberPermissionView>> cycleMemberPermission(
            UUID ownerId,
            String ownerName,
            UUID memberId,
            String permission
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            if (!knownRights.contains(permission)) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            Optional<ClanMember> member = clan.get().member(memberId);
            if (member.isEmpty()) {
                return OperationResult.failure(OperationCode.MEMBER_NOT_FOUND);
            }
            if (clan.get().ownerId().equals(memberId)) {
                return OperationResult.failure(OperationCode.OWNER_ROLE_LOCKED);
            }
            Map<String, Boolean> current = memberPermissions(clan.get().id(), memberId);
            PermissionOverride next = current.containsKey(permission)
                    ? current.get(permission)
                    ? PermissionOverride.ALLOW.next()
                    : PermissionOverride.DENY.next()
                    : PermissionOverride.INHERIT.next();
            Boolean stored = switch (next) {
                case INHERIT -> null;
                case ALLOW -> true;
                case DENY -> false;
            };
            repository.setMemberPermission(
                    clan.get().id(),
                    memberId,
                    permission,
                    stored
            );
            Map<String, Boolean> updatedPermissions = withPermission(
                    current,
                    permission,
                    stored
            );
            memberPermissionCache.put(
                    clan.get().id(),
                    updatedPermissionCache(
                            memberPermissionCache.get(clan.get().id()),
                            memberId,
                            updatedPermissions
                    )
            );
            audit.log(clan.get(), "MEMBER_PERMISSION_CHANGED", ownerId, ownerName,
                    "target=" + memberId + " permission=" + permission + " state=" + next);
            ClanRole role = findRole(clan.get().id(), member.get().roleId()).orElseThrow();
            return OperationResult.success(new MemberPermissionView(
                    member.get(),
                    role,
                    knownRights,
                    updatedPermissions
            ));
        });
    }

    public CompletableFuture<OperationResult<Clan>> assignRole(
            UUID ownerId,
            String ownerName,
            UUID memberId,
            String roleId
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            if (clan.get().ownerId().equals(memberId)) {
                return OperationResult.failure(OperationCode.OWNER_ROLE_LOCKED);
            }
            if (clan.get().member(memberId).isEmpty()) {
                return OperationResult.failure(OperationCode.MEMBER_NOT_FOUND);
            }
            Optional<ClanRole> role = findRole(clan.get().id(), roleId);
            if (role.isEmpty() || "owner".equals(roleId)) {
                return OperationResult.failure(OperationCode.ROLE_NOT_FOUND);
            }
            RankId fallbackRank = RankId.fromRoleId(roleId).orElse(RankId.RECRUIT);
            Clan updated = clan.get().withMemberRole(memberId, roleId, fallbackRank);
            repository.save(updated);
            cache.put(updated);
            audit.log(updated, "MEMBER_ROLE_CHANGED", ownerId, ownerName,
                    "target=" + memberId + " role=" + roleId);
            return OperationResult.success(updated);
        });
    }

    public String displayRole(UUID clanId, ClanMember member) {
        ClanRole cachedRole = roleSnapshot(clanId).rolesById().get(member.roleId());
        return Optional.ofNullable(cachedRole)
                .map(ClanRole::displayName)
                .orElseGet(() -> RankId.fromRoleId(member.roleId())
                        .map(rankPolicy::displayName)
                        .orElseGet(() -> rankPolicy.displayName(member.rank())));
    }

    public CompletableFuture<Optional<Clan>> findClanForPlayer(UUID playerId) {
        return CompletableFuture.completedFuture(cache.findByPlayer(playerId));
    }

    public CompletableFuture<Optional<Clan>> findPublicClan(String search) {
        return CompletableFuture.completedFuture(
                cache.findByNameOrTag(rules.normalizeKey(search))
        );
    }

    public CompletableFuture<List<Clan>> listClans() {
        return CompletableFuture.completedFuture(cache.list());
    }

    public boolean rankingsEnabled() {
        return rankingSettings.enabled();
    }

    public boolean rankingMoneyAvailable() {
        return rankingSettings.enabled() && rankingSettings.bankEnabled();
    }

    public CompletableFuture<List<ClanRankingEntry>> ranking(
            RankingCategory category
    ) {
        if (!rankingSettings.enabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return submit(() -> {
            if (rankingDirty) {
                rebuildRankingSnapshot();
            }
            return rankingSnapshot.entries().getOrDefault(category, List.of());
        });
    }

    public OptionalInt cachedRankingPosition(UUID clanId) {
        ClanRankingEntry entry = rankingSnapshot.totalByClan().get(clanId);
        return entry == null
                ? OptionalInt.empty()
                : OptionalInt.of(entry.position());
    }

    public BigDecimal cachedRankingPoints(UUID clanId) {
        ClanRankingEntry entry = rankingSnapshot.totalByClan().get(clanId);
        return entry == null ? BigDecimal.ZERO : entry.totalPoints();
    }

    public BigDecimal cachedBankBalance(UUID clanId) {
        return stats(clanId).bankBalance();
    }

    public CompletableFuture<Boolean> registerRankingActivity(UUID playerId) {
        if (!rankingSettings.enabled()) {
            return CompletableFuture.completedFuture(false);
        }
        return submit(() -> cache.findByPlayer(playerId)
                .map(Clan::id)
                .map(this::recordCurrentRankingDay)
                .orElse(false));
    }

    public CompletableFuture<RankingKillResult> recordRankingKill(
            UUID killerId,
            UUID victimId
    ) {
        return recordRankingKill(killerId, victimId, Instant.now());
    }

    public CompletableFuture<RankingKillResult> recordRankingKill(
            UUID killerId,
            UUID victimId,
            Instant occurredAt
    ) {
        if (!rankingSettings.enabled()) {
            return CompletableFuture.completedFuture(
                    new RankingKillResult(false, false)
            );
        }
        return submit(() -> {
            Optional<Clan> killerClan = cache.findByPlayer(killerId);
            Optional<Clan> victimClan = cache.findByPlayer(victimId);
            return recordRankingKillInternal(
                    killerId,
                    victimId,
                    occurredAt,
                    killerClan,
                    victimClan
            );
        });
    }

    public CompletableFuture<PvpKillProcessingResult> processPvpKill(
            UUID killerId,
            String killerName,
            UUID victimId,
            Instant occurredAt,
            boolean awardBattlepassXp
    ) {
        if (!awardBattlepassXp && !rankingSettings.enabled()) {
            return CompletableFuture.completedFuture(new PvpKillProcessingResult(
                    OperationResult.failure(OperationCode.BATTLEPASS_DISABLED),
                    new RankingKillResult(false, false)
            ));
        }
        return submit(() -> {
            Optional<Clan> killerClan = cache.findByPlayer(killerId);
            Optional<Clan> victimClan = cache.findByPlayer(victimId);
            OperationResult<XpAwardResult> battlepass = awardBattlepassXp
                    ? awardPvpKillInternal(
                            killerId,
                            killerName,
                            victimId,
                            occurredAt,
                            killerClan,
                            victimClan
                    )
                    : OperationResult.failure(OperationCode.BATTLEPASS_DISABLED);
            RankingKillResult ranking = rankingSettings.enabled()
                    ? recordRankingKillInternal(
                            killerId,
                            victimId,
                            occurredAt,
                            killerClan,
                            victimClan
                    )
                    : new RankingKillResult(false, false);
            return new PvpKillProcessingResult(battlepass, ranking);
        });
    }

    private RankingKillResult recordRankingKillInternal(
            UUID killerId,
            UUID victimId,
            Instant occurredAt,
            Optional<Clan> killerClan,
            Optional<Clan> victimClan
    ) throws Exception {
        if (killerClan.isEmpty()
                || victimClan.isEmpty()
                || killerClan.get().id().equals(victimClan.get().id())) {
            return new RankingKillResult(false, false);
        }
        RankingKillResult result = repository.recordRankingKill(
                killerClan.get().id(),
                victimClan.get().id(),
                victimId,
                occurredAt,
                occurredAt.minus(rankingSettings.repeatedVictimCooldown())
        );
        if (result.combatPointAwarded()) {
            ClanRankingStats current = stats(killerClan.get().id());
            rankingStatsCache.put(
                    killerClan.get().id(),
                    new ClanRankingStats(
                            current.clanId(),
                            current.combatKills() + 1,
                            current.warsWon(),
                            current.warsLost(),
                            current.activeDays(),
                            current.lastActiveDate(),
                            current.bankBalance()
                    )
            );
            rankingDirty = true;
            audit.log(
                    killerClan.get(),
                    "RANKING_COMBAT_POINT",
                    killerId,
                    "SYSTEM",
                    "victim=" + victimId
            );
        }
        return result;
    }

    public CompletableFuture<List<ClanWarResult>> performRankingMaintenance(
            Set<UUID> onlineClanIds
    ) {
        if (!rankingSettings.enabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return submit(() -> {
            for (UUID clanId : onlineClanIds) {
                if (cache.findById(clanId).isPresent()) {
                    recordCurrentRankingDay(clanId);
                }
            }
            List<ClanWarResult> results = repository.finalizeExpiredWars(
                    Instant.now()
            );
            for (ClanWarResult result : results) {
                result.winner().ifPresent(winnerId -> {
                    updateWarStats(winnerId, true);
                    result.loser().ifPresent(loserId -> updateWarStats(loserId, false));
                });
                logWarResult(result);
            }
            if (!results.isEmpty()) {
                rankingDirty = true;
            }
            if (rankingSettings.refreshPersistedStats()) {
                Map<UUID, ClanRankingStats> persistedStats =
                        repository.findAllRankingStats();
                Map<UUID, ClanRankingStats> refreshedStats = new LinkedHashMap<>();
                cache.list().forEach(clan -> refreshedStats.put(
                        clan.id(),
                        persistedStats.getOrDefault(
                                clan.id(),
                                ClanRankingStats.empty(clan.id())
                        )
                ));
                if (!refreshedStats.equals(Map.copyOf(rankingStatsCache))) {
                    rankingDirty = true;
                }
                rankingStatsCache.clear();
                rankingStatsCache.putAll(refreshedStats);
            }
            if (rankingDirty) {
                rebuildRankingSnapshot();
            }
            return results;
        });
    }

    public Optional<Clan> findCachedClanForPlayer(UUID playerId) {
        return cache.findByPlayer(playerId);
    }

    public Optional<ClanMember> findCachedMember(UUID playerId) {
        return cache.findMember(playerId);
    }

    public Optional<Clan> findCachedClan(UUID clanId) {
        return cache.findById(clanId);
    }

    public Optional<Clan> findCachedClan(String clanSearch) {
        return cache.findByNameOrTag(rules.normalizeKey(clanSearch));
    }

    public BattlepassProgress findCachedBattlepass(UUID clanId) {
        BattlepassProgress progress = battlepassCache.get(clanId);
        return progress == null
                ? BattlepassProgress.initial(clanId, Instant.EPOCH)
                : progress;
    }

    public BigDecimal requiredBattlepassXp(int level) {
        return battlepassSettings.curve().requiredXp(level);
    }

    private boolean recordCurrentRankingDay(UUID clanId) {
        LocalDate today = LocalDate.now(rankingSettings.activityZone());
        ClanRankingStats current = stats(clanId);
        if (current.lastActiveDate() != null
                && !current.lastActiveDate().isBefore(today)) {
            return false;
        }
        try {
            if (!repository.recordDailyRankingActivity(clanId, today)) {
                return false;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Leaderboard activity could not be saved",
                    exception);
        }
        rankingStatsCache.put(clanId, new ClanRankingStats(
                current.clanId(),
                current.combatKills(),
                current.warsWon(),
                current.warsLost(),
                current.activeDays() + 1,
                today,
                current.bankBalance()
        ));
        rankingDirty = true;
        cache.findById(clanId).ifPresent(clan -> audit.log(
                clan,
                "RANKING_ACTIVE_DAY",
                clan.ownerId(),
                "SYSTEM",
                "date=" + today
        ));
        return true;
    }

    private void updateWarStats(UUID clanId, boolean won) {
        ClanRankingStats current = stats(clanId);
        rankingStatsCache.put(clanId, new ClanRankingStats(
                current.clanId(),
                current.combatKills(),
                current.warsWon() + (won ? 1 : 0),
                current.warsLost() + (won ? 0 : 1),
                current.activeDays(),
                current.lastActiveDate(),
                current.bankBalance()
        ));
    }

    private void logWarResult(ClanWarResult result) {
        String details = warResultDetails(result);
        cache.findById(result.firstClanId()).ifPresent(clan -> audit.log(
                clan,
                "CLAN_WAR_COMPLETED",
                clan.ownerId(),
                "SYSTEM",
                details
        ));
        cache.findById(result.secondClanId()).ifPresent(clan -> audit.log(
                clan,
                "CLAN_WAR_COMPLETED",
                clan.ownerId(),
                "SYSTEM",
                details
        ));
    }

    private static String warResultDetails(ClanWarResult result) {
        String outcome = result.draw()
                ? "draw=true"
                : "winner=" + result.winnerClanId()
                + " loser=" + result.loserClanId();
        return outcome
                + " firstDeaths=" + result.firstDeaths()
                + " secondDeaths=" + result.secondDeaths()
                + " war=" + result.warId();
    }

    private ClanRankingStats stats(UUID clanId) {
        return rankingStatsCache.computeIfAbsent(clanId, ClanRankingStats::empty);
    }

    private ClanBankView bankView(Clan clan, UUID playerId, BigDecimal balance)
            throws Exception {
        return new ClanBankView(
                clan.id(),
                balance,
                permissionDecision(clan, playerId, "bank.deposit").allowed(),
                permissionDecision(clan, playerId, "bank.withdraw").allowed(),
                permissionDecision(clan, playerId, "bank.log").allowed(),
                clan.ownerId().equals(playerId)
        );
    }

    private ClanHomeView homeView(Clan clan, UUID playerId) throws Exception {
        return new ClanHomeView(
                clan.id(),
                unlockedHomeSlots(clan.id()),
                homeSettings.absoluteMaxSlots(),
                repository.findHomes(clan.id()),
                permissionDecision(clan, playerId, "home.teleport").allowed(),
                permissionDecision(clan, playerId, "home.set").allowed(),
                permissionDecision(clan, playerId, "home.delete").allowed()
        );
    }

    private boolean homeSlotUnlocked(UUID clanId, int number) throws Exception {
        return number >= 1 && number <= unlockedHomeSlots(clanId);
    }

    private int unlockedHomeSlots(UUID clanId) throws Exception {
        long total = (long) homeSettings.defaultSlots()
                + repository.findClanUnlocks(clanId).bonusHomeSlots();
        return (int) Math.min(homeSettings.absoluteMaxSlots(), total);
    }

    private Optional<BigDecimal> bankAmount(BigDecimal requested, BigDecimal minimum) {
        if (requested == null || requested.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal normalized = bankSettings.normalize(requested);
        if (normalized.signum() <= 0 || normalized.compareTo(minimum) < 0) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private void updateCachedBankBalance(UUID clanId, BigDecimal balance) {
        ClanRankingStats current = stats(clanId);
        rankingStatsCache.put(clanId, new ClanRankingStats(
                current.clanId(),
                current.combatKills(),
                current.warsWon(),
                current.warsLost(),
                current.activeDays(),
                current.lastActiveDate(),
                balance
        ));
        rankingDirty = true;
    }

    private synchronized void rebuildRankingSnapshot() {
        if (!rankingSettings.enabled()) {
            rankingSnapshot = RankingSnapshot.empty();
            rankingDirty = false;
            return;
        }
        List<RankedClan> calculated = new ArrayList<>();
        for (Clan clan : cache.list()) {
            ClanRankingStats stats = stats(clan.id());
            Map<RankingCategory, BigDecimal> points =
                    RankingCalculator.categoryPoints(clan, stats, rankingSettings);
            calculated.add(new RankedClan(
                    clan,
                    stats,
                    points,
                    RankingCalculator.total(points)
            ));
        }

        EnumMap<RankingCategory, List<ClanRankingEntry>> snapshot =
                new EnumMap<>(RankingCategory.class);
        for (RankingCategory category : RankingCategory.values()) {
            List<RankedClan> sorted = new ArrayList<>(calculated);
            sorted.sort((left, right) -> RankingCalculator.compare(
                    left.clan(),
                    left.points(category),
                    right.clan(),
                    right.points(category)
            ));
            List<ClanRankingEntry> entries = new ArrayList<>(sorted.size());
            for (int index = 0; index < sorted.size(); index++) {
                RankedClan ranked = sorted.get(index);
                entries.add(new ClanRankingEntry(
                        index + 1,
                        ranked.clan(),
                        ranked.total(),
                        ranked.categoryPoints(),
                        ranked.stats()
                ));
            }
            snapshot.put(category, List.copyOf(entries));
        }
        Map<RankingCategory, List<ClanRankingEntry>> entries = Map.copyOf(snapshot);
        Map<UUID, ClanRankingEntry> totalByClan = new LinkedHashMap<>();
        for (ClanRankingEntry entry : entries.getOrDefault(
                RankingCategory.TOTAL,
                List.of()
        )) {
            totalByClan.put(entry.clan().id(), entry);
        }
        rankingSnapshot = new RankingSnapshot(entries, Map.copyOf(totalByClan));
        rankingDirty = false;
    }

    public CompletableFuture<BattlepassView> battlepassView(
            UUID clanId,
            int fromLevel,
            int toLevel
    ) {
        return submit(() -> {
            if (!battlepassSettings.enabled()) {
                throw new IllegalStateException("Battlepass is disabled");
            }
            BattlepassProgress progress = progress(clanId);
            return new BattlepassView(
                    clanId,
                    progress,
                    battlepassSettings.curve().requiredXp(progress.level()),
                    enabledRewards(repository.findBattlepassRewards(fromLevel, toLevel)),
                    repository.findClaimedRewardKeys(clanId, fromLevel, toLevel)
            );
        });
    }

    public CompletableFuture<List<BattlepassReward>> battlepassRewards(
            int fromLevel,
            int toLevel
    ) {
        return submit(() -> enabledRewards(
                repository.findBattlepassRewards(fromLevel, toLevel)
        ));
    }

    public CompletableFuture<OperationResult<XpAwardResult>> registerDailyLogin(
            UUID playerId,
            String playerName
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!battlepassSettings.enabled()) {
                return OperationResult.failure(OperationCode.BATTLEPASS_DISABLED);
            }
            LocalDate today = LocalDate.now(battlepassSettings.loginZone());
            Optional<DailyLoginState> previous = repository.findDailyLoginState(playerId);
            if (previous.isPresent() && previous.get().lastLoginDate().equals(today)) {
                return OperationResult.success(new XpAwardResult(
                        progress(found.get().id()),
                        BigDecimal.ZERO,
                        0,
                        previous.get().streakDays()
                ));
            }
            int streakDays = previous
                    .filter(state -> state.lastLoginDate().plusDays(1).equals(today))
                    .map(state -> state.streakDays() + 1)
                    .orElse(1);
            DailyLoginState state = new DailyLoginState(playerId, today, streakDays);
            BigDecimal xp = battlepassSettings.loginStreak().xpForDay(streakDays);
            BattlepassCurve.ProgressionResult progression = battlepassSettings.curve()
                    .addXp(progress(found.get().id()), xp);
            repository.saveDailyLoginAndBattlepass(state, progression.progress());
            battlepassCache.put(found.get().id(), progression.progress());
            logXp(
                    found.get(), playerId, playerName, xp, progression,
                    "DAILY_LOGIN", "streak=" + streakDays
            );
            return OperationResult.success(new XpAwardResult(
                    progression.progress(),
                    xp,
                    progression.levelsGained(),
                    streakDays
            ));
        });
    }

    public CompletableFuture<OperationResult<XpAwardResult>> awardOnlineXp(
            UUID clanId,
            int onlineMembers
    ) {
        return submit(() -> {
            Optional<Clan> clan = cache.findById(clanId);
            if (clan.isEmpty()) {
                return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
            }
            if (!battlepassSettings.enabled()) {
                return OperationResult.failure(OperationCode.BATTLEPASS_DISABLED);
            }
            BigDecimal amount = battlepassSettings.onlineXp()
                    .multiply(BigDecimal.valueOf(Math.max(0, onlineMembers)));
            return OperationResult.success(awardXp(
                    clan.get(),
                    clan.get().ownerId(),
                    "SYSTEM",
                    amount,
                    "ONLINE_ACTIVITY",
                    "onlineMembers=" + onlineMembers
            ));
        });
    }

    public CompletableFuture<OperationResult<XpAwardResult>> awardPvpKill(
            UUID killerId,
            String killerName,
            UUID victimId
    ) {
        return submit(() -> {
            Optional<Clan> killerClan = cache.findByPlayer(killerId);
            Optional<Clan> victimClan = cache.findByPlayer(victimId);
            return awardPvpKillInternal(
                    killerId,
                    killerName,
                    victimId,
                    Instant.now(),
                    killerClan,
                    victimClan
            );
        });
    }

    private OperationResult<XpAwardResult> awardPvpKillInternal(
            UUID killerId,
            String killerName,
            UUID victimId,
            Instant occurredAt,
            Optional<Clan> killerClan,
            Optional<Clan> victimClan
    ) throws Exception {
        if (killerClan.isEmpty()) {
            return OperationResult.failure(OperationCode.NOT_IN_CLAN);
        }
        if (!battlepassSettings.enabled()) {
            return OperationResult.failure(OperationCode.BATTLEPASS_DISABLED);
        }
        if (victimClan.isPresent()
                && victimClan.get().id().equals(killerClan.get().id())
                && !battlepassSettings.allowSameClanKills()) {
            return OperationResult.failure(OperationCode.REWARD_NOT_AVAILABLE);
        }
        Optional<Instant> previous = repository.findPvpRewardTime(victimId);
        if (previous.isPresent() && previous.get()
                .plus(Duration.ofMinutes(battlepassSettings.pvpCooldownMinutes()))
                .isAfter(occurredAt)) {
            return OperationResult.failure(OperationCode.PVP_REWARD_COOLDOWN);
        }
        BigDecimal xp = battlepassSettings.pvpKillXp();
        BattlepassCurve.ProgressionResult progression = battlepassSettings.curve()
                .addXp(progress(killerClan.get().id()), xp);
        repository.savePvpRewardAndBattlepass(
                victimId,
                occurredAt,
                progression.progress()
        );
        battlepassCache.put(killerClan.get().id(), progression.progress());
        logXp(
                killerClan.get(), killerId, killerName, xp, progression,
                "PVP_KILL", "victim=" + victimId
        );
        return OperationResult.success(new XpAwardResult(
                progression.progress(),
                xp,
                progression.levelsGained(),
                0
        ));
    }

    public CompletableFuture<OperationResult<XpAwardResult>> removeWarLossXp(
            UUID clanId,
            BigDecimal amount,
            UUID actorId,
        String actorName
    ) {
        return submit(() -> {
            if (!battlepassSettings.enabled()) {
                return OperationResult.failure(OperationCode.BATTLEPASS_DISABLED);
            }
            Optional<Clan> clan = cache.findById(clanId);
            if (clan.isEmpty()) {
                return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
            }
            BattlepassProgress previous = progress(clanId);
            BattlepassProgress updated = battlepassSettings.curve()
                    .removeWithinCurrentLevel(previous, amount);
            BigDecimal removed = previous.currentXp().subtract(updated.currentXp());
            repository.saveBattlepassProgress(updated);
            battlepassCache.put(clanId, updated);
            audit.log(clan.get(), "BATTLEPASS_WAR_XP_REMOVED", actorId, actorName,
                    "amount=" + removed.toPlainString()
                            + " level=" + updated.level()
                            + " currentXp=" + updated.currentXp().toPlainString());
            return OperationResult.success(new XpAwardResult(
                    updated,
                    removed.negate(),
                    0,
                    0
            ));
        });
    }

    public CompletableFuture<OperationResult<Void>> adjustBattlepassReward(
            UUID adminId,
            int level,
            BattlepassRewardType type,
            int delta
    ) {
        return submit(() -> {
            if (!battlepassSettings.enabled()) {
                return OperationResult.failure(OperationCode.BATTLEPASS_DISABLED);
            }
            if (level < 1 || delta == 0) {
                return OperationResult.failure(OperationCode.REWARD_NOT_AVAILABLE);
            }
            if (!battlepassSettings.enabledRewardTypes().contains(type)) {
                return OperationResult.failure(OperationCode.REWARD_NOT_AVAILABLE);
            }
            Optional<BattlepassReward> current = repository
                    .findBattlepassRewards(level, level)
                    .stream()
                    .filter(reward -> reward.type() == type)
                    .findFirst();
            int amount = current.map(BattlepassReward::amount).orElse(0) + delta;
            if (amount <= 0) {
                repository.deleteBattlepassReward(level, type);
                return OperationResult.success(null);
            }
            repository.saveBattlepassReward(new BattlepassReward(
                    level,
                    type,
                    amount,
                    adminId,
                    Instant.now()
            ));
            return OperationResult.success(null);
        });
    }

    public CompletableFuture<OperationResult<BattlepassView>> claimBattlepassLevel(
            UUID ownerId,
            String ownerName,
            int level
    ) {
        return submit(() -> {
            Optional<Clan> clan = ownerClan(ownerId);
            if (clan.isEmpty()) {
                return OperationResult.failure(ownerFailure(ownerId));
            }
            BattlepassProgress progress = progress(clan.get().id());
            if (level < 1 || progress.level() < level) {
                return OperationResult.failure(OperationCode.REWARD_NOT_AVAILABLE);
            }
            List<BattlepassReward> rewards = enabledRewards(
                    repository.findBattlepassRewards(level, level)
            );
            if (rewards.isEmpty()) {
                return OperationResult.failure(OperationCode.REWARD_NOT_AVAILABLE);
            }
            Set<String> claimed = repository.findClaimedRewardKeys(
                    clan.get().id(),
                    level,
                    level
            );
            List<BattlepassReward> pending = rewards.stream()
                    .filter(reward -> !claimed.contains(rewardKey(reward)))
                    .toList();
            if (pending.isEmpty()) {
                return OperationResult.failure(OperationCode.REWARD_ALREADY_CLAIMED);
            }
            if (!rewardsFitLimits(clan.get(), pending)) {
                return OperationResult.failure(OperationCode.REWARD_LIMIT_REACHED);
            }
            for (BattlepassReward reward : pending) {
                RewardClaimResult result = repository.claimBattlepassReward(
                        clan.get().id(),
                        ownerId,
                        reward,
                        battlepassSettings.absoluteMaxMembers(),
                        battlepassSettings.absoluteMaxRoles(),
                        battlepassSettings.absoluteMaxVaultPages(),
                        battlepassSettings.absoluteMaxBonusHomeSlots(),
                        defaultMaxRoles
                );
                if (result.claimed()) {
                    audit.log(clan.get(), "BATTLEPASS_REWARD_CLAIMED", ownerId, ownerName,
                            "level=" + level + " type=" + reward.type()
                                    + " amount=" + reward.amount());
                }
            }
            Clan updatedClan = repository.findById(clan.get().id()).orElseThrow();
            cache.put(updatedClan);
            return OperationResult.success(new BattlepassView(
                    updatedClan.id(),
                    progress,
                    battlepassSettings.curve().requiredXp(progress.level()),
                    rewards,
                    repository.findClaimedRewardKeys(updatedClan.id(), level, level)
            ));
        });
    }

    public CompletableFuture<OperationResult<VaultPageView>> openVault(
            UUID playerId,
            int requestedPage
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!vaultSettings.enabled()) {
                return OperationResult.failure(OperationCode.VAULT_DISABLED);
            }
            Clan clan = found.get();
            PermissionDecision view = permissionDecision(clan, playerId, "vault.view");
            if (!view.allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            ClanUnlocks unlocks = repository.findClanUnlocks(clan.id());
            int page = Math.max(1, Math.min(requestedPage, unlocks.vaultPages()));
            return OperationResult.success(new VaultPageView(
                    clan.id(),
                    page,
                    unlocks.vaultPages(),
                    repository.findVaultPage(clan.id(), page),
                    permissionDecision(clan, playerId, "vault.deposit").allowed(),
                    permissionDecision(clan, playerId, "vault.withdraw").allowed(),
                    permissionDecision(clan, playerId, "vault.log").allowed(),
                    permissionDecision(
                            clan,
                            playerId,
                            "vault.extensions.manage"
                    ).allowed()
            ));
        });
    }

    public CompletableFuture<OperationResult<List<ClanMember>>> vaultLogMembers(
            UUID playerId
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!vaultSettings.enabled()) {
                return OperationResult.failure(OperationCode.VAULT_DISABLED);
            }
            if (!permissionDecision(found.get(), playerId, "vault.log").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            return OperationResult.success(List.copyOf(found.get().members()));
        });
    }

    public CompletableFuture<OperationResult<List<AuditLogEntry>>> vaultLogEntries(
            UUID playerId,
            UUID actorId,
            int limit
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!vaultSettings.enabled()) {
                return OperationResult.failure(OperationCode.VAULT_DISABLED);
            }
            if (!permissionDecision(found.get(), playerId, "vault.log").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            boolean member = found.get().members().stream()
                    .anyMatch(clanMember -> clanMember.playerId().equals(actorId));
            if (!member) {
                return OperationResult.failure(OperationCode.MEMBER_NOT_FOUND);
            }
            return OperationResult.success(vaultAudit.recent(
                    found.get().id(),
                    actorId,
                    Math.max(1, Math.min(limit, 18))
            ));
        });
    }

    public CompletableFuture<OperationResult<ClanBankView>> openBank(UUID playerId) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!bankSettings.enabled()) {
                return OperationResult.failure(OperationCode.BANK_DISABLED);
            }
            if (!permissionDecision(found.get(), playerId, "bank.view").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            return OperationResult.success(bankView(
                    found.get(),
                    playerId,
                    repository.findBankBalance(found.get().id())
            ));
        });
    }

    public CompletableFuture<OperationResult<ClanBankView>> depositBank(
            UUID playerId,
            String playerName,
            BigDecimal requestedAmount
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!bankSettings.enabled()) {
                return OperationResult.failure(OperationCode.BANK_DISABLED);
            }
            Clan clan = found.get();
            if (!permissionDecision(clan, playerId, "bank.deposit").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            Optional<BigDecimal> amount = bankAmount(
                    requestedAmount,
                    bankSettings.minimumDeposit()
            );
            if (amount.isEmpty()) {
                return OperationResult.failure(OperationCode.INVALID_BANK_AMOUNT);
            }
            Optional<BigDecimal> balance = repository.depositBank(
                    clan.id(),
                    amount.get(),
                    Instant.now()
            );
            BigDecimal updatedBalance = balance.orElseThrow(
                    () -> new IllegalStateException(
                            "Clan bank deposit could not be saved"
                    )
            );
            updateCachedBankBalance(clan.id(), updatedBalance);
            if (bankSettings.logDeposits()) {
                bankAudit.log(clan, "BANK_DEPOSIT", playerId, playerName,
                        "amount=" + amount.get().toPlainString()
                                + " balance=" + updatedBalance.toPlainString());
            }
            return OperationResult.success(bankView(clan, playerId, updatedBalance));
        });
    }

    public CompletableFuture<OperationResult<ClanBankView>> withdrawBank(
            UUID playerId,
            String playerName,
            BigDecimal requestedAmount
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!bankSettings.enabled()) {
                return OperationResult.failure(OperationCode.BANK_DISABLED);
            }
            Clan clan = found.get();
            if (!permissionDecision(clan, playerId, "bank.withdraw").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            Optional<BigDecimal> amount = bankAmount(
                    requestedAmount,
                    bankSettings.minimumWithdrawal()
            );
            if (amount.isEmpty()) {
                return OperationResult.failure(OperationCode.INVALID_BANK_AMOUNT);
            }
            Optional<BigDecimal> balance = repository.withdrawBank(
                    clan.id(),
                    amount.get(),
                    Instant.now()
            );
            if (balance.isEmpty()) {
                return OperationResult.failure(OperationCode.BANK_INSUFFICIENT_FUNDS);
            }
            updateCachedBankBalance(clan.id(), balance.get());
            if (bankSettings.logWithdrawals()) {
                bankAudit.log(clan, "BANK_WITHDRAW", playerId, playerName,
                        "amount=" + amount.get().toPlainString()
                                + " balance=" + balance.get().toPlainString());
            }
            return OperationResult.success(bankView(clan, playerId, balance.get()));
        });
    }

    public CompletableFuture<Void> restoreBankWithdrawal(
            UUID clanId,
            BigDecimal amount,
            UUID actorId,
            String actorName
    ) {
        return submitCritical(() -> {
            BigDecimal balance = repository.restoreBankBalance(
                    clanId,
                    bankSettings.normalize(amount),
                    Instant.now()
            );
            updateCachedBankBalance(clanId, balance);
            cache.findById(clanId).ifPresent(clan -> bankAudit.log(
                    clan,
                    "BANK_WITHDRAW_ROLLBACK",
                    actorId,
                    actorName,
                    "amount=" + amount.toPlainString()
                            + " balance=" + balance.toPlainString()
            ));
            return null;
        });
    }

    public CompletableFuture<OperationResult<List<ClanMember>>> bankLogMembers(
            UUID playerId
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!bankSettings.enabled()) {
                return OperationResult.failure(OperationCode.BANK_DISABLED);
            }
            if (!permissionDecision(found.get(), playerId, "bank.log").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            return OperationResult.success(List.copyOf(found.get().members()));
        });
    }

    public CompletableFuture<OperationResult<List<AuditLogEntry>>> bankLogEntries(
            UUID playerId,
            UUID actorId
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!bankSettings.enabled()) {
                return OperationResult.failure(OperationCode.BANK_DISABLED);
            }
            if (!permissionDecision(found.get(), playerId, "bank.log").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            boolean isMember = found.get().members().stream()
                    .anyMatch(clanMember -> clanMember.playerId().equals(actorId));
            if (!isMember) {
                return OperationResult.failure(OperationCode.MEMBER_NOT_FOUND);
            }
            return OperationResult.success(bankAudit.recent(
                    found.get().id(),
                    actorId,
                    bankSettings.maximumLogEntries()
            ));
        });
    }

    public CompletableFuture<OperationResult<ClanHomeView>> openHomes(UUID playerId) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!homeSettings.enabled()) {
                return OperationResult.failure(OperationCode.HOME_DISABLED);
            }
            if (!permissionDecision(found.get(), playerId, "home.view").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            return OperationResult.success(homeView(found.get(), playerId));
        });
    }

    public CompletableFuture<OperationResult<ClanHomeView>> setHome(
            UUID playerId,
            String playerName,
            int number,
            UUID worldId,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!homeSettings.enabled()) {
                return OperationResult.failure(OperationCode.HOME_DISABLED);
            }
            Clan clan = found.get();
            if (!permissionDecision(clan, playerId, "home.set").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            if (!homeSlotUnlocked(clan.id(), number)) {
                return OperationResult.failure(OperationCode.HOME_SLOT_LOCKED);
            }
            ClanHome home = new ClanHome(
                    clan.id(),
                    number,
                    worldId,
                    worldName,
                    x,
                    y,
                    z,
                    yaw,
                    pitch,
                    playerId,
                    Instant.now()
            );
            repository.saveHome(home);
            audit.log(
                    clan,
                    "HOME_SET",
                    playerId,
                    playerName,
                    "home=" + number + " world=" + worldName
                            + " x=" + x + " y=" + y + " z=" + z
            );
            return OperationResult.success(homeView(clan, playerId));
        });
    }

    public CompletableFuture<OperationResult<ClanHomeView>> deleteHome(
            UUID playerId,
            String playerName,
            int number
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!homeSettings.enabled()) {
                return OperationResult.failure(OperationCode.HOME_DISABLED);
            }
            Clan clan = found.get();
            if (!permissionDecision(clan, playerId, "home.delete").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            if (!homeSlotUnlocked(clan.id(), number)) {
                return OperationResult.failure(OperationCode.HOME_SLOT_LOCKED);
            }
            if (!repository.deleteHome(clan.id(), number)) {
                return OperationResult.failure(OperationCode.HOME_NOT_SET);
            }
            audit.log(
                    clan,
                    "HOME_DELETE",
                    playerId,
                    playerName,
                    "home=" + number
            );
            return OperationResult.success(homeView(clan, playerId));
        });
    }

    public CompletableFuture<OperationResult<ClanHome>> homeForTeleport(
            UUID playerId,
            int number
    ) {
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!homeSettings.enabled()) {
                return OperationResult.failure(OperationCode.HOME_DISABLED);
            }
            Clan clan = found.get();
            if (!permissionDecision(clan, playerId, "home.teleport").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            if (!homeSlotUnlocked(clan.id(), number)) {
                return OperationResult.failure(OperationCode.HOME_SLOT_LOCKED);
            }
            return repository.findHome(clan.id(), number)
                    .map(OperationResult::success)
                    .orElseGet(() -> OperationResult.failure(
                            OperationCode.HOME_NOT_SET
                    ));
        });
    }

    public CompletableFuture<Void> recordHomeTeleport(
            UUID playerId,
            String playerName,
            int number
    ) {
        return submit(() -> {
            cache.findByPlayer(playerId).ifPresent(clan -> audit.log(
                    clan,
                    "HOME_TELEPORT",
                    playerId,
                    playerName,
                    "home=" + number
            ));
            return null;
        });
    }

    public CompletableFuture<OperationResult<Void>> saveVaultSlot(
            UUID playerId,
            String playerName,
            int page,
            int slot,
            byte[] itemData,
            VaultMutationType mutation,
            String details
    ) {
        byte[] defensive = itemData == null ? null : itemData.clone();
        return submit(() -> {
            Optional<Clan> found = cache.findByPlayer(playerId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!vaultSettings.enabled()) {
                return OperationResult.failure(OperationCode.VAULT_DISABLED);
            }
            Clan clan = found.get();
            ClanUnlocks unlocks = repository.findClanUnlocks(clan.id());
            if (page < 1 || page > unlocks.vaultPages()
                    || slot < 0 || slot >= vaultSettings.slotsPerPage()) {
                return OperationResult.failure(OperationCode.VAULT_PAGE_LOCKED);
            }
            if (defensive != null
                    && defensive.length > vaultSettings.maxSerializedItemBytes()) {
                return OperationResult.failure(OperationCode.VAULT_ITEM_TOO_LARGE);
            }
            boolean deposit = permissionDecision(clan, playerId, "vault.deposit").allowed();
            boolean withdraw = permissionDecision(clan, playerId, "vault.withdraw").allowed();
            boolean allowed = switch (mutation) {
                case DEPOSIT -> deposit;
                case WITHDRAW -> withdraw;
                case REPLACE -> deposit && withdraw;
            };
            if (!allowed) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            repository.saveVaultSlot(clan.id(), page, slot, defensive);
            if (vaultSettings.logs(mutation)) {
                vaultAudit.log(clan, "VAULT_" + mutation.name(), playerId, playerName,
                        "page=" + page + " slot=" + slot + " " + details);
            }
            return OperationResult.success(null);
        });
    }

    public CompletableFuture<Void> restoreVaultSlot(
            UUID clanId,
            int page,
            int slot,
            byte[] itemData,
            UUID actorId,
        String actorName
    ) {
        byte[] defensive = itemData == null ? null : itemData.clone();
        return submitCritical(() -> {
            Exception lastFailure = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    repository.saveVaultSlot(clanId, page, slot, defensive);
                    lastFailure = null;
                    break;
                } catch (Exception exception) {
                    lastFailure = exception;
                    if (attempt < 3) {
                        Thread.sleep(10L * attempt);
                    }
                }
            }
            if (lastFailure != null) {
                throw lastFailure;
            }
            cache.findById(clanId).ifPresent(clan -> vaultAudit.log(
                    clan,
                    "VAULT_ROLLBACK",
                    actorId,
                    actorName,
                    "page=" + page + " slot=" + slot
            ));
            return null;
        });
    }

    public CompletableFuture<OperationResult<DiplomacyView>> findDiplomacyView(
            UUID playerId,
            UUID targetClanId
    ) {
        return submit(() -> {
            if (!diplomacySettings.enabled()) {
                return OperationResult.failure(OperationCode.DIPLOMACY_DISABLED);
            }
            Optional<Clan> source = cache.findByPlayer(playerId);
            Optional<Clan> target = cache.findById(targetClanId);
            if (source.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (target.isEmpty()) {
                return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
            }
            if (source.get().id().equals(targetClanId)) {
                return OperationResult.failure(OperationCode.DIPLOMACY_SELF_TARGET);
            }
            return OperationResult.success(repository.findDiplomacyView(
                    source.get().id(),
                    targetClanId,
                    Instant.now()
            ));
        });
    }

    public CompletableFuture<OperationResult<AdminWarEndResult>> endWarAsAdmin(
            String firstClanSearch,
            String secondClanSearch,
            UUID actorId,
            String actorName
    ) {
        return submitCritical(() -> {
            Optional<Clan> firstClan = cache.findByNameOrTag(
                    rules.normalizeKey(firstClanSearch)
            );
            Optional<Clan> secondClan = cache.findByNameOrTag(
                    rules.normalizeKey(secondClanSearch)
            );
            if (firstClan.isEmpty() || secondClan.isEmpty()) {
                return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
            }
            if (firstClan.get().id().equals(secondClan.get().id())) {
                return OperationResult.failure(OperationCode.DIPLOMACY_SELF_TARGET);
            }

            Instant endedAt = Instant.now();
            var endedResult = repository.endActiveWar(
                    firstClan.get().id(),
                    secondClan.get().id(),
                    endedAt
            );
            if (endedResult.isEmpty()) {
                return OperationResult.failure(OperationCode.WAR_NOT_ACTIVE);
            }
            ClanWarResult result = endedResult.get();
            result.winner().ifPresent(winnerId -> {
                updateWarStats(winnerId, true);
                result.loser().ifPresent(loserId -> updateWarStats(loserId, false));
            });
            rankingDirty = true;
            rebuildRankingSnapshot();

            String details = warResultDetails(result) + " endedAt=" + endedAt;
            audit.log(firstClan.get(), "ADMIN_WAR_ENDED", actorId, actorName, details);
            audit.log(secondClan.get(), "ADMIN_WAR_ENDED", actorId, actorName, details);
            return OperationResult.success(new AdminWarEndResult(
                    firstClan.get(),
                    secondClan.get(),
                    result
            ));
        });
    }

    public boolean alliancesEnabled() {
        return diplomacySettings.alliancesEnabled();
    }

    public boolean warsEnabled() {
        return diplomacySettings.warsEnabled();
    }

    public CompletableFuture<OperationResult<List<DiplomacyRequest>>>
            findIncomingAllyRequests(UUID actorId) {
        return submit(() -> {
            if (!diplomacySettings.alliancesEnabled()) {
                return OperationResult.failure(OperationCode.DIPLOMACY_DISABLED);
            }
            Optional<Clan> clan = cache.findByPlayer(actorId);
            if (clan.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!permissionDecision(clan.get(), actorId, "diplomacy.manage").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            return OperationResult.success(repository.findIncomingDiplomacyRequests(
                    clan.get().id(),
                    DiplomacyType.ALLY,
                    Instant.now()
            ));
        });
    }

    public CompletableFuture<OperationResult<DiplomacyView>> sendDiplomacyRequest(
            UUID actorId,
            String actorName,
            UUID targetClanId,
            DiplomacyType type,
            int warDurationHours
    ) {
        return submit(() -> {
            if (!diplomacySettings.enabled(type)) {
                return OperationResult.failure(OperationCode.DIPLOMACY_DISABLED);
            }
            Optional<Clan> source = cache.findByPlayer(actorId);
            Optional<Clan> target = cache.findById(targetClanId);
            if (source.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (target.isEmpty()) {
                return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
            }
            Clan sourceClan = source.get();
            if (sourceClan.id().equals(targetClanId)) {
                return OperationResult.failure(OperationCode.DIPLOMACY_SELF_TARGET);
            }
            if (!permissionDecision(sourceClan, actorId, "diplomacy.manage").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            if (type == DiplomacyType.WAR
                    && !diplomacySettings.allowedWarDurations().contains(warDurationHours)) {
                return OperationResult.failure(OperationCode.INVALID_WAR_DURATION);
            }
            int requestedDurationHours = type == DiplomacyType.ALLY
                    ? 0
                    : warDurationHours;
            Instant now = Instant.now();
            DiplomacyView current = repository.findDiplomacyView(
                    sourceClan.id(),
                    targetClanId,
                    now
            );
            if (current.allied() || current.activeWar().isPresent()) {
                return OperationResult.failure(OperationCode.DIPLOMACY_RELATION_EXISTS);
            }
            boolean requestPending = type == DiplomacyType.ALLY
                    ? current.incomingAllyRequest().isPresent()
                    || current.outgoingAllyRequest().isPresent()
                    : current.incomingWarRequest().isPresent()
                    || current.outgoingWarRequest().isPresent();
            if (requestPending) {
                return OperationResult.failure(OperationCode.DIPLOMACY_REQUEST_PENDING);
            }
            if (diplomacySettings.maximumPendingRequestsPerClan() > 0
                    && repository.countPendingDiplomacyRequests(sourceClan.id(), now)
                    >= diplomacySettings.maximumPendingRequestsPerClan()) {
                return OperationResult.failure(OperationCode.DIPLOMACY_REQUEST_LIMIT);
            }
            DiplomacyRequest request = new DiplomacyRequest(
                    UUID.randomUUID(),
                    sourceClan.id(),
                    targetClanId,
                    type,
                    requestedDurationHours,
                    actorId,
                    now,
                    now.plus(diplomacySettings.requestDuration())
            );
            if (!repository.saveDiplomacyRequest(request)) {
                return OperationResult.failure(OperationCode.DIPLOMACY_REQUEST_PENDING);
            }
            audit.log(
                    sourceClan,
                    type == DiplomacyType.ALLY
                            ? "ALLY_REQUEST_SENT"
                            : "WAR_REQUEST_SENT",
                    actorId,
                    actorName,
                    "targetClan=" + targetClanId
                            + " durationHours=" + requestedDurationHours
            );
            return OperationResult.success(repository.findDiplomacyView(
                    sourceClan.id(),
                    targetClanId,
                    now
            ));
        });
    }

    public CompletableFuture<OperationResult<DiplomacyView>> respondDiplomacyRequest(
            UUID actorId,
            String actorName,
            UUID requestId,
            boolean accept
    ) {
        return submit(() -> {
            if (!diplomacySettings.enabled()) {
                return OperationResult.failure(OperationCode.DIPLOMACY_DISABLED);
            }
            Optional<DiplomacyRequest> found = repository.findDiplomacyRequest(requestId);
            if (found.isEmpty()) {
                return OperationResult.failure(OperationCode.DIPLOMACY_REQUEST_NOT_FOUND);
            }
            DiplomacyRequest request = found.get();
            if (!diplomacySettings.enabled(request.type())) {
                return OperationResult.failure(OperationCode.DIPLOMACY_DISABLED);
            }
            Instant now = Instant.now();
            if (request.expired(now)) {
                repository.declineDiplomacyRequest(requestId);
                return OperationResult.failure(OperationCode.DIPLOMACY_REQUEST_NOT_FOUND);
            }
            Optional<Clan> target = cache.findByPlayer(actorId);
            if (target.isEmpty()) {
                return OperationResult.failure(OperationCode.NOT_IN_CLAN);
            }
            if (!target.get().id().equals(request.targetClanId())) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            if (!permissionDecision(target.get(), actorId, "diplomacy.manage").allowed()) {
                return OperationResult.failure(OperationCode.CLAN_RIGHT_MISSING);
            }
            if (accept) {
                DiplomacyView current = repository.findDiplomacyView(
                        request.targetClanId(),
                        request.sourceClanId(),
                        now
                );
                if (current.allied() || current.activeWar().isPresent()) {
                    return OperationResult.failure(OperationCode.DIPLOMACY_RELATION_EXISTS);
                }
                repository.acceptDiplomacyRequest(request, actorId, now);
            } else {
                repository.declineDiplomacyRequest(requestId);
            }
            audit.log(
                    target.get(),
                    request.type().name() + "_REQUEST_" + (accept ? "ACCEPTED" : "DECLINED"),
                    actorId,
                    actorName,
                    "sourceClan=" + request.sourceClanId()
                            + " durationHours=" + request.warDurationHours()
            );
            return OperationResult.success(repository.findDiplomacyView(
                    request.targetClanId(),
                    request.sourceClanId(),
                    now
            ));
        });
    }

    @Override
    public void close() throws Exception {
        worker.shutdown();
        if (!worker.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
            worker.shutdownNow().forEach(task -> {
                if (task instanceof ServiceTask<?> serviceTask) {
                    serviceTask.reject(new IllegalStateException(
                            "Plugin is shutting down; operation was discarded"
                    ));
                }
            });
            if (!worker.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "ClanService worker did not stop in time; "
                                + "SQLite remains open to protect data"
                );
            }
        }
        repository.close();
    }

    private OperationResult<Clan> addMember(
            Clan clan,
            UUID playerId,
            String playerName,
            String action
    ) throws Exception {
        if (clan.isFull()) {
            return OperationResult.failure(OperationCode.CLAN_FULL);
        }
        Instant now = Instant.now();
        Clan updated = clan.withMember(new ClanMember(playerId, playerName, RankId.RECRUIT, now));
        repository.saveAndDeleteInvitesForPlayer(updated, playerId);
        cache.put(updated);
        rankingDirty = true;
        if (rankingSettings.enabled()) {
            recordCurrentRankingDay(updated.id());
        }
        audit.log(updated, action, playerId, playerName, "joined as RECRUIT");
        return OperationResult.success(updated);
    }

    private void deleteInviteUnchecked(UUID clanId, UUID playerId) {
        try {
            repository.deleteInvite(clanId, playerId);
        } catch (Exception ignored) {
            // The operation itself already returns INVITE_NOT_FOUND.
        }
    }

    private Optional<Clan> ownerClan(UUID ownerId) {
        return cache.findByPlayer(ownerId)
                .filter(clan -> clan.ownerId().equals(ownerId));
    }

    private OperationCode ownerFailure(UUID playerId) {
        return cache.findByPlayer(playerId).isPresent()
                ? OperationCode.OWNER_ONLY
                : OperationCode.NOT_IN_CLAN;
    }

    private Optional<ClanMember> selectOwnershipSuccessor(Clan clan, UUID leavingOwnerId) {
        Comparator<ClanMember> order = Comparator
                .comparingInt((ClanMember member) -> findRole(clan.id(), member.roleId())
                        .map(ClanRole::priority)
                        .orElse(0))
                .reversed()
                .thenComparing(ClanMember::joinedAt)
                .thenComparing(member -> member.playerId().toString());
        return clan.members().stream()
                .filter(member -> !member.playerId().equals(leavingOwnerId))
                .min(order);
    }

    private OperationResult<Clan> deleteClan(
            Clan clan,
            UUID actorId,
            String actorName,
            String action
    ) throws Exception {
        if (!repository.deleteClan(clan.id())) {
            return OperationResult.failure(OperationCode.CLAN_NOT_FOUND);
        }
        cache.remove(clan.id());
        roleCache.remove(clan.id());
        rolePermissionCache.remove(clan.id());
        memberPermissionCache.remove(clan.id());
        battlepassCache.remove(clan.id());
        rankingStatsCache.remove(clan.id());
        rankingDirty = true;
        rebuildRankingSnapshot();
        audit.log(clan, action, actorId, actorName,
                "members=" + clan.members().size());
        return OperationResult.success(clan);
    }

    private List<ClanRole> rolesForClan(UUID clanId) {
        return roleSnapshot(clanId).sortedRoles();
    }

    private RoleSnapshot roleSnapshot(UUID clanId) {
        return roleCache.computeIfAbsent(
                clanId,
                ignored -> createRoleSnapshot(clanId, List.of())
        );
    }

    private RoleSnapshot createRoleSnapshot(UUID clanId, Iterable<ClanRole> persistedRoles) {
        Map<String, ClanRole> rolesById = new LinkedHashMap<>();
        rankPolicy.standardRoles(clanId)
                .forEach(role -> rolesById.put(role.id(), role));
        persistedRoles.forEach(role -> rolesById.put(role.id(), role));
        List<ClanRole> sortedRoles = rolesById.values().stream()
                .sorted(Comparator.comparingInt(ClanRole::priority)
                        .reversed()
                        .thenComparing(ClanRole::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new RoleSnapshot(sortedRoles, Map.copyOf(rolesById));
    }

    private Optional<ClanRole> findRole(UUID clanId, String roleId) {
        return Optional.ofNullable(roleSnapshot(clanId).rolesById().get(roleId));
    }

    private void cacheRole(ClanRole role) {
        roleCache.compute(role.clanId(), (clanId, current) -> {
            Map<String, ClanRole> rolesById = new LinkedHashMap<>();
            if (current != null) {
                rolesById.putAll(current.rolesById());
            }
            rolesById.put(role.id(), role);
            return createRoleSnapshot(clanId, rolesById.values());
        });
    }

    private RolePermissionView rolePermissionView(ClanRole role) throws Exception {
        return rolePermissionView(role, rolePermissions(role));
    }

    private RolePermissionView rolePermissionView(
            ClanRole role,
            Map<String, Boolean> stored
    ) {
        Map<String, Boolean> effective = new LinkedHashMap<>();
        for (String permission : knownRights) {
            effective.put(
                    permission,
                    rolePermissionAllowed(role, permission, stored)
            );
        }
        return new RolePermissionView(role, knownRights, effective);
    }

    private boolean rolePermissionAllowed(
            ClanRole role,
            String permission
    ) throws Exception {
        return rolePermissionAllowed(role, permission, rolePermissions(role));
    }

    private boolean rolePermissionAllowed(
            ClanRole role,
            String permission,
            Map<String, Boolean> stored
    ) {
        if ("owner".equals(role.id())) {
            return true;
        }
        Boolean configured = stored.get(permission);
        if (configured != null) {
            return configured;
        }
        return RankId.fromRoleId(role.id())
                .map(rank -> rankPolicy.has(rank, permission))
                .orElse(false);
    }

    private PermissionDecision permissionDecision(
            Clan clan,
            UUID memberId,
            String permission
    ) throws Exception {
        if (clan.ownerId().equals(memberId)) {
            return new PermissionDecision(true, false);
        }
        Map<String, Boolean> memberPermissions = memberPermissions(clan.id(), memberId);
        if (memberPermissions.containsKey(permission)) {
            boolean allowed = Boolean.TRUE.equals(memberPermissions.get(permission));
            return new PermissionDecision(allowed, allowed);
        }
        Optional<ClanMember> member = clan.member(memberId);
        if (member.isEmpty()) {
            return new PermissionDecision(false, false);
        }
        Optional<ClanRole> role = findRole(clan.id(), member.get().roleId());
        if (role.isEmpty()) {
            return new PermissionDecision(false, false);
        }
        return new PermissionDecision(
                rolePermissionAllowed(role.get(), permission),
                false
        );
    }

    private Map<String, Boolean> rolePermissions(ClanRole role) throws Exception {
        Map<String, Map<String, Boolean>> clanPermissions =
                rolePermissionCache.get(role.clanId());
        Map<String, Boolean> cached = clanPermissions == null
                ? null
                : clanPermissions.get(role.id());
        if (cached != null) {
            return cached;
        }
        Map<String, Boolean> loaded = Map.copyOf(repository.findRolePermissions(
                role.clanId(),
                role.id()
        ));
        rolePermissionCache.compute(role.clanId(), (ignored, current) ->
                updatedPermissionCache(current, role.id(), loaded));
        return rolePermissionCache.get(role.clanId()).get(role.id());
    }

    private Map<String, Boolean> memberPermissions(UUID clanId, UUID memberId)
            throws Exception {
        Map<UUID, Map<String, Boolean>> clanPermissions =
                memberPermissionCache.get(clanId);
        Map<String, Boolean> cached = clanPermissions == null
                ? null
                : clanPermissions.get(memberId);
        if (cached != null) {
            return cached;
        }
        Map<String, Boolean> loaded = Map.copyOf(
                repository.findMemberPermissions(clanId, memberId)
        );
        memberPermissionCache.compute(clanId, (ignored, current) ->
                updatedPermissionCache(current, memberId, loaded));
        return memberPermissionCache.get(clanId).get(memberId);
    }

    private void evictMemberPermissions(UUID clanId, UUID memberId) {
        memberPermissionCache.computeIfPresent(clanId, (ignored, current) -> {
            if (!current.containsKey(memberId)) {
                return current;
            }
            Map<UUID, Map<String, Boolean>> updated = new LinkedHashMap<>(current);
            updated.remove(memberId);
            return updated.isEmpty() ? null : Map.copyOf(updated);
        });
    }

    private static <K> Map<K, Map<String, Boolean>> updatedPermissionCache(
            Map<K, Map<String, Boolean>> current,
            K key,
            Map<String, Boolean> permissions
    ) {
        Map<K, Map<String, Boolean>> updated = current == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(current);
        updated.put(key, permissions);
        return Map.copyOf(updated);
    }

    private static Map<String, Boolean> withPermission(
            Map<String, Boolean> current,
            String permission,
            Boolean allowed
    ) {
        Map<String, Boolean> updated = new LinkedHashMap<>(current);
        if (allowed == null) {
            updated.remove(permission);
        } else {
            updated.put(permission, allowed);
        }
        return Map.copyOf(updated);
    }

    private BattlepassProgress progress(UUID clanId) throws Exception {
        BattlepassProgress cached = battlepassCache.get(clanId);
        if (cached != null) {
            return cached;
        }
        BattlepassProgress loaded = repository.findBattlepassProgress(clanId, Instant.now());
        battlepassCache.put(clanId, loaded);
        return loaded;
    }

    private XpAwardResult awardXp(
            Clan clan,
            UUID actorId,
            String actorName,
            BigDecimal amount,
            String source,
            String details
    ) throws Exception {
        BattlepassCurve.ProgressionResult result = battlepassSettings.curve()
                .addXp(progress(clan.id()), amount);
        repository.saveBattlepassProgress(result.progress());
        battlepassCache.put(clan.id(), result.progress());
        logXp(clan, actorId, actorName, amount, result, source, details);
        return new XpAwardResult(
                result.progress(),
                amount,
                result.levelsGained(),
                0
        );
    }

    private void logXp(
            Clan clan,
            UUID actorId,
            String actorName,
            BigDecimal amount,
            BattlepassCurve.ProgressionResult result,
            String source,
            String details
    ) {
        audit.log(clan, "BATTLEPASS_XP_ADDED", actorId, actorName,
                "source=" + source
                        + " amount=" + amount.toPlainString()
                        + " level=" + result.progress().level()
                        + " currentXp=" + result.progress().currentXp().toPlainString()
                        + " " + details);
    }

    private boolean rewardsFitLimits(Clan clan, List<BattlepassReward> rewards)
            throws Exception {
        long memberSlots = clan.maxMembers();
        long roleSlots = repository.findRoleLimit(clan.id(), defaultMaxRoles);
        ClanUnlocks unlocks = repository.findClanUnlocks(clan.id());
        long vaultPages = unlocks.vaultPages();
        long homeSlots = unlocks.bonusHomeSlots();
        for (BattlepassReward reward : rewards) {
            switch (reward.type()) {
                case MEMBER_SLOTS -> memberSlots += reward.amount();
                case ROLE_SLOTS -> roleSlots += reward.amount();
                case VAULT_PAGES -> vaultPages += reward.amount();
                case HOME_SLOTS -> homeSlots += reward.amount();
            }
        }
        return memberSlots <= battlepassSettings.absoluteMaxMembers()
                && roleSlots <= battlepassSettings.absoluteMaxRoles()
                && vaultPages <= battlepassSettings.absoluteMaxVaultPages()
                && homeSlots <= battlepassSettings.absoluteMaxBonusHomeSlots();
    }

    private static String rewardKey(BattlepassReward reward) {
        return reward.level() + ":" + reward.type().name();
    }

    private List<BattlepassReward> enabledRewards(List<BattlepassReward> rewards) {
        return rewards.stream()
                .filter(reward -> battlepassSettings.enabledRewardTypes()
                        .contains(reward.type()))
                .toList();
    }

    private <T> CompletableFuture<T> submit(CheckedSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (!normalOperationSlots.tryAcquire()) {
            future.completeExceptionally(new RejectedExecutionException(
                    "CatClans-Servicewarteschlange ist voll"
            ));
            return future;
        }
        ServiceTask<T> task = new ServiceTask<>(
                supplier,
                future,
                normalOperationSlots::release
        );
        try {
            worker.execute(task);
        } catch (RuntimeException exception) {
            task.reject(exception);
        }
        return future;
    }

    private <T> CompletableFuture<T> submitCritical(CheckedSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        ServiceTask<T> task = new ServiceTask<>(supplier, future, () -> {
        });
        try {
            worker.execute(task);
        } catch (RuntimeException exception) {
            task.reject(exception);
        }
        return future;
    }

    private static RankingSettings disabledRankingSettings() {
        return new RankingSettings(
                false,
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
    }

    private record RoleSnapshot(
            List<ClanRole> sortedRoles,
            Map<String, ClanRole> rolesById
    ) {
    }

    private record RankingSnapshot(
            Map<RankingCategory, List<ClanRankingEntry>> entries,
            Map<UUID, ClanRankingEntry> totalByClan
    ) {
        private static RankingSnapshot empty() {
            return new RankingSnapshot(Map.of(), Map.of());
        }
    }

    private record RankedClan(
            Clan clan,
            ClanRankingStats stats,
            Map<RankingCategory, BigDecimal> categoryPoints,
            BigDecimal total
    ) {
        private BigDecimal points(RankingCategory category) {
            return category == RankingCategory.TOTAL
                    ? total
                    : categoryPoints.getOrDefault(category, BigDecimal.ZERO);
        }
    }

    private static final class ServiceTask<T> implements Runnable {

        private final CheckedSupplier<T> supplier;
        private final CompletableFuture<T> future;
        private final Runnable onFinish;
        private boolean finished;

        private ServiceTask(
                CheckedSupplier<T> supplier,
                CompletableFuture<T> future,
                Runnable onFinish
        ) {
            this.supplier = supplier;
            this.future = future;
            this.onFinish = onFinish;
        }

        @Override
        public void run() {
            try {
                future.complete(supplier.get());
            } catch (Exception exception) {
                future.completeExceptionally(new ClanServiceException(exception));
            } finally {
                finish();
            }
        }

        private void reject(Throwable throwable) {
            future.completeExceptionally(throwable);
            finish();
        }

        private synchronized void finish() {
            if (finished) {
                return;
            }
            finished = true;
            onFinish.run();
        }
    }

    private record PermissionDecision(boolean allowed, boolean explicitMemberAllow) {
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static final class ClanServiceException extends RuntimeException {
        private ClanServiceException(Throwable cause) {
            super(cause);
        }
    }
}
