package dev.catgirlyannick.catclans.model;

import java.util.List;
import java.util.Map;

public record RolePermissionView(
        ClanRole role,
        List<String> permissions,
        Map<String, Boolean> values
) {
    public RolePermissionView {
        permissions = List.copyOf(permissions);
        values = Map.copyOf(values);
    }
}
