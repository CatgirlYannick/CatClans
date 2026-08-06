package dev.catgirlyannick.catclans.model;

public record RewardClaimResult(
        boolean claimed,
        int maximumMembers,
        int maximumRoles,
        ClanUnlocks unlocks
) {
}
