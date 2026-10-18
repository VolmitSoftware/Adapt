package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.minion.ServantOrder;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityTargetEvent;

final class ServantPulse {
  static final double FOLLOW_DISTANCE = 4D;
  static final double ANCHOR_TOLERANCE = 1.5D;
  static final double PATH_SPEED = 1D;

  private ServantPulse() {
  }

  static Step step(ServantOrder order, Senses senses) {
    if (senses.commandHolds()) {
      return Step.ATTACK;
    }
    return switch (order.mode()) {
      case FREE -> Step.HUNT;
      case FOLLOW -> follows(senses) ? Step.FOLLOW : Step.HUNT;
      case HOLD -> !senses.fighting() && senses.anchorDistanceSquared() > ANCHOR_TOLERANCE * ANCHOR_TOLERANCE ? Step.RETURN : Step.STAND;
    };
  }

  static boolean takesPriority(ServantOrder order) {
    return !order.commanded() && order.mode() != ServantOrder.Mode.HOLD;
  }

  static TargetGate gate(ServantOrder order, EntityTargetEvent.TargetReason reason, LivingEntity target, long now) {
    if (commanded(order, target, now)) {
      return TargetGate.COMMANDED;
    }
    return order.mode() != ServantOrder.Mode.HOLD || reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY ? TargetGate.USUAL : TargetGate.REFUSED;
  }

  static boolean commanded(ServantOrder order, LivingEntity target, long now) {
    return target != null && order.target() == target && order.commandHolds(now, true);
  }

  private static boolean follows(Senses senses) {
    double owner = senses.ownerDistanceSquared();
    double leash = senses.leashRadius();
    return owner > leash * leash || (!senses.fighting() && owner > FOLLOW_DISTANCE * FOLLOW_DISTANCE);
  }

  enum TargetGate {
    COMMANDED,
    REFUSED,
    USUAL
  }

  enum Step {
    HUNT,
    ATTACK,
    FOLLOW,
    RETURN,
    STAND
  }

  record Senses(boolean commandHolds, double ownerDistanceSquared, double anchorDistanceSquared, boolean fighting, double leashRadius) {
  }
}
