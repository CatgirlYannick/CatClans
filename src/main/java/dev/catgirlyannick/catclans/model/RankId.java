package dev.catgirlyannick.catclans.model;

import java.util.Optional;

public enum RankId {
    OWNER("owner"),
    CO_OWNER("co-owner"),
    MODERATOR("moderator"),
    MEMBER("member"),
    RECRUIT("recruit");

    private static final RankId[] VALUES = values();

    private final String configKey;

    RankId(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static RankId fromStorage(String value) {
        if (value == null) {
            return RECRUIT;
        }
        for (RankId rank : VALUES) {
            if (rank.name().equalsIgnoreCase(value)) {
                return rank;
            }
        }
        return RECRUIT;
    }

    public static Optional<RankId> fromRoleId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (RankId rank : VALUES) {
            if (rank.configKey.equalsIgnoreCase(value)) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }
}
