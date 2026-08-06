package dev.catgirlyannick.catclans.model;

import java.util.List;

public record ClanRoleOverview(
        List<ClanRole> roles,
        int maximumRoles
) {
    public ClanRoleOverview {
        roles = List.copyOf(roles);
    }

    public boolean canCreateRole() {
        return roles.size() < maximumRoles;
    }
}
