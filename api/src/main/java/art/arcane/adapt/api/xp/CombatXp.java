package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * Combat XP that pays each point of a mob's health once per player and skill, so hitting the same mob again or
 * healing it does not farm XP.
 */
@ApiStatus.Experimental
public final class CombatXp {
  private CombatXp() {
  }

  /**
   * The damage of {@code event} the player may still earn XP for in {@code skill}, capped by the victim's remaining
   * health and recorded; 0 for a cancelled hit, a farmed or dead victim, or the player hitting themself.
   */
  public static double credit(String skill, PlayerRef player, EntityDamageByEntityEvent event) {
    return Engine.bridge().world().creditCombat(skill, player, event);
  }

  /** As {@link #credit}, for {@code damage} the player took from the attacker of {@code event}. */
  public static double creditDefense(String skill, PlayerRef player, EntityDamageByEntityEvent event, double damage) {
    return Engine.bridge().world().creditDefense(skill, player, event, damage);
  }

  /**
   * Records {@code damage} against {@code victim} in the player's {@code ledger}, up to {@code healthBudget} per victim
   * within 10 minutes, and returns what was newly recorded; 0 without loaded player data. For XP that a skill pays
   * once per mob, such as a first strike, under a ledger name of its own.
   */
  public static double claim(String ledger, PlayerRef player, UUID victim, double damage, double healthBudget) {
    return Engine.bridge().progress().claimCombat(Objects.requireNonNull(ledger, "ledger"),
        Objects.requireNonNull(player, "player"), Objects.requireNonNull(victim, "victim"), damage, healthBudget);
  }

  /** {@code finalDamage} capped by {@code remainingHealth}; 0 for a non-finite input. */
  public static double effectiveDamage(double finalDamage, double remainingHealth) {
    if (!Double.isFinite(finalDamage) || !Double.isFinite(remainingHealth)) {
      return 0D;
    }
    return Math.max(0D, Math.min(finalDamage, remainingHealth));
  }

  /** True for a mob raised from a grave, which pays no combat XP. */
  public static boolean isGraveMob(EntityRef entity) {
    return Engine.bridge().world().isGraveMob(entity);
  }
}
