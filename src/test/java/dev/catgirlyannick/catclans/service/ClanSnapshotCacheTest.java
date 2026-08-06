package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanMember;
import dev.catgirlyannick.catclans.model.JoinMode;
import dev.catgirlyannick.catclans.model.RankId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanSnapshotCacheTest {

    @Test
    void preloadsAndFindsClanWithoutRepositoryAccess() {
        UUID ownerId = UUID.randomUUID();
        Clan clan = clan(UUID.randomUUID(), ownerId, List.of(owner(ownerId)));
        ClanSnapshotCache cache = new ClanSnapshotCache(10);

        cache.preload(List.of(clan));

        assertEquals(clan.id(), cache.findByPlayer(ownerId).orElseThrow().id());
        assertEquals(clan.id(), cache.findByNameOrTag("alpha").orElseThrow().id());
        assertEquals(clan.id(), cache.findByNameOrTag("ac").orElseThrow().id());
        assertEquals(RankId.OWNER, cache.findMember(ownerId).orElseThrow().rank());
        assertSame(cache.list(), cache.list());
    }

    @Test
    void replacingSnapshotRemovesDepartedMemberIndex() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        ClanSnapshotCache cache = new ClanSnapshotCache(10);
        Clan withMember = clan(
                UUID.randomUUID(),
                ownerId,
                List.of(owner(ownerId), new ClanMember(
                        memberId,
                        "Member",
                        RankId.MEMBER,
                        Instant.now()
                ))
        );
        cache.put(withMember);

        cache.put(withMember.withoutMember(memberId));

        assertTrue(cache.findByPlayer(memberId).isEmpty());
        assertEquals(withMember.id(), cache.findByPlayer(ownerId).orElseThrow().id());
    }

    @Test
    void rejectsNewClanPastConfiguredLimit() {
        ClanSnapshotCache cache = new ClanSnapshotCache(1);
        UUID firstOwner = UUID.randomUUID();
        cache.put(clan(UUID.randomUUID(), firstOwner, List.of(owner(firstOwner))));

        assertThrows(IllegalStateException.class, cache::ensureCapacityForNewClan);
    }

    @Test
    void rejectsCrossCollisionBetweenClanNameAndTag() {
        ClanSnapshotCache cache = new ClanSnapshotCache(10);
        UUID firstOwner = UUID.randomUUID();
        cache.put(clan(UUID.randomUUID(), firstOwner, List.of(owner(firstOwner))));
        UUID secondOwner = UUID.randomUUID();
        Clan colliding = new Clan(
                UUID.randomUUID(),
                "Beta",
                "beta",
                "ALPHA",
                "alpha",
                secondOwner,
                JoinMode.INVITE_ONLY,
                27,
                Instant.now(),
                List.of(owner(secondOwner))
        );

        assertThrows(IllegalStateException.class, () -> cache.put(colliding));
    }

    private static Clan clan(UUID clanId, UUID ownerId, List<ClanMember> members) {
        return new Clan(
                clanId,
                "Alpha",
                "alpha",
                "AC",
                "ac",
                ownerId,
                JoinMode.INVITE_ONLY,
                27,
                Instant.now(),
                members
        );
    }

    private static ClanMember owner(UUID ownerId) {
        return new ClanMember(ownerId, "Owner", RankId.OWNER, Instant.now());
    }
}
