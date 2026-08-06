package dev.catgirlyannick.catclans.model;

import java.util.Locale;
import java.util.Optional;

public enum BattlepassRewardType {
    MEMBER_SLOTS,
    HOME_SLOTS,
    VAULT_PAGES,
    ROLE_SLOTS;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<BattlepassRewardType> fromConfigKey(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
