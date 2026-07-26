# Adapt integration API

Adapt is a skills and abilities plugin: more than three hundred **adaptations** spread across twenty-three
**skill lines**, each with levels, experience, cooldowns and — for a good number of them — an item, hunger,
health, durability or experience cost paid at the moment of use. This directory documents the surfaces
another plugin uses to observe that system, gate it, or take payment for it.

There are five, and they are not interchangeable.

| You want to…                                                        | Use                                                              | Document |
|---------------------------------------------------------------------|------------------------------------------------------------------|----------|
| answer "may this player use this adaptation right now", with a reason | `AbilityUsePolicy` (ServicesManager)                             | [ability-policy.md](ability-policy.md) |
| price an activation, or charge for it from your own economy          | `AbilityCostProvider` (ServicesManager)                          | [ability-cost.md](ability-cost.md) |
| gate adaptations on land claims, regions and protection flags        | `Protector` (`ProtectorRegistry`)                                | [protection.md](protection.md) |
| watch adaptations being used, or veto them for free                  | the `AdaptEvent` family, `AdaptAbilityActivateEvent`             | [events.md](events.md) |
| read a player's levels, power and mutations as text                  | the `%adapt_…%` PlaceholderAPI expansion                          | [placeholders.md](placeholders.md) |

**None of these can grant an ability.** Every one of them can only refuse, price, or observe. A player who
has not learned an adaptation is not reachable through any integration surface here — see
[Skill items are inert without their adaptation](#skill-items-are-inert-without-their-adaptation).

---

## Depending on Adapt

Adapt ships as a single shaded jar, `Adapt-<version>-all.jar`. **Compile against that jar**, and against
that jar only. A thin `Adapt-<version>.jar` is produced alongside it during a build and contains none of
the shaded libraries; the ability API happens to link against it, but `Adaptation`, `Skill`, `AdaptPlayer`
and the `Adapt` plugin class all name VolmLib types that are simply not in it, and javac fails to resolve
them.

Gradle:

```groovy
dependencies {
    compileOnly files('libs/Adapt-2.0.0-26.2-all.jar')
}
```

Bukkit plugin (`plugin.yml`):

```yaml
softdepend: [Adapt]
```

Paper plugin (`paper-plugin.yml`):

```yaml
dependencies:
  server:
    Adapt:
      load: BEFORE
      required: false
      join-classpath: true
```

`join-classpath: true` is mandatory on Paper — plugin classloaders are isolated, and without it every
`art.arcane.adapt.*` reference fails at runtime with `NoClassDefFoundError` even though the classes are
right there in Adapt's jar.

`softdepend` (or `load: BEFORE`) is what guarantees Adapt has finished `onEnable` before yours starts. That
matters: the `ProtectorRegistry` is constructed during Adapt's `onEnable`, so a plugin that enables first
finds nothing to register with.

Adapt declares `folia-supported: true` and targets Java 25. Your plugin can target any release Paper
accepts; the samples in these documents compile at `--release 21` against the shipped jar.

---

## Adapt relocates VolmLib. Do not reference relocated types

Adapt shades its shared utility library, VolmLib, and rewrites its package at build time:

```
art.arcane.volmlib.**   ->   art.arcane.adapt.util.arcane.volmlib.**
```

The same rewrite is applied to Manifold, the UltimateAdvancementAPI, Libby and CustomBlockData. Everything
under **`art.arcane.adapt.util.`** is either a relocated third-party library or Adapt's own vendored
plumbing. That package path is a build artifact. It is not versioned, not announced, and free to move.

The rule for a consumer:

- **Reference freely:** `art.arcane.adapt.api.ability` (except its `internal` subpackage),
  `art.arcane.adapt.api.protection`, `art.arcane.adapt.api.potion.AdaptBrewCompleteEvent` and
  `art.arcane.adapt.content.event.**`. These are the surfaces this directory documents.
- **Never name:** anything under `art.arcane.adapt.util.**`, in an import, a field type, a method
  signature, a cast or a generic argument.
- **Read the signature first** anywhere else under `art.arcane.adapt.api.**`. That tree is Adapt's own
  authoring surface, and a good deal of it — `Skill`, `PlayerSkillLine`, `MutationType.keys()`, everything
  under `api.xp` and `api.fx` — names a relocated type in a return type or a parameter.

`art.arcane.adapt.api.ability` is deliberately built from Bukkit types, `java.*` types and its own types
only. A build-time gate fails Adapt's own test suite if any public member of that package so much as
mentions a relocated or internal type, so the whole ability API links against a plain Paper compile
classpath. `Protector` holds to the same standard.

### The one place this leaks into a documented surface

The older event family does not hold that line. `Skill.getAdaptations()` returns VolmLib's `KList`, and the
`PlayerSkillLine` collection getters return `KList` and `KMap`. Those calls compile — `KList extends
ArrayList`, `KMap extends ConcurrentHashMap`, and the relocated classes are present in the shaded jar — but
the *call site* is what bites you. This source:

```java
List<Adaptation<?>> adaptations = skill.getAdaptations();
```

compiles to this instruction in **your** jar:

```
invokeinterface art/arcane/adapt/api/skill/Skill.getAdaptations:()Lart/arcane/adapt/util/arcane/volmlib/util/collection/KList;
```

The relocated class name is baked into your bytecode and your plugin breaks with `NoSuchMethodError` the
day that path changes. Reach for the accessors that return JDK or Bukkit types instead — `getName()`,
`isEnabled()`, `getMaxLevel()`, `getLevel(Player)`, `getXp()`, `getKnowledge()` — and get the adaptation
itself from the event rather than by walking a skill's collection. [events.md](events.md) lists the safe
accessors.

---

## What is not API

| Path | Why |
|------|-----|
| `art.arcane.adapt.api.ability.internal.**` | The gateway, provider index, guard and service listener that drive the ability API. Public for Adapt's own tests. Not a contract. |
| `art.arcane.adapt.util.**` | Relocated libraries and vendored plumbing. See above. |
| `art.arcane.adapt.content.**` except `content.event` | The adaptation and skill catalogue itself. Class names, packages and behaviour change release to release. |
| `art.arcane.adapt.service.**` | Adapt's own service objects. |

`AbilityApiBridge` sits in `art.arcane.adapt.api.adaptation` and looks like an entry point. It is not. The
methods that drive the funnel are package-private. Of its three public statics, `install` and `uninstall`
are Adapt's own lifecycle, and `lastDenial(UUID)` returns an
`art.arcane.adapt.api.ability.internal.AbilityDenial` — an internal type this directory does not document,
and nothing in Adapt reads it. Register through the `ServicesManager`.

---

## Threading

Adapt runs on Paper and on Folia, and the answer is the same on both: **the tick thread that owns the
player.** On Paper that is the single main thread. On Folia it is the region thread that currently owns the
region the player stands in.

`AbilityUsePolicy.evaluate`, `AbilityCostProvider.quote` and `reserve`, every `Protector` method, and both
ability events arrive on that thread. Reading and mutating the player's inventory, experience, health and
location is legal there.

Two surfaces do not fit that sentence and are documented where they live rather than papered over here:

- **`AbilityCostProvider.commit` and `refund`.** `commit` is on the owning thread for every charge the
  shipped catalogue makes; `refund` is on the owning thread for a rollback and on some other thread
  entirely for `EXPIRED`, `ADAPTATION_DISABLED` and `SERVER_SHUTDOWN` — see
  [ability-cost.md](ability-cost.md#threading).
- **The older `AdaptEvent` family** carries an async flag computed from the firing thread and can genuinely
  be asynchronous — see [events.md](events.md#threading).

`providerId()` and `scope()` are not calls in this sense at all: Adapt reads them once when it rebuilds its
provider index after a service registration change, on whichever thread triggered the rebuild.

Adapt checks before it calls. `AbilityUsePolicy` and `AbilityCostProvider` are skipped outright, with a
throttled warning, when the calling thread is not `Bukkit.isPrimaryThread()`, or when Folia is in use and
that thread does not own the player. You are never handed a player you may not touch.

**Do not block.** No I/O, no `CompletableFuture.join`, no `Bukkit.getScheduler().callSyncMethod`, no lock
held across the call. These callbacks sit inside the ability-check path, which runs many times per tick per
player. A provider call that takes at least `[abilityApi] slowProviderMillis` (default 2 ms) is logged
with a throttled warning naming your plugin, but the warning never changes the decision — a provider that
hangs cannot be interrupted, so the contract is the only protection. If you need remote data, cache it and
prime the cache on `PlayerJoinEvent`.

Placeholders are the exception and are documented separately: they are served from an immutable snapshot
and are safe to resolve from any thread. See [placeholders.md](placeholders.md).

---

## Permissions: `adapt.use.*` is granted to everyone by default

At startup Adapt mints one permission per adaptation, one per skill, one per mutation, and a root
`adapt.use.*`. **Every one of them is registered with `PermissionDefault.TRUE`.**

```
adapt.use.<adaptation name with hyphens removed>   e.g. adapt.use.riftconduit
adapt.use.<skill name>                             e.g. adapt.use.rift   (parent of its adaptations)
adapt.use.mutation.<mutation id>                   e.g. adapt.use.mutation.bastion-spine
adapt.use.*                                        parent of every skill and mutation node
```

Two consequences that will otherwise cost you an afternoon:

1. **Absence is not denial.** Adapt's check asks `player.isPermissionSet(node)` and treats an unset node as
   *granted*, without ever calling `hasPermission`. A node Adapt never registered — a custom adaptation, a
   node typed wrong — is permitted, not refused. To deny, negate the node explicitly
   (`-adapt.use.riftconduit`).
2. **Operators and debug-mode players bypass the check entirely.** `player.isOp()` short-circuits to
   granted before the node is ever consulted, as does a player with Adapt's debug mode active. Do not use
   `adapt.use.*` as a security boundary and do not test your integration while opped.

Only the adaptation node is read directly. The skill node and `adapt.use.*` work through Bukkit's normal
parent/child recalculation, so negating `adapt.use.rift` negates every `rift-*` adaptation under it.

If you want a decision that is genuinely yours, do not fight the permission tree — register an
[`AbilityUsePolicy`](ability-policy.md).

---

## Skill items are inert without their adaptation

A number of adaptations mint items: a bound eye of ender, a chalk wand, a redstone torch with a target
baked into it. Those items are **deliberately inert** in the hands of a player who has not learned the
owning adaptation. They are not merely weakened; the handlers that give them behaviour never run.

This is enforced by the active-level gate. `Adaptation.getActiveLevel(player)` returns `0` for an
adaptation the player has not learned — that is the *first* thing it checks, before world, game mode,
protection, permissions, conflicts or any policy — and every item-bearing handler in the catalogue starts
by refusing to proceed on a zero level. Several go further and cancel the interaction outright so the item
does not even behave like the vanilla item it is made from. A second, narrower gate in Adapt's event
dispatch drops movement and jump events for non-learners before they reach a handler at all, purely to keep
those two hot events cheap.

**No surface documented here routes around that.** `AbilityUseDecision` carries exactly two statuses,
`AbilityUseStatus.ALLOW` and `AbilityUseStatus.DENY`, and `ALLOW` means only "this policy does not
object" — every other gate still runs.
`AbilityQuote.waived(...)` suppresses a *cost*, not a requirement. The cost gateway refuses to consult
providers at all when the acting player's level is zero, logs it, and charges the built-in cost. There is
no method, event or service that grants an adaptation, and adding one is not a supported request.

If you want a player to use a skill item, teach them the adaptation.

---

## Identifiers

**Skill ids** are lowercase single words. The full set:

```
agility     architect   axes        blocking    brewing     chronos     crafting
discovery   enchanting  excavation  herbalism   hunter      kinetics    nether
pickaxe     ranged      rift        seaborne    stealth     swords      taming
tragoul     unarmed
```

**Adaptation ids** are `<skill>-<name>`, lowercase, hyphen-separated: `rift-blink`, `pickaxe-veinminer`,
`tragoul-skeletal-servant`, `stealth-shadowmeld`. The skill prefix is a convention, not a parsing rule —
always read the skill from `AbilityContext.skillId()` or `Adaptation.getSkill().getName()` rather than
splitting the adaptation id.

**Mutation ids** are lowercase and hyphenated: `gale-lung`, `bastion-spine`, `verdant-molt`, `temperbound`,
`paradox-scar`, `arsenal-cortex`, `packmind`, `trophy-crucible`, `umbral-echo`, `living-lattice`,
`masterwork-bond`, `deepblood`, `mycelial-nerve`, `gravebloom`, `resonant-formula`.

Every id you hand to the ability API is trimmed of control characters, truncated to 128 characters and
lowercased before it is compared, so casing and stray whitespace in your own configuration will not cause a
silent mismatch. A blank id is rejected with `IllegalArgumentException`.

The live counts are readable at runtime through `%adapt_catalog.skills%`, `%adapt_catalog.adaptations%` and
`%adapt_catalog.mutations%`.

---

## Configuration

Everything an admin can tune about these surfaces lives in `plugins/Adapt/adapt/adapt.toml`.

| Section | Governs | Document |
|---------|---------|----------|
| `[abilityApi]` | the whole ability API: master switch, failure modes, fault limit, slow-call threshold | [ability-policy.md](ability-policy.md#configuration), [ability-cost.md](ability-cost.md#configuration) |
| `[protectorSupport]` | which built-in protectors are on by default | [protection.md](protection.md#configuration) |
| `[protectionOverrides]` | per-adaptation protector overrides | [protection.md](protection.md#configuration) |

The file is rewritten in canonical form on load, so keys you add by hand that Adapt does not recognise are
dropped. Edit only documented keys.

---

## Switching over the enums

`AbilityOutcome`, `AbilityRefundReason`, `AbilityQuoteStatus`, `AbilityReservationStatus`,
`AbilityUseStatus`, `AbilityCostKind`, `AbilityPhase`, `MutationState` and `MutationType` may gain
constants in a future release. A `switch` **expression** over an enum is exhaustive, so it stops compiling —
and throws `IncompatibleClassChangeError` on an already-compiled jar — the moment one is added.

**Always write a `default` arm** in third-party code:

```java
String label = switch (charge.outcome()) {
    case ALLOWED_CHARGED -> "charged";
    case ALLOWED_WAIVED -> "waived";
    default -> "";
};
```

`AbilityOutcome.allowed()`, `AbilityUseDecision.allowed()`, `AbilityQuote.payable()`,
`AbilityQuote.suppressesDefaultCost()` and `AbilityReservation.reserved()` answer the question most
consumers actually have without a switch at all. Prefer them.
