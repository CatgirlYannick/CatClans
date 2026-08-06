package dev.catgirlyannick.catclans.config;

import dev.catgirlyannick.catclans.model.RankId;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RankPolicyTest {

    @Test
    void enforcesPermissionAndRankPyramid() {
        RankPolicy policy = policy();

        assertTrue(policy.canManage(RankId.OWNER, RankId.CO_OWNER, "kick"));
        assertTrue(policy.canManage(RankId.MODERATOR, RankId.MEMBER, "kick"));
        assertFalse(policy.canManage(RankId.MODERATOR, RankId.CO_OWNER, "kick"));
        assertFalse(policy.canManage(RankId.MEMBER, RankId.RECRUIT, "kick"));
    }

    public static RankPolicy policy() {
        YamlConfiguration configuration = new YamlConfiguration();
        setRank(configuration, RankId.OWNER, 100, List.of("*"));
        setRank(configuration, RankId.CO_OWNER, 80, List.of("invite", "kick"));
        setRank(configuration, RankId.MODERATOR, 60, List.of("invite", "kick"));
        setRank(configuration, RankId.MEMBER, 40, List.of());
        setRank(configuration, RankId.RECRUIT, 20, List.of());
        return new RankPolicy(configuration);
    }

    private static void setRank(
            YamlConfiguration configuration,
            RankId rank,
            int priority,
            List<String> permissions
    ) {
        String path = "standard-ranks." + rank.configKey();
        configuration.set(path + ".display-name", rank.name());
        configuration.set(path + ".priority", priority);
        configuration.set(path + ".permissions", permissions);
    }
}
