# CatClans

CatClans is a configurable Paper 1.21 clan plugin by CatgirlYannick. It provides GUI-first clan management, persistent roles and member permissions, a clan vault, an economy-backed bank, homes, diplomacy, wars, rankings, and a clan battlepass.

## Download

Download the current JAR and the complete offline documentation from the [GitHub Releases](https://github.com/CatgirlYannick/CatClans/releases) page.

The public download text is available in [docs/DOWNLOAD_DESCRIPTION.md](docs/DOWNLOAD_DESCRIPTION.md). The complete configuration and rewards guide is available as [offline HTML](docs/CONFIG_AND_REWARDS_GUIDE.html).

## Features

- GUI-first clan management with chat-based text input
- Persistent roles, priorities, role rights, and member overrides
- Clan bank through Vault and EzEconomy
- Persistent item vault and safe clan homes
- Alliances, clan wars, rankings, and clan-wide progression rewards
- SQLite and configurable MySQL storage
- LuckPerms, PlaceholderAPI, and InteractiveChat support

## Requirements

- Java 21
- Paper 1.21.x
- Maven 3.9+ for building
- Vault and EzEconomy for the clan bank
- Optional: LuckPerms, PlaceholderAPI, InteractiveChat

## Build

```text
mvn clean package
```

The release JAR is written as `target/CatClans-v0.1.0-BETA.jar`. The Maven build runs the test suite, stages documentation, verifies the JAR size, and creates the upload package.

## First Start

1. Put the JAR in `plugins/`.
2. Install Vault and EzEconomy when the bank is enabled.
3. Start Paper once.
4. Edit `plugins/CatClans/config.yml` and replace `{{SERVER_NAME}}`.
5. Restart Paper after configuration changes.

The JAR must remain below 3 MiB. Runtime database drivers are loaded through Paper libraries and are not shaded into the plugin.

See [BRANDING.md](BRANDING.md), [docs/INSTALLATION.md](docs/INSTALLATION.md), and [docs/COMMANDS.md](docs/COMMANDS.md).

---
Made By CatgirlYannick
