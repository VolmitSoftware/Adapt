# Adapt ability use policy

`art.arcane.adapt.api.ability.AbilityUsePolicy` answers one question: may this player use this adaptation
right now, and if not, why not. Adapt asks every registered policy before it resolves an adaptation's
active level, and the first refusal wins. A refusal makes the adaptation completely inert for that player
for the rest of the tick: no effect, no cooldown, no experience, no cost.

This is the surface for rules that live outside Adapt. A jail that should silence a player's whole skill
set. An arena that bans two abilities during a duel. A minigame that turns everything off between rounds.
You get the player, the adaptation id, the skill id and the learned level, and you say allow or deny.

A policy can only refuse. `AbilityUseDecision.allow()` means "I have no objection", not "let this
through". Every other gate still runs, and an adaptation the player has never learned stays unreachable no
matter what you return. See
[Skill items are inert without their adaptation](<41 - API - Getting Started.md#skill-items-are-inert-without-their-adaptation>).

The interface is built from Bukkit types, `java.*` types and its own types only. No VolmLib, no Adventure,
no shaded types: it links against a plain Paper compile classpath.

---

## Registering

Compile against the shaded `Adapt-<version>-all.jar` and declare the dependency in your plugin manifest.
The full instructions are in [41 - API - Getting Started.md](<41 - API - Getting Started.md#depending-on-adapt>).

Acquire nothing. You do not look Adapt up; you register with Bukkit's `ServicesManager` and Adapt finds
you.

```java
@Override
public void onEnable() {
    getServer().getServicesManager().register(
        AbilityUsePolicy.class, new JailUsePolicy(jails), this, ServicePriority.Normal);
}
```

Bukkit unregisters your services automatically when your plugin disables. Adapt listens for
`ServiceRegisterEvent` and `ServiceUnregisterEvent`, so a policy registered or dropped at any point during
the server's life takes effect on the next ability check without a reload.

## Where the policy sits in Adapt's pipeline

Adapt resolves an adaptation's active level every time it needs to know whether that adaptation should do
something. The resolution runs a fixed sequence of gates and stops at the first failure. Your policy is
last, so reaching it means the player owns the adaptation, is somewhere they may use it, and is permitted
by both the permission tree and the older event. You get the final word. The full sequence is in
Reference.

`AbilityScope` narrows which checks reach you. Adapt asks its provider index whether any policy matches
this `(abilityId, skillId)` pair before it builds a context at all, so a scoped policy costs nothing on
the adaptations it does not claim.

## The rules Adapt guarantees

There is one call, `evaluate(context)`, and it must be side-effect free. Answer from state you already
hold in memory.

- **`evaluate` is called at most once per policy per resolution**, and never after an earlier policy
  denied.
- **Policies run in `ServicePriority` order, highest first**, tie-broken by plugin name then
  `providerId()`. The order is deterministic and survives a restart.
- **The first `DENY` wins.** Later policies are not consulted, so ordering is meaningful.
- **The decision is cached for the rest of the game tick.** Adapt memoises the whole active-level
  resolution per `(player, adaptation)` against the current 50 ms tick bucket and the player's learned
  level. Denying at 09:00:00.000 and un-denying at 09:00:00.010 will not be observed until the next tick.
  Never expect `evaluate` to be called at a rate you control.
- **Adapt's own gate never consults a policy for an adaptation the player has not learned.** The
  learned-level test fails first. The public `Adaptation.canUse(Player)` is the one way around that, and
  it is why `level()` can be `0`.
- **`providerId()` and `scope()` are read once**, when Adapt rebuilds its provider index after a service
  registration change. Returning a different scope later has no effect until you unregister and register
  again. Treat both as constants.

## Threading

`evaluate` runs on the tick thread that owns the player: the main thread on Paper, the owning region
thread on Folia. Reading the player's inventory, location, potion effects and attributes is legal there.

Adapt checks before it calls. If the calling thread is not `Bukkit.isPrimaryThread()`, or if Folia is in
use and the current region does not own the player, no policy is consulted at all and the decision is
`ALLOW`. Adapt counts it and logs a throttled warning. You cannot rely on being asked from an async
context, and you will never be handed a player you may not touch.

**Do not block.** No I/O, no `CompletableFuture.join`, no `callSyncMethod`, no lock held across the call.
This runs inside the ability-check path, which executes for many adaptations per player per tick. A slow
call is logged with a throttled warning naming your plugin, but the warning never changes the decision. A
policy that hangs cannot be interrupted.

**Do not re-enter Adapt.** Calling `Adaptation.getActiveLevel`, `hasActiveAdaptation` or anything else
that triggers an ability check from inside `evaluate` is detected: the nested evaluation is skipped,
returns `ALLOW`, is counted, and produces a throttled warning. Your outer decision still stands, but the
inner answer may be stale before it is used.

If you need remote data, cache it and prime the cache on `PlayerJoinEvent`.

---

## Worked example: a jail that seals a player's skills

A plugin that jails players and wants their whole skill set to go quiet while they are locked up.

```java
package com.example.warden;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;

public final class JailUsePolicy implements AbilityUsePolicy {
  public static final String ID = "warden-jail";

  private final JailIndex jails;

  public JailUsePolicy(JailIndex jails) {
    this.jails = jails;
  }

  @Override
  public String providerId() {
    return ID;
  }

  @Override
  public AbilityUseDecision evaluate(AbilityContext context) {
    if (!jails.isJailed(context.playerId())) {
      return AbilityUseDecision.allow();
    }

    return AbilityUseDecision.deny("Your skills are sealed while you are jailed");
  }
}
```

`JailIndex` is application-owned; the sample needs only `boolean isJailed(UUID)`, and it must answer from
memory without touching a database.

This policy declares no `scope()`, so it inherits `AbilityScope.everything()` and Adapt consults it for
every adaptation on every player. That is correct here, because a jail applies to everything, and Adapt
says so with one warning naming your provider the first time it indexes you. When the warning is not
deserved, scope yourself.

### A scoped policy

An arena that bans blood magic and blink during a duel but leaves everything else alone:

```java
package com.example.warden;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import java.util.Set;
import java.util.UUID;

public final class ArenaUsePolicy implements AbilityUsePolicy {
  private static final AbilityScope SCOPE = AbilityScope.skills("swords", "ranged", "tragoul")
      .and(AbilityScope.abilities("rift-blink"));

  private final Set<UUID> duelists;

  public ArenaUsePolicy(Set<UUID> duelists) {
    this.duelists = duelists;
  }

  @Override
  public String providerId() {
    return "warden-arena";
  }

  @Override
  public AbilityScope scope() {
    return SCOPE;
  }

  @Override
  public AbilityUseDecision evaluate(AbilityContext context) {
    if (!duelists.contains(context.playerId())) {
      return AbilityUseDecision.allow();
    }

    if ("rift-blink".equals(context.abilityId())) {
      return AbilityUseDecision.deny("Blink is banned in the arena");
    }

    return switch (context.skillId()) {
      case "tragoul" -> AbilityUseDecision.deny("Blood magic is banned in the arena");
      default -> AbilityUseDecision.allow();
    };
  }
}
```

The scope and `evaluate` have to agree. `SCOPE` names `swords`, `ranged` and `tragoul` so those three
skills reach this policy at all, plus the single ability `rift-blink`; `evaluate` then refuses two of them
and permits the rest. Widening the scope without widening `evaluate` adds an unnecessary call per
adaptation per tick. Narrowing it below what `evaluate` tests prevents the test from running.

### Registration

```java
package com.example.warden;

import art.arcane.adapt.api.ability.AbilityUsePolicy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class WardenPlugin extends JavaPlugin {
  private final JailIndex jails = new JailIndex();

  @Override
  public void onEnable() {
    getServer().getServicesManager().register(
        AbilityUsePolicy.class, new JailUsePolicy(jails), this, ServicePriority.Normal);
  }
}
```

## The minimum: one lambda

`AbilityUsePolicy` is a `@FunctionalInterface`. `providerId()` and `scope()` both have defaults, so a
policy can be a single expression:

```java
getServer().getServicesManager().register(AbilityUsePolicy.class,
    context -> jails.isJailed(context.playerId())
        ? AbilityUseDecision.deny("Your skills are sealed while you are jailed")
        : AbilityUseDecision.allow(),
    this, ServicePriority.Normal);
```

The default `providerId()` is the implementation class name, which for a lambda is a generated name like
`com.example.Warden$$Lambda/0x00007f2a…` that changes on every restart and on unrelated edits to your
plugin. Adapt logs one warning naming your plugin when it sees that and keeps using the generated name. It
is unique within a run, so deduplication and quarantine still work, but it is not a name an admin can
recognise. Production policies should override `providerId()`.

## Scoping, and two traps

`AbilityScope` is immutable and cheap to build once as a `static final`. `matches` is a union, not an
intersection: a scope naming skill `rift` and ability `pickaxe-veinminer` matches every `rift-*`
adaptation *and* `pickaxe-veinminer`. The factories are listed in Reference.

The first trap is silent widening. `AbilityScope.abilities()` and `AbilityScope.skills()` with no
arguments return `AbilityScope.everything()`, as does any call whose arguments are all blank or null. A
scope built from an empty configuration list quietly covers the whole catalogue. The singular
`AbilityScope.skill(id)` does not: it throws `IllegalArgumentException` on a blank id and
`NullPointerException` on null.

The second is that ids are normalised, not validated. `"Rift-Blink"` becomes `"rift-blink"`; a typo
becomes a scope that matches nothing and is never called. Adapt does not warn about a scope naming an
adaptation that does not exist.

`unscoped()`, `size()` and `matches(abilityId, skillId)` are available if you want to assert your own
scope in a test. `equals` and `hashCode` are value-based.

## Telling the player

`AbilityUseDecision.deny(reason)` records your reason against the player for diagnostics, deduplicated per
player while an identical denial repeats inside the `denyMessageThrottleMillis` window.

Adapt does not show that reason to the player. There is no message, no action bar, no sound. Nothing in
Adapt reads the recorded denial. A policy that wants the player to know must send its own message, and it
must throttle that message itself, because evaluation can happen roughly once per tick per adaptation
while the player keeps trying.

Passing `null` or `""` to `deny` is legal and denies just as hard; the reason is only ever diagnostic. All
third-party text is truncated to 128 characters and stripped of control characters before Adapt stores or
logs it.

## Failure policy

Adapt assumes a policy will throw, return null, register twice, or be disabled mid-operation. The full
behaviour table is in Reference; the shape of it is this.

The default for use policies is **fail-closed**, the opposite of the cost funnel, which fails open. The
asymmetry is deliberate. A broken cost provider making abilities free is recoverable and loudly logged; a
broken permission check that silently permits is a security hole, so a policy that cannot answer is
treated as a refusal. Admins who prefer the other trade-off set `[abilityApi] usePolicyFailureMode =
"allow"`.

A deliberate `DENY` always denies, whatever the failure mode says. The mode governs faults only.

Repeat faults get you quarantined and skipped entirely, which means a quarantined policy stops denying.
Clear a quarantine by unregistering the service and registering it again; Adapt drops quarantine and fault
counts for any provider id that is no longer in the index.

---

## Reference

### Active-level gate order

Adapt stops at the first failure.

| Step | Gate | Documented in |
|---|---|---|
| 1 | learned level > 0 (also requires the skill and adaptation to be enabled) | |
| 2 | world is not blacklisted | |
| 3 | game mode is allowed: never spectator, creative only when configured | |
| 4 | `Protector.checkRegion(...)` for land claims and regions | [46 - API - Protection.md](<46 - API - Protection.md>) |
| 5 | `adapt.use.<adaptation>` permission; unset or op means granted | [41 - API - Getting Started.md](<41 - API - Getting Started.md>) |
| 6 | no usage conflict with another adaptation | |
| 7 | `AdaptAdaptationUseEvent` not cancelled | [45 - API - Events.md](<45 - API - Events.md>) |
| 8 | every `AbilityUsePolicy` allows | this document |

### `AbilityContext`

```java
public record AbilityContext(UUID activationId, String abilityId, String skillId, int level,
                             AbilityPhase phase, Player player, Optional<Location> origin)
```

| Component | What it is |
|---|---|
| `activationId` | A fresh `UUID` per check. Not a session id, not a cooldown key, and it does not correlate a check with the activation that may follow it. Key on `playerId()` and `abilityId()` instead |
| `abilityId` | `rift-blink`. Lowercased, trimmed, never blank |
| `skillId` | `rift`. Lowercased, trimmed, never blank |
| `level` | The player's learned level in this adaptation. Ignores world blacklists, game mode, protection and every other gate. At least 1 in every check Adapt's own gate makes; `0` only when a third party calls `Adaptation.canUse(Player)` directly |
| `phase` | Always `CHECK` for a use policy. Check contexts are built by `AbilityContext.check(...)`, which fixes the phase and leaves `origin` empty. `ACTIVATE` contexts belong to the cost funnel |
| `player` | The live `Player` |
| `origin` | Always empty for a use policy. Read `player().getLocation()` if you need a position |

Plus `UUID playerId()`, a shortcut for `player().getUniqueId()`. `origin()` returns a defensive copy on
every read, and the canonical constructor clones on the way in.

### `AbilityScope` factories

| Factory | Matches |
|---|---|
| `AbilityScope.everything()` | every adaptation, every skill |
| `AbilityScope.abilities(String...)` | those adaptation ids; no usable arguments widens to everything |
| `AbilityScope.skill(String)` | every adaptation under that skill; throws on a blank or null id |
| `AbilityScope.skills(String...)` | every adaptation under those skills; no usable arguments widens to everything |
| `a.and(b)` | the union of both; widens to everything if either side is unscoped |

### Failure behaviour

| Misbehaviour | What Adapt does |
|---|---|
| `evaluate` throws | Counted as a fault, logged with the stack trace, then the configured failure mode decides |
| `evaluate` returns null | Identical to throwing: a fault, logged, then the failure mode decides |
| Failure mode `deny` (the default) | The activation is denied with an empty reason and the denial is recorded against your provider id |
| Failure mode `allow` | Your policy is skipped and the next one is consulted |
| Repeated faults | On the `providerFaultLimit`-th fault the policy is quarantined with a `SEVERE` log line naming your plugin and its provider id, and skipped entirely. A quarantined policy stops denying |
| Clearing a quarantine | Unregister the service, then register it again. Quarantine and fault counts are dropped for any provider id no longer present in the index |
| Slow call | Throttled warning naming your plugin. Never changes the decision |
| `providerId()` throws or returns blank | The registration is ignored entirely, with a warning |
| `scope()` throws or returns null | Treated as `AbilityScope.everything()`, with a warning |
| Two policies claim one `providerId` | The one that sorts first is kept and the other is ignored with a warning. Sort order is `ServicePriority` descending, then plugin name, then provider id |
| The same instance registered twice | The duplicate is dropped silently; you are consulted once |
| Your plugin is disabled while still registered | The policy is skipped |
| The `ServicesManager` itself fails | Adapt logs `SEVERE` once and treats every policy as absent until registrations change |
| Adapt is disabled or `[abilityApi] enabled = false` | No policy is consulted; every check allows |

Adapt's warning throttle is one message per minute per key, where the key is the provider id plus the call
phase, or a shared key for the off-thread and re-entrant cases.

### Configuration

`plugins/Adapt/adapt/adapt.toml`, `[abilityApi]`:

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Master switch for the whole ability API. When false, no policy and no cost provider is called and neither ability event fires |
| `usePolicyFailureMode` | `"deny"` | What a faulting use policy means. `deny`: the activation is refused. `allow`: the policy is skipped |
| `costProviderFailureMode` | `"allow"` | The cost funnel's equivalent. See [44 - API - Ability Cost.md](<44 - API - Ability Cost.md#configuration>) |
| `providerFaultLimit` | `5` | Fault count that trips quarantine, so the default tolerates four. Clamped to 0 to 1000; `0` disables quarantine |
| `slowProviderMillis` | `2` | Milliseconds one provider call may take before a warning is logged. Clamped to 0 to 60000; `0` disables the watchdog |
| `denyMessageThrottleMillis` | `2000` | Milliseconds an identical denial is deduplicated for, per player. Negative values are clamped to 0 |

Both failure-mode keys accept `deny`, `denied`, `closed`, `fail-closed` and `allow`, `allowed`, `open`,
`fail-open`, case-insensitively. An unrecognised value falls back to that key's default rather than
failing to load.

### `AbilityUseStatus`

| Constant | Meaning |
|---|---|
| `ALLOW` | This policy does not object. Other gates still apply |
| `DENY` | This policy refuses. No later policy is consulted |

Prefer `AbilityUseDecision.allowed()` over switching on `status()`.

### `AbilityPhase`

| Constant | Meaning |
|---|---|
| `CHECK` | Adapt is deciding whether the adaptation is usable. The only phase a use policy sees |
| `ACTIVATE` | Adapt is charging for a use that is happening now. Reaches `AbilityCostProvider` only |

Both enums may gain constants. Write a `default` arm. See
[Switching over the enums](<41 - API - Getting Started.md#switching-over-the-enums>).
