package dev.catgirlyannick.catclans.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Clan {

    private final UUID id;
    private final String name;
    private final String normalizedName;
    private final String tag;
    private final String normalizedTag;
    private final String formattedTag;
    private final UUID ownerId;
    private final JoinMode joinMode;
    private final int maxMembers;
    private final Instant createdAt;
    private final List<ClanMember> members;

    public Clan(
            UUID id,
            String name,
            String normalizedName,
            String tag,
            String normalizedTag,
            UUID ownerId,
            JoinMode joinMode,
            int maxMembers,
            Instant createdAt,
            List<ClanMember> members
    ) {
        this(
                id,
                name,
                normalizedName,
                tag,
                normalizedTag,
                tag,
                ownerId,
                joinMode,
                maxMembers,
                createdAt,
                members
        );
    }

    public Clan(
            UUID id,
            String name,
            String normalizedName,
            String tag,
            String normalizedTag,
            String formattedTag,
            UUID ownerId,
            JoinMode joinMode,
            int maxMembers,
            Instant createdAt,
            List<ClanMember> members
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.normalizedName = Objects.requireNonNull(normalizedName, "normalizedName");
        this.tag = Objects.requireNonNull(tag, "tag");
        this.normalizedTag = Objects.requireNonNull(normalizedTag, "normalizedTag");
        this.formattedTag = Objects.requireNonNull(formattedTag, "formattedTag");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.joinMode = Objects.requireNonNull(joinMode, "joinMode");
        this.maxMembers = maxMembers;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.members = List.copyOf(members);

        if (maxMembers < 1) {
            throw new IllegalArgumentException("maxMembers must be at least 1");
        }
        if (member(ownerId).map(ClanMember::rank).orElse(null) != RankId.OWNER) {
            throw new IllegalArgumentException("The owner must be included as an OWNER member");
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String normalizedName() {
        return normalizedName;
    }

    public String tag() {
        return tag;
    }

    public String normalizedTag() {
        return normalizedTag;
    }

    public String formattedTag() {
        return formattedTag;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public JoinMode joinMode() {
        return joinMode;
    }

    public int maxMembers() {
        return maxMembers;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<ClanMember> members() {
        return members;
    }

    public Optional<ClanMember> member(UUID playerId) {
        return members.stream().filter(member -> member.playerId().equals(playerId)).findFirst();
    }

    public boolean isFull() {
        return members.size() >= maxMembers;
    }

    public Clan withMember(ClanMember member) {
        if (member(member.playerId()).isPresent()) {
            return this;
        }
        List<ClanMember> updated = new ArrayList<>(members);
        updated.add(member);
        return copy(joinMode, updated);
    }

    public Clan withoutMember(UUID playerId) {
        return copy(joinMode, members.stream()
                .filter(member -> !member.playerId().equals(playerId))
                .toList());
    }

    public Clan transferOwnershipAndRemoveOwner(UUID currentOwnerId, UUID successorId) {
        if (!ownerId.equals(currentOwnerId)) {
            throw new IllegalArgumentException("Only the current owner can be transferred");
        }
        if (currentOwnerId.equals(successorId) || member(successorId).isEmpty()) {
            throw new IllegalArgumentException("The successor must be another clan member");
        }
        List<ClanMember> updated = members.stream()
                .filter(member -> !member.playerId().equals(currentOwnerId))
                .map(member -> member.playerId().equals(successorId)
                        ? member.withRole(RankId.OWNER.configKey(), RankId.OWNER)
                        : member)
                .toList();
        return new Clan(
                id,
                name,
                normalizedName,
                tag,
                normalizedTag,
                formattedTag,
                successorId,
                joinMode,
                maxMembers,
                createdAt,
                updated
        );
    }

    public Clan withJoinMode(JoinMode newJoinMode) {
        return copy(newJoinMode, members);
    }

    public Clan withName(String newName, String newNormalizedName) {
        return new Clan(
                id,
                newName,
                newNormalizedName,
                tag,
                normalizedTag,
                formattedTag,
                ownerId,
                joinMode,
                maxMembers,
                createdAt,
                members
        );
    }

    public Clan withTag(String newTag, String newNormalizedTag, String newFormattedTag) {
        return new Clan(
                id,
                name,
                normalizedName,
                newTag,
                newNormalizedTag,
                newFormattedTag,
                ownerId,
                joinMode,
                maxMembers,
                createdAt,
                members
        );
    }

    public Clan withMaxMembers(int newMaxMembers) {
        return new Clan(
                id,
                name,
                normalizedName,
                tag,
                normalizedTag,
                formattedTag,
                ownerId,
                joinMode,
                newMaxMembers,
                createdAt,
                members
        );
    }

    public Clan withMemberRole(UUID playerId, String roleId, RankId fallbackRank) {
        List<ClanMember> updated = members.stream()
                .map(member -> member.playerId().equals(playerId)
                        ? member.withRole(roleId, fallbackRank)
                        : member)
                .toList();
        return copy(joinMode, updated);
    }

    private Clan copy(JoinMode newJoinMode, List<ClanMember> newMembers) {
        return new Clan(
                id,
                name,
                normalizedName,
                tag,
                normalizedTag,
                formattedTag,
                ownerId,
                newJoinMode,
                maxMembers,
                createdAt,
                newMembers
        );
    }
}
