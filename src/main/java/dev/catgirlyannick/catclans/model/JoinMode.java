package dev.catgirlyannick.catclans.model;

import java.util.Locale;
import java.util.Optional;

public enum JoinMode {
    INVITE_ONLY,
    OPEN;

    public static Optional<JoinMode> fromCommand(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "invite", "invite_only", "invite-only" -> Optional.of(INVITE_ONLY);
            case "open" -> Optional.of(OPEN);
            default -> Optional.empty();
        };
    }
}
