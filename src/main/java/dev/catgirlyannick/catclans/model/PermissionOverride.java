package dev.catgirlyannick.catclans.model;

public enum PermissionOverride {
    INHERIT,
    ALLOW,
    DENY;

    public PermissionOverride next() {
        return switch (this) {
            case INHERIT -> ALLOW;
            case ALLOW -> DENY;
            case DENY -> INHERIT;
        };
    }
}
