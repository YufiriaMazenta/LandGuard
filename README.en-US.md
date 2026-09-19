# LandGuard

[简体中文](README.md) | English

> A modern, organization-oriented chunk land protection plugin for Minecraft.

LandGuard protects land at the granularity of **16×16 chunks**. Ownership is abstracted behind an SPI (player / built-in group / server / third-party organizations), roles are strictly separated from flags, all database writes go through a single writer thread with zero JDBC on the server thread, and the plugin is fully compatible with Folia's region threading model.

## Features

- **Pure chunk claims**: claim, unclaim, radius batch, walk-to-auto-claim, particle boundary visualization
- **Organization-first**: built-in groups (invites / roles / transfer / claim gifting) plus an open ownership SPI for third-party organization systems
- **Role × flag model**: owner / manager / member / visitor and custom roles, each with independently configurable behavioral and natural protection flags
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

## Commands

| Command | Description |
|---|---|
| `/land` | Open the claims GUI |
| `/land claim [radius <r> \| auto]` | Claim the standing chunk / square batch / toggle walk auto-claim |
| `/land unclaim` | Unclaim the standing chunk |
| `/land list` | List your claims |
| `/land info` | Information about the claim you stand on |
| `/land group create\|disband\|invite\|accept\|deny\|leave\|kick\|transfer\|role\|giveclaim\|list\|info` | Group management |
| `/land buy <amount>` / `/land sell <amount>` | Buy/sell chunk quota (economy required) |
| `/land bank [deposit\|withdraw <amount>]` | Claim bank (economy required) |
| `/land admin claim\|unclaim\|transfer\|release\|exempt\|info\|orphans\|run` | Administration |
| `/land reload` / `/land version` | Reload / version |

Aliases: `landguard`, `lg` (configurable in `config.yml`).

## Permissions

| Node | Default | Description |
|---|---|---|
| `landguard.command` | true | Use the main command |
| `landguard.command.admin` | OP | Administration commands |
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
