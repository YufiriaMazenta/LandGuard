# LandGuard

[简体中文](README.md) | English

> A modern, organization-oriented chunk land protection plugin for Minecraft.

LandGuard protects land at the granularity of **16×16 chunks**. Ownership is abstracted behind an SPI (player / built-in group / server / third-party organizations), roles are strictly separated from flags, all database writes go through a single writer thread with zero JDBC on the server thread, and the plugin is fully compatible with Folia's region threading model.

## Features

- **Pure chunk claims**: claim, unclaim (your own claims, plus group claims your in-group identity may unclaim), radius batch (skips chunks that are already claimed), walk-to-auto-claim / auto-unclaim, particle boundary visualization
- **Entry notices**: configurable action bar or chat message when you step into a claim, plus particle boundary rendering (with both a server-level and a per-player toggle)
- **Organization-first**: built-in groups (invites / identities / transfer / claim gifting) plus an open ownership SPI for third-party organization systems
- **Identity × flag model**: identities such as owner / manager / member / visitor are all defined in `identities.yml` (server-wide, groups cannot define their own), and each identity independently configures group-management permissions and in-claim behavior flags
- **Comprehensive protection vectors**: place / break / container / door / redstone / crafting / vehicle / animal / interaction entity / planting / harvest / item / bank; PvP / explosion / fire spread / fluid flow / piston / mob spawn / mob grief / trample. Cross-boundary pistons and fluids are decided by the target chunk
- **GUI management**: `/land` opens the claim list, detail view, tri-state flag cycling, members and claim bank menus
- **Economy**: optional Vault hook for quota trading, claim banks, periodic upkeep and debt grace periods
- **Lifecycle governance**: three configurable auto-reclaim chains — upkeep debt, inactivity, and orphaned owners (missing owner entities)
- **Admin tooling**: force-claim (server-owned), force unclaim / release / transfer, upkeep exemption, orphan listing, manual maintenance run
- **Storage**: built-in zero-config SQLite; optional MySQL; automatic column migrations for old databases
- **Performance**: O(1) in-memory chunk ownership index; protection checks average about **0.05–0.1µs/event** with 10,000 claimed chunks (see `ProtectionHotPathBenchmarkTest`)
- **Platforms**: Spigot / Paper 1.20 ~ 1.21.x, Folia supported (`folia-supported: true`), Java 21

## Install

1. Run Java 21 and a Spigot/Paper (or Folia) server 1.20+
2. Put `LandGuard-<version>.jar` into `plugins/`
3. (Optional) Install Vault and an economy provider to enable quota trading and claim banks
4. Start the server. Generated files under `plugins/LandGuard/`:
   - `config.yml`: main command aliases, bStats, etc.
   - `claim.yml`: default quota, radius limit, boundary particle duration, inactivity rules
   - `database.yml`: sqlite / mysql
   - `economy.yml`: economy toggle and quota prices
   - `upkeep.yml`: upkeep fee and debt/inactivity/orphan grace periods
   - `identities.yml`: group identities and permissions (group-management permission points + in-claim behavior flags)

## Identities & permissions

A group **identity** (role) and its two kinds of permissions are entirely defined in `identities.yml`, **server-wide**: groups may only use the identities defined in the config, and players cannot define their own.

Each identity configures two kinds of permissions:

- `permissions`: group-management permission points (e.g. `group.invite`, `group.assign_identity`, `claim.expand`, `claim.flags`) that decide whether the identity can invite / kick / assign identities / claim and expand group claims, etc.;
- `behaviors`: in-claim behavior flags (e.g. `place`, `container`, `bank`) that decide what the identity may do inside a claim; `behaviors: ['*']` means all behaviors.

Two semantic anchors (at most one each):

- `leader: true`: the leader identity — the highest in the group, obtainable only via "transfer leadership"; cannot be assigned, cannot be kicked;
- `default: true`: the non-member default identity — players who are not in the group are judged by it.

When the config is missing (file absent / section empty / every entry invalid), the plugin falls back to the built-in seed identities `owner` / `manager` / `member` / `visitor`, matching the old behavior item by item.

> ⚠️ An identity `id` is a stable identifier (stored on member rows and used as the key of claim flag override rows). **Changing an id invalidates existing claim flag override rows**, so prefer changing only `name` / `priority` / permissions and keep the id stable.

### How old data falls back after an identity is deleted

When a member row's `role_id` points to an identity no longer present in the config, that member takes effect as the "member" (`member`) identity, and **nothing is written back to the database**: the config can be reverted at any time, the read-time fallback is reversible and idempotent, and the member row itself is an audit trail. Likewise, claim flag override rows pointing to a deleted identity stop taking effect, but are neither leaked nor misapplied.

The `lg_group_role` table used by the old custom-role feature is no longer used. It is **not dropped automatically** (so you can roll back to an older version); run `DROP TABLE lg_group_role` manually to clean it up.

## Commands

| Command | Description |
|---|---|
| `/land` | Open the claims GUI |
| `/land claim [radius <r> \| auto] [--group <group id>]` | Claim the standing chunk / square batch / toggle walk auto-claim (batches skip chunks that are already claimed); by default as yourself, `--group` claims / expands group claims as that group |
| `/land unclaim [auto]` | Unclaim the standing chunk / toggle walk auto-unclaim (your own claims, plus group claims your in-group identity may unclaim; that permission is held by the leader and managers by default) |
| `/land list` | List your claims |
| `/land info` | Information about the claim you stand on |
| `/land boundary` | Toggle particle boundary rendering on claim entry (per player, on by default) |
| `/land rename <new name>` | Rename the claim you stand on (owner only; spaces allowed, up to 32 characters) |
| `/land transfer --player <player>` / `--group <group id>` | Transfer the claim you stand on to another player or group (owner only) |
| `/land group create\|disband\|invite\|accept\|deny\|leave\|kick\|transfer\|rename\|role\|list\|info` | Group management (`transfer` transfers group leadership, `rename` changes the display name) |
| `/land group role assign <group id> <player> <identity>` | Assign a member identity (identities come from `identities.yml`) |
| `/land group role list [group id]` | List all identities (id / display name / priority / permission count / behavior count); with a group id, also the member count per identity |
| `/land buy <amount>` / `/land sell <amount>` | Buy/sell chunk quota (economy required) |
| `/land bank [deposit\|withdraw <amount>]` | Claim bank (economy required) |
| `/land admin claim\|unclaim\|transfer\|release\|exempt\|rename\|info\|orphans\|run` | Administration |
| `/land reload` / `/land version` | Reload / version |

Aliases: `landguard`, `lg` (configurable in `config.yml`).

## Permissions

Each subcommand is granted independently (ungranted subcommands are hidden from tab completion); all nodes default to OP except `landguard.bypass`.

| Node | Default | Description |
|---|---|---|
| `landguard.command` | true | Use the main `/land` command |
| `landguard.command.claim` / `unclaim` / `list` / `info` / `boundary` / `rename` / `transfer` | OP | Claim / unclaim / claim list / claim info / boundary toggle / rename claim / transfer claim |
| `landguard.command.reload` / `version` | OP | Reload config / show version |
| `landguard.command.buy` / `sell` / `bank` | OP | Buy blocks / sell blocks / claim bank (requires Vault) |
| `landguard.command.group` | OP | Root of the `/land group` subtree (prints usage with no args) |
| `landguard.command.group.<action>` | OP | `create`, `disband`, `invite`, `accept`, `deny`, `leave`, `kick`, `transfer`, `rename`, `role`, `list`, `info` |
| `landguard.command.group.role.assign` / `.list` | OP | Assign a member identity / list all identities |
| `landguard.command.admin` | OP | Root of the `/land admin` subtree (prints usage with no args) |
| `landguard.command.admin.<action>` | OP | `claim`, `unclaim`, `transfer`, `release`, `exempt`, `rename`, `info`, `orphans`, `run` |
| `landguard.bypass` | **false (including OPs)** | Bypass all behavioral protection checks; must be granted explicitly |

## Developers: plug in your own organization system

Implement `ClaimOwnerProvider` + `ClaimOwner` and register with a single call to
`ClaimOwnerRegistry.INSTANCE.register(provider)` — any organization can then own claims.
See [docs/spi-claim-owner.zh-CN.md](docs/spi-claim-owner.zh-CN.md) for the complete minimal example.

## Build

```powershell
$env:JAVA_HOME="path-to-jdk21"
.\gradlew.bat build
```

Artifact: `build/libs/LandGuard-<version>.jar`.

## License

MIT recommended.
