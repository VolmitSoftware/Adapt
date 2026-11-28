package art.arcane.adapt.api.ability;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Damage an ability deals in a player's name, such as a shockwave or a thrown block. The server reports it as that
 * player's attack, so protection, kill credit and combat XP treat it as theirs, but no adaptation reads it as a melee
 * hit: melee upgrades, on-hit procs and attack contexts skip it. Call on the target's thread.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class AbilityDamage {
  private AbilityDamage() {
  }

  /** Deals {@code amount} damage to {@code target}, attributed to {@code source}. Needs a running core. */
  public static void deal(LivingRef target, double amount, EntityRef source) {
    Engine.bridge().combat().dealAbilityDamage(Objects.requireNonNull(target, "target"), amount,
        Objects.requireNonNull(source, "source"));
  }

  /**
   * Turns a melee hit into an ability's fixed strike of {@code amount}: the hit's damage is replaced, the victim's
   * armor and protection still apply, and from here on no adaptation reads the hit as a melee hit, so nothing adds to
   * or multiplies it. Call it from a {@code LOWEST} handler so every later priority sees the hit as fixed.
   */
  public static void fix(EntityDamageByEntityEvent hit, double amount) {
    Objects.requireNonNull(hit, "hit");
    hit.setDamage(amount);
    hit.setIgnoreReductions(false);
    Engine.bridge().combat().fixHit(hit.dispatchId());
  }

  /**
   * True once an ability fixed {@code hit}, for a damage handler outside the attack contexts that must not add to or
   * scale it.
   */
  public static boolean fixed(EntityDamageByEntityEvent hit) {
    return Engine.bridge().combat().hitFixed(Objects.requireNonNull(hit, "hit").dispatchId());
  }

  /** True on the thread that is dealing ability damage right now, for a damage handler that must skip it. */
  public static boolean dealing() {
    return Engine.bridge().combat().dealingAbilityDamage();
  }
}
