# Architecture

CatClans separates command and GUI handling, domain services, cached read models, persistence, integrations, and audit logging.

## Runtime Rules

- Bukkit and inventory operations run on the main thread.
- SQL and file cleanup run asynchronously.
- PlaceholderAPI reads the in-memory cache only.
- Repository writes use explicit service boundaries.
- Text logs are retained independently for bank, vault, and administrative actions.

## Storage

SQLite is the default. MySQL is configurable through environment-backed credentials. The repository layer owns schema initialization and transactions. Configuration migrations create bounded backups before modifying files.

## Integrations

Vault provides the economy contract. EzEconomy is validated as the current provider. LuckPerms is read for permission integration without automatic group mutation. PlaceholderAPI and InteractiveChat are optional.

---
Made By CatgirlYannick
