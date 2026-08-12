# Adapt protection API

`art.arcane.adapt.api.protection.Protector` is how a land, claims, or region plugin tells Adapt "not here". It answers questions shaped like "may this player do this kind of thing at this location, given that an adaptation is what is asking". Adapt ships implementations for WorldGuard, GriefDefender, GriefPrevention, Residence, Factions, ChestProtect and LockettePro, and your plugin registers alongside them the same way.

A protector can only refuse. Returning `true` means "no objection from me". Every other gate still runs, and an adaptation the player has not learned stays unreachable no matter what you return.

The interface is deliberately small. It touches Bukkit types and Adapt's own `Adaptation` type, nothing else. No VolmLib, no Adventure, no shaded classes, so it links against a plain Paper compile classpath.

The other half of this page is `RegionPolicySource`, a separate single-provider hook for region-driven XP, ability power, and temporary adaptation grants. That one grants rather than refuses, and only one plugin can own it at a time.

## Protector or AbilityUsePolicy

Adapt has two third-party veto surfaces and they overlap. Pick based on whether the rule depends on where the player is standing or on who the player is.

Use `Protector` when the answer depends on location. A veinminer chewing through fifty blocks calls `canBlockBreak` fifty times, once per block, so a protector can refuse block forty-one while permitting block forty. An ability use policy cannot express that. It either kills the whole activation or it does not.

Use [`AbilityUsePolicy`](<43 - API - Ability Use Policy.md>) when the answer depends on the player or their state. Jails, duels, quest states, rank gating, per-skill bans. It carries a reason string, it can be scoped to selected adaptations or skills, and a fault in your provider gets contained instead of taking an adaptation down with it.

Use both when you have both kinds of rule. They do not conflict. The protector's `checkRegion` runs first and both have to permit.

Do not reach for `Protector` as a general veto by returning `false` from `checkRegion` for reasons unrelated to location. It sits on the hottest gate in the plugin, it cannot explain itself to the player, and an admin can switch it off per adaptation without telling you. The full side-by-side comparison is in the [Reference](#protector-compared-to-abilityusepolicy).

## Depending on Adapt

Compile against the shaded `Adapt-<version>-all.jar` and declare the dependency in your plugin manifest. Full instructions are in [41 - API - Getting Started.md](<41 - API - Getting Started.md#depending-on-adapt>).

Unlike the ability API, this one is not a Bukkit service. There is no `ServicesManager` entry for `ProtectorRegistry`. The only way in is through Adapt's plugin instance, which means your code names the class `art.arcane.adapt.Adapt`:

```java
private ProtectorRegistry protectorRegistry() {
    Plugin plugin = getServer().getPluginManager().getPlugin("Adapt");

    if (!(plugin instanceof Adapt adapt) || !adapt.isEnabled()) {
        return null;
    }

    return adapt.getProtectorRegistry();
}
```

Three things follow from that.

1. Declare the dependency in your manifest. Use `softdepend: [Adapt]`, or `join-classpath: true` on a Paper plugin. Without it your classloader cannot see `art.arcane.adapt.Adapt` and the `instanceof` fails at class-load with `NoClassDefFoundError`.
2. The registry is created during Adapt's `onEnable`. A plugin that enables before Adapt gets `null`. `softdepend` or `load: BEFORE` fixes the ordering, and the null check covers the case where Adapt is absent or failed to start.
3. The registry does not survive an Adapt restart. Adapt builds a fresh `ProtectorRegistry` on every `onEnable` and calls `unregisterAll()` on shutdown. If Adapt is reloaded underneath you, your protector is gone. Re-register on `PluginEnableEvent` for the plugin named `Adapt` if you want to survive that.

## The lifecycle

```
registerProtector(protector)     you are added; isEnabledByDefault() is read now
   |
   |  Adapt calls your seven methods, many times, for as long as you are registered
   v
refreshDefaultProtectors()       rebuilds default-active membership
   |
   v
unregisterProtector(protector)   Adapt calls your unregister(), then drops you
   or
unregisterAll()                  Adapt shutting down: unregister() on everyone, then drop
```

Four rules are worth saying plainly.

`isEnabledByDefault()` is not polled. It is read when the registry rebuilds its snapshots, which happens on every register, every unregister, every explicit `refreshDefaultProtectors()`, and every successful Adapt core-config hotload. A protector that flips its own answer at runtime has to call `refreshDefaultProtectors()` itself.

Duplicate detection is by `equals`, not by name. `registerProtector` ignores a protector already in the list, which for an ordinary class means the same instance. A protector written as a `record` will collapse two value-equal instances instead. Either way it never compares `getName()`, so two different protectors sharing a name both register, and the `[protectionOverrides]` lookup resolves that name to only one of them. Keep names unique.

`getName()` is the admin-facing key. It is what an admin types into `[protectionOverrides]`. Pick something short and stable and never change it across releases. The built-in names are listed in the [Reference](#built-in-protectors).

`unregister()` is the teardown hook. Adapt calls it on shutdown and on explicit unregistration. Release listeners and caches there. Do not touch the registry from inside it.

## Threading

Every `Protector` method runs on the tick thread that owns the acting player: the main thread on Paper, the owning region thread on Folia. Reading blocks, entities and the player is legal there.

That is enforced upstream rather than at the call. Adapt's active-level resolution, which is what invokes `checkRegion`, returns zero immediately when Folia is in use and the current region does not own the player, so the protector is never reached off-region. The ad-hoc calls run inside adaptation event handlers, which are on the same thread.

This is the hottest third-party call surface in Adapt. Active-level resolution is cached per player, per adaptation, per tick, so `checkRegion` costs you at most one call per adaptation per tick, but a block-affecting adaptation calls `canBlockBreak` once per candidate block and a single veinminer activation can be dozens of calls in one tick.

- No I/O. No database, no HTTP, no file reads.
- No blocking. No `CompletableFuture.join`, no `callSyncMethod`, no lock held across the call.
- Avoid allocation you do not need. Cache region lookups by chunk and invalidate them on your provider's own change events.
- Do not call back into Adapt. `Adaptation.getActiveLevel`, `hasActiveAdaptation` and anything else that resolves an active level will re-enter the gate you are currently inside.

There is no watchdog and no quarantine here. A slow protector is not warned about and a broken one is not disabled. The contract is the only protection.

Adapt does time every world-policy evaluation across all protectors in a rolling sixty-second window. `WorldPolicyLatencyTelemetry.averageMillis(System.currentTimeMillis())` returns the mean in milliseconds. It is read-only diagnostics that Adapt's own commands display, and nothing about its value changes any decision.

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

`checkRegion` is the global gate. Adapt asks it with the player's own location during active-level resolution, before the use-permission check, and a `false` makes the adaptation completely inert for that player. The other six are asked ad hoc by the adaptation that is about to touch something. The full list of where each one fires is in the [Reference](#where-adapt-asks).

The built-in protectors treat `checkRegion` as the base test and AND it with a flag lookup. WorldGuard's `canBlockBreak`, for example, is `checkRegion(...) && flag(BLOCK_BREAK)`. You are not obliged to follow that shape, but it is a sane default: implement `checkRegion` for "may they be doing anything here at all" and let the verbs add specificity.

### The adaptation argument can be null

`Adaptation<?> adaptation` is the adaptation asking, and it is `null` whenever the caller is not an adaptation. Adapt's mutation runtime passes `null` on every combat, block break, block place, interact and region-occupancy check, and the Adapt activator interaction check (the sneak-right-click on a lectern or observer that opens the skills menu) passes `null` to `canInteract`.

```java
if (adaptation != null && adaptation.getName().startsWith("pickaxe-")) {
    return miningAllowed(player, blockLocation);
}
```

A protector that dereferences `adaptation` without a null check throws on the first mutation check, and because nothing quarantines a protector, it keeps throwing.

Those null-adaptation callers also ask a different set of protectors. They read `getDefaultProtectors()` directly, so `[protectionOverrides]`, which is keyed by adaptation id, has no effect on them. A protector an admin removed from every adaptation is still consulted for mutations, and one added by an override is not.

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

`ClaimIndex` is yours; the sample needs `boolean isClaimed(Location)` and `boolean isTrusted(UUID, Location)`, both answering from an in-memory index.

This protector deliberately does not override `checkRegion`. It has nothing to say about whether a player may use an adaptation at all, only about specific blocks and containers, so it leaves the global gate alone and stays out of the hot path. That is the right shape for most claims plugins.

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

Unregistering in `onDisable` is not optional the way it is with the `ServicesManager`. Bukkit does not know about this registry, so a disabled plugin's protector stays registered and keeps being consulted.

## The minimum: one verb

Everything except `getName()` and `isEnabledByDefault()` has a default. A protector that only cares about chests is three methods:

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

Write defensively everywhere. Null-check `player`, `location` and `adaptation`, return `true` when you genuinely do not know, and never let an exception out. What happens when you do is in the [Reference](#failure-policy).

## Region policy source

`RegionPolicySource` is the other side of the coin. Instead of refusing an action it hands Adapt a `RegionPolicy` describing what a region gives a player: whether XP is allowed there, an XP multiplier, an ability power bonus, and a set of adaptation ids to grant temporarily while the player stands in it.

Implement two methods, `getName()` and `resolve(player, location)`, and return an immutable `RegionPolicy`. The record's constructor clamps the multiplier and the power bonus into range and lower-cases the adaptation ids for you. Returning the id `"*"` (the `RegionPolicy.UNLOCK_ALL` constant) grants every enabled catalogue adaptation while the policy is active.

There is exactly one slot. `RegionPolicyService.install(source)` replaces whatever is currently installed. It does not compose sources and it does not keep the one it displaced. Adapt installs `WorldGuardRegionPolicySource` during enable when WorldGuard is present, so calling `install` from a third-party plugin replaces WorldGuard flag behavior for the whole server. Call it only after Adapt and your region provider have both enabled, and call `clear()` on your own disable only when you deliberately own that slot. There is no compare-and-clear and no automatic restoration of WorldGuard.

`resolve(player, location)` runs synchronously on the caller's owning thread. If it throws, Adapt logs the stack trace and quarantines your source until the next `install` or `clear`. While no source is installed or the installed one is quarantined, `RegionPolicy.DEFAULT` applies: XP allowed at multiplier `1`, no power bonus, no adaptation grants. `RegionPolicyService.adjustXp(xp, policy)` returns `0` when the policy denies XP and otherwise applies the multiplier, and `isActive()` tells you whether a live, non-quarantined source is installed.

`RegionGrantRuntime` is Adapt's own reconciler on top of that. It applies the resolved power bonus, grants and revokes region-owned level 1 adaptation entries, and prunes adaptations when a shrunk power budget is exceeded. External plugins return policy from the source and stop there; they do not call `RegionGrantRuntime.refresh` or edit region-granted records. `WorldPolicyLatencyTelemetry` is Adapt's own timing instrumentation, not a provider API.

## Configuration

Everything lives in `plugins/Adapt/adapt/adapt.toml`.

`[protectorSupport]` toggles the built-in protectors' `isEnabledByDefault()`. It has no effect on a third-party protector, whose default comes from your own `isEnabledByDefault()`. A built-in protector is only constructed at all when its plugin is enabled, so these keys switch off support that would otherwise be active. A successful core-config hotload refreshes the default-active snapshot; adding or removing the provider plugin still needs an Adapt restart.

`[protectionOverrides]` adds or removes individual protectors for one adaptation, keyed by adaptation id and then by `getName()`:

```toml
[protectionOverrides.pickaxe-veinminer]
WardenClaims = true
GriefPrevention = false
```

`true` adds a protector that is not enabled by default, `false` removes one that is. A name that matches no registered protector is logged as an error and skipped. The resolved set is cached per adaptation and re-derived automatically when the registered protectors or the override map change, so an admin editing the file still needs a reload for the config itself to be re-read.

Overrides apply to adaptation checks only. Mutation and activator checks use the default set. See [The adaptation argument can be null](#the-adaptation-argument-can-be-null).

The default file ships a placeholder entry under the literal adaptation id `adaptation-name`, which matches nothing. Leave it or replace it; it is inert either way.

`worldguard` additionally gates Adapt's region flags for XP, power, and temporary adaptation grants through the `RegionPolicySource` slot. See [08 - Protection & Region Policy.md](<08 - Protection & Region Policy.md>).

---

## Reference

### Protector compared to AbilityUsePolicy

| | `Protector` | `AbilityUsePolicy` |
|---|---|---|
| The question it answers | may this happen at this location | may this player use this adaptation at all, right now |
| Granularity | seven verbs: break, place, PvP, PvE, interact, chest access, region | one decision per adaptation |
| Location-aware | yes, every method takes the `Location` in question | no, you get the `Player` and read their location yourself |
| Consulted where | once per active-level resolution as a global gate (`checkRegion`), then ad hoc by individual adaptations for each block or entity they touch | once per player, per adaptation, per tick |
| Carries a reason | no, a `boolean` | yes, `AbilityUseDecision.deny("...")`, recorded for diagnostics |
| Scoping | none; every registered protector sees every adaptation | `AbilityScope` by adaptation id or skill id |
| Per-adaptation admin override | yes, `[protectionOverrides]` in the config | no |
| Registration | `Adapt.instance.getProtectorRegistry()`, requires naming Adapt's plugin class | Bukkit `ServicesManager`, no Adapt class named |
| Fault containment | none, a throwing protector is not quarantined | quarantined after `providerFaultLimit` faults (default `5`) |
| Failure mode | an exception makes the adaptation inert and prints a stack trace | configurable, defaults to deny |

### Where Adapt asks

| Method | Where Adapt asks |
|--------|------------------|
| `checkRegion` | The global gate. Consulted with the player's own location during active-level resolution, before the use-permission check. `false` makes the adaptation completely inert for that player. Also called with a `null` adaptation for mutation region-occupancy checks |
| `canBlockBreak` | Before an adaptation breaks or harvests a block: veinminer, tree feller, tunnel bore, chisel, every other block-consuming adaptation, once per block. Also for mutation block breaks |
| `canBlockPlace` | Before an adaptation places a block. Also for mutation block placement |
| `canPVP` | Before an adaptation damages or applies an effect to a player target. Also for mutation effects on players |
| `canPVE` | Before an adaptation damages or applies an effect to a non-player entity. Also for mutation effects on non-players |
| `canInteract` | Before an adaptation interacts with a block or entity that is neither a break, a place nor a chest. Also for mutation interaction and for the sneak-right-click Adapt activator on a lectern or observer |
| `canAccessChest` | Before an adaptation reads or writes a container. Not used by the mutation runtime |

### Protector interface

| Member | Contract |
|--------|----------|
| `String getName()` | Required. The admin-facing key used by `[protectionOverrides]`. Must be unique and stable |
| `boolean isEnabledByDefault()` | Required. Read at snapshot rebuild time, not polled |
| The seven verbs | All `default`, all return `true`. Override only what you can answer |
| `void unregister()` | `default`, empty. Called on explicit unregistration and on Adapt shutdown |

### ProtectorRegistry

| Method | Contract |
|--------|----------|
| `void registerProtector(Protector)` | `synchronized`. Null, and an instance already registered by `equals`, are ignored. Rebuilds both snapshots |
| `void unregisterProtector(Protector)` | `synchronized`. Calls your `unregister()` first, then removes you, so `unregister()` runs even for an instance that was never registered |
| `List<Protector> getDefaultProtectors()` | Immutable snapshot of protectors whose `isEnabledByDefault()` was true at the last rebuild |
| `List<Protector> getAllProtectors()` | Immutable snapshot of every registered protector |
| `void refreshDefaultProtectors()` | `synchronized`. Re-reads `isEnabledByDefault()` on every registered protector and rebuilds both snapshots |
| `void unregisterAll()` | `synchronized`. `unregister()` on everyone, then clear. Adapt calls this on shutdown |

### Failure policy

There is no fault budget, quarantine, slow-call watchdog, or configurable failure mode on this surface.

| Misbehaviour | What happens |
|--------------|--------------|
| A method throws during `checkRegion` | The active-level resolver catches it, prints the stack trace, and returns level `0`. The adaptation is inert for that player until the protector stops throwing |
| A method throws during an ad-hoc call | The exception propagates out of the adaptation's event handler. Bukkit logs "Could not pass event ... to Adapt" and the rest of that handler does not run |
| A method dereferences a null `adaptation` | The above, on every mutation check and on every Adapt activator interaction |
| Repeated faults | Nothing. There is no quarantine. It throws forever |
| A slow method | Nothing is logged. It shows up as tick time |
| `getName()` returns null | The per-adaptation protector-set builder throws `NullPointerException` while hashing names. In the active-level path that is caught and every adaptation goes inert; elsewhere it propagates |
| The same instance registered twice | The second call is ignored |
| Two instances share one `getName()` | Both are registered. `[protectionOverrides]` resolves the name to whichever the internal name map saw last |
| Your plugin is disabled while still registered | You keep being consulted. Unregister yourself in `onDisable` |
| Adapt shuts down | `unregister()` is called on every protector and the registry is cleared |

### Built-in protectors

Each is registered only when its plugin is enabled at Adapt startup.

| `getName()` | Provider plugin | Config key under `[protectorSupport]` | Default |
|-------------|-----------------|----------------------------------------|---------|
| `WorldGuard` | WorldGuard | `worldguard` | `true` |
| `GriefDefender` | GriefDefender | `griefdefender` | `true` |
| `Factions` | Factions | `factionsClaim` | `false` |
| `Residence` | Residence | `residence` | `true` |
| `ChestProtect` | ChestProtect | `chestProtect` | `true` |
| `GriefPrevention` | GriefPrevention | `griefprevention` | `true` |
| `LockettePro` | LockettePro | `lockettePro` | `true` |

`WorldGuardRegionPolicySource`, installed alongside `WorldGuardProtector`, also reports `getName()` as `WorldGuard` and returns `RegionPolicy.DEFAULT` whenever `protectorSupport.worldguard` is `false`.

### RegionPolicy

`RegionPolicy(boolean xpAllowed, double xpMultiplier, int powerBonus, Set<String> unlockedAdaptations)`.

| Constant | Value | Meaning |
|----------|-------|---------|
| `UNLOCK_ALL` | `"*"` | Placed in `unlockedAdaptations` to grant every enabled catalogue adaptation |
| `MIN_XP_MULTIPLIER` | `0.0` | Lower clamp applied by the constructor |
| `MAX_XP_MULTIPLIER` | `1000.0` | Upper clamp applied by the constructor |
| `MIN_POWER_BONUS` | `-4096` | Lower clamp applied by the constructor |
| `MAX_POWER_BONUS` | `4096` | Upper clamp applied by the constructor |
| `DEFAULT` | `(true, 1.0, 0, {})` | Served when no source is installed, the source is quarantined, or it returned `null` |

A non-finite `xpMultiplier` is replaced with `1.0`. Ids in `unlockedAdaptations` are trimmed, lower-cased, and empties dropped. `unlocksEverything()` tests for `"*"`; `grantsAnyAdaptation()` tests for a non-empty set.

### RegionPolicyService

| Method | Contract |
|--------|----------|
| `void install(RegionPolicySource)` | Replaces the single global source and clears the quarantine flag. Does not compose or retain the displaced source |
| `void clear()` | Removes the source and clears the quarantine flag |
| `boolean isActive()` | `true` when a source is installed and not quarantined |
| `RegionPolicy resolve(Player, Location)` | Calls the source on the caller's thread. Returns `DEFAULT` on null source, null player, null location, quarantine, or a `null` return. A throw quarantines the source and returns `DEFAULT` |
| `double adjustXp(double, RegionPolicy)` | `0` when the policy is null or denies XP, otherwise `xp * xpMultiplier` |

### WorldPolicyLatencyTelemetry

Read-only diagnostics over a rolling sixty-second window of one-second slots, covering every `Protector` verb call made through an `Adaptation`.

| Method | Value |
|--------|-------|
| `double averageMillis(long now)` | Mean milliseconds per world-policy evaluation across the window; `0` when the window has no samples |
| `void recordNanos(long)` | Adapt-owned instrumentation. Negative durations are dropped |
| `void clear()` | Adapt-owned; zeroes every slot |

## See also

- [08 - Protection & Region Policy.md](<08 - Protection & Region Policy.md>)
- [41 - API - Getting Started.md](<41 - API - Getting Started.md>)
- [43 - API - Ability Use Policy.md](<43 - API - Ability Use Policy.md>)
