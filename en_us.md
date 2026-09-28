# Mod Access Control

[中文](README.md)

A **server-side** Minecraft mod that gives server owners precise control over
which mods players may (or must not) have installed. It behaves identically on
**Forge**, **Fabric**, and **NeoForge**, with one shared config file and the
same `/mac` command suite.

> **Note:** Clients must also install this mod to complete the handshake.
> Clients without it are denied on join (configurable via `requireClientMod`).

## Features

- **Two-stage handshake** — a lightweight protocol/loader exchange at login,
  full mod-list verification before entering the world, then periodic re-checks
  using the reported list (no repeated transfers).
- **Required-mod checks** — enforce presence (`presence`), version ranges
  (`version_range`), or strict matching (`strict`).
- **Policy system** — `whitelist`, `blacklist`, or `switch` (dual-mode with a
  hot-swappable active policy and interval re-checks).
- **In-game rule management** — view, edit, reload, and save
  whitelist/blacklist/required lists live via `/mac` commands, with
  tab-completion and one-click list building from any player's mods
  (`/mac learn`).
- **Multi-language, server-side** — all messages (kick screens, admin alerts,
  command feedback) follow the server's configured language:
  `zh_cn`, `zh_tw`, `en_us`, `ja_jp`, `ru_ru` (`/mac lang <language>`, keys are
  shipped as JSON catalogs and easy to extend).
- **Clear player feedback** — the disconnect screen lists exactly which required
  mods are missing, which versions mismatch, and which banned/off-list mods
  were found, plus a customizable footer hint area.
- **Exemptions** — skip all checks for specific players (name or UUID) or all
  OPs.
- **Dry-run mode** — detect and log violations without kicking, so you can
  validate rules before going live.
- **Mod history & audit** — every player's mod list is persisted (JSONL) and
  auditable via `/mac audit` / `/mac recent`.
- **Admin alerts** — online admins get chat + action-bar + sound notifications
  on every block.
- **Robust error handling** — corrupted configs are auto-backed up and reset;
  failures are caught and logged, never crashing the server.

## Configuration

File: `<server>/config/mod_access_control.json` (auto-generated on first start)

Key options:

- `enabled` — master switch
- `requireClientMod` — require the client mod (default `true`)
- `strictLoader` — require a matching mod loader
- `requiredCheckMode` — `presence` / `version_range` / `strict`
- `policy.mode` — `whitelist` / `blacklist` / `switch`
- `language` — server message language: `zh_cn` / `zh_tw` / `en_us` / `ja_jp` / `ru_ru`
- `exemptPlayers` / `exemptOps` — exemptions
- `dryRun` — log violations without kicking
- `allowedMacVersions` — restrict which versions of this mod may connect

## Commands

`/mac` — `status`, `recent`, `check`, `audit`, `learn`, `reload`, `save`,
`recheck`, `enabled`, `dryrun`, `mode`, `active`, `exempt`, `allowedmac`,
`required`, `whitelist`, `blacklist`, and `lang`.
Run `/mac help` in-game for the full list.
