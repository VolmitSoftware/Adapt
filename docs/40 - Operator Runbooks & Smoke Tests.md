# Operator Runbooks & Smoke Tests

Use a disposable player and isolated Paper/Folia instance for destructive command and persistence checks. Record automated tests, plugin startup, live gameplay, cross-server behavior, and clean shutdown as separate evidence; none substitutes for another.

## Fresh installation and upgrade

1. Confirm Java 25 and a supported 26.1 Paper/Purpur/Folia API build, then place the shaded Adapt jar in `plugins/`.
2. Before an upgrade, stop the server and back up `plugins/Adapt/` plus the SQL database when enabled.
3. Start once and confirm the plugin enables, `plugins/Adapt/adapt/adapt.toml` and skill/adaptation TOML files are generated or canonicalized, and no NMS/version binding error is present.
4. Review legacy migration/backup messages, optional integration messages, the config summary, and any `plugins/Adapt/data/players/<uuid>.json.pending-sql` recovery files.
5. Join in Survival, right-click the side of a bookshelf with neither hand holding a placeable block, and confirm the GUI opens. Sneaking, clicking the top/bottom face, or holding a placeable block should not trigger the default activator.
6. Stop cleanly and confirm no persistence-flush, Redis-close, SQL-close, or scheduler error.

## Progression and permissions

1. Earn XP for one skill and verify its XP, level, and knowledge against the formulas in `02 - Concepts.md` and `05 - Configuration Math.md`.
2. Learn and unlearn an inexpensive adaptation. Verify prerequisite, knowledge, power, world blacklist, `adapt.use.<skill>.<adaptation>`, refund, and recipe-book behavior.
3. Test with a non-op whose permissions are explicit; operator status can conceal missing grants.
4. Run `/adapt help`, `/adapt help 2`, and `/adapt ?`; confirm only commands the player can execute are shown.
5. Run `/adapt gui target=skill:<name> force=true` or `/adapt gui target=adaptation:<id> force=true` as an authorized administrator and confirm the GUI actually opens while bypassing only the target's use permission. An adaptation that is disabled in config remains disabled.
6. Exercise `/adapt clear` only on a disposable profile. Use `/adapt reset` only after backing up that profile and confirm the reset/default permission requirements in `04 - Commands & Permissions.md`.

## Items, recipes, and brewing

1. Give an experience orb and knowledge orb, throw each, and verify the encoded target skill and amount are applied once.
2. Learn Crafting: Backpacks, craft the eight-leather/center-chest recipe, and test both slot and bundle modes at the configured capacity.
3. Confirm only an empty backpack cycles when crafted alone, direct nesting is rejected, configured indirect nesting is rejected, byte-ceiling deposits are refused, and contents survive close, quit/rejoin, restart, death/drop policy, and a forced inventory interruption.
4. Learn one crafting adaptation and confirm its recipe is discovered; unlearn it and confirm discovery is removed unless another learned unlock owns the key.
5. Test one weak and one strong custom brew with exact base potion, ingredient, fuel, owner adaptation, 320-tick completion, ingredient/fuel consumption, and all three bottle slots. Change an input mid-brew and confirm cancellation.
6. Have another listener cancel an ingredient-slot click, then switch away from the stand before Adapt's delayed click handling. Confirm neither case moves the cursor item or starts a custom brew.
7. After changing `value.baseValue` or `value.valueMultipliers`, hotload core config and verify a consuming adaptation uses the rebuilt value rather than stale `value-cache.json` data.

## Protection and integrations

1. Start without any optional plugins and confirm Adapt still enables.
2. For each installed protector, test a denied location through a normal adaptation and the bookshelf activator. Toggle its `protectorSupport.*` key, hotload core config, and confirm it enters or leaves the default-active set.
3. Test an adaptation-level `enabledProtectors`/`disabledProtectors` override by exact protector name. Confirm an absent protector plugin cannot be enabled by config alone.
4. With WorldGuard, deny `use-adaptations` and `adapt-xp` separately; confirm the first gates ability use and the second zeros location XP.
5. If installed, test the exact surfaces in `09 - Integrations.md`: PlaceholderAPI keys/offline expiry, Vault charge and refund failure, HiddenOre block rewards, Iris tree-feller routing, AdvancedChests Rift Access, and MagicCosmetics armor slots.
6. In an event-driven claim denial such as GriefPrevention, verify a Reliquary Portkey cannot bind or remotely open the container. Repeat with a double chest and deny either half; neither half may open, and every held chunk ticket must release when the attempt ends or the view closes.
7. Link two Rift Conduit containers, change protection before a deferred bind stage and again before a flow reaches its partner, and confirm the denied bind rolls back and denied-flow items return to the source.
8. Deny pickup events for Drop-To-Inventory, Fetch Shot, Item Snatch, Taming Fetch, Void Magnet, and Compost Cascade. Confirm the original item entity remains, with no success XP or statistic.
9. Deny a ray-targeted Time In A Bottle or Compost Cascade use and a passive Accelerate crop or station target. Confirm the target block, station progress, stored time, and cooldown remain unchanged.
10. Deny one secondary Axe Chop and Pickaxe Veinminer block. Confirm Axe Chop applies no per-block wear or cooldown for the refused log, and that the denied Veinminer block does not contribute to its success statistic or aggregate effect.

## Configuration, localization, and migration

1. Make one reversible edit in each hotloaded family: core, skill, adaptation, language, GUI, and farm blocks. Confirm the expected behavior changes without a restart and that malformed TOML reports an error without corrupting the prior file.
2. Change a restart-bound SQL, Redis, metrics, plugin load-order, or Velocity setting and confirm it does not receive a false live-reload claim; restart before acceptance testing. Ability API policy settings are core-hotloaded and should change without a restart.
3. Put a legacy JSON file only inside an Adapt-managed config tree, run `/adapt migrate-configs` with both required permissions, and verify its TOML equivalent before confirming JSON deletion.
4. Override one localization key, delete the override, and confirm bundled fallback. Reject an override file larger than 2 MiB.
5. Change a GUI entry and verify slot, item, name, and lore while confirming gameplay costs and gates remain unchanged.

## SQL persistence and recovery

1. Test local JSON first. Earn progression, quit, stop cleanly, restart, and verify the same state.
2. Enable SQL with a disposable database, restart, and verify schema/table access and a complete player round trip.
3. Interrupt SQL during a save, stop cleanly after retries fail, and confirm `plugins/Adapt/data/players/<uuid>.json.pending-sql` appears. Restore SQL, load that player, and confirm the recovery file is replayed and removed before deleting any recovery data manually.
4. Verify purge settings never remove an online player's active data and exercise purge only against backed-up disposable records.

## Mutations

1. Enable mutations and one profile at a time; restart when changing deployment-bound state, then use `/adapt mutations menu` and admin discovery/equip commands.
2. Verify discovery chance, unlock requirements, slot count, consent mode, allowed worlds, combat lock, cooldowns, resource costs, and profile-specific settings from `34 - Mutations Overview.md` and `35 - Mutations Catalog.md`.
3. Test `EXPLICIT`, `PARTY`, `FRIEND`, and `DISABLED` consent with distinct players. The current `FRIEND` mode has no friendship provider and therefore accepts nobody.
4. Confirm `%adapt_mutation.enabled%`, `%adapt_mutation.slot-1%`, `%adapt_mutation.slot-1-id%`, and a type key such as `%adapt_mutation.gale-lung.state%`.

## Velocity handoff

1. Establish identical SQL/Redis settings on two disposable backends and install the companion on Velocity as described in `39 - Velocity & Cross-Server.md`.
2. Verify proxy publication plus backend SQL/Redis initialization separately.
3. Change progression and mutation equipment on backend A, quit or switch normally, connect to backend B, and compare the full profile.
4. Repeat while one Redis connection is unavailable; confirm the failure is visible and SQL/pending-write behavior matches the documented authority model.

## Folia and lifecycle

1. On Folia, open and navigate GUIs, activate movement/combat/block abilities in more than one region, use custom brewing and backpack inventories, teleport, die/respawn, and quit/rejoin.
2. Inspect the complete console for async world/entity/inventory access, region ownership, rejected scheduler tasks, listener failures, and stack traces.
3. Stop with recently changed player data and an active optional integration. Confirm the persistence queue flushes within its 30-second shutdown allowance and all clients/services close cleanly.

## Acceptance record

| Evidence | Record |
|---|---|
| Automated | Exact Gradle command, test totals, failures, and warnings |
| Startup | Server implementation/build, Java version, Adapt version, enabled integrations, and clean enable log |
| Live gameplay | Player/permission context, actions, expected result, actual result, and console errors |
| Cross-server | Proxy/backend versions, SQL/Redis topology, handoff direction, and full state comparison |
| Shutdown | Pending queue before/after, flush result, and close/scheduler errors |

## Related pages

- `01 - Installation & Configuration.md`
- `04 - Commands & Permissions.md`
- `08 - Protection & Region Policy.md`
- `34 - Mutations Overview.md`
- `39 - Velocity & Cross-Server.md`
