# Integrations

Adapt looks for thirteen optional plugins while it enables, and wires itself to whichever ones are running. Nothing here is required. A missing plugin is skipped silently and Adapt runs the same as it would on a bare server.

Because the discovery happens once during enable, installing, removing, enabling or disabling any of these plugins needs a server restart. Bukkit load order decides what Adapt can see, and Adapt's protector and adaptation registries are built from what it saw.

The integrations fall into three groups. Protection plugins get to veto what adaptations do. Vault and PlaceholderAPI hook Adapt into the rest of your server's economy and text. HiddenOre, Iris, AdvancedChests and MagicCosmetics change how specific adaptations behave.

## PlaceholderAPI

If PlaceholderAPI is enabled when Adapt enables, Adapt registers a persistent expansion under the identifier `adapt`. Placeholder paths are dot-separated segments after `%adapt_`, so you get things like `%adapt_player.level%`, `%adapt_skill.agility.level%` and `%adapt_mutation.slot-1%`.

Values come from a snapshot, not a live read, which keeps placeholder-heavy scoreboards off Adapt's data structures. Each online player's snapshot is republished about once per second on that player's owning thread. When a player leaves, their last snapshot stays readable for sixty seconds and then resolves to `---`. The complete key and result table is in `47 - API - PlaceholderAPI.md`.

## Vault

Vault lets you charge real currency for learning adaptations on top of the normal knowledge cost.

1. Install Vault and an economy provider.
2. Set `learningEconomy.enabled = true` in `adapt/adapt.toml`.
3. Set `learningEconomy.moneyPerKnowledge` to the price per point of knowledge.
4. Set `learningEconomy.refundPercent` to how much of that comes back on unlearn, or `0` for none.

Adapt withdraws `knowledgeCost * moneyPerKnowledge` before it changes anything, and only spends knowledge once the withdrawal succeeds. A failed withdrawal rejects the whole learning transaction, so nobody loses knowledge to a bounced payment.

Each level bought stores its own refund receipt on the skill line. A normal unlearn pays back `refundPercent` of the receipts covering the levels being dropped, unless `hardcoreNoRefunds` is on. If the deposit itself fails, the amount is parked on the skill line and paid out by the next learn or unlearn that player performs.

If Vault is missing, or Vault has no active economy provider, learning stays knowledge-only. Adapt warns once when prices are enabled with no provider available, and stops warning as soon as one appears.

## HiddenOre

The HiddenOre bridge only activates when Bukkit reports HiddenOre as enabled. Once it is, hidden veins stop being invisible to Adapt: breaking one awards Pickaxes XP from the same material-value table normal ores use, and several pickaxe and excavation adaptations start seeing veins as real targets.

Gem Polish adds a chance of an extra gem plus bonus vanilla experience, but only on diamond, emerald and lapis veins. Autosmelt turns raw iron, gold and copper drops into ingots. Drop to Inventory asks HiddenOre to deliver straight to the player's inventory. Pickaxe Veinminer chains through HiddenOre vein siblings, and Quarry Sense and Excavation's Seismic Ping both include hidden veins in what they detect.

If HiddenOre is installed but disabled, Adapt logs a warning and runs without the bridge.

## Iris

Iris controls whether the `axe-iris-feller` adaptation exists at all. Adapt only registers it when Iris is enabled, and the adaptation reports itself disabled if Iris goes away.

Tree recognition and the felling itself belong to Iris, through the `IrisTreeFellerService` Bukkit service. Adapt supplies the parts that are its business: the hunger reservation, the durability preservation chance for the player's level, the activation cooldown, and the stop and refund hooks Iris calls back into. Adapt's other axe veinminers ignore any break the Iris tree-feller service already owns, so the two never fight over the same tree. There is no general Iris biome bridge; this is the only Iris hook.

## AdvancedChests

When AdvancedChests is enabled, `rift-access` checks the block it is about to open remotely through `AdvancedChestsAPI`. If that block is an AdvancedChests container, Adapt opens page 1 of that chest instead of a plain Bukkit inventory.

A failed lookup is logged with a stack trace and the remote open fails safely rather than falling through to the vanilla inventory. Normal protection and active-adaptation checks still run either way, and the remote session only activates once the API has actually replaced the player's top inventory.

## MagicCosmetics

Several Adapt abilities scale off how much armor a player is wearing, and MagicCosmetics puts cosmetic items in the helmet and chestplate slots where they would otherwise read as real armor. When MagicCosmetics is enabled and reports an equipped `HAT` or `BAG`, Adapt drops the matching slot from its armor-value sum, so cosmetic carriers contribute nothing.

## Protection plugins

WorldGuard and six claim or container plugins are registered whenever they are present at enable. The `protectorSupport.*` keys then pick which of those registered protectors are active by default; Factions defaults off and the rest default on.

Indirect Rift container use and transfers from live item entities also dispatch Bukkit's normal interaction or pickup events before committing. Event-driven protection plugins can deny the same action that way without implementing anything Adapt-specific, and the registered protectors stay active as an extra gate.

Flags, exact config names, per-adaptation overrides and failure behavior are in `08 - Protection & Region Policy.md`.

## Velocity and Redis

The Velocity companion publishes a Redis data request before a player connects to a backend. Backends with SQL enabled publish the player's JSON and cache it for one minute; SQL stays authoritative. Installation, configuration and operational limits are in `39 - Velocity & Cross-Server.md`.

## Third-party Java API

Other Bukkit plugins can register `AbilityUsePolicy`, `AbilityCostProvider`, `Protector` and region-policy services, and can listen to Adapt's events. Registering a provider never grants an unlearned adaptation. See docs `41` through `50`.

## Reference

### Bukkit soft dependencies

Declared in `plugin.yml`, resolved during Adapt's enable.

| Plugin | Runtime behavior |
|---|---|
| PlaceholderAPI | Registers the persistent `adapt` placeholder expansion |
| WorldGuard | Registers region flags, the region policy source, and a protector |
| Factions | Registers the Factions claim protector |
| ChestProtect | Registers its container protector |
| Residence | Registers its residence protector |
| GriefDefender | Registers its claim protector |
| GriefPrevention | Registers its claim protector |
| LockettePro | Registers its lock/sign protector |
| Vault | Resolves the active economy provider for learning charges and refunds |
| HiddenOre | Connects hidden veins to mining XP and applicable pickaxe/excavation adaptations |
| Iris | Enables `axe-iris-feller` against Iris-managed trees |
| AdvancedChests | Lets `rift-access` open an AdvancedChests container |
| MagicCosmetics | Keeps cosmetic hat and bag equipment out of Adapt's armor-value sum |

Protection registration and default-enable settings are defined in `08 - Protection & Region Policy.md`.

### PlaceholderAPI behaviour

| Item | Value |
|---|---|
| Identifier | `adapt` |
| Author / version | `Volmit Software` / `1.0.0` |
| Persistent | Yes, survives a PlaceholderAPI reload |
| Player snapshot refresh | About once per second, on the player's owning thread |
| Offline grace before eviction | 60000 ms |
| Value when the snapshot is missing, or the resolver throws | `---` |
| Value for a key the expansion does not publish | `null`, so PlaceholderAPI leaves the placeholder text unchanged |

### Vault settings

| Key | Default | What it does |
|---|---|---|
| `learningEconomy.enabled` | `false` | Turns on Vault charges for learning; with it off, learning is knowledge-only |
| `learningEconomy.moneyPerKnowledge` | `1.0` | Currency charged per point of knowledge. Values at or below zero, or non-finite, disable the charge |
| `learningEconomy.refundPercent` | `100.0` | Percentage of the recorded receipts returned on unlearn, capped at 100 |
| `hardcoreNoRefunds` | `false` | When true, unlearning refunds neither knowledge nor currency |

Skill-line storage keys used by the economy: `vault-learning-refund-<adaptation>-level-<n>` for per-level receipts, and `vault-learning-pending-refund` for a deposit that failed and is awaiting retry.

### HiddenOre bridge

The bridge listens to `HiddenOreDropsEvent` and applies Gem Polish, Autosmelt, Drop to Inventory and the ore XP award in that order. Autosmelt covers `RAW_IRON`, `RAW_GOLD` and `RAW_COPPER`. Gem Polish qualifies on `DIAMOND`, `EMERALD` and `LAPIS_LAZULI` veins only, adding its bonus experience to the event and rolling its gem chance for one extra drop. The XP award uses the vein's display material against the Pickaxes value table, credited at the block's location. Stats recorded: `pickaxe.gem-polish.gems-polished`, `pickaxe.autosmelt.ores-smelted`.

Outside that event the bridge also answers nearest-vein and vein-radius queries for Quarry Sense and Seismic Ping, and vein-sibling lookups for Pickaxe Veinminer.

### Iris tree feller

`axe-iris-feller` is registered only when Iris is enabled, and delegates to `art.arcane.iris.api.tree.IrisTreeFellerService` from the Bukkit services manager. Max level 3, tick interval 6127 ms, durability preservation chance 0% / 25% / 75% by level. It triggers on `BlockBreakEvent` at `HIGH` priority ignoring cancelled events, and skips any break that is already vein-mined or already managed by the Iris service.

## See also

- `01 - Installation & Configuration.md`
- `08 - Protection & Region Policy.md`
- `39 - Velocity & Cross-Server.md`
- `47 - API - PlaceholderAPI.md`
