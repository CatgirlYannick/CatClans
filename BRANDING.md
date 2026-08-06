# Branding and Placeholder Guide

CatClans separates product identity from server branding. The JAR, Java package, command namespace, permissions, and PlaceholderAPI identifier use the permanent product name `CatClans`. The public server name is configurable.

## Change the Server Name

After the first start, edit `plugins/CatClans/config.yml`:

```yaml
branding:
  server-name: "Your Server"
  author-name: "CatgirlYannick"
  plugin-name: "CatClans"
```

`server-name` replaces `{{SERVER_NAME}}` in public messages. `plugin-name` changes display text only; it does not rename the JAR, data folder, commands, permissions, or PAPI identifier.

## Message Placeholders

- `{server_name}`: configured server name
- `{plugin_name}`: configured display name
- `{author_name}`: configured author label
- `{currency}`: configured economy display name

## PlaceholderAPI

- `%catclans_server_name%`
- `%catclans_plugin_name%`
- `%catclans_author_name%`

Clan placeholders remain under `%catclans_*%`. See `docs/PLACEHOLDERS.md`.

## Renaming Technical Identifiers

Changing `catclans` in commands, permissions, the data folder, or PAPI is a breaking API change and requires a new source build. Do not change these values in a normal server rebrand.

## Migrating an Older Installation

Stop Paper, back up the server, remove the old JAR, and copy required data into `plugins/CatClans/`. Never run two generations of the plugin at once. Review permissions because the active namespace is `catclans.*`.

---
Made By CatgirlYannick
