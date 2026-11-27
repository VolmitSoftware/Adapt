package art.arcane.adapt.api.fx;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.ApiStatus;

/** Ready-made effects with Adapt's look: charge, impact, success, failure, ready and progression cues. */
@ApiStatus.Experimental
public final class FxPresets {
  private FxPresets() {
  }

  /** A dust ring that shrinks onto {@code location} over {@code durationTicks}, at least 4, with a rising tick. */
  public static void chargeRing(ContentComponent owner, Loc location, int durationTicks) {
    FxTimeline.at(owner, location)
        .duration(Math.max(4, durationTicks))
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.dustRing(1.8D - (1.5D * progress), 16, 0.9F);
          if ((tick & 3) == 0) {
            fx.sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.5F, (float) (0.7D + (progress * 0.9D)));
          }
        })
        .start();
  }

  /** A dust ring that grows out to {@code radius}, at least 0.5, over 8 ticks, with a low chord. */
  public static void shockwave(ContentComponent owner, Loc location, double radius) {
    double maxRadius = Math.max(0.5D, radius);
    FxTimeline.at(owner, location)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.dustRing(0.3D + ((maxRadius - 0.3D) * progress), 24, 1.1F);
          if (tick == 0) {
            fx.chord(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.6F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 0.7F);
          }
        })
        .start();
  }

  /** A hit spark of crits and dust; {@code intensity} 1 is a normal hit and scales the particle count. */
  public static void impact(ContentComponent owner, Loc location, double intensity) {
    int count = (int) Math.max(4, Math.min(24, Math.round(8.0D * intensity)));
    Fx.now(owner, location, FxPriority.COMBAT)
        .burst(Particles.CRIT, count, 0.4D)
        .dustBurst(Math.max(3, count / 2), 0.5D, 1.0F)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 0.9F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
  }

  /** A small column of sparkles and a high chime, for something that worked. */
  public static void successShimmer(ContentComponent owner, Loc location) {
    Fx.now(owner, location, FxPriority.AMBIENT)
        .column(Particles.END_ROD, 5, 1.2D)
        .particle(Particles.VILLAGER_HAPPY, 4, 0, 0.8D, 0, 0.3D, 0)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.8F);
  }

  /** A puff of smoke and a dull chord, for something that failed. */
  public static void failFizzle(ContentComponent owner, Loc location) {
    Fx.now(owner, location, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 8, 0.25D)
        .chord(Sounds.BLOCK_FIRE_EXTINGUISH, 0.4F, 1.2F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
  }

  /** The fizzle at the player's location; does nothing for null. */
  public static void failFizzle(ContentComponent owner, PlayerRef player) {
    if (player == null) {
      return;
    }
    failFizzle(owner, player.location());
  }

  /** A short firework trail behind a moving entity, opposite its velocity; does nothing for null. */
  public static void streakTrail(ContentComponent owner, EntityRef entity) {
    if (entity == null) {
      return;
    }
    Vec3 velocity = entity.velocity();
    streakTrail(owner, entity.location(), -velocity.x(), -velocity.y(), -velocity.z(), 1.6D);
  }

  /** A firework trail from {@code location} along the direction, {@code length} blocks long. */
  public static void streakTrail(ContentComponent owner, Loc location, double dirX, double dirY, double dirZ, double length) {
    Fx.now(owner, location, FxPriority.TRAIL)
        .trail(Particles.FIREWORK, dirX, dirY, dirZ, length, 6);
  }

  /** A small dust ring and a tick at the player, for an ability that is ready again; does nothing for null. */
  public static void readyPing(ContentComponent owner, PlayerRef player) {
    if (player == null) {
      return;
    }
    Fx.now(owner, player.location(), FxPriority.TRANSITION)
        .dustRing(0.7D, 12, 0.8F)
        .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.6F, 1.6F);
  }

  /**
   * A dust spiral that follows the player for 18 ticks, with a level-up chord that rises with {@code level} when
   * {@link art.arcane.adapt.api.AdaptSettings#progressionSounds()} allows. Does nothing for null.
   */
  public static void levelUpBurst(ContentComponent owner, PlayerRef player, int level) {
    if (player == null) {
      return;
    }
    float pitch = (float) Math.min(1.8D, 1.1D + (level * 0.01D));
    FxTimeline.follow(owner, player)
        .duration(18)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.dustHelix(0.9D, 2.2D, 12, progress * Math.PI * 2.0D, 1.0F);
          if (tick == 0 && AdaptApi.settings().progressionSounds()) {
            fx.chord(Sounds.ENTITY_PLAYER_LEVELUP, 0.6F, pitch, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.45F);
          }
        })
        .start();
  }

  /** A closing dome and ring of particles that follow the player for 14 ticks, with a chime. Does nothing for null. */
  public static void learnCelebration(ContentComponent owner, PlayerRef player) {
    if (player == null) {
      return;
    }
    FxTimeline.follow(owner, player)
        .duration(14)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.dome(Particles.ENCHANT, 2.4D - (2.1D * progress), 24);
          fx.dustRing(1.6D - (1.3D * progress), 12, 0.8F);
          if (tick == 0) {
            fx.chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 0.9F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
          }
        })
        .start();
  }
}
