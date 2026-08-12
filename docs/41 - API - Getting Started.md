# Adapt integration API

Adapt is a skills plugin. It ships 23 skill lines and more than three hundred adaptations, each with its
own levels, experience, cooldowns and, where the author configured one, a cost paid at the moment of use:
an item, hunger, health, tool durability or vanilla experience. These numbered API documents describe what
another plugin may safely touch, and what only looks touchable.

Most integrations need two things: a way to say "not here, not now", and a way to charge for a use in your
own currency. Both are Bukkit services. You register with the `ServicesManager` and Adapt finds you. There
is nothing to look up, no instance to fetch, and no reload to trigger.

Everything else is reading. You can walk the catalogue, listen to Adapt's events, read levels and power
through PlaceholderAPI, and change a learned level through one transaction class. What you cannot do is
grant anything. Use policies, cost providers, protectors and event listeners can only refuse, price, or
watch. The two surfaces that do change state are `AdaptationLearningTransaction` and a
`RegionPolicySource`, and both have stricter ownership rules documented where they live.

Read [Adapt relocates VolmLib](#adapt-relocates-volmlib) before you write a line of code. Adapt rewrites
its utility library's package name at build time, and a consumer that names one of those types compiles
today and fails at runtime later.

---

## Depending on Adapt

Adapt ships as a single shaded jar, `Adapt-<version>-all.jar`. Compile against that jar and no other. A
thin jar with no classifier is produced alongside it and contains none of the shaded libraries. The ability
API happens to link against the thin jar, but `Adaptation`, `Skill`, `AdaptPlayer` and the `Adapt` plugin
class all name VolmLib types that are absent from it, so javac cannot resolve them.

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

`join-classpath: true` is mandatory on Paper. Plugin classloaders are isolated, and without it every
`art.arcane.adapt.*` reference fails at runtime with `NoClassDefFoundError` even though the classes sit in
Adapt's jar.

`softdepend` (or `load: BEFORE`) is what guarantees Adapt has finished `onEnable` before yours starts.
That matters: `AbilityApiBridge.install(...)` and the `ProtectorRegistry` are both wired during Adapt's
`onEnable`, so a plugin that enables first finds nothing to register with.

Adapt declares `folia-supported: true` and compiles at Java release 25. The server running Adapt must use
Java 25.

---

## Adapt relocates VolmLib

Adapt shades its shared utility library, VolmLib, and rewrites its package at build time:

```
art.arcane.volmlib.**   ->   art.arcane.adapt.util.arcane.volmlib.**
```

The same rewrite is applied to Manifold, the UltimateAdvancementAPI, Libby, CustomBlockData, bStats and
the Fukkit and Amulet extension packages. Everything under `art.arcane.adapt.util.` is either a relocated
third-party library or Adapt's own vendored plumbing. That package path is a build artifact. It is not
versioned, not announced, and free to move.

The rule for a consumer:

- **Reference freely:** `art.arcane.adapt.api.ability` (except its `internal` subpackage), the supported
  members listed in docs `42`, `46`, and `48` to `50`, `AdaptBrewCompleteEvent`, and
  `art.arcane.adapt.content.event.**`.
- **Never name** anything under `art.arcane.adapt.util.**`, in an import, a field type, a method
  signature, a cast or a generic argument.
- **Read the signature first** anywhere else under `art.arcane.adapt.api.**`. That tree is Adapt's own
  authoring surface, and a good deal of it (`Skill`, `PlayerSkillLine`, `MutationType.keys()`, everything
  under `api.xp` and `api.fx`) names a relocated type in a return type or a parameter.

`art.arcane.adapt.api.ability` is deliberately built from Bukkit types, `java.*` types and its own types
only. `PublicSurfacePurityTest` fails Adapt's build if any public member of that package so much as
mentions a relocated, internal, or content type, so the whole ability API links against a plain Paper
compile classpath. The same test checks that `Adaptation` and `Protector` name nothing from an internal
package; `Protector` itself only names `Adaptation`, `Player` and `Location`.

### Where this leaks into a documented surface

The older event family does not hold that line. `Skill.getAdaptations()` returns VolmLib's `KList`, and
several `PlayerSkillLine` collection getters return `KList` and `KMap`. Those calls compile, because
`KList extends ArrayList` and `KMap extends ConcurrentHashMap` and the relocated classes are present in
the shaded jar, but the call site records the concrete relocated return type. This source:

```java
List<Adaptation<?>> adaptations = skill.getAdaptations();
```

compiles to this instruction in **your** jar:

```
invokeinterface art/arcane/adapt/api/skill/Skill.getAdaptations:()Lart/arcane/adapt/util/arcane/volmlib/util/collection/KList;
```

The relocated class name is baked into consumer bytecode and can produce `NoSuchMethodError` if that path
changes. Use accessors that return JDK or Bukkit types instead (`getName()`, `isEnabled()`,
`getMaxLevel()`, `getLevel(Player)`, `getXp()`, `getKnowledge()`) and take the adaptation from the event
rather than by walking a skill's collection. [45 - API - Events.md](<45 - API - Events.md>) lists the safe
accessors side by side with the unsafe ones.

---

## What is not API

`art.arcane.adapt.api.ability.internal` is Java-public so Adapt's own tests can drive it. Do not import,
instantiate, register, or reflect against anything in it. The Reference section lists the classes.

`AbilityApiBridge` sits in `art.arcane.adapt.api.adaptation` and looks like an entry point. It is not. The
methods that drive the funnel are package-private. Of its three public statics, `install` and `uninstall`
are Adapt's own lifecycle, and `lastDenial(UUID)` returns an internal `AbilityDenial` that nothing in
Adapt reads. Register through the `ServicesManager` instead.

---

## Threading

Adapt runs on Paper and on Folia, and the answer is the same on both: **the tick thread that owns the
player.** On Paper that is the single main thread. On Folia it is the region thread that currently owns
the region the player stands in.

`AbilityUsePolicy.evaluate`, `AbilityCostProvider.quote` and `reserve`, every `Protector` method, and both
ability events arrive on that thread. Reading and mutating the player's inventory, experience, health and
location is legal there.

Two surfaces do not fit that sentence and are documented where they live rather than papered over here:

- **`AbilityCostProvider.commit` and `refund`.** `commit` is on the owning thread whenever the charge
  resolves inside one call; a deferred charge commits on whichever thread settles the ticket. `refund` is
  on the owning thread for a rollback and on some other thread entirely for `EXPIRED`,
  `ADAPTATION_DISABLED` and `SERVER_SHUTDOWN`. See
  [44 - API - Ability Cost.md](<44 - API - Ability Cost.md#threading>).
- **The older `AdaptEvent` family** carries an async flag computed from the firing thread and can
  genuinely be asynchronous. See [45 - API - Events.md](<45 - API - Events.md#threading>).

`providerId()` and `scope()` are not calls in this sense at all. Adapt reads them once when it rebuilds
its provider index after a service registration change, on whichever thread triggered the rebuild.

Adapt checks before it calls. `AbilityUsePolicy` and `AbilityCostProvider` are skipped outright, with a
throttled warning, when the calling thread is not `Bukkit.isPrimaryThread()`, or when Folia is in use and
that thread does not own the player. You are never handed a player you may not touch.

**Do not block.** No I/O, no `CompletableFuture.join`, no `Bukkit.getScheduler().callSyncMethod`, no lock
held across the call. These callbacks sit inside the ability-check path, which runs many times per tick
per player. A provider call that takes at least `[abilityApi] slowProviderMillis` is logged with a
throttled warning naming your plugin, but the warning never changes the decision. A provider that hangs
cannot be interrupted, so the contract is the only protection. If you need remote data, cache it and prime
the cache on `PlayerJoinEvent`.

Placeholders are the exception. They are served from an immutable snapshot and are safe to resolve from
any thread. See [47 - API - PlaceholderAPI.md](<47 - API - PlaceholderAPI.md>).

---

## Permissions are granted to everyone by default

At startup Adapt mints one permission per adaptation, one per skill, one per mutation, and a root
`adapt.use.*`. Every one of them is registered with `PermissionDefault.TRUE`. The node shapes are in the
Reference section.

Three behaviours matter:

1. **An unset node means granted.** Adapt asks `player.isPermissionSet(node)` first and returns granted
   when the node is unset, without consulting the value. A node Adapt never registered, such as a custom
   adaptation or a mistyped node, is permitted rather than refused. To deny, negate the node explicitly
   (`-adapt.use.riftconduit`). When the node *is* set, Adapt honours `player.hasPermission(node)` normally.
2. **Operators and debug-mode players bypass the check entirely.** Adapt's debug mode is tested first,
   then `player.isOp()`, and either short-circuits to granted before the node is consulted. Do not use
   `adapt.use.*` as a security boundary, and do not test your integration while opped.
3. **Only the adaptation node is read directly.** The skill node and `adapt.use.*` work through Bukkit's
   normal parent and child recalculation, so negating `adapt.use.rift` negates every `rift-*` adaptation
   under it.

For a decision independent of the permission tree, register an
[`AbilityUsePolicy`](<43 - API - Ability Use Policy.md>).

---

## Skill items are inert without their adaptation

A number of adaptations mint items: a bound eye of ender, a chalk wand, a redstone torch with a target
baked into it. Those items are deliberately inert in the hands of a player who has not learned the owning
adaptation. They are not merely weakened; the handlers that give them behaviour never run.

The active-level gate enforces this. `Adaptation.getActiveLevel(player)` returns `0` for an adaptation the
player has not learned, and that is the *first* thing it checks, before world, game mode, protection,
permissions, conflicts or any policy. Every item-bearing handler in the catalogue refuses to proceed on a
zero level. Several go further and cancel the interaction outright so the item does not even behave like
the vanilla item it is made from. A second, narrower gate in Adapt's event dispatch drops `PlayerMoveEvent`
and `PlayerJumpEvent` for non-learners before they reach a handler at all, purely to keep those two hot
events cheap.

No surface documented here routes around that. `AbilityUseDecision` carries two statuses, and `ALLOW`
means only "this policy does not object"; every other gate still runs. `AbilityQuote.waived(...)`
suppresses a cost, not a requirement, and the cost gateway refuses to consult providers at all when the
acting player's level is zero: it logs, charges the built-in cost, and returns. There is no method, event
or service that grants an adaptation, and adding one is not a supported request.

If you want a player to use a skill item, teach them the adaptation.

---

## Identifiers

Skill ids are lowercase single words. Adaptation ids are `<skill>-<name>`, lowercase and hyphenated:
`rift-blink`, `pickaxe-veinminer`, `tragoul-skeletal-servant`, `stealth-shadowmeld`. The skill prefix is a
convention, not a parsing rule. Always read the skill from `AbilityContext.skillId()` or
`Adaptation.getSkill().getName()` rather than splitting the adaptation id. Mutation ids are lowercase and
hyphenated. The full lists are in the Reference section.

Every id you hand to the ability API is truncated to 128 characters, stripped of control characters and
lowercased before it is compared, so casing and stray whitespace in your own configuration will not cause
a silent mismatch. A blank id is rejected with `IllegalArgumentException`.

Live counts are readable at runtime through `%adapt_catalog.skills%`, `%adapt_catalog.adaptations%` and
`%adapt_catalog.mutations%`.

---

## Switching over the enums

`AbilityOutcome`, `AbilityRefundReason`, `AbilityQuoteStatus`, `AbilityReservationStatus`,
`AbilityUseStatus`, `AbilityCostKind`, `AbilityPhase`, `MutationState` and `MutationType` may gain
constants in a future release. A `switch` **expression** over an enum is exhaustive, so it stops compiling,
and throws `IncompatibleClassChangeError` on an already-compiled jar, the moment one is added.

Always write a `default` arm in third-party code:

```java
String label = switch (charge.outcome()) {
    case ALLOWED_CHARGED -> "charged";
    case ALLOWED_WAIVED -> "waived";
    default -> "";
};
```

`AbilityOutcome.allowed()`, `AbilityUseDecision.allowed()`, `AbilityQuote.payable()`,
`AbilityQuote.suppressesDefaultCost()` and `AbilityReservation.reserved()` answer the question most
consumers actually have without a switch. Prefer them.

---

## Reference

### Where to go for what

| You want to | Use | Document |
|---|---|---|
| answer "may this player use this adaptation right now", with a reason | `AbilityUsePolicy` (ServicesManager) | [43 - API - Ability Use Policy.md](<43 - API - Ability Use Policy.md>) |
| price an activation, or charge for it from your own economy | `AbilityCostProvider` (ServicesManager) | [44 - API - Ability Cost.md](<44 - API - Ability Cost.md>) |
| gate block and entity actions on claims or regions | `Protector` (`ProtectorRegistry`) | [46 - API - Protection.md](<46 - API - Protection.md>) |
| supply region XP, power, and temporary adaptation grants | `RegionPolicySource` (`RegionPolicyService`) | [46 - API - Protection.md](<46 - API - Protection.md#region-policy-source>) |
| watch or veto activation and adaptation events | the ability events and the `AdaptEvent` family | [45 - API - Events.md](<45 - API - Events.md>) |
| inspect catalogue objects, or change a learned level transactionally | `SkillRegistry`, `AdaptationLearningTransaction` | [42 - API - Skills & Adaptations.md](<42 - API - Skills & Adaptations.md>) |
| inspect mutation catalogue and snapshot value objects | `MutationCatalog`, `MutationSnapshot` | [48 - API - Mutations.md](<48 - API - Mutations.md>) |
| look up online data, or award progression through production paths | `AdaptServer`, `XP` | [49 - API - Player Data, XP & World.md](<49 - API - Player Data, XP & World.md>) |
| build recipes and effects, or read diagnostics | recipe, FX, telemetry, projectile, and value helpers | [50 - API - Recipes, FX, Telemetry & Utilities.md](<50 - API - Recipes, FX, Telemetry & Utilities.md>) |
| read levels, power, and mutations as text | the `%adapt_…%` PlaceholderAPI expansion | [47 - API - PlaceholderAPI.md](<47 - API - PlaceholderAPI.md>) |

### Not API

| Path | Why |
|---|---|
| `art.arcane.adapt.api.ability.internal.**` | The gateway, provider index, guard and service listener that drive the ability API. Public for Adapt's own tests. Not a contract. |
| `art.arcane.adapt.util.**` | Relocated libraries and vendored plumbing. |
| `art.arcane.adapt.content.**` except `content.event` | The adaptation and skill catalogue itself. Class names, packages and behaviour change release to release. |
| `art.arcane.adapt.service.**` | Adapt's own service objects. |

The Java-public classes in `art.arcane.adapt.api.ability.internal` are `AbilityApiPolicy`,
`AbilityApiRuntime`, `AbilityCostGateway`, `AbilityDenial`, `AbilityEventSink`, `AbilityFailureMode`,
`AbilityProviderGuard`, `AbilityProviderIndex`, `AbilityProviderRegistration`,
`AbilityProviderRegistrations`, `AbilityProviderSource`, `AbilityServiceListener`,
`AbilityUsePolicyGateway`, `BukkitAbilityEventSink`, and `BukkitAbilityProviderSource`.

### Permission nodes

| Node | Example | Default |
|---|---|---|
| `adapt.use.<adaptation name, hyphens removed>` | `adapt.use.riftconduit` | `TRUE` |
| `adapt.use.<skill name>` (parent of its adaptations) | `adapt.use.rift` | `TRUE` |
| `adapt.use.mutation.<mutation id>` (hyphens kept) | `adapt.use.mutation.bastion-spine` | `TRUE` |
| `adapt.use.*` (parent of every skill and mutation node) | | `TRUE` |

### Skill ids

```
agility     architect   axes        blocking    brewing     chronos     crafting
discovery   enchanting  excavation  herbalism   hunter      kinetics    nether
pickaxe     ranged      rift        seaborne    stealth     swords      taming
tragoul     unarmed
```

### Mutation ids

```
gale-lung        bastion-spine    verdant-molt     temperbound      paradox-scar
arsenal-cortex   packmind         trophy-crucible  umbral-echo      living-lattice
masterwork-bond  deepblood        mycelial-nerve   gravebloom       resonant-formula
```

### Configuration

Everything an admin can tune about these surfaces lives in `plugins/Adapt/adapt/adapt.toml`.

| Section | Governs | Document |
|---|---|---|
| `[abilityApi]` | the whole ability API: master switch, failure modes, fault limit, slow-call threshold, denial throttle | [43 - API - Ability Use Policy.md](<43 - API - Ability Use Policy.md#configuration>), [44 - API - Ability Cost.md](<44 - API - Ability Cost.md#configuration>) |
| `[protectorSupport]` | which built-in protectors are on by default | [46 - API - Protection.md](<46 - API - Protection.md#configuration>) |
| `[protectionOverrides]` | per-adaptation protector overrides | [46 - API - Protection.md](<46 - API - Protection.md#configuration>) |

The file is rewritten in canonical form on load, so keys you add by hand that Adapt does not recognise are
dropped. Edit only documented keys.
