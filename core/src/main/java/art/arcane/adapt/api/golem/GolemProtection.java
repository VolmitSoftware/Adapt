package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;

final class GolemProtection {
  private GolemProtection() {
  }

  static boolean canDamage(PlayerRef owner, LivingRef victim) {
    return canDamage(GolemancyRuntime.protectionPolicy().orElse(null), owner, victim);
  }

  static boolean canDamage(Adaptation<?> policy, PlayerRef owner, LivingRef victim) {
    if (policy == null) {
      return false;
    }
    MinionTargeting.Candidate candidate = MinionTargeting.captureOwned(owner.uuid(), victim);
    return candidate != null && MinionTargeting.canDamageOwned(policy, owner, candidate);
  }
}
