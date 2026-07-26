# Adapt protection API

`art.arcane.adapt.api.protection.Protector` is Adapt's land-and-region surface. It answers questions of the
form "may this player do *this kind of thing* at *this location*, given that an adaptation is what is
asking". Adapt ships implementations for WorldGuard, GriefDefender, GriefPrevention, Residence, Factions,
ChestProtect and LockettePro, and a third-party claims or region plugin registers alongside them the same
way.

It is built from Bukkit types and Adapt's own `Adaptation` type only. No VolmLib, no Adventure, no shaded
types: it links against a plain Paper compile classpath.

A protector can **only refuse**. Returning `true` means "no objection from me"; every other gate still runs
and an unlearned adaptation stays unreachable.

---

## Protector or AbilityUsePolicy?

The two coexist and overlap. Choosing wrong is the most likely way to waste an afternoon here, so decide
with this table before writing anything.

| | `Protector` | `AbilityUsePolicy` |
|---|---|---|
| **The question it answers** | may this happen *at this location* | may this player use this adaptation *at all, right now* |
| **Granularity** | seven verbs: break, place, PvP, PvE, interact, chest access, region | one decision per adaptation |
| **Location-aware** | yes — every method takes the `Location` in question | no — you get the `Player`, read their location yourself |
| **Consulted where** | once per active-level resolution as a global gate (`checkRegion`), then ad hoc by individual adaptations for each block or entity they touch | once per player, per adaptation, per tick |
| **Carries a reason** | no — a `boolean` | yes — `AbilityUseDecision.deny("…")`, recorded for diagnostics |
| **Scoping** | none; every registered protector sees every adaptation | `AbilityScope` by adaptation id or skill id |
| **Per-adaptation admin override** | yes — `[protectionOverrides]` in the config | no |
| **Registration** | `Adapt.instance.getProtectorRegistry()`, requires naming Adapt's plugin class | Bukkit `ServicesManager`, no Adapt class named |
| **Fault containment** | none — a throwing protector is not quarantined | quarantined after `providerFaultLimit` faults |
| **Failure mode** | an exception makes the adaptation inert and prints a stack trace | configurable, defaults to deny |

**Use `Protector` when the answer depends on where.** A veinminer chewing through fifty blocks asks
`canBlockBreak` fifty times, once per block, and only a protector can refuse block forty-one while
permitting block forty. A use policy cannot express that at all — it either kills the whole activation or
it does not.

**Use `AbilityUsePolicy` when the answer depends on who, or on their state.** Jails, duels, quest states,
rank gating, per-skill bans. It carries a reason, it can be scoped so you are not asked about adaptations
you do not care about, and a bug in your code is contained rather than taking an adaptation down with it.

**Use both** when you have both kinds of rule. They do not conflict: the protector's `checkRegion` runs
first, and both must permit.

**Do not** use `Protector` as a general-purpose veto by returning `false` from `checkRegion` for reasons
unrelated to location. It is slower — it is consulted from the hottest gate in the plugin — it cannot
explain itself, and an admin can switch it off per adaptation without you knowing.

---

## Depending on Adapt

Compile against the shaded `Adapt-<version>-all.jar` and declare the dependency in your plugin manifest.
The full instructions are in [README.md](README.md#depending-on-adapt).

Unlike the ability API, this one is not a Bukkit service. There is no `ServicesManager` entry for
`ProtectorRegistry`; the only way to reach it is through Adapt's plugin instance, which means your code
names the class `art.arcane.adapt.Adapt`:

```java
private ProtectorRegistry protectorRegistry() {
    Plugin plugin = getServer().getPluginManager().getPlugin("Adapt");

    if (!(plugin instanceof Adapt adapt) || !adapt.isEnabled()) {
        return null;
    }

    return adapt.getProtectorRegistry();
}
```

Three consequences:

- **You must declare the dependency in your manifest.** `softdepend: [Adapt]`, or `join-classpath: true`
  on a Paper plugin. Without it your classloader cannot see `art.arcane.adapt.Adapt` and the `instanceof`
  fails at class-load with `NoClassDefFoundError`.
- **The registry is created during Adapt's `onEnable`.** A plugin that enables before Adapt gets `null`.
  `softdepend`/`load: BEFORE` fixes the ordering; the null check above is your safety net for the case
  where Adapt is absent or failed to start.
- **The registry does not survive an Adapt restart.** Adapt constructs a fresh `ProtectorRegistry` every
  `onEnable` and calls `unregisterAll()` on shutdown. If Adapt is reloaded underneath you, your protector is
  gone. Re-register on `PluginEnableEvent` for the plugin named `Adapt` if you want to survive that.

## The lifecycle

```
registerProtector(protector)     you are added; isEnabledByDefault() is read now
   |
   |  Adapt calls your seven methods, many times, for as long as you are registered
   v
unregisterProtector(protector)   Adapt calls your unregister(), then drops you
   or
unregisterAll()                  Adapt shutting down: unregister() on everyone, then drop
```

`ProtectorRegistry`:

| Method | Contract |
|--------|----------|
| `void registerProtector(Protector)` | `synchronized`. Null and an instance already registered are ignored. Rebuilds both snapshots |
| `void unregisterProtector(Protector)` | `synchronized`. Calls your `unregister()` **first**, then removes you — so `unregister()` is called even for an instance that was never registered |
| `List<Protector> getDefaultProtectors()` | Immutable snapshot of the protectors whose `isEnabledByDefault()` was true at the last rebuild |
| `List<Protector> getAllProtectors()` | Immutable snapshot of every registered protector |
| `void unregisterAll()` | `synchronized`. `unregister()` on everyone, then clear. Adapt calls this on shutdown |

Rules worth stating plainly:

- **`isEnabledByDefault()` is not polled.** It is read when the registry rebuilds its snapshots, which
  happens on every register and unregister — yours or anyone else's. A protector that flips its own answer
  at runtime will not be picked up until some registration changes. Return a stable value, or re-register.
- **Duplicate detection is by `equals`, not by name.** `registerProtector` ignores a protector already in
  the list, which for an ordinary class means the same instance; a protector written as a `record` will
  collapse two value-equal instances instead. Either way it does not compare `getName()`. Two different
  protectors returning the same name both register, and the `[protectionOverrides]` lookup resolves that
  name to only one of them. Keep names unique.
- **`getName()` is the admin-facing key.** It is what an admin types into `[protectionOverrides]`. Pick
  something short and stable — the built-ins use `WorldGuard`, `GriefDefender`, `GriefPrevention`,
  `Residence`, `Factions`, `ChestProtect`, `LockettePro` — and never change it across releases.
- **`unregister()` is your teardown hook.** It is called on shutdown and on explicit unregistration. Use it
  to release listeners or caches; do not use it to touch the registry.

## Threading

Every `Protector` method runs on the tick thread that owns the acting player: the main thread on Paper, the
owning region thread on Folia. Reading blocks, entities and the player is legal there.

This is enforced upstream rather than at the call. Adapt's active-level resolution — which is what invokes
`checkRegion` — returns zero immediately when Folia is in use and the current region does not own the
player, so the protector is never reached off-region. The ad-hoc calls (`canBlockBreak` and friends) are
made from inside adaptation event handlers, which run on the same thread.

**This is the hottest third-party call surface in Adapt.** `checkRegion` is consulted during every
active-level resolution, and a block-affecting adaptation calls `canBlockBreak` once per candidate block —
a single veinminer activation can be dozens of calls in one tick.

- No I/O. No database, no HTTP, no file reads.
- No blocking. No `CompletableFuture.join`, no `callSyncMethod`, no lock held across the call.
- No allocation you can avoid. Cache your region lookups by chunk and invalidate on your own change events.
- Do not call back into Adapt. `Adaptation.getActiveLevel`, `hasActiveAdaptation` and anything else that
  resolves an active level will re-enter the gate you are standing inside.

There is **no watchdog and no quarantine** on this surface. A slow protector is not warned about and a
broken one is not disabled. The only protection is the contract.

Adapt does record how long each world-policy evaluation takes, across all protectors, in a rolling
sixty-second window. `WorldPolicyLatencyTelemetry.averageMillis(System.currentTimeMillis())` returns the
mean in milliseconds and is read-only diagnostics — Adapt's own commands use it, and nothing about its
value changes any decision.

---

## The seven verbs

Every method is a `default` returning `true`. Override only what you can actually answer.

```java
boolean canBlockBreak(Player player, Location blockLocation, Adaptation<?> adaptation)
boolean canBlockPlace(Player player, Location blockLocation, Adaptation<?> adaptation)
boolean canPVP(Player player, Location victimLocation, Adaptation<?> adaptation)
boolean canPVE(Player player, Location victimLocation, Adaptation<?> adaptation)
boolean canInteract(Player player, Location targetLocation, Adaptation<?> adaptation)
boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation)
boolean checkRegion(Player player, Location location, Adaptation<?> adaptation)
```

| Method | Where Adapt asks |
|--------|------------------|
| `checkRegion` | The global gate. Consulted with the player's **own** location during every active-level resolution, before the permission check. Returning `false` makes the adaptation completely inert for that player |
| `canBlockBreak` | Before an adaptation breaks or harvests a block — veinminer, tree feller, tunnel bore, chisel, and every other block-consuming adaptation, once per block |
| `canBlockPlace` | Before an adaptation places a block |
| `canPVP` | Before an adaptation damages or applies an effect to a **player** target |
| `canPVE` | Before an adaptation damages or applies an effect to a **non-player** entity |
| `canInteract` | Before an adaptation interacts with a block or entity that is neither a break, a place nor a chest |
| `canAccessChest` | Before an adaptation reads or writes a container |

The built-in protectors treat `checkRegion` as the base test and `&&` it with a flag lookup — WorldGuard's
`canBlockBreak`, for example, is `checkRegion(...) && flag(BLOCK_BREAK)`. You are not obliged to follow
that shape, but it is a sane default: implement `checkRegion` for "may they be doing anything here at all"
and let the verbs add specificity.

### The adaptation argument can be null

`Adaptation<?> adaptation` is the adaptation asking. **It is `null` when Adapt's mutation runtime asks**,
because a mutation is not an adaptation and has none to name. Every mutation-driven protection check —
combat, block break, block place, interact, region occupancy — passes `null`.

```java
if (adaptation != null && adaptation.getName().startsWith("pickaxe-")) {
    return miningAllowed(player, blockLocation);
}
```

A protector that dereferences `adaptation` without a null check throws on the first mutation check, and
because nothing quarantines a protector, it will keep throwing.

The mutation runtime also asks a **different set** of protectors than an adaptation does. It reads
`getDefaultProtectors()` directly, so `[protectionOverrides]` — which is keyed by adaptation id — has no
effect on any mutation check. A protector an admin removed from every adaptation is still consulted for
mutations, and one added by an override is not.

---

## Worked example: a claims plugin

```java
package com.example.warden;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ClaimProtector implements Protector {
  private final ClaimIndex claims;

  public ClaimProtector(ClaimIndex claims) {
    this.claims = claims;
  }

  @Override
  public String getName() {
    return "WardenClaims";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean canBlockBreak(Player player, Location blockLocation, Adaptation<?> adaptation) {
    return trusted(player, blockLocation);
  }

  @Override
  public boolean canBlockPlace(Player player, Location blockLocation, Adaptation<?> adaptation) {
    return trusted(player, blockLocation);
  }

  @Override
  public boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation) {
    return trusted(player, chestLocation);
  }

  @Override
  public boolean canPVP(Player player, Location victimLocation, Adaptation<?> adaptation) {
    return !claims.isClaimed(victimLocation);
  }

  @Override
  public void unregister() {
  }

  private boolean trusted(Player player, Location location) {
    if (player == null || location == null || !claims.isClaimed(location)) {
      return true;
    }

    return claims.isTrusted(player.getUniqueId(), location);
  }
}
```

`ClaimIndex` is yours; the sample needs `boolean isClaimed(Location)` and
`boolean isTrusted(UUID, Location)`, both answering from an in-memory index.

This protector deliberately does **not** override `checkRegion`. It has nothing to say about whether a
player may use an adaptation at all — only about specific blocks and containers — so it leaves the global
gate alone and stays out of the hot path. That is the right shape for most claims plugins.

### Registration

```java
package com.example.warden;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.protection.ProtectorRegistry;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class WardenPlugin extends JavaPlugin {
  private final ClaimIndex claims = new ClaimIndex();

  private Protector protector;

  @Override
  public void onEnable() {
    ProtectorRegistry registry = protectorRegistry();

    if (registry == null) {
      getLogger().warning("Adapt is not enabled; Warden claims will not gate adaptations");
      return;
    }

    protector = new ClaimProtector(claims);
    registry.registerProtector(protector);
  }

  @Override
  public void onDisable() {
    ProtectorRegistry registry = protectorRegistry();

    if (registry != null && protector != null) {
      registry.unregisterProtector(protector);
    }

    protector = null;
  }

  private ProtectorRegistry protectorRegistry() {
    Plugin plugin = getServer().getPluginManager().getPlugin("Adapt");

    if (!(plugin instanceof Adapt adapt) || !adapt.isEnabled()) {
      return null;
    }

    return adapt.getProtectorRegistry();
  }
}
```

Unregistering in `onDisable` is not optional the way it is with the `ServicesManager`. Bukkit does not know
about this registry, so nothing cleans up after you, and a protector left behind by a disabled plugin keeps
being consulted.

---

## The minimum: one verb

Everything except `getName()` and `isEnabledByDefault()` has a default. A protector that only cares about
chests is three methods:

```java
package com.example.warden;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class LockProtector implements Protector {
  private final ClaimIndex claims;

  public LockProtector(ClaimIndex claims) {
    this.claims = claims;
  }

  @Override
  public String getName() {
    return "WardenLocks";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation) {
    return player != null && chestLocation != null && claims.isTrusted(player.getUniqueId(), chestLocation);
  }
}
```

---

## Failure policy

This surface is older and blunter than the ability API, and it is honest about it: there is no fault
budget, no quarantine, no slow-call watchdog and no failure mode to configure.

| Misbehaviour | What happens |
|--------------|--------------|
| A method throws during `checkRegion` | The active-level resolver catches it, prints the stack trace, and returns level `0`. The adaptation is inert for that player until the protector stops throwing |
| A method throws during an ad-hoc call | The exception propagates out of the adaptation's event handler. Bukkit logs "Could not pass event … to Adapt" and the rest of that handler does not run |
| A method dereferences a null `adaptation` | The above, on every mutation check |
| Repeated faults | Nothing. There is no quarantine. It throws forever |
| A slow method | Nothing is logged. It shows up as tick time |
| `getName()` returns null | The per-adaptation protector-set builder throws `NullPointerException` while hashing names. In the active-level path that is caught and every adaptation goes inert; elsewhere it propagates |
| The same instance registered twice | The second call is ignored |
| Two instances share one `getName()` | Both are registered. `[protectionOverrides]` resolves the name to whichever the internal map saw last |
| Your plugin is disabled while still registered | You keep being consulted. Unregister yourself in `onDisable` |
| Adapt shuts down | `unregister()` is called on every protector and the registry is cleared |

Write defensively. Null-check `player`, `location` and `adaptation`, return `true` when you genuinely do
not know, and never let an exception out.

### Configuration

`plugins/Adapt/adapt/adapt.toml`.

`[protectorSupport]` toggles the built-in protectors' `isEnabledByDefault()`. It has no effect on a
third-party protector, whose default comes from your own `isEnabledByDefault()`.

| Key | Default | Protector |
|-----|---------|-----------|
| `worldguard` | `true` | `WorldGuard` |
| `griefdefender` | `true` | `GriefDefender` |
| `factionsClaim` | `false` | `Factions` |
| `residence` | `true` | `Residence` |
| `chestProtect` | `true` | `ChestProtect` |
| `griefprevention` | `true` | `GriefPrevention` |
| `lockettePro` | `true` | `LockettePro` |

A built-in protector is only registered at all when its plugin is present, so these keys switch off support
that would otherwise be active.

`[protectionOverrides]` adds or removes individual protectors for one adaptation, keyed by adaptation id
and then by `getName()`:

```toml
[protectionOverrides.pickaxe-veinminer]
WardenClaims = true
GriefPrevention = false
```

`true` adds a protector that is not enabled by default; `false` removes one that is. A name that matches no
registered protector is logged as an error and skipped. The resolved set is cached per adaptation and
invalidated automatically whenever a protector registers or unregisters, so an admin adding an override
still needs a reload for the config change itself to be read.

Overrides apply to **adaptation** checks only. Mutation checks use the default set — see
[The adaptation argument can be null](#the-adaptation-argument-can-be-null).

The default file ships a placeholder entry under the literal adaptation id `adaptation-name`, which matches
nothing. Leave it or replace it; it is inert either way.
