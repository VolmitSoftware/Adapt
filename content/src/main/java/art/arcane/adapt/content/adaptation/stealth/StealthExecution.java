package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

/** Assassinate's killing blow: the strike deals the target's remaining health through every reduction. */
public final class StealthExecution {
  private static final Id WARDEN = Id.minecraft("warden");

  private StealthExecution() {
  }

  /** Mobs that may be executed at all: not the attacker, not players, bosses or wardens. */
  public static boolean isExecutable(PlayerRef attacker, LivingRef target) {
    return !target.uuid().equals(attacker.uuid()) && !(target instanceof PlayerRef) && !target.has(EntityTrait.BOSS)
        && !target.type().equals(WARDEN);
  }

  public static double maxHealth(LivingRef entity) {
    return entity.hasAttribute(Attributes.MAX_HEALTH) ? entity.attributeValue(Attributes.MAX_HEALTH) : entity.health();
  }

  static double executionDamage(double currentHealth) {
    return Math.max(0D, currentHealth);
  }

  public static void applyExecutionDamage(EntityDamageByEntityEvent event, double currentHealth) {
    event.setDamage(executionDamage(currentHealth));
    event.setIgnoreReductions(true);
  }
}
