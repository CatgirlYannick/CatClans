# Permissions

## Bundles

| Permission | Default | Purpose |
| --- | --- | --- |
| `catclans.default.*` | true | Normal player clan features |
| `catclans.support.*` | false | Read-only clan, log, and player inspection |
| `catclans.management.*` | false | Transfer, deletion, sanctions, and level correction |
| `catclans.admin.*` | false | Full administrative bundle |

LuckPerms groups are configurable as `default`, `support`, `management`, and `administration`. CatClans does not automatically modify group memberships.

## Important Nodes

- `catclans.command.clan`
- `catclans.command.clanadmin`
- `catclans.clan.create`
- `catclans.clan.delete`
- `catclans.clan.permissions.manage`
- `catclans.clan.vault.open`
- `catclans.clan.bank.view`
- `catclans.clan.homes.open`
- `catclans.clan.diplomacy`
- `catclans.admin.battlepass.rewards`
- `catclans.admin.war.end`
- `catclans.management.clan.delete`

Clan role permissions are separate from Bukkit permissions. Owners can configure role and individual-member rights in the GUI. Role priority ranges from 0 to 100; nonzero priorities must be unique. The rank pyramid applies unless an explicit individual permission overrides it.

---
Made By CatgirlYannick
