package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.ability.AbilityDamage;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Deals damage back to an attacker on the attacker's thread, then reports on the defender's thread. The damage it
 * deals is keyed so the owner can skip its own retaliation when that hit comes back through the damage event.
 */
public final class BlockingRetaliation {
  private final Adaptation<?> owner;
  private final Set<ReflectionKey> pendingReflections = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean acceptingReflections = new AtomicBoolean(true);

  public BlockingRetaliation(Adaptation<?> owner) {
    this.owner = owner;
  }

  public void activate() {
    acceptingReflections.set(true);
  }

  public void close() {
    acceptingReflections.set(false);
    pendingReflections.clear();
  }

  public boolean accepting() {
    return acceptingReflections.get();
  }

  /** True, once, when this hit is damage this retaliation is dealing right now. */
  public boolean consume(EntityRef damager, EntityRef damaged) {
    return pendingReflections.remove(new ReflectionKey(damager.uuid(), damaged.uuid()));
  }

  /**
   * Schedules {@code damage} onto the attacker. {@code onFailed} runs whenever the strike is dropped after scheduling;
   * {@code onLanded} runs on the defender's thread with the attacker's location. False when nothing was scheduled.
   */
  public boolean strike(PlayerRef defender, LivingRef attacker, double damage, Runnable onFailed, Consumer<Loc> onLanded) {
    return AdaptApi.scheduler().runEntity(attacker, () -> applyOnAttacker(defender, attacker, damage, onFailed, onLanded));
  }

  private void applyOnAttacker(PlayerRef defender, LivingRef attacker, double damage, Runnable onFailed,
      Consumer<Loc> onLanded) {
    if (!acceptingReflections.get() || !attacker.isValid() || attacker.isDead()
        || !owner.canDamageTarget(defender, attacker)) {
      onFailed.run();
      return;
    }

    ReflectionKey key = new ReflectionKey(defender.uuid(), attacker.uuid());
    pendingReflections.add(key);
    try {
      AbilityDamage.deal(attacker, damage, defender);
    } catch (Throwable error) {
      AdaptApi.log().error("Counter Guard could not reflect damage from "
          + defender.uuid() + " to " + attacker.uuid() + ".", null);
      AdaptApi.log().error("BlockingRetaliation failed", error);
      onFailed.run();
      return;
    } finally {
      pendingReflections.remove(key);
    }

    Loc attackerLocation = attacker.location();
    if (!AdaptApi.scheduler().runEntity(defender, () -> onLanded.accept(attackerLocation))) {
      onFailed.run();
    }
  }

  record ReflectionKey(UUID defenderId, UUID attackerId) {
  }
}
