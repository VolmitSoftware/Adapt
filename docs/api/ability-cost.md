# Adapt ability cost API

`art.arcane.adapt.api.ability.AbilityCostProvider` lets another plugin **price**, **charge for**, **waive**
or **veto** the cost of one adaptation activation. Every consumable an adaptation takes — an item, hunger,
health, tool durability, vanilla experience — passes through a single funnel, and that funnel offers each
registered provider the chance to substitute its own currency, declare the cost paid, or refuse outright.

It is built from Bukkit types, `java.*` types and its own types only. No VolmLib, no Adventure, no shaded
types: it links against a plain Paper compile classpath.

Two entry points, and they are not interchangeable:

| You want to…                                                     | Use |
|------------------------------------------------------------------|-----|
| take something from the player, and give it back if the use fails | `AbilityCostProvider` (ServicesManager) |
| watch activations, or veto them for free                          | `AdaptAbilityActivateEvent` / `AdaptAbilityActivatedEvent` |

**Events are never the transport for value.** A cancelled event costs nothing and refunds nothing. Only a
registered `AbilityCostProvider` ever holds value.

A cost provider **cannot grant an ability**. It prices a use that has already been permitted. See
[Skill items are inert without their adaptation](README.md#skill-items-are-inert-without-their-adaptation).

---

## Depending on Adapt

Compile against the shaded `Adapt-<version>-all.jar` and declare the dependency in your plugin manifest.
The full instructions are in [README.md](README.md#depending-on-adapt).

Register in `onEnable`. Bukkit unregisters you automatically when your plugin disables, and Adapt notices
the unregistration on the next check.

```java
@Override
public void onEnable() {
    getServer().getServicesManager().register(
        AbilityCostProvider.class, new ManaCostProvider(pool), this, ServicePriority.Normal);
}
```

---

## The consumable funnel

Adapt's adaptations do not take things from players directly. Each one calls a typed anchor on
`Adaptation`, hands Adapt the cost it *would* have taken as a callback, and lets the funnel decide whether
that callback runs.

| Anchor an adaptation calls | `AbilityCostKind` | `defaultItem` | `defaultAmount` |
|----------------------------|-------------------|---------------|-----------------|
| `payItemCost` | `ITEM` | the unit stack being consumed, when the adaptation names one | how many units |
| `payHungerCost` | `HUNGER` | empty | hunger points |
| `payHealthCost` | `HEALTH` | empty | health points, rounded up |
| `payDurabilityCost` | `DURABILITY` | empty | durability damage |
| `payExperienceCost` | `VANILLA_EXPERIENCE` | empty | vanilla experience points |

Every anchor carries a **cost key**, `adaptation:<adaptation id>:<what>`, lowercased. That key is the
stable name of one charge inside one adaptation, and it is what you branch on when you want to price
individual actions rather than whole skills. Real keys from the shipped catalogue:

```
adaptation:tragoul-skeletal-servant:summon
adaptation:tragoul-marrow-armor:absorb
adaptation:rift-gate:bind
adaptation:rift-gate:teleport
adaptation:rift-gate:unlink
adaptation:hunter-speed:consumable
adaptation:chronos-instant-recall:clock
adaptation:chronos-aberrant-touch:hunger
adaptation:discovery-better-mending:experience
adaptation:<any adaptation>:durability
adaptation:<any adaptation>:health
```

The `:durability` and `:health` keys reach the funnel from shared machinery — hand and off-hand tool damage,
and self-damage applied by an adaptation — so they appear under many adaptation ids rather than a fixed
few. Cost keys are not exhaustively enumerable from outside; branch on `kind()` for broad rules and on
`costKey()` only for the specific charges you care about, always with a `default` arm.

### Pass, waive, pay, refuse

`AbilityQuote` has five statuses, and the difference between the first two is the one readers get wrong.

| Quote | Meaning | Does Adapt take its own cost? | Does the activation proceed? |
|-------|---------|-------------------------------|------------------------------|
| `AbilityQuote.pass()` | "Not my business." | **Yes** — the built-in cost is charged normally | Yes |
| `AbilityQuote.waived(desc)` | "This is free, on my authority." | **No** — Adapt must not take it | Yes |
| `AbilityQuote.payable(desc)` | "I will charge for this instead." | **No** — you replace it | Yes, once you reserve |
| `AbilityQuote.insufficient(desc)` | "They cannot afford my price." | No | **No** |
| `AbilityQuote.denied(reason)` | "Refused, regardless of price." | No | **No** |

`suppressesDefaultCost()` answers this directly: it is true for `WAIVED` and `PAYABLE`, false for the rest.

Read the first row again. **Passing is not free.** A provider that returns `pass()` for an item cost has
told Adapt to go ahead and take the item. If your intent is "this player pays nothing at all", you must
return `waived(...)`, not `pass()`. Getting this backwards produces a bug that looks like your provider
being ignored.

The built-in cost is suppressed if **any** provider waives or successfully reserves. Providers do not
negotiate: the first `INSUFFICIENT` or `DENIED` ends the activation immediately and nothing is charged.

## The lifecycle

```
quote(context)          side-effect free. Say PASS, WAIVED, PAYABLE, INSUFFICIENT or DENIED.
   |
   |  (only for a PAYABLE quote, and only if nobody denied)
   v
reserve(context, quote) take the value now. Return a receipt.
   |
   +--> commit(receipt)                 the activation happened. Value is yours. FINAL.
   +--> refund(receipt, reason)         it did not happen. Give it back.
```

Rules Adapt guarantees:

- `quote` is called at most **once** per provider per activation, and never after another provider denied.
- Providers run in `ServicePriority` order, highest first, tie-broken by plugin name then `providerId()`.
- `reserve` is called **only** for a `PAYABLE` quote, and only after every provider has quoted without
  denying.
- **All-or-nothing.** If any provider fails to reserve, every provider that already reserved is refunded in
  strict reverse order with `CHARGE_ROLLBACK`, and nobody pays.
- If nobody waived and nobody reserved, Adapt takes its own built-in cost last. A player who cannot afford
  it is denied with `DENIED_INSUFFICIENT` — and because nothing was reserved in that case, there is nothing
  to roll back.
- Exactly **one** of `commit` or `refund` is called for each receipt. Whichever arrives first wins.
- **`commit` is final.** A refund attempted after commit is a no-op and never reaches you.
- `providerId()` and `scope()` are read **once**, when Adapt rebuilds its provider index after a service
  registration change. Treat both as constants.

**Every adaptation in the shipped catalogue charges immediately**: `quote`, `reserve` and `commit` happen
inside one call, before `payItemCost` returns to the adaptation. A deferred form exists for activations
that resolve later — it holds an open ticket which Adapt settles or refunds — and it changes nothing about
your contract except the delay before `commit` or `refund` arrives. No shipped adaptation uses it, so
unless Adapt's own catalogue changes, your `commit` always follows your `reserve` inside the same call.
Write your provider so the delay is legal anyway; that is the contract, and the immediate path is the
special case.

An open ticket that is never settled or refunded is reclaimed with `EXPIRED` once it is 30 seconds old. The
reclaim is a backstop, not a timer: the sweep runs at most once a second and only when the funnel is next
used, so on an idle server the refund waits until something else happens, or until shutdown.

## Threading

`quote` and `reserve` run on the tick thread that owns the player: the main thread on Paper, the owning
region thread on Folia. Reading and mutating the player's inventory, experience, health and location is
legal there. `commit` runs on that same thread in the immediate form of the funnel, where it happens inside
the `reserve` call's own stack; in the deferred form it runs on whichever thread settles the ticket.

Adapt checks before it calls. If the calling thread is not `Bukkit.isPrimaryThread()`, or if Folia is in
use and the current region does not own the player, **no provider is consulted**, the built-in cost is
taken, and a throttled warning is logged.

`refund` runs on that same thread for `CHARGE_ROLLBACK`, which is the only reason the immediate form of the
funnel produces. The three ticket reasons do not carry that guarantee:

- **`EXPIRED`.** The reclaim happens at the head of the next charge, so the call arrives on the tick thread
  of whatever player triggered that charge — not the one you were quoted for.
- **`ADAPTATION_DISABLED`.** Adapt drains orphaned tickets from its `ServiceUnregisterEvent` handler.
- **`SERVER_SHUTDOWN`.** Adapt refunds from its own uninstall path, on whichever thread is disabling the
  plugin.

On all three the player may be owned by a different region thread, or may not be on the server at all.
Reverse the charge against your own state and nothing else: no inventory writes, no teleports, no entity,
block or chunk access. If a refund genuinely has to touch the player, hop to that player's entity scheduler
and handle the hop being refused.

**Do not block.** No I/O, no `CompletableFuture.join`, no `callSyncMethod`, no lock held across the call. A
call taking at least `[abilityApi] slowProviderMillis` (default 2 ms) is logged with a throttled warning
naming your plugin, but the warning never changes the outcome.

**Do not re-enter Adapt.** If a provider triggers another charge on the same thread, the nested charge is
refused outright with `AbilityOutcome.DENIED_REENTRANT`, is counted, and logs a throttled warning. Nothing
is charged and nothing is rolled back, because nothing had been reserved.

If you need remote data, cache it and prime the cache on `PlayerJoinEvent`.

---

## Worked example: charging from your own resource pool

A plugin with its own "Mana" pool that wants blood magic to cost mana instead of bones and blood.

### The receipt

The receipt is yours and it is **opaque**. `AbilityReceipt` declares no instance methods: Adapt stores the
object verbatim, never calls anything on it — not even `toString()` — and hands the exact same instance
back to `commit` or `refund`. Put whatever you need to reverse the charge in it.

```java
package com.example.warden;

import art.arcane.adapt.api.ability.AbilityReceipt;
import java.util.UUID;

public record ManaReceipt(UUID playerId, int amount) implements AbilityReceipt {
}
```

Adapt pairs every receipt with the provider that returned it, so a receipt never has to identify itself and
can never be attributed to anyone else. `AbilityReceipt.of("label")` exists for providers that need no
state — the label is for your own logs and Adapt never reads it.

### The provider

```java
package com.example.warden;

import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityQuote;
import art.arcane.adapt.api.ability.AbilityReceipt;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.AbilityReservation;
import art.arcane.adapt.api.ability.AbilityScope;
import java.util.UUID;

public final class ManaCostProvider implements AbilityCostProvider {
  private static final AbilityScope SCOPE = AbilityScope.skill("tragoul");

  private final ManaPool pool;

  public ManaCostProvider(ManaPool pool) {
    this.pool = pool;
  }

  @Override
  public String providerId() {
    return "warden-mana";
  }

  @Override
  public AbilityScope scope() {
    return SCOPE;
  }

  @Override
  public AbilityQuote quote(AbilityCostContext context) {
    UUID playerId = context.ability().playerId();

    if (!pool.isAttuned(playerId)) {
      return AbilityQuote.pass();
    }

    int price = priceOf(context);

    if (price <= 0) {
      return AbilityQuote.waived("Blood magic is free for the attuned");
    }

    if (pool.balance(playerId) < price) {
      return AbilityQuote.insufficient(price + " Mana").withPrice(price, "Mana");
    }

    return AbilityQuote.payable(price + " Mana").withPrice(price, "Mana");
  }

  @Override
  public AbilityReservation reserve(AbilityCostContext context, AbilityQuote quote) {
    UUID playerId = context.ability().playerId();
    int price = priceOf(context);

    if (!pool.withdraw(playerId, price)) {
      return AbilityReservation.failed("Your mana ran out");
    }

    return AbilityReservation.reserved(new ManaReceipt(playerId, price));
  }

  @Override
  public void commit(AbilityReceipt receipt) {
    if (receipt instanceof ManaReceipt mana) {
      pool.recordSpend(mana.playerId(), mana.amount());
    }
  }

  @Override
  public void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
    if (receipt instanceof ManaReceipt mana) {
      pool.deposit(mana.playerId(), mana.amount());
    }
  }

  private int priceOf(AbilityCostContext context) {
    int units = Math.max(1, context.defaultAmount());

    return switch (context.kind()) {
      case ITEM -> 4 * units;
      case HEALTH -> 6 * units;
      case HUNGER, DURABILITY, VANILLA_EXPERIENCE -> 0;
    };
  }
}
```

`ManaPool` is yours; the sample needs `isAttuned(UUID)`, `balance(UUID)`, `withdraw(UUID, int)`,
`deposit(UUID, int)` and `recordSpend(UUID, int)`, all answering from memory.

Three things this example is doing on purpose:

- A player who is not attuned gets `pass()`, so Adapt takes its own bones and blood as usual. Returning
  `waived(...)` there would make blood magic free for everyone who never joined your system.
- A price of zero for an attuned player is `waived(...)`, not `pass()`: the mana system owns this cost now,
  and it has decided the cost is nothing.
- `priceOf` is deterministic and reads nothing but the context, so `quote` and `reserve` cannot disagree.
  If yours can, quote once and stash the number on a field keyed by `context.ability().activationId()` —
  `reserve` always follows its own `quote` on the same thread.

The `switch` in `priceOf` is exhaustive over `AbilityCostKind` because it is inside code compiled against a
known Adapt version in a sample. In shipping code, add a `default` arm — see
[Switching over the enums](README.md#switching-over-the-enums).

### Registration

```java
package com.example.warden;

import art.arcane.adapt.api.ability.AbilityCostProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class WardenPlugin extends JavaPlugin {
  private final ManaPool pool = new ManaPool();

  @Override
  public void onEnable() {
    getServer().getServicesManager().register(
        AbilityCostProvider.class, new ManaCostProvider(pool), this, ServicePriority.Normal);
  }
}
```

---

## The minimum: waive or veto, no money

`quote` is the only method without a default. If you never take value, that is the only method you write,
and Adapt's `AbilityCostProvider` accepts a lambda:

```java
getServer().getServicesManager().register(AbilityCostProvider.class,
    context -> context.ability().level() >= 5
        ? AbilityQuote.waived("Veterans pay nothing")
        : AbilityQuote.pass(),
    this, ServicePriority.Normal);
```

The default `reserve` returns `AbilityReservation.failed("this provider quoted a price but does not
implement reserve")`, which is a deliberate late refusal rather than a fault — so a provider that quotes
`PAYABLE` without implementing `reserve` denies the activation cleanly instead of charging nothing and
proceeding. `commit` and `refund` default to doing nothing.

If you charge, override `providerId()` too. The default is your class name, which for a lambda is a
generated name like `com.example.Warden$$Lambda/0x00007f2a…` that changes on every restart. Adapt logs one
warning naming your plugin when it sees that, and keeps using the generated name — unique within a run, so
deduplication and quarantine still work, but not a name an admin can recognise.

---

## What the context tells you

```java
public record AbilityCostContext(AbilityContext ability, String costKey, AbilityCostKind kind,
                                 Optional<ItemStack> defaultItem, int defaultAmount)
```

| Component       | What it is                                                                       |
|-----------------|-----------------------------------------------------------------------------------|
| `ability`       | Who, what and where. See the table below                                           |
| `costKey`       | `adaptation:rift-gate:teleport`. Lowercased, trimmed, never blank                  |
| `kind`          | `ITEM`, `HUNGER`, `HEALTH`, `DURABILITY` or `VANILLA_EXPERIENCE`                    |
| `defaultItem`   | The unit the adaptation would consume. Empty for every kind except `ITEM`           |
| `defaultAmount` | How many units. Never negative                                                      |

```java
public record AbilityContext(UUID activationId, String abilityId, String skillId, int level,
                             AbilityPhase phase, Player player, Optional<Location> origin)
```

| Component      | What it is                                                                        |
|----------------|------------------------------------------------------------------------------------|
| `activationId` | Unique per activation. The key for this whole transaction                           |
| `abilityId`    | `rift-gate`. Lowercased, trimmed, never blank                                       |
| `skillId`      | `rift`. Lowercased, trimmed, never blank                                            |
| `level`        | The player's learned level in this adaptation                                       |
| `phase`        | Always `ACTIVATE` in a cost context                                                 |
| `player`       | The live `Player`                                                                    |
| `origin`       | The player's location when the charge began                                          |

Plus `UUID playerId()` on `AbilityContext`, a shortcut for `player().getUniqueId()`.

`defaultItem()` and `origin()` return a **defensive copy on every read**. Mutating what you get back changes
nothing, and calling them in a loop allocates — read once into a local.

`defaultItem` is the *unit* the adaptation would consume, not the stack in the player's inventory. Its
amount is not meaningful; `defaultAmount` is how many units. It is empty for every kind except `ITEM`, and
it can be empty for `ITEM` too when the adaptation did not name a specific stack.

Two guarantees worth relying on:

- **`phase()` is always `ACTIVATE` here.** If the funnel is somehow called outside `ACTIVATE`, Adapt logs a
  throttled warning, charges the built-in cost and never consults a provider.
- **`level()` is at least 1.** The funnel refuses to consult providers for a player who has not learned the
  adaptation: it logs, charges the built-in cost, and returns.

### Two public types you will never be handed

`AbilityDefaultCost` is the callback an adaptation hands the funnel — the built-in cost it *would* have
taken, wrapped as `boolean take()`. It is public because the `pay*Cost` anchors on `Adaptation` name it in
their signatures, and it never reaches a provider. `AbilityDefaultCost.NONE` is the no-op that always
succeeds. You observe its effect through `AbilityCharge.defaultCostSuppressed()`, not by calling it.

`AbilityReceipt.SimpleReceipt` is the record behind `AbilityReceipt.of(label)`. Match on it only if you
created it; a `SimpleReceipt` you did not create belongs to another provider and will never reach you. The
factory sanitises the label on the way in, so `label()` is not necessarily the string you passed.

---

## Observing and vetoing with events

Two events, each with its own `HandlerList`, both fired on the tick thread that owns the player. They are
*not* part of the `AdaptEvent` family and share nothing with it — see [events.md](events.md).

```java
@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
public void onActivate(AdaptAbilityActivateEvent event) {
  if (!duelists.contains(event.getContext().playerId())) {
    return;
  }

  event.setCancelReason("Skills are disabled during a duel");
  event.setCancelled(true);
}

@EventHandler(priority = EventPriority.MONITOR)
public void onActivated(AdaptAbilityActivatedEvent event) {
  AbilityCharge charge = event.getCharge();
  String settled = switch (charge.outcome()) {
    case ALLOWED_CHARGED -> "charged " + charge.chargedProviderIds();
    case ALLOWED_WAIVED -> "waived";
    case ALLOWED_DEFAULT -> "paid the built-in cost";
    case ALLOWED_PROVIDER_FAILED -> "allowed after provider " + charge.providerId() + " faulted";
    default -> "unknown";
  };

  logger.info(event.getContext().abilityId() + " for " + event.getCost().costKey() + ": " + settled);
}
```

`AdaptAbilityActivateEvent` fires **before any provider is quoted**, so cancelling costs nothing and
refunds nothing. Your cancel reason is sanitised to 128 characters and is not shown to the player.

`AdaptAbilityActivatedEvent` fires **only for an activation that was allowed and settled**. Denials never
reach it, so the only outcomes you will observe there are `ALLOWED_DEFAULT`, `ALLOWED_WAIVED`,
`ALLOWED_CHARGED` and `ALLOWED_PROVIDER_FAILED`. There is no event for a refused activation.

Registering a listener on either event is enough on its own to wake the funnel: Adapt short-circuits the
whole cost path when there are no cost providers *and* no listeners on either event, so a pure-observer
plugin costs nothing until it exists.

---

## Hostile-provider policy

Adapt assumes a provider will throw, return null, hand back somebody else's receipt, or fail to refund.

| Misbehaviour | What Adapt does |
|--------------|-----------------|
| `quote` throws or returns null | Counted as a fault, logged with the stack trace, then the configured failure mode decides |
| Failure mode `allow` (**the default**) | The faulting provider is skipped, the activation proceeds, and the outcome becomes `ALLOWED_PROVIDER_FAILED` naming your provider id |
| Failure mode `deny` | The activation is refused with `DENIED_PROVIDER_FAILED` |
| `reserve` throws or returns null | Everything already reserved is refunded in reverse order with `CHARGE_ROLLBACK`; nobody pays. Then the failure mode decides |
| `reserve` returns `failed(reason)` | Not a fault — a deliberate late refusal. Rollback, then deny with `DENIED_INSUFFICIENT` and your reason |
| A receipt that throws from `toString`/`equals`/`hashCode` | Nothing. Adapt never calls a receipt |
| `commit` throws | Logged and counted. The activation already happened; it is not undone |
| `refund` throws | Logged and counted, and the rollback loop **continues** to the next receipt |
| Repeated faults | After `providerFaultLimit` faults (default 5) the provider is quarantined with a `SEVERE` log line naming your plugin, and skipped until its registration disappears and returns |
| Slow call | Throttled warning naming your plugin. Never changes the outcome |
| `providerId()` throws or returns blank | The registration is ignored entirely, with a warning |
| `scope()` throws or returns null | Treated as `AbilityScope.everything()`, with a warning |
| Two providers claim one `providerId` | The higher-priority one is kept, the other is ignored with a warning |
| The same instance registered twice | The duplicate is dropped silently; you are quoted once |
| Your plugin is disabled while still registered | You are not quoted. If you hold an open reservation, Adapt still calls `refund` and logs that it is refunding through a disabled plugin |
| Your service is unregistered with tickets open | Every open ticket holding one of your charges is refunded with `ADAPTATION_DISABLED` |
| Adapt shuts down | Every unresolved receipt is refunded with `SERVER_SHUTDOWN` |
| Adapt is disabled or `[abilityApi] enabled = false` | No provider is called, neither event fires, and Adapt takes its own built-in cost |

**The default for cost providers is fail-open** — the opposite of use policies, which fail closed. The
asymmetry is deliberate. A third-party bug that makes every adaptation on the server free is recoverable and
loudly logged; a third-party bug that makes every adaptation on the server unusable is not. Admins who need
hard gating set `[abilityApi] costProviderFailureMode = "deny"`.

A deliberate `DENIED` or `INSUFFICIENT` quote always denies, whatever the failure mode says. The mode
governs **faults only**.

No value ever moves through this API. `AbilityQuote.withPrice(long amount, String unit)` attaches an
**optional** `OptionalLong amount()` and `String unit()` so Adapt can name a price in its own voice; it is
display-only, refused at construction if it is negative, and Adapt never reconstructs, inspects or does
arithmetic on what you actually charge. You own the movement of value end to end, and a quote without
`withPrice(…)` is perfectly valid.

All third-party text — quote descriptions, refusal reasons, event cancel reasons, receipt labels — is
trimmed of control characters and truncated to 128 characters before Adapt stores or logs it.

### Configuration

`plugins/Adapt/adapt/adapt.toml`, `[abilityApi]`:

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `true` | Master switch for the whole ability API. When false, no provider is called and neither event fires |
| `costProviderFailureMode` | `"allow"` | `allow`: a faulting provider is skipped and the activation proceeds. `deny`: a faulting provider refuses the activation |
| `usePolicyFailureMode` | `"deny"` | The use-policy equivalent. See [ability-policy.md](ability-policy.md#configuration) |
| `providerFaultLimit` | `5` | Quarantine trips on the Nth fault, so the default tolerates four. Clamped to `0`–`1000`; `0` disables quarantine |
| `slowProviderMillis` | `2` | Warn when one provider call takes at least this long. Clamped to `0`–`60000`; `0` disables the watchdog |
| `denyMessageThrottleMillis` | `2000` | Use-policy denial deduplication window. Does not affect the cost funnel |

Both failure-mode keys accept `deny`/`denied`/`closed`/`fail-closed` and `allow`/`allowed`/`open`/
`fail-open`, case-insensitively. An unrecognised value falls back to that key's default.

---

## Enum reference

### `AbilityCostKind`

| Constant | The built-in cost it replaces |
|----------|-------------------------------|
| `ITEM` | consuming `defaultAmount` of `defaultItem` from the player |
| `HUNGER` | draining `defaultAmount` hunger points |
| `HEALTH` | dealing `defaultAmount` damage to the player |
| `DURABILITY` | damaging the held or off-hand tool by `defaultAmount` |
| `VANILLA_EXPERIENCE` | taking `defaultAmount` vanilla experience |

### `AbilityQuoteStatus`

| Constant | Built-in cost taken | Activation proceeds |
|----------|---------------------|---------------------|
| `PASS` | yes | yes |
| `WAIVED` | no | yes |
| `PAYABLE` | no | yes, after `reserve` succeeds |
| `INSUFFICIENT` | no | no |
| `DENIED` | no | no |

### `AbilityReservationStatus`

| Constant | Meaning |
|----------|---------|
| `RESERVED` | Value taken. A receipt is required and is validated at construction |
| `FAILED` | Deliberate late refusal. Everything already reserved is rolled back |

### `AbilityRefundReason`

| Constant | When Adapt sends it | Needs an open ticket |
|----------|---------------------|----------------------|
| `CHARGE_ROLLBACK` | Another provider failed to reserve, or faulted while reserving. Nobody pays | no |
| `ADAPTATION_DISABLED` | A cost provider holding part of an open ticket was unregistered | yes |
| `EXPIRED` | An open ticket was never settled or refunded and aged past 30 seconds | yes |
| `SERVER_SHUTDOWN` | Adapt is shutting down with tickets still open | yes |
| `ACTIVATION_ABORTED` | The activation was called off before it resolved. Also the fallback when `refundCost` is called with no reason | yes |
| `ACTIVATION_FAILED` | The activation ran and failed | yes |
| `TARGET_LOST` | The activation's target went away | yes |
| `PLAYER_DIED` | The player died before the activation resolved | yes |
| `PLAYER_LEFT` | The player left before the activation resolved | yes |

`CHARGE_ROLLBACK` is **not** sent when the built-in cost turns out to be unaffordable. That branch is only
reachable with nothing reserved — see [the lifecycle](#the-lifecycle) — so there is never anything to give
back.

**Only `CHARGE_ROLLBACK` is reachable with the catalogue as it ships.** A ticket is opened only by the
deferred form of the funnel, and no shipped adaptation uses it, so on a stock server the other eight
constants never arrive. They are the contract for a future deferred charge, not something to design a
refund path around today. Handle all nine the same way — give the value back — and do not branch on the
reason to decide *whether* to refund.

### `AbilityOutcome`

Carried by `AbilityCharge`. `allowed()` answers the only question most consumers have.

| Constant | `allowed()` | Meaning | Visible on `AdaptAbilityActivatedEvent` |
|----------|-------------|---------|------------------------------------------|
| `ALLOWED_DEFAULT` | `true` | Nobody waived or charged; Adapt took its own cost | yes |
| `ALLOWED_WAIVED` | `true` | A provider waived; nothing was taken | yes |
| `ALLOWED_CHARGED` | `true` | At least one provider reserved and committed | yes |
| `ALLOWED_PROVIDER_FAILED` | `true` | A provider faulted under `allow` mode; the activation went ahead | yes |
| `DISABLED` | `true` | The ability API is switched off; the built-in cost was taken | no |
| `DENIED_BY_LISTENER` | `false` | `AdaptAbilityActivateEvent` was cancelled | no |
| `DENIED_BY_PROVIDER` | `false` | A provider quoted `DENIED` | no |
| `DENIED_INSUFFICIENT` | `false` | A provider quoted `INSUFFICIENT`, a `reserve` refused, or the built-in cost was unaffordable | no |
| `DENIED_PROVIDER_FAILED` | `false` | A provider faulted under `deny` mode | no |
| `DENIED_REENTRANT` | `false` | A provider re-entered the cost path on the same thread | no |

### `AbilityCharge`

```java
public record AbilityCharge(UUID activationId, AbilityOutcome outcome, boolean defaultCostSuppressed,
                            String reason, String providerId, List<String> chargedProviderIds)
```

| Component               | What it is                                                              |
|-------------------------|--------------------------------------------------------------------------|
| `activationId`          | The activation this settles                                              |
| `outcome`               | The final verdict. `allowed()` is the shorthand                          |
| `defaultCostSuppressed` | `true` when no built-in cost was taken                                   |
| `reason`                | The deciding provider's text, sanitised. Empty when allowed              |
| `providerId`            | The deciding or faulting provider. Empty when nobody decided             |
| `chargedProviderIds`    | Every provider that reserved, in charge order. Immutable                 |

All enums here may gain constants. Write a `default` arm — see
[Switching over the enums](README.md#switching-over-the-enums).
