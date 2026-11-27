package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;

/**
 * Swimming through lava: clear sight while in it, a push to water speed along the way the player moves, and dropped
 * items that float instead of burning for a while.
 */
public final class NetherLavaSwim {
  private static final Id LAVA = Id.minecraft("lava");
  private static final Id FIRE_RESISTANCE = Id.minecraft("fire_resistance");
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");
  private static final int FIRE_RESISTANCE_TICKS = 60;
  public static final int SIGHT_TICKS = 300;
  private static final int FLOAT_STEP_TICKS = 2;
  private static final double FLOAT_LIFT = 0.08D;
  private static final double STEEP_PITCH_DEGREES = 25D;
  private static final double MOVING = 0.01D;

  private NetherLavaSwim() {
  }

  /** Whether the player's feet or eyes are in lava. */
  public static boolean inLava(PlayerRef p) {
    return isLava(p.world().blockAt(p.location())) || eyesInLava(p);
  }

  public static boolean eyesInLava(PlayerRef p) {
    return isLava(p.world().blockAt(p.eyeLocation()));
  }

  /** Whether lava lies within {@code radius} blocks of the player's feet. */
  public static boolean lavaNear(PlayerRef p, int radius) {
    BlockRef feet = p.world().blockAt(p.location());
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dz = -radius; dz <= radius; dz++) {
          if (isLava(feet.relative(dx, dy, dz))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Fire Resistance and Night Vision without particles, so lava neither hurts nor blinds; a longer effect the player
   * already has is left alone. Returns whether Night Vision was granted.
   */
  public static boolean shield(PlayerRef p) {
    grantShorter(p, FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS);
    return grantShorter(p, NIGHT_VISION, SIGHT_TICKS);
  }

  /** Removes the Night Vision {@link #shield} granted, never one the player got elsewhere. */
  public static void clearSight(PlayerRef p) {
    EffectInstance sight = p.effect(NIGHT_VISION);
    if (sight != null && shielding(sight) && sight.durationTicks() >= 0 && sight.durationTicks() <= SIGHT_TICKS) {
      p.removeEffect(NIGHT_VISION);
    }
  }

  /**
   * The velocity that carries a player moving from {@code from} to {@code to} at {@code speed} blocks per tick along
   * the way they move, rising or diving with a steep look; null when they are not moving.
   */
  public static Vec3 swimVelocity(Loc from, Loc to, Vec3 current, double speed) {
    double dx = to.x() - from.x();
    double dz = to.z() - from.z();
    double length = Math.sqrt((dx * dx) + (dz * dz));
    if (length < MOVING) {
      return null;
    }
    double pitch = to.pitch();
    double y = Math.abs(pitch) >= STEEP_PITCH_DEGREES ? -Math.sin(Math.toRadians(pitch)) * speed : current.y();
    return new Vec3((dx / length) * speed, y, (dz / length) * speed);
  }

  /**
   * Lifts a dropped item while it sits in lava for {@code ticks}, then runs {@code done}, also when the item unloads or
   * is gone first. Keeping it from burning is the caller's: nothing is stored on the item, so nothing outlives a restart.
   */
  public static void buoy(EntityRef item, int ticks, Runnable done) {
    if (!item.isValid() || ticks <= 0) {
      done.run();
      return;
    }
    if (item.isInLava()) {
      Vec3 velocity = item.velocity();
      item.setVelocity(new Vec3(velocity.x() * 0.5D, Math.max(velocity.y(), FLOAT_LIFT), velocity.z() * 0.5D));
    }
    if (!AdaptApi.scheduler().runEntity(item, () -> buoy(item, ticks - FLOAT_STEP_TICKS, done), FLOAT_STEP_TICKS)) {
      done.run();
    }
  }

  private static boolean grantShorter(PlayerRef p, Id effect, int ticks) {
    EffectInstance current = p.effect(effect);
    if (current != null && (current.durationTicks() < 0 || current.durationTicks() > ticks)) {
      return false;
    }
    p.addEffect(EffectInstance.of(effect, ticks, 0).withAmbient(true).withParticles(false).withIcon(false));
    return true;
  }

  /** Whether {@code effect} carries the flags {@link #grantShorter} sets, which potions, beacons and stews never do. */
  private static boolean shielding(EffectInstance effect) {
    return effect.ambient() && !effect.particles() && !effect.icon();
  }

  private static boolean isLava(BlockRef block) {
    return block.type().equals(LAVA);
  }
}
