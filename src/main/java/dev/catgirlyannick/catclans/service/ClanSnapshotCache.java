package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanMember;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanSnapshotCache {

    private final int maximumClans;
    private final Map<UUID, Clan> clansById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> clanByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ClanMember> memberByPlayer = new ConcurrentHashMap<>();
    private final Map<String, UUID> clanBySearchKey = new ConcurrentHashMap<>();
    private volatile List<Clan> sortedSnapshot;

    public ClanSnapshotCache(int maximumClans) {
        this.maximumClans = maximumClans;
    }

    public void preload(List<Clan> clans) {
        if (clans.size() > maximumClans) {
            throw new IllegalStateException("Cache limit exceeded: " + clans.size()
                    + " Clans bei maximal " + maximumClans);
        }
        for (Clan clan : clans) {
            put(clan);
        }
    }

    public synchronized void put(Clan clan) {
        Clan previous = clansById.get(clan.id());
        if (previous == null && clansById.size() >= maximumClans) {
            throw new IllegalStateException("Cache limit of " + maximumClans + " clans reached");
        }
        ensureSearchKeyAvailable(clan.normalizedName(), clan.id());
        ensureSearchKeyAvailable(clan.normalizedTag(), clan.id());
        if (previous != null) {
            previous.members().forEach(member ->
                    clanByPlayer.remove(member.playerId(), previous.id()));
            previous.members().forEach(member ->
                    memberByPlayer.remove(member.playerId(), member));
            clanBySearchKey.remove(previous.normalizedName(), previous.id());
            clanBySearchKey.remove(previous.normalizedTag(), previous.id());
        }

        clansById.put(clan.id(), clan);
        clanBySearchKey.put(clan.normalizedName(), clan.id());
        clanBySearchKey.put(clan.normalizedTag(), clan.id());
        clan.members().forEach(member -> {
            clanByPlayer.put(member.playerId(), clan.id());
            memberByPlayer.put(member.playerId(), member);
        });
        sortedSnapshot = null;
    }

    public synchronized Optional<Clan> remove(UUID clanId) {
        Clan removed = clansById.remove(clanId);
        if (removed == null) {
            return Optional.empty();
        }
        removed.members().forEach(member -> {
            clanByPlayer.remove(member.playerId(), clanId);
            memberByPlayer.remove(member.playerId(), member);
        });
        clanBySearchKey.remove(removed.normalizedName(), clanId);
        clanBySearchKey.remove(removed.normalizedTag(), clanId);
        sortedSnapshot = null;
        return Optional.of(removed);
    }

    public void ensureCapacityForNewClan() {
        if (clansById.size() >= maximumClans) {
            throw new IllegalStateException("Cache limit of " + maximumClans + " clans reached");
        }
    }

    public Optional<Clan> findByPlayer(UUID playerId) {
        UUID clanId = clanByPlayer.get(playerId);
        return clanId == null ? Optional.empty() : Optional.ofNullable(clansById.get(clanId));
    }

    public Optional<Clan> findById(UUID clanId) {
        return Optional.ofNullable(clansById.get(clanId));
    }

    public Optional<Clan> findByNameOrTag(String normalizedSearch) {
        UUID clanId = clanBySearchKey.get(normalizedSearch);
        return clanId == null ? Optional.empty() : Optional.ofNullable(clansById.get(clanId));
    }

    public Optional<ClanMember> findMember(UUID playerId) {
        return Optional.ofNullable(memberByPlayer.get(playerId));
    }

    public List<Clan> list() {
        List<Clan> current = sortedSnapshot;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (sortedSnapshot == null) {
                List<Clan> clans = new ArrayList<>(clansById.values());
                clans.sort(Comparator.comparing(Clan::normalizedName));
                sortedSnapshot = List.copyOf(clans);
            }
            return sortedSnapshot;
        }
    }

    public int size() {
        return clansById.size();
    }

    private void ensureSearchKeyAvailable(String key, UUID clanId) {
        UUID existingClanId = clanBySearchKey.get(key);
        if (existingClanId != null && !existingClanId.equals(clanId)) {
            throw new IllegalStateException(
                    "Ambiguous clan name/tag cache key: " + key
            );
        }
    }

}
