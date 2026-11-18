package art.arcane.adapt.api.golem;

import java.util.UUID;
import java.util.function.BooleanSupplier;

final class GolemFriendlyFire {
  private GolemFriendlyFire() {
  }

  static boolean cancels(Party damager, Party victim, BooleanSupplier golemMayHitPlayer) {
    if (damager.golem()) {
      return golemHitCancels(damager, victim, golemMayHitPlayer);
    }
    if (!victim.golem() || victim.owner() == null) {
      return false;
    }
    if (damager.player()) {
      return victim.owner().equals(damager.entityId());
    }
    return victim.owner().equals(damager.owner());
  }

  private static boolean golemHitCancels(Party golem, Party victim, BooleanSupplier golemMayHitPlayer) {
    UUID owner = golem.owner();
    if (owner != null && (owner.equals(victim.entityId()) || owner.equals(victim.owner()))) {
      return true;
    }
    return victim.player() && !golemMayHitPlayer.getAsBoolean();
  }

  record Party(UUID entityId, boolean player, boolean golem, UUID owner) {
  }
}
