package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Vec3;

import java.util.concurrent.ThreadLocalRandom;
/** The sidestep out of a projectile's path once the hit has been cancelled. */
public final class AgilityDodge {
  private AgilityDodge() {
  }

  public static void sidestep(Adaptation<?> owner, PlayerRef p, ProjectileRef projectile) {
    Vec3 look = p.location().direction().withY(0);
    if (look.lengthSquared() > 1.0E-4D) {
      look = look.normalize();
      Vec3 side = new Vec3(-look.z(), 0, look.x()).multiply((ThreadLocalRandom.current().nextDouble() < 0.5D) ? 0.24D : -0.24D);
      p.setVelocity(p.velocity().add(side));
    }

    AgilityCatReflexes.sidestepFx(owner, projectile);
  }
}
