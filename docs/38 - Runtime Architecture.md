# Runtime Architecture

Adapt separates server lifecycle, skill/adaptation registration, player simulation, persistence, and optional integrations. Bukkit, Paper, and Folia state is scheduled through the appropriate server, region, or entity owner rather than an arbitrary asynchronous thread.

## Load and enable lifecycle

During `onLoad`, Adapt registers its WorldGuard flags when WorldGuard is present. During enable it performs the following work in order:

1. Backs up legacy configuration and removes retired configuration files.
2. Initializes platform bindings, audience/HUD support, services, language, Vault, models, PlaceholderAPI, and server information.
3. Opens SQL when enabled, then Redis only when both SQL and Redis are enabled; starts the persistence queue and glow support.
4. Creates the server simulation, discovers and registers skills/adaptations, canonicalizes configuration, and registers gameplay listeners.
5. Starts metrics and update/splash work, installs the Bukkit Ability API provider gateways, then registers protectors, WorldGuard region policy, HiddenOre integration, item/entity listings, and remaining services.

An unavailable optional plugin disables only its integration. A configured required external service can fail its own startup path; operators should inspect the complete exception and configuration summary rather than treating a partially enabled integration as proof that storage or synchronization is active.

## Server and player simulation

`AdaptServer` owns the `SkillRegistry`, server-scoped listeners, and online `AdaptPlayer` objects. `Ticked`, `TickedObject`, and `Ticker` schedule skill and adaptation intervals; player/entity/world access is marshalled to its owning scheduler on Folia.

At login, an `AdaptPlayer` loads progression and runtime state through the configured persistence path. Failed or incomplete loads use the plugin's recovery/fallback path rather than exposing a half-loaded player object. At quit, state is queued for persistence and player-scoped tasks, HUD state, and temporary runtime objects are released.

## Persistence

| Mode | Authority | Operational behavior |
|---|---|---|
| Local JSON | `plugins/Adapt/data/players/<uuid>.json` | Default when SQL is disabled; writes run through the persistence queue. |
| SQL | MySQL-compatible `ADAPT_DATA` table | Enabled by `sql.enabled`; the configured database must exist and be reachable. |
| SQL recovery file | `plugins/Adapt/data/players/<uuid>.json.pending-sql` | Preserves a shutdown fallback that did not reach SQL and replays it on that player's next load. |

The queue coalesces player writes and protects active sessions from premature purging. PlaceholderAPI has a separate approximately 60-second snapshot cache for recently offline players; that snapshot is a display fallback, not persistence authority.

## Hotload and restart boundaries

Core config, skill config, adaptation config, language overrides, GUI layout, and farm-block configuration have explicit hotload paths. Core hotload refreshes language, custom models, advancement synchronization, the material-value cache, default-active protector membership, and online mutation qualification; Ability API gateways resolve their live policy settings from the reloaded core config. SQL/Redis clients, metrics, protector registration, plugin load order, and Velocity deployment remain restart boundaries; see the exact matrix in `01 - Installation & Configuration.md`.

`/adapt migrate-configs` recursively converts legacy JSON configuration under Adapt's managed config roots to canonical TOML and deletes a source JSON file only after its matching TOML is written successfully. Normal startup also canonicalizes current configuration and retains legacy backups where supported.

## Runtime services

| Service | Responsibility |
|---|---|
| `CommandSVC` | `/adapt` command tree and permission-aware help |
| `HotloadSVC` | Watched core, skill, adaptation, language, GUI, and farm-block reload paths |
| `MutationSVC` / `MutationRuntimeSVC` | Mutation persistence, discovery, equip state, combat locks, and runtime effects |
| `AdaptIntegrationService` | Optional plugin bridges |
| `ConfigInputSVC` | GUI-backed configuration input |

XP provenance, spatial novelty, entropy, stillness, field-cycle, and pooled-payout listeners are controlled by `xpIntegrity`. NMS/version bindings provide version-specific attribute and packet access; operators should validate a supported server build at startup before testing gameplay behavior.

## Disable lifecycle

Disable unregisters PlaceholderAPI, disables services, stops metrics, simulation, and HUD work, and gives the persistence queue up to 30 seconds to flush. It then closes Redis and SQL, removes glow state, and clears API, region-policy, and protector registrations. A clean console shutdown is part of storage verification because a passing test suite cannot prove that queued live player data was flushed.

## Related pages

- `01 - Installation & Configuration.md`
- `08 - Protection & Region Policy.md`
- `09 - Integrations.md`
- `39 - Velocity & Cross-Server.md`
- `40 - Operator Runbooks & Smoke Tests.md`
