# CatClans Download Description

## Short Description

Configurable, GUI-first clans for Paper with roles, permissions, homes, wars, rankings, a clan bank, item vault, and progression rewards.

## Full Description

CatClans is a modular clan-management plugin for Paper servers. Players can create and manage clans through clean inventory menus while text input, such as clan names and formatted tags, is handled safely through chat.

Clan owners can configure persistent roles, exact role priorities, role permissions, and individual member permissions. The built-in hierarchy prevents members from managing players above their own rank unless an explicit permission allows it.

The plugin includes a persistent item vault, an EzEconomy-backed clan bank through Vault, safe clan homes, alliances, clan wars, permanent category-based rankings, and a clan-wide Battlepass. Administrators can configure progression rewards in game, including additional member slots, home slots, role slots, and vault pages.

CatClans uses SQLite by default and can be configured for MySQL. Expensive storage work runs outside the main server thread, placeholders read from cache, GUI inventories render when opened, and queues and caches have configurable limits.

## Main Features

- GUI-first clan management through `/clan` or `/clans`
- Chat-based clan creation and editing without anvil input menus
- Configurable clan names, RGB and MiniMessage tags, and Small Caps text
- Persistent roles, priorities, role permissions, and individual overrides
- Member invitations with confirmation menus
- Automatic owner succession when the owner leaves
- Separate money bank and persistent item vault
- Safe clan homes with configurable world and block checks
- Alliances, ally chat, clan wars, and administrator war completion
- Permanent leaderboards for combat, members, money, wars, and activity
- Clan-wide Battlepass with administrator-defined unlock rewards
- SQLite and configurable MySQL storage
- Text audit logs with bounded retention
- LuckPerms, PlaceholderAPI, InteractiveChat, Vault, and EzEconomy support
- MiniMessage, RGB colors, gradients, and configurable GUI layouts
- Bounded configuration backups and validation

## Requirements

- Java 21
- Paper 1.21.x
- Vault and EzEconomy when the clan bank is enabled

## Optional Integrations

- LuckPerms for permission bundles
- PlaceholderAPI for `%catclans_*%` placeholders
- InteractiveChat through the PlaceholderAPI bridge

## Installation

1. Stop the Paper server completely.
2. Install Vault and EzEconomy if the clan bank will be used.
3. Place the CatClans JAR in the server's `plugins` directory.
4. Start the server once and inspect the console for dependency warnings.
5. Replace the branding placeholders and review the generated configuration files.
6. Restart the server before opening it to players.

Do not use Bukkit `/reload` or plugin hot reloaders.

## Development Status

This download is a beta build. Test it on a staging server and create a complete backup before production use. The future CatChunks claim integration, final administration configuration GUI, and additional reward types are intentionally not advertised as completed features.

---
Made By CatgirlYannick
