# Commands

CatClans is GUI-first. `/clan` or `/clans` opens the main menu, where most actions are available.

## Player Commands

| Command | Purpose |
| --- | --- |
| `/clan` | Open the main clan GUI |
| `/clans` | Alias for `/clan` |
| `/clan create` | Start chat-based clan creation and show the required input format |
| `/clan invite <player>` | Invite a player; the invitation action requires confirmation |
| `/clan delete` | Delete your own clan after confirmation |
| `/clan leave` | Leave the current clan; ownership passes to the highest eligible role when necessary |
| `/clan allychat` | Use the in-game ally chat |
| `/clan ac` | Short ally-chat form |
| `/clantop` | Open the permanent clan leaderboard GUI |

Clan names, tags, formatted tags, role names, role priorities, and custom bank amounts use chat input. Enter `cancel` to abort an active input flow.

## Administration

| Command | Permission | Purpose |
| --- | --- | --- |
| `/clanadmin delete <clan>` | `catclans.management.clan.delete` | Delete any clan |
| `/clanadmin war end <clan-one> <clan-two>` | `catclans.admin.war.end` | Score and end an active war using stored deaths |

The final configuration-GUI command is intentionally disabled until its contract is finalized.

---
Made By CatgirlYannick
