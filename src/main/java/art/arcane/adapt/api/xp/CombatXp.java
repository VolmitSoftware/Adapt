package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.content.adaptation.excavation.ExcavationGraveDigger;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class CombatXp {
  private CombatXp() {
  }

  public static double credit(CombatXpLedger ledger, String skill, Player player, EntityDamageByEntityEvent event) {
    if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity victim)
        || !J.isOwnedByCurrentRegion(victim) || victim.isDead() || victim.isInvulnerable()
        || victim.getUniqueId().equals(player.getUniqueId())
        || MinionIdentity.isMinion(victim) || ExcavationGraveDigger.isGraveMob(victim)
        || victim instanceof Tameable pet && pet.getOwner() != null
        && pet.getOwner().getUniqueId().equals(player.getUniqueId())) {
      return 0D;
    }
    AttributeInstance health = victim.getAttribute(Attribute.MAX_HEALTH);
    double budget = health == null ? victim.getHealth() : health.getValue();
    return ledger.claim(skill, victim.getUniqueId(), effectiveDamage(event.getFinalDamage(), victim.getHealth()),
        budget, System.currentTimeMillis());
  }

  public static double effectiveDamage(double finalDamage, double remainingHealth) {
    if (!Double.isFinite(finalDamage) || !Double.isFinite(remainingHealth)) {
      return 0D;
    }
    return Math.max(0D, Math.min(finalDamage, remainingHealth));
  }

  public static double creditDefense(CombatXpLedger ledger, String skill, Player player,
                                    EntityDamageByEntityEvent event, double damage) {
    Entity attacker = event.getDamager();
    if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
      attacker = shooter;
    }
    if (event.isCancelled() || attacker.getUniqueId().equals(player.getUniqueId())) {
      return 0D;
    }
    AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
    double budget = health == null ? player.getHealth() : health.getValue();
    return ledger.claim(skill, attacker.getUniqueId(), damage, budget, System.currentTimeMillis());
  }
}
