package dev.catgirlyannick.catclans.model;

import java.util.List;
import java.util.Map;

public record MemberPermissionView(
        ClanMember member,
        ClanRole role,
        List<String> permissions,
        Map<String, Boolean> overrides
) {
    public MemberPermissionView {
        permissions = List.copyOf(permissions);
        overrides = Map.copyOf(overrides);
    }

    public PermissionOverride state(String permission) {
        if (!overrides.containsKey(permission)) {
            return PermissionOverride.INHERIT;
        }
        return overrides.get(permission)
                ? PermissionOverride.ALLOW
                : PermissionOverride.DENY;
    }
}
