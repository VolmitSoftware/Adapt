package art.arcane.adapt.api.xp;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.UUID;

public final class CombatXp {
  private static final DataKey<Byte> GRAVE_MOB_KEY = DataKey.adapt("excavation_grave_mob", DataCodecs.BYTE);

  private CombatXp() {
  }

  public static double credit(CombatXpLedger ledger, String skill, PlayerRef player, EntityDamageByEntityEvent event) {
    if (event.isCancelled() || !(event.entity() instanceof LivingRef victim)
        || !Tasks.isOwnedByCurrentRegion(victim) || victim.isDead() || victim.isInvulnerable()
        || victim.uuid().equals(player.uuid())
        || MinionIdentity.isMinion(victim) || isGraveMob(victim)
        || ownedBy(victim, player)) {
      return 0D;
    }
    return ledger.claim(skill, victim.uuid(), effectiveDamage(event.finalDamage(), victim.health()),
        victim.maxHealth(), System.currentTimeMillis());
  }

  public static double effectiveDamage(double finalDamage, double remainingHealth) {
    if (!Double.isFinite(finalDamage) || !Double.isFinite(remainingHealth)) {
      return 0D;
    }
    return Math.max(0D, Math.min(finalDamage, remainingHealth));
  }

  public static double creditDefense(CombatXpLedger ledger, String skill, PlayerRef player,
                                    EntityDamageByEntityEvent event, double damage) {
    EntityRef attacker = event.damager();
    if (attacker instanceof ProjectileRef projectile && projectile.shooter() != null) {
      attacker = projectile.shooter();
    }
    if (event.isCancelled() || attacker.uuid().equals(player.uuid())) {
      return 0D;
    }
    return ledger.claim(skill, attacker.uuid(), damage, player.maxHealth(), System.currentTimeMillis());
  }

  public static boolean isGraveMob(EntityRef entity) {
    return entity.data().has(GRAVE_MOB_KEY);
  }

  private static boolean ownedBy(EntityRef victim, PlayerRef player) {
    UUID owner = victim.tameOwner();
    return owner != null && owner.equals(player.uuid());
  }
}
