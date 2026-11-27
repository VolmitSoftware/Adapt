package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

/** Recovering from a hit: a push toward where the player is heading and a short movement speed burst. */
public final class AgilityKip {
  private static final String SPEED_SLOT = "speed";

  private AgilityKip() {
  }

  /** Survival or adventure, on foot and not flying, gliding or swimming. */
  public static boolean eligible(PlayerRef p) {
    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isFlying() && !p.isGliding() && !p.isSwimming() && p.vehicle() == null;
  }

  /** The horizontal direction of this move, else the look direction, else null. */
  public static Vec3 intendedDirection(PlayerRef p, PlayerMoveEvent e) {
    Loc to = e.to();
    Vec3 move = to == null ? null : new Vec3(to.x() - e.from().x(), 0D, to.z() - e.from().z());
    if (move != null && move.lengthSquared() > 1.0E-4D) {
      return move.normalize();
    }

    Vec3 look = p.location().direction().withY(0);
    if (look.lengthSquared() > 1.0E-4D) {
      return look.normalize();
    }

    return null;
  }

  /** Sets the horizontal velocity along {@code direction} and plays the recovery effect. */
  public static void push(Adaptation<?> owner, PlayerRef p, Vec3 direction, double magnitude) {
    Vec3 current = p.velocity();
    p.setVelocity(new Vec3(direction.x() * magnitude, current.y(), direction.z() * magnitude));
    AgilityKipUp.recoveryFx(owner, p, direction);
  }

  public static void speedBurst(Adaptation<?> owner, PlayerRef p, int amplifier, int durationTicks) {
    AdaptAttributeService.get().applyTimed(p, owner.getId().toString(), SPEED_SLOT, Attributes.MOVEMENT_SPEED,
        speedBonus(amplifier), AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
  }

  public static void clearSpeedBurst(Adaptation<?> owner, PlayerRef p) {
    AdaptAttributeService.get().remove(p, owner.getId().toString(), SPEED_SLOT, Attributes.MOVEMENT_SPEED);
  }

  static double speedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }
}
