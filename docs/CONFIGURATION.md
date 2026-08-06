# Configuration

CatClans uses modular YAML files. Every file has `config-version: 36` and is validated before startup.

| File | Purpose |
| --- | --- |
| `config.yml` | Branding, features, clan limits, names, tags, and roles |
| `messages.yml` | MiniMessage text, RGB, gradients, and Small Caps |
| `gui.yml` | Inventory layouts, materials, slots, names, and lore |
| `permissions.yml` | Clan-level rights and defaults |
| `ranks.yml` | Default clan roles and priorities |
| `storage.yml` | SQLite/MySQL and backup retention |
| `economy.yml` | Bank and currency display settings |
| `vault.yml` | Item vault limits and retention |
| `battlepass.yml` | XP curve, activity rewards, losses, and reward tree |
| `rankings.yml` | Ranking categories and point weights |
| `homes.yml` | Home limits and teleport safety |
| `diplomacy.yml` | Alliances, requests, and war durations |
| `integrations.yml` | Vault, EzEconomy, LuckPerms, PAPI, and InteractiveChat |
| `placeholders.yml` | Enabled PlaceholderAPI values and fallbacks |
| `performance.yml` | Cache and diagnostic settings |

Unknown future versions stop startup. Older supported versions are backed up and migrated. At most two managed backups are retained per configuration file.

---
Made By CatgirlYannick
