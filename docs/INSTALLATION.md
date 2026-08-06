# Installation

## Required

- Paper 1.21.x
- Java 21
- `CatClans-v0.1.0-BETA.jar`

## Economy

The bank uses Vault as its abstraction and EzEconomy as the current provider. Install both before enabling the bank. `Coins` is only the default visible currency name; balances come from the provider.

## Optional Integrations

- LuckPerms for network permission groups
- PlaceholderAPI for `%catclans_*%`
- InteractiveChat for placeholder use in interactive chat

## Procedure

1. Stop Paper completely.
2. Remove older or duplicate clan JARs.
3. Put the current JAR and required dependencies in `plugins/`.
4. Start Paper once and inspect the console.
5. Configure branding and database settings.
6. Restart Paper.

Do not use `/reload` or plugin hot reloaders.

---
Made By CatgirlYannick
