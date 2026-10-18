package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.protection.ProtectorRegistry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class GolemProtection {
  private GolemProtection() {
  }

  static boolean canDamage(Player owner, LivingEntity victim) {
    MinionTargeting.Candidate candidate = MinionTargeting.captureOwned(owner.getUniqueId(), victim);
    if (candidate == null || candidate.protectedFriendly()) {
      return false;
    }
    Adapt plugin = Adapt.instance;
    ProtectorRegistry registry = plugin == null ? null : plugin.getProtectorRegistry();
    if (registry == null) {
      return true;
    }
    for (Protector protector : registry.getDefaultProtectors()) {
      boolean allowed = candidate.player()
          ? protector.canPVP(owner, candidate.location(), null)
          : protector.canPVE(owner, candidate.location(), null);
      if (!allowed) {
        return false;
      }
    }
    return true;
  }
}
