package dev.catgirlyannick.catclans.config;

import dev.catgirlyannick.catclans.model.RankId;
import dev.catgirlyannick.catclans.model.ClanRole;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;

public final class RankPolicy {

    private final Map<RankId, Set<String>> permissions = new EnumMap<>(RankId.class);
    private final Map<RankId, String> displayNames = new EnumMap<>(RankId.class);
    private final Map<RankId, Integer> priorities = new EnumMap<>(RankId.class);

    public RankPolicy(YamlConfiguration configuration) {
        for (RankId rank : RankId.values()) {
            String path = "standard-ranks." + rank.configKey();
            displayNames.put(rank, configuration.getString(path + ".display-name", rank.name()));
            priorities.put(rank, configuration.getInt(path + ".priority", 0));
            permissions.put(rank, Set.copyOf(new HashSet<>(
                    configuration.getStringList(path + ".permissions"))));
        }
    }

    public boolean has(RankId rank, String permission) {
        Set<String> rankPermissions = permissions.getOrDefault(rank, Set.of());
        return rankPermissions.contains("*") || rankPermissions.contains(permission);
    }

    public String displayName(RankId rank) {
        return displayNames.getOrDefault(rank, rank.name());
    }

    public int priority(RankId rank) {
        return priorities.getOrDefault(rank, 0);
    }

    public boolean canManage(RankId actor, RankId target, String permission) {
        return has(actor, permission) && priority(actor) > priority(target);
    }

    public Set<String> permissions(RankId rank) {
        return permissions.getOrDefault(rank, Set.of());
    }

    public List<ClanRole> standardRoles(UUID clanId) {
        return java.util.Arrays.stream(RankId.values())
                .map(rank -> new ClanRole(
                        clanId,
                        rank.configKey(),
                        displayName(rank),
                        priority(rank),
                        true
                ))
                .sorted(java.util.Comparator.comparingInt(ClanRole::priority).reversed())
                .toList();
    }
}
