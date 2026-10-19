package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.minion.MinionTargeting;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class GolemProtection {
  private GolemProtection() {
  }

  static boolean canDamage(Player owner, LivingEntity victim) {
    return canDamage(GolemancyRuntime.protectionPolicy().orElse(null), owner, victim);
  }

  static boolean canDamage(Adaptation<?> policy, Player owner, LivingEntity victim) {
    if (policy == null) {
      return false;
    }
    MinionTargeting.Candidate candidate = MinionTargeting.captureOwned(owner.getUniqueId(), victim);
    return candidate != null && MinionTargeting.canDamageOwned(policy, owner, candidate);
  }
}
