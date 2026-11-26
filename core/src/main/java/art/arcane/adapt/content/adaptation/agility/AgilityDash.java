package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;

/** The mid-air dash: a flat horizontal throw along the look direction with a little lift. */
public final class AgilityDash {
  private AgilityDash() {
  }

  /** Airborne on foot in survival or adventure, not flying, gliding, swimming or climbing. */
  public static boolean eligible(PlayerRef p) {
    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isOnGround()
        && !p.isFlying()
        && !p.isGliding()
        && !p.isSwimming()
        && !p.isClimbing()
        && p.vehicle() == null;
  }

  /** The horizontal look direction, or null when looking straight up or down. */
  public static Vec3 direction(PlayerRef p) {
    Vec3 direction = p.location().direction().withY(0);
    return direction.lengthSquared() <= 1.0E-4D ? null : direction.normalize();
  }

  public static void launch(Adaptation<?> owner, PlayerRef p, Vec3 direction, double force, double upwardLift) {
    Vec3 current = p.velocity();
    double liftedY = Math.max(current.y(), -0.15D) + upwardLift;
    p.setVelocity(new Vec3(direction.x() * force, liftedY, direction.z() * force));
    p.setFallDistance(0F);

    AgilityAirDash.dashFx(owner, p, direction);
  }
}
