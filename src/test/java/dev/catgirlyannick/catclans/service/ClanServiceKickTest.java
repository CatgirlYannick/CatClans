package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.audit.TextAuditLogService;
import dev.catgirlyannick.catclans.config.RankPolicy;
import dev.catgirlyannick.catclans.config.RankPolicyTest;
import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankId;
import dev.catgirlyannick.catclans.model.RoleMoveDirection;
import dev.catgirlyannick.catclans.storage.SqliteClanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanServiceKickTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsKickByHigherRank() throws Exception {
        TestContext context = context("success.db");
        try (ClanService service = context.service()) {
            OperationResult<Clan> result = service.kickMember(
                    context.ownerId(),
                    "Owner",
                    context.recruitId(),
                    "GUI"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.SUCCESS, result.code());
            assertFalse(result.value().member(context.recruitId()).isPresent());
        }

        try (SqliteClanRepository repository = repository("success.db")) {
            repository.initialize();
            assertFalse(repository.findByMember(context.recruitId()).isPresent());
        }
    }

    @Test
    void rejectsKickAgainstHigherRank() throws Exception {
        TestContext context = context("hierarchy.db");
        try (ClanService service = context.service()) {
            OperationResult<Clan> result = service.kickMember(
                    context.moderatorId(),
                    "Moderator",
                    context.coOwnerId(),
                    "GUI"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.RANK_TOO_LOW, result.code());
        }
    }

    @Test
    void explicitMemberPermissionBypassesHierarchyAndPersists() throws Exception {
        TestContext context = context("override.db");
        try (ClanService service = context.service()) {
            OperationResult<?> permissionResult = service.cycleMemberPermission(
                    context.ownerId(),
                    "Owner",
                    context.moderatorId(),
                    "kick"
            ).get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, permissionResult.code());
            assertEquals(
                    OperationCode.SUCCESS,
                    service.cycleMemberPermission(
                            context.ownerId(),
                            "Owner",
                            context.coOwnerId(),
                            "invite"
                    ).get(5, TimeUnit.SECONDS).code()
            );

            OperationResult<Clan> kickResult = service.kickMember(
                    context.moderatorId(),
                    "Moderator",
                    context.coOwnerId(),
                    "GUI"
            ).get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, kickResult.code());
        }

        try (SqliteClanRepository repository = repository("override.db")) {
            repository.initialize();
            assertEquals(
                    Boolean.TRUE,
                    repository.findMemberPermissions(
                            context.clanId(),
                            context.moderatorId()
                    ).get("kick")
            );
            assertTrue(repository.findMemberPermissions(
                    context.clanId(),
                    context.coOwnerId()
            ).isEmpty());
        }
    }

    @Test
    void renamesStandardRoleAndCreatesUnlockedRolePersistently() throws Exception {
        TestContext context = context("roles.db");
        try (ClanService service = context.service()) {
            assertEquals(
                    OperationCode.SUCCESS,
                    service.renameRole(
                            context.ownerId(),
                            "Owner",
                            "moderator",
                            "Wächter"
                    ).get(5, TimeUnit.SECONDS).code()
            );
            var createdRole = service.createRole(
                    context.ownerId(),
                    "Owner",
                    "Veteran"
            ).get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, createdRole.code());
            String roleId = createdRole.value().id();
            assertEquals(
                    OperationCode.SUCCESS,
                    service.toggleRolePermission(
                            context.ownerId(),
                            "Owner",
                            roleId,
                            "kick"
                    ).get(5, TimeUnit.SECONDS).code()
            );
            assertEquals(
                    OperationCode.SUCCESS,
                    service.assignRole(
                            context.ownerId(),
                            "Owner",
                            context.moderatorId(),
                            roleId
                    ).get(5, TimeUnit.SECONDS).code()
            );
            assertEquals(
                    OperationCode.RANK_TOO_LOW,
                    service.kickMember(
                            context.moderatorId(),
                            "Moderator",
                            context.recruitId(),
                            "before move"
                    ).get(5, TimeUnit.SECONDS).code()
            );
            assertEquals(
                    OperationCode.SUCCESS,
                    service.moveRole(
                            context.ownerId(),
                            "Owner",
                            roleId,
                            RoleMoveDirection.UP
                    ).get(5, TimeUnit.SECONDS).code()
            );
            assertEquals(
                    OperationCode.SUCCESS,
                    service.kickMember(
                            context.moderatorId(),
                            "Moderator",
                            context.recruitId(),
                            "after move"
                    ).get(5, TimeUnit.SECONDS).code()
            );
        }

        try (SqliteClanRepository repository = repository("roles.db")) {
            repository.initialize();
            assertTrue(repository.findRoles(context.clanId()).stream()
                    .anyMatch(role -> role.id().equals("moderator")
                            && role.displayName().equals("Wächter")));
            assertTrue(repository.findRoles(context.clanId()).stream()
                    .anyMatch(role -> role.displayName().equals("Veteran")));
        }
    }

    @Test
    void changesFormattedClanTagAndPersistsIt() throws Exception {
        TestContext context = context("formatted-tag.db");
        try (ClanService service = context.service()) {
            OperationResult<Clan> result = service.changeTag(
                    context.ownerId(),
                    "Owner",
                    "<gradient:#FF0000:#00FFFF><strikethrough>NEW"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.SUCCESS, result.code());
            assertEquals("NEW", result.value().tag());
            assertEquals(
                    "<gradient:#FF0000:#00FFFF><strikethrough>NEW",
                    result.value().formattedTag()
            );
            assertEquals(
                    context.clanId(),
                    service.findPublicClan("NEW")
                            .get(5, TimeUnit.SECONDS)
                            .orElseThrow()
                            .id()
            );
            assertTrue(
                    service.findPublicClan("ASH")
                            .get(5, TimeUnit.SECONDS)
                            .isEmpty()
            );
        }

        try (SqliteClanRepository repository = repository("formatted-tag.db")) {
            repository.initialize();
            Clan loaded = repository.findById(context.clanId()).orElseThrow();
            assertEquals("NEW", loaded.tag());
            assertEquals(
                    "<gradient:#FF0000:#00FFFF><strikethrough>NEW",
                    loaded.formattedTag()
            );
        }
    }

    @Test
    void changesClanNameAndUpdatesPersistentSearchIndex() throws Exception {
        TestContext context = context("clan-name.db");
        try (ClanService service = context.service()) {
            OperationResult<Clan> result = service.changeName(
                    context.ownerId(),
                    "Owner",
                    "Ashen Watchers"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.SUCCESS, result.code());
            assertEquals("Ashen Watchers", result.value().name());
            assertEquals(
                    context.clanId(),
                    service.findPublicClan("Ashen Watchers")
                            .get(5, TimeUnit.SECONDS)
                            .orElseThrow()
                            .id()
            );
            assertTrue(service.findPublicClan("Ashen")
                    .get(5, TimeUnit.SECONDS)
                    .isEmpty());
        }

        try (SqliteClanRepository repository = repository("clan-name.db")) {
            repository.initialize();
            assertEquals(
                    "Ashen Watchers",
                    repository.findById(context.clanId()).orElseThrow().name()
            );
        }
    }

    @Test
    void rejectsClanNameChangeWithoutClanRight() throws Exception {
        TestContext context = context("clan-name-permission.db");
        try (ClanService service = context.service()) {
            OperationResult<Clan> result = service.changeName(
                    context.moderatorId(),
                    "Moderator",
                    "Neuer Name"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.CLAN_RIGHT_MISSING, result.code());
            assertEquals("Ashen", service.findCachedClan(context.clanId())
                    .orElseThrow()
                    .name());
        }
    }

    @Test
    void assignsExactUniqueRolePrioritiesAndAllowsDuplicateZero() throws Exception {
        TestContext context = context("role-priorities.db");
        String customRoleId;
        try (ClanService service = context.service()) {
            OperationResult<dev.catgirlyannick.catclans.model.ClanRole> created =
                    service.createRole(
                            context.ownerId(),
                            "Owner",
                            "Veteran"
                    ).get(5, TimeUnit.SECONDS);
            assertEquals(OperationCode.SUCCESS, created.code());
            assertEquals(0, created.value().priority());
            customRoleId = created.value().id();

            assertEquals(
                    OperationCode.SUCCESS,
                    service.setRolePriority(
                            context.ownerId(),
                            "Owner",
                            customRoleId,
                            75
                    ).get(5, TimeUnit.SECONDS).code()
            );
            assertEquals(
                    OperationCode.ROLE_PRIORITY_TAKEN,
                    service.setRolePriority(
                            context.ownerId(),
                            "Owner",
                            customRoleId,
                            80
                    ).get(5, TimeUnit.SECONDS).code()
            );
            assertEquals(
                    OperationCode.INVALID_ROLE_PRIORITY,
                    service.setRolePriority(
                            context.ownerId(),
                            "Owner",
                            customRoleId,
                            101
                    ).get(5, TimeUnit.SECONDS).code()
            );
            assertEquals(
                    OperationCode.SUCCESS,
                    service.setRolePriority(
                            context.ownerId(),
                            "Owner",
                            "moderator",
                            0
                    ).get(5, TimeUnit.SECONDS).code()
            );
            assertEquals(
                    OperationCode.SUCCESS,
                    service.setRolePriority(
                            context.ownerId(),
                            "Owner",
                            customRoleId,
                            0
                    ).get(5, TimeUnit.SECONDS).code()
            );
        }

        try (SqliteClanRepository repository = repository("role-priorities.db")) {
            repository.initialize();
            long zeroPriorities = repository.findRoles(context.clanId()).stream()
                    .filter(role -> role.priority() == 0)
                    .count();
            assertEquals(2, zeroPriorities);
        }
    }

    @Test
    void rejectsTagChangeWithoutClanRight() throws Exception {
        TestContext context = context("tag-permission.db");
        try (ClanService service = context.service()) {
            OperationResult<Clan> result = service.changeTag(
                    context.moderatorId(),
                    "Moderator",
                    "<#55D6C2>NEW"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.CLAN_RIGHT_MISSING, result.code());
            assertEquals("ASH", service.findCachedClan(context.clanId())
                    .orElseThrow()
                    .tag());
        }
    }

    @Test
    void ownerLeaveTransfersOwnershipToHighestPriorityMember() throws Exception {
        TestContext context = context("owner-successor.db", false);
        try (ClanService service = context.service()) {
            OperationResult<Clan> result = service.leaveClan(
                    context.ownerId(),
                    "Owner",
                    "command"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.SUCCESS, result.code());
            assertEquals(context.coOwnerId(), result.value().ownerId());
            assertFalse(result.value().member(context.ownerId()).isPresent());
            assertEquals(
                    RankId.OWNER,
                    result.value().member(context.coOwnerId()).orElseThrow().rank()
            );
            assertEquals(
                    RankId.OWNER.configKey(),
                    result.value().member(context.coOwnerId()).orElseThrow().roleId()
            );
        }

        try (SqliteClanRepository repository = repository("owner-successor.db")) {
            repository.initialize();
            Clan stored = repository.findById(context.clanId()).orElseThrow();
            assertEquals(context.coOwnerId(), stored.ownerId());
            assertFalse(repository.findByMember(context.ownerId()).isPresent());
        }
    }

    @Test
    void soleOwnerMustDeleteInsteadOfLeaving() throws Exception {
        TestContext context = context("sole-owner.db", false);
        try (ClanService service = context.service()) {
            for (UUID memberId : List.of(
                    context.coOwnerId(),
                    context.moderatorId(),
                    context.recruitId()
            )) {
                assertEquals(
                        OperationCode.SUCCESS,
                        service.kickMember(
                                context.ownerId(),
                                "Owner",
                                memberId,
                                "test"
                        ).get(5, TimeUnit.SECONDS).code()
                );
            }

            OperationResult<Clan> result = service.leaveClan(
                    context.ownerId(),
                    "Owner",
                    "command"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.OWNER_CANNOT_LEAVE, result.code());
            assertTrue(service.findCachedClan(context.clanId()).isPresent());
        }
    }

    @Test
    void ownerCanDeleteClanPersistently() throws Exception {
        TestContext context = context("owner-delete.db", false);
        try (ClanService service = context.service()) {
            OperationResult<Clan> result = service.deleteOwnedClan(
                    context.ownerId(),
                    "Owner"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.SUCCESS, result.code());
            assertTrue(service.findCachedClan(context.clanId()).isEmpty());
            assertTrue(service.findCachedClanForPlayer(context.coOwnerId()).isEmpty());
        }

        try (SqliteClanRepository repository = repository("owner-delete.db")) {
            repository.initialize();
            assertTrue(repository.findById(context.clanId()).isEmpty());
            assertTrue(repository.findByMember(context.ownerId()).isEmpty());
        }
    }

    @Test
    void adminCanDeleteClanByName() throws Exception {
        TestContext context = context("admin-delete.db", false);
        try (ClanService service = context.service()) {
            OperationResult<Clan> result = service.deleteClanAsAdmin(
                    "Ashen",
                    UUID.randomUUID(),
                    "Admin"
            ).get(5, TimeUnit.SECONDS);

            assertEquals(OperationCode.SUCCESS, result.code());
            assertEquals(context.clanId(), result.value().id());
            assertTrue(service.findCachedClan(context.clanId()).isEmpty());
        }
    }

    private TestContext context(String databaseName) throws Exception {
        return context(databaseName, true);
    }

    private TestContext context(String databaseName, boolean preventOwnerLeaving)
            throws Exception {
        UUID clanId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID coOwnerId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        UUID recruitId = UUID.randomUUID();
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
                        new ClanMember(coOwnerId, "CoOwner", RankId.CO_OWNER, now),
                        new ClanMember(moderatorId, "Moderator", RankId.MODERATOR, now),
                        new ClanMember(recruitId, "Recruit", RankId.RECRUIT, now)
                )
        );
        SqliteClanRepository repository = repository(databaseName);
        repository.initialize();
        repository.save(clan);
        ClanSnapshotCache cache = new ClanSnapshotCache(100);
        cache.preload(List.of(clan));
        RankPolicy rankPolicy = RankPolicyTest.policy();
        TextAuditLogService audit = new TextAuditLogService(
                temporaryDirectory.resolve("logs"),
                false,
                14,
                "yyyy-MM-dd'.log'",
                "yyyy-MM-dd HH:mm:ss",
                ignored -> {
                }
        );
        ClanService service = new ClanService(
                repository,
                audit,
                rankPolicy,
                new ClanRules(1, 20, "^[A-Za-z ]+$", 2, 6, "^[A-Za-z0-9]+$"),
                JoinMode.INVITE_ONLY,
                27,
                Duration.ofHours(48),
                preventOwnerLeaving,
                "ClanServiceKickTest",
                cache,
                64,
                5,
                10,
                10,
                List.of(
                        "invite",
                        "kick",
                        "diplomacy.manage",
                        "name.change",
                        "tag.change",
                        "home.set",
                        "rank.manage",
                        "bank.view",
                        "bank.deposit",
                        "bank.withdraw",
                        "vault.view",
                        "vault.deposit",
                        "vault.withdraw",
                        "vault.log"
                ),
                Map.of()
        );
        return new TestContext(
                service,
                clanId,
                ownerId,
                coOwnerId,
                moderatorId,
                recruitId
        );
    }

    private SqliteClanRepository repository(String databaseName) {
        return new SqliteClanRepository(
                temporaryDirectory.resolve(databaseName),
                true,
                5000
        );
    }

    private record TestContext(
            ClanService service,
            UUID clanId,
            UUID ownerId,
            UUID coOwnerId,
            UUID moderatorId,
            UUID recruitId
    ) {
    }
}
