# Operator Runbooks

These are the checks worth running before you trust an Adapt install with a real player base. Each section is a short procedure you can follow verbatim on a throwaway server. Run them with a disposable player on an isolated Paper or Folia instance, because several steps delete progression on purpose.

A clean startup, working live gameplay, correct cross-server behavior, and a clean shutdown are four separate things; a plugin that enabled without an error says nothing about whether SQL is actually reachable, and a working session says nothing about whether queued player data reached disk on the way down.

Check with a non-op account whose permissions are written out explicitly. Operator status quietly satisfies almost every gate in the plugin and will hide a missing grant right up until a real player hits it.

## First install or upgrade

1. Confirm Java 25 and a Paper, Purpur, or Folia server on the Minecraft 26.1 API line, then put the shaded Adapt jar in `plugins/`.
2. For an upgrade, stop the server first and back up `plugins/Adapt/` plus the SQL database if you use one.
3. Start once. Confirm the plugin enables, `plugins/Adapt/adapt/adapt.toml` and the skill and adaptation TOML files are generated or canonicalized, and no version binding error appears.
4. Read the rest of the boot log: legacy migration and backup lines, optional integration lines, the config summary, and any `plugins/Adapt/data/players/<uuid>.json.pending-sql` files left from a previous run.
5. Join in Survival and right-click the side of a bookshelf with neither hand holding a placeable block. The skills menu should open. Sneaking, clicking the top or bottom face, or holding a placeable block in either hand should do nothing.
6. Stop cleanly. Confirm no persistence flush, Redis close, SQL close, or scheduler error on the way down.

## Progression and permissions

1. Earn XP in one skill and check its XP, level, and knowledge against the formulas in `02 - Concepts.md` and `05 - Configuration Math.md`.
2. Learn and then unlearn a cheap adaptation. Watch the prerequisite, knowledge cost, power cost, world blacklist, `adapt.use.<skill>.<adaptation>` grant, refund, and recipe book behavior on both halves.
3. Run `/adapt help`, `/adapt help 2`, and `/adapt ?`. Only commands that account can actually run should be listed.
4. As an administrator holding `adapt.gui`, run `/adapt gui target=skill:<name> force=true` and `/adapt gui target=adaptation:<id> force=true`. The menu should open, and `force` should bypass only the target's use permission. An adaptation disabled in config stays closed either way.
5. Use `/adapt clear` on a disposable profile only. Use `/adapt reset` only after backing that profile up; it needs `adapt.clear` and a second run to confirm.
6. Reset an offline player as well as an online one. An online reset swaps the live data so the player keeps playing; an offline reset deletes the stored file and marks that UUID so a stale in-memory copy cannot write it back.

## Items, recipes, and brewing

1. Give an experience orb and a knowledge orb, throw each one, and confirm the encoded skill and amount are applied exactly once.
2. Learn Crafting: Backpacks, craft the eight-leather and center-chest recipe, and exercise both slot mode and bundle mode at the configured capacity.
3. Confirm that only an empty backpack cycles mode when crafted alone, that a backpack cannot go directly inside another, that configured indirect nesting is refused, that a deposit past the byte ceiling is refused, and that contents survive a close, a quit and rejoin, a restart, the death and drop policy, and a forced inventory interruption.
4. Learn one crafting adaptation and confirm its recipe is discovered in the recipe book, then unlearn it and confirm the discovery is removed.
5. Brew one weak and one strong custom potion end to end: exact base potion, exact ingredient, enough fuel, the owning adaptation learned, the full 320-tick timer, ingredient and fuel consumption, and all three bottle slots converting. Change an input mid-brew and confirm the task cancels.
6. Have another plugin cancel an ingredient-slot click, then separately walk away from the stand before Adapt's one-tick-later handling runs. Neither case may move the cursor item or start a brew.
7. Change `value.baseValue` or a `value.valueMultipliers` entry, hotload the core config, and confirm a consuming adaptation uses the rebuilt value instead of the old `value-cache.json` data.
8. On Folia only: change an adaptation's enabled flag while players are online and confirm Adapt logs that it is deferring recipe registration. Empty the server and confirm registration then completes.

## Protection and integrations

1. Start with no optional plugins installed at all and confirm Adapt still enables.
2. For each installed protector, test a denied location through a normal adaptation and through the bookshelf activator. Toggle its `protectorSupport.*` key, hotload the core config, and confirm it joins or leaves the default-active set.
3. Test an adaptation-level `enabledProtectors` or `disabledProtectors` override by exact protector name. A protector whose plugin is absent must not be enabled by config alone.
4. With WorldGuard, deny `use-adaptations` and `adapt-xp` separately. The first should gate ability use and the second should zero location XP.
5. If installed, test the exact surfaces listed in `09 - Integrations.md`: PlaceholderAPI keys and offline expiry, Vault charge and refund failure, HiddenOre block rewards, Iris tree-feller routing, AdvancedChests with Rift Access, and MagicCosmetics armor slots.
6. In an event-driven claim denial such as GriefPrevention, confirm a Reliquary Portkey can neither bind nor remotely open the container. Repeat on a double chest with only one half denied; neither half may open, and every held chunk ticket must release when the attempt ends or the view closes.
7. Link two Rift Conduit containers. Change protection once before a deferred bind stage and once before a flow reaches its partner. A denied bind must roll back and denied-flow items must return to the source.
8. Deny pickup events for Drop-To-Inventory, Fetch Shot, Item Snatch, Taming Fetch, Void Magnet, and Compost Cascade. The original item entity must survive with no success XP and no statistic.
9. Deny a ray-targeted Time In A Bottle or Compost Cascade use, and a passive Accelerate crop or station target. The target block, station progress, stored time, and cooldown must all be untouched.
10. Deny one secondary Axe Chop and one secondary Pickaxe Veinminer block. Axe Chop must apply no wear or cooldown for the refused log, and the denied Veinminer block must not count toward its success statistic or its aggregate effect.

## Config, localization, and migration

1. Make one reversible edit in each hotloaded family: `adapt.toml`, a skill file, an adaptation file, `models.toml`, `mutations.toml`, and a locale override. Confirm the behavior changes without a restart, and that malformed TOML is rejected with an error rather than corrupting the file already in memory.
2. Change a restart-bound setting (SQL, Redis, metrics, plugin load order, Velocity) and confirm nothing claims it reloaded live. Restart before you accept it. Ability API policy settings are core-hotloaded and should change without a restart.
3. Put a legacy JSON file inside an Adapt-managed config tree, run `/adapt migrate-configs` as an account holding `adapt.main` and `adapt.debug`, and verify the TOML equivalent before you accept that the JSON was deleted. A JSON file with no TOML twin should still be there.
4. Override one localization key, delete the override, and confirm the bundled string comes back. Confirm an override file larger than 2 MiB is rejected.
5. Change a GUI entry and verify the slot, item, name, and lore, while confirming that gameplay costs and gates did not move with it.

## SQL persistence and recovery

1. Test local JSON first. Earn progression, quit, stop cleanly, restart, and confirm the same state comes back.
2. Enable SQL against a disposable database, restart, and confirm table access plus a complete player round trip.
3. Interrupt SQL during a save, then stop cleanly after the retries fail. Confirm `plugins/Adapt/data/players/<uuid>.json.pending-sql` appears. Restore SQL, load that player, and confirm the recovery file is replayed and removed before you delete any recovery data by hand.
4. Confirm a reset never removes an online player's active data, and only exercise resets against backed-up disposable records.

## Mutations

1. Enable mutations and one profile at a time. Restart when you change deployment-bound state, then use `/adapt mutations menu` and the admin discovery and equip commands.
2. Verify discovery chance, unlock requirements, slot count, consent mode, allowed worlds, combat lock, cooldowns, resource costs, and profile-specific settings against `34 - Mutations Overview.md` and `35 - Mutations Catalog.md`.
3. Test `EXPLICIT`, `PARTY`, `FRIEND`, and `DISABLED` consent with separate players. `FRIEND` currently has no friendship provider behind it, so it accepts nobody.
4. Confirm `%adapt_mutation.enabled%`, `%adapt_mutation.slot-1%`, `%adapt_mutation.slot-1-id%`, and a type key such as `%adapt_mutation.gale-lung.state%`.

## Velocity handoff

1. Put identical SQL and Redis settings on two disposable backends and install the companion on Velocity as described in `39 - Velocity & Cross-Server.md`.
2. Verify proxy publication and backend SQL/Redis startup as two separate facts.
3. Change progression and mutation equipment on backend A, switch normally, connect to backend B, and compare the whole profile.
4. Repeat with one Redis connection unavailable. The failure should be visible in the log, and SQL plus pending-write behavior should match the authority model.

## Folia and lifecycle

1. On Folia, open and navigate menus, use movement, combat, and block abilities in more than one region, use custom brewing and backpack inventories, teleport, die and respawn, and quit and rejoin.
2. Read the whole console afterwards for async world, entity, or inventory access, region ownership complaints, rejected scheduler tasks, listener failures, and stack traces.
3. Stop the server with recently changed player data and an active optional integration. Confirm the persistence queue flushes inside its 30 second allowance and every client and service closes cleanly.

## Reference

### Fixed values these checks depend on

| Item | Value |
|---|---|
| Custom brew duration | 320 ticks for every registered recipe |
| Locale override size limit | 2 MiB |
| Shutdown flush allowance | 30 s |
| Default activator | `BOOKSHELF`, side faces only, both hands free of placeable blocks, not sneaking |
| `/adapt migrate-configs` | `adapt.main` plus `adapt.debug` |
| `/adapt reset` | `adapt.clear`, confirmed by running it twice |

## See also

- `01 - Installation & Configuration.md`
- `04 - Commands & Permissions.md`
- `08 - Protection & Region Policy.md`
- `34 - Mutations Overview.md`
- `39 - Velocity & Cross-Server.md`
