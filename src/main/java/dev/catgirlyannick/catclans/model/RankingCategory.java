package dev.catgirlyannick.catclans.model;

import java.util.Locale;
import java.util.Optional;

public enum RankingCategory {
    TOTAL("total"),
    COMBAT("combat"),
    MEMBERS("members"),
    MONEY("money"),
    WARS_WON("wars-won"),
    WARS_LOST("wars-lost"),
    ACTIVITY("activity");

    private final String configKey;

    RankingCategory(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static Optional<RankingCategory> fromConfigKey(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', '-');
        for (RankingCategory category : values()) {
            if (category.configKey.equals(normalized)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
