package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

/** The lava stride Lava Walker runs: a push across the lava surface paid for with hunger. */
public final class NetherLavaStride {
  private static final Id LAVA = Id.minecraft("lava");
  private static final Id FIRE_RESISTANCE = Id.minecraft("fire_resistance");

  private NetherLavaStride() {
  }

  /** Whether the player can be pushed at all: on foot, not flying, and with food left. */
  public static boolean canStride(PlayerRef p) {
    return !p.isFlying() && !p.isGliding() && p.vehicle() == null && p.foodLevel() > 0;
  }

  public static boolean onLava(PlayerRef p) {
    Loc at = p.location();
    return p.world().blockAt(at).type().equals(LAVA) || p.world().blockAt(at.add(0, -1, 0)).type().equals(LAVA);
  }

  /** Pushes the player along their look direction, shields them from the lava and takes the hunger cost. */
  public static void stride(Adaptation<?> owner, PlayerRef p, double stride, int hungerCost, int fireResistTicks) {
    Vec3 velocity = p.velocity();
    Vec3 dir = horizontalStride(p.location().yaw(), stride);
    p.setVelocity(new Vec3(dir.x(), Math.max(0.16, velocity.y()), dir.z()));
    p.setFallDistance(0);
    p.setFireTicks(0);
    boolean hadFireResistance = p.hasEffect(FIRE_RESISTANCE);
    p.addEffect(new EffectInstance(FIRE_RESISTANCE, fireResistTicks, 0, false, false, false));
    p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));
    NetherLavaWalker.strideFx(owner, p, dir, hadFireResistance);
  }

  /** The facing flattened to the ground and scaled to the stride; looking straight up or down keeps the facing. */
  private static Vec3 horizontalStride(float yaw, double stride) {
    Vec3 facing = Vec3.fromRotation(yaw, 0F);
    return new Vec3(facing.x() * stride, 0D, facing.z() * stride);
  }
}
