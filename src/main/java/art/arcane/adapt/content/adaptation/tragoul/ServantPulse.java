package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.minion.ServantOrder;
import org.bukkit.event.entity.EntityTargetEvent;

final class ServantPulse {
  static final double FOLLOW_DISTANCE = 4D;
  static final double ANCHOR_TOLERANCE = 1.5D;
  static final double PATH_SPEED = 1D;

  private ServantPulse() {
  }

  static Step step(ServantOrder order, boolean commandHolds, double ownerDistanceSquared, double anchorDistanceSquared, boolean fighting) {
    if (commandHolds) {
      return Step.ATTACK;
    }
    return switch (order.mode()) {
      case FREE -> Step.HUNT;
      case FOLLOW -> ownerDistanceSquared > FOLLOW_DISTANCE * FOLLOW_DISTANCE ? Step.FOLLOW : Step.HUNT;
      case HOLD -> !fighting && anchorDistanceSquared > ANCHOR_TOLERANCE * ANCHOR_TOLERANCE ? Step.RETURN : Step.STAND;
    };
  }

  static boolean takesPriority(ServantOrder order) {
    return !order.commanded() && order.mode() != ServantOrder.Mode.HOLD;
  }

  static boolean allowsTarget(ServantOrder order, EntityTargetEvent.TargetReason reason) {
    return order.mode() != ServantOrder.Mode.HOLD || reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY;
  }

  enum Step {
    HUNT,
    ATTACK,
    FOLLOW,
    RETURN,
    STAND
  }
}
