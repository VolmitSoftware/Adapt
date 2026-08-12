# Adapt events

Adapt fires three groups of Bukkit event, and they have nothing to do with one another. The `AdaptEvent`
family in `art.arcane.adapt.content.event` lets you veto an adaptation use or an adaptation-driven
teleport. The two ability events in `art.arcane.adapt.api.ability` let you veto or observe a charged
activation, and they are documented with the cost funnel they belong to, in
[44 - API - Ability Cost.md](<44 - API - Ability Cost.md#observing-and-vetoing-with-events>).
`AdaptBrewCompleteEvent` reports one of Adapt's brewing recipes finishing. This document covers the first
and the last.

The `AdaptEvent` family has one property you have to know about before you write a listener: **every
subclass shares a single `HandlerList`**. Only the base class declares `getHandlerList()`, so a listener
on any of the five classes goes into the same list, and every registered handler is walked for every event
in the family before Bukkit filters by type.

No event grants anything. Cancelling refuses; not cancelling permits nothing that was not already
permitted. See
[Skill items are inert without their adaptation](<41 - API - Getting Started.md#skill-items-are-inert-without-their-adaptation>).

---

## The shared HandlerList

```
AdaptEvent                            implements Cancellable, declares the HandlerList
 +-- AdaptPlayerEvent                 inherits it
      +-- AdaptAdaptationEvent        inherits it
           +-- AdaptAdaptationUseEvent          inherits it
           +-- AdaptAdaptationTeleportEvent     inherits it
```

What follows from one shared list:

- **A listener declared on `AdaptEvent` receives every subclass**, including subclasses added later.
- **A blanket cancel on `AdaptEvent` cancels broadly.** `AdaptAdaptationUseEvent` is fired during the
  active-level gate, so cancelling everything there silently disables every adaptation for that player for
  that tick.
- **Sibling classes do not cross-talk.** Bukkit's event executor checks `eventClass.isInstance(event)`
  before invoking your method, so a listener declared on `AdaptAdaptationUseEvent` is never called for an
  `AdaptAdaptationTeleportEvent`. Only supertype listeners see everything.
- **`EventPriority` is global across the family.** Your `AdaptAdaptationTeleportEvent` handler at `HIGH`
  runs after somebody else's `AdaptEvent` handler at `NORMAL`, even though they are different events.
- **You cannot unregister one subclass.** `HandlerList.unregisterAll(plugin)` and
  `AdaptEvent.getHandlerList().unregister(...)` operate on the whole family. There is no per-subclass list
  to reach.
- **Every registered handler is walked for every event in the family**, then filtered. Registering a
  listener on `AdaptEvent` to catch "anything" puts your handler in the path of the hottest gate in the
  plugin.

Declare the narrowest applicable subclass.

## `AdaptAdaptationUseEvent`

Fired from Adapt's active-level gate, immediately before any registered
[`AbilityUsePolicy`](<43 - API - Ability Use Policy.md>) is consulted, and after the world, game-mode,
protection, permission and conflict checks have already passed. Cancelling makes the adaptation completely
inert for that player: no effect, no cooldown, no experience, no cost.

```java
public class AdaptAdaptationUseEvent extends AdaptAdaptationEvent {
  public AdaptAdaptationUseEvent(boolean async, AdaptPlayer player, Adaptation<?> adaptation)
}
```

**This event is hot.** Adapt memoises each active-level resolution per `(player, adaptation)` for the
current 50 ms tick bucket, so you see at most one event per player per adaptation per tick. A player with
thirty adaptations learned, moving and fighting, still produces a lot of them. Do nothing expensive here.
No I/O, no needless allocation, no blocking.

Cancelling is cached along with everything else: the decision holds until the tick bucket rolls over or
the player's learned level in that adaptation changes.

If your rule needs a reason, needs scoping, or needs fault containment, register an
[`AbilityUsePolicy`](<43 - API - Ability Use Policy.md>) instead. It does the same job with all three.

## `AdaptAdaptationTeleportEvent`

Fired before an adaptation moves a player. In the shipped catalogue that is `rift-blink` and `rift-gate`
and nothing else. Cancelling aborts the teleport; the adaptation cleans up, and `rift-gate` hands the
reserved gate eye back to the player rather than consuming it.

```java
public class AdaptAdaptationTeleportEvent extends AdaptAdaptationEvent {
  public AdaptAdaptationTeleportEvent(boolean async, AdaptPlayer player, Adaptation<?> adaptation,
                                      Location fromLocation, Location toLocation)

  public Location getFromLocation()
  public Location getToLocation()
}
```

There is no `setToLocation`, and the event hands you the `Location` references it was constructed with
rather than defensive copies. Both shipped callers pass a clone of the destination, so writing through
`getToLocation()` changes nothing about where the player ends up. `getFromLocation()` is a live reference
the adaptation still uses afterwards for effects and stats, so writing through that one corrupts its
bookkeeping. Treat both as read-only and clone before you touch anything.

This is not a general teleport hook. Adaptations that move a player by other means (velocity, mounts,
vanilla portals) do not fire it, and neither does any adaptation outside the `rift` skill. Listen to
`PlayerTeleportEvent` if you need coverage; use this one when you specifically need to know that Adapt did
it and which adaptation was responsible.

## `AdaptAdaptationEvent`, `AdaptPlayerEvent`, `AdaptEvent`

Base classes. Adapt never constructs any of them directly; they exist to carry accessors and the shared
handler list. Listening on them is legal and is how you receive the whole family at once, with the
consequences listed above.

One accessor is worth calling out. `AdaptAdaptationEvent.getPlayerSkill()` resolves the player's skill
line with `adaptation.getSkill().getName()`, which is the catalogue skill key. Use that accessor rather
than looking the line up yourself, and never use `Skill.getId()` as a skill-line key: that one comes from
the ticker and is a random UUID with a suffix.

## Reading an event without importing a relocated type

`AdaptPlayer`, `Skill` and `PlayerSkillLine` are Adapt's own types, but some of their accessors return
VolmLib collections that Adapt relocates at build time. Calling one bakes a relocated class name into your
jar and breaks the day that path moves. See
[Adapt relocates VolmLib](<41 - API - Getting Started.md#adapt-relocates-volmlib>).

Take the adaptation from the event rather than walking a skill's collection and you never need the unsafe
column. The Reference section lists both columns side by side.

## Threading

Both `AdaptEvent` subclasses carry an `async` flag computed from the firing thread
(`!Bukkit.isPrimaryThread()`), which Bukkit exposes as `Event.isAsynchronous()`.

In practice you will see `false`. `AdaptAdaptationUseEvent` is fired from the active-level gate, which
returns zero without firing anything when Folia is in use and the current region does not own the player.
`AdaptAdaptationTeleportEvent` is fired from an entity-scheduled task for `rift-gate` and from a
`PlayerMoveEvent` handler for `rift-blink`. All three paths are on the tick thread that owns the player,
and touching that player is legal.

Do not *assume* it. `Adaptation.canUse(Player)` is public, and a caller reaching it from an async task on
Paper, where there is no region ownership check to stop them, fires an asynchronous
`AdaptAdaptationUseEvent`. If your handler touches the world, guard it:

```java
@EventHandler(ignoreCancelled = true)
public void onUse(AdaptAdaptationUseEvent event) {
  if (event.isAsynchronous()) {
    return;
  }

  applyRule(event);
}
```

The ability events (`AdaptAbilityActivateEvent`, `AdaptAbilityActivatedEvent`) are always synchronous and
always on the owning thread. The cost funnel refuses to run off it.

---

## Worked example

A plugin that seals a jailed player's skills and stops adaptation teleports into other people's claims.

```java
package com.example.warden;

import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.content.event.AdaptAdaptationUseEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class WardenAdaptListener implements Listener {
  private final JailIndex jails;
  private final ClaimIndex claims;

  public WardenAdaptListener(JailIndex jails, ClaimIndex claims) {
    this.jails = jails;
    this.claims = claims;
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onUse(AdaptAdaptationUseEvent event) {
    AdaptPlayer adaptPlayer = event.getPlayer();
    Player player = adaptPlayer.getPlayer();

    if (player == null || !jails.isJailed(player.getUniqueId())) {
      return;
    }

    event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onTeleport(AdaptAdaptationTeleportEvent event) {
    Location destination = event.getToLocation();
    Player player = event.getPlayer().getPlayer();

    if (player == null || destination == null) {
      return;
    }

    if (claims.isClaimed(destination) && !claims.isTrusted(player.getUniqueId(), destination)) {
      event.setCancelled(true);
    }
  }
}
```

`JailIndex` and `ClaimIndex` are application-owned; the sample needs `boolean isJailed(UUID)`,
`boolean isClaimed(Location)` and `boolean isTrusted(UUID, Location)`, all answering from memory.

Note the two separate handlers. Writing one handler on `AdaptAdaptationEvent` and switching on the
concrete type would work, and would also put your code in the path of every future subclass.

### Registration

```java
@Override
public void onEnable() {
    getServer().getPluginManager().registerEvents(new WardenAdaptListener(jails, claims), this);
}
```

## `AdaptBrewCompleteEvent`

`art.arcane.adapt.api.potion.AdaptBrewCompleteEvent` has its own `HandlerList` and is **not cancellable**.
It fires after one of Adapt's brewing recipes finishes in a brewing stand, on the region thread that owns
the stand, synchronously, once per completed brew. There is nothing to veto and nothing to change; it is
there so you can log, reward or count brews.

```java
public final class AdaptBrewCompleteEvent extends Event {
  public AdaptBrewCompleteEvent(Block block, BrewingRecipe recipe, UUID brewerId, int brewedPotions)

  public Block getBlock()
  public BrewingRecipe getRecipe()
  public UUID getBrewerId()
  public int getBrewedPotions()
}
```

`getBrewerId()` is the player Adapt attributed the brew to. The constructor does not reject null, but the
only place Adapt fires this event supplies one, so a null id means somebody else constructed the event.
`getRecipe()` returns `art.arcane.adapt.api.potion.BrewingRecipe`, a plain Adapt type.

## Failure policy

There is none. These are ordinary Bukkit events with ordinary Bukkit semantics. There is no watchdog, no
fault counting and no quarantine on this surface, unlike the ability API. A handler that blocks blocks the
tick thread, and a handler that throws is logged by Bukkit while Adapt carries on.

`ignoreCancelled = true` behaves normally: your handler is skipped once anything has cancelled. Because
the family shares one handler list, "anything" includes a handler for a *different* subclass at an earlier
priority that cancelled the same in-flight event.

There are no configuration keys for these events. `[abilityApi] enabled = false` switches off the two
ability events but has no effect on the `AdaptEvent` family; `AdaptAdaptationUseEvent` fires regardless.

---

## Reference

### Event families

| Family | Package | Handler lists | Use it to |
|---|---|---|---|
| `AdaptEvent` and its subclasses | `art.arcane.adapt.content.event` | one, shared by all of them | veto an adaptation use, veto an adaptation-driven teleport |
| `AdaptAbilityActivateEvent`, `AdaptAbilityActivatedEvent` | `art.arcane.adapt.api.ability` | one each, private to that class | veto or observe a charged activation |
| `AdaptBrewCompleteEvent` | `art.arcane.adapt.api.potion` | its own | observe an Adapt brewing recipe finishing |

### `AdaptAdaptationUseEvent` and `AdaptAdaptationTeleportEvent` accessors

| From | Accessor | Returns |
|---|---|---|
| `AdaptAdaptationEvent` | `getAdaptation()` | `Adaptation<?>`, the adaptation being checked |
| `AdaptAdaptationEvent` | `getSkill()` | `Skill<?>`, its owning skill |
| `AdaptAdaptationEvent` | `getPlayerSkill()` | `PlayerSkillLine`, the player's line for that skill |
| `AdaptPlayerEvent` | `getPlayer()` | `AdaptPlayer`, Adapt's player wrapper, not a Bukkit `Player` |
| `AdaptEvent` | `getServer()` | `AdaptServer` |
| `AdaptEvent` | `isCancelled()` / `setCancelled(boolean)` | the veto |
| `AdaptAdaptationTeleportEvent` | `getFromLocation()` | `Location`, a live reference the adaptation still uses |
| `AdaptAdaptationTeleportEvent` | `getToLocation()` | `Location`, a clone in both shipped callers |

### Safe and unsafe accessors on event payloads

| Safe, returns a JDK or Bukkit type | Unsafe, returns a relocated `KList` or `KMap` |
|---|---|
| `AdaptPlayer.getPlayer()` returns `Player` | |
| `AdaptPlayer.hasAdaptation(String)` returns `boolean` | |
| `AdaptPlayer.hasSkill(Skill)` returns `boolean` | |
| `Adaptation.getName()`, `getDisplayName()` return `String` | |
| `Adaptation.getLevel(Player)`, `getMaxLevel()` return `int` | |
| `Adaptation.isEnabled()`, `isPermanent()` return `boolean` | |
| `Adaptation.getSkill()` returns `Skill<?>` | `Skill.getAdaptations()` returns `KList<Adaptation<?>>` |
| `Skill.getName()`, `getLocalizedName()` return `String` | `Skill.getRecipes()`, `Skill.getStatTrackers()` |
| `Skill.isEnabled()` returns `boolean` | |
| `PlayerSkillLine.getXp()`, `getMultiplier()` return `double` | `PlayerSkillLine.getAdaptations()` returns `KMap<…>` |
| `PlayerSkillLine.getKnowledge()` returns `long` | `PlayerSkillLine.getMultipliers()` returns `KList<…>` |
| `PlayerSkillLine.getLine()` returns `String` | `PlayerSkillLine.getStorage()` returns `KMap<…>` |

### `AdaptBrewCompleteEvent` accessors

| Accessor | Returns |
|---|---|
| `getBlock()` | The brewing stand |
| `getRecipe()` | The Adapt recipe that completed |
| `getBrewerId()` | The player Adapt attributed the brew to |
| `getBrewedPotions()` | How many output potions were produced |

### What happens when a listener misbehaves

| Misbehaviour | What happens |
|---|---|
| A handler throws | Bukkit logs "Could not pass event … to <your plugin>". Adapt is unaffected and the remaining handlers still run |
| A handler blocks | It blocks the tick thread. There is no watchdog on this surface |
| A handler is slow | Nothing is logged by Adapt |
| Repeated faults | Nothing. There is no quarantine on Bukkit events |
| A handler cancels | Honoured for `AdaptAdaptationUseEvent` and `AdaptAdaptationTeleportEvent`. The base classes are never fired on their own, so cancelling one means cancelling whichever concrete subclass is in flight |
| Your plugin is disabled | Bukkit unregisters your listeners |
