package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;

/** Hydro Jet's burst along the swimmer's view and its hunger reserve check. */
public final class SeaborneJet {
  private static final double MAX_RESULTING_VELOCITY = 2.6;

  private SeaborneJet() {
  }

  /** Pushes the player along their view and returns that direction. */
  public static Vec3 launch(PlayerRef player, double force, double hungerCost) {
    Vec3 direction = player.eyeLocation().direction().normalize();
    Vec3 velocity = player.velocity().multiply(0.4D).add(direction.multiply(force));
    if (velocity.lengthSquared() > MAX_RESULTING_VELOCITY * MAX_RESULTING_VELOCITY) {
      velocity = velocity.normalize().multiply(MAX_RESULTING_VELOCITY);
    }
    player.setVelocity(velocity);
    player.setExhaustion(player.exhaustion() + (float) hungerCost);
    return direction;
  }

  public static boolean respectsFoodReserve(int food, float saturation, float exhaustion, double cost, int reserve) {
    if (food <= 0) {
      return false;
    }
    if (reserve <= 0) {
      return true;
    }
    int exhaustionUnits = (int) Math.ceil(Math.max(0D, exhaustion + cost) / 4D);
    int saturationUnits = (int) Math.ceil(Math.max(0D, saturation));
    return food - Math.max(0, exhaustionUnits - saturationUnits) >= reserve;
  }
}
