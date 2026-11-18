package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

public final class FxPresets {
  private FxPresets() {
  }

  public static void chargeRing(Adaptation<?> adaptation, Loc location, int durationTicks) {
    FxTimeline.at(adaptation, location)
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

  public static void shockwave(Adaptation<?> adaptation, Loc location, double radius) {
    double maxRadius = Math.max(0.5D, radius);
    FxTimeline.at(adaptation, location)
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

  public static void impact(Adaptation<?> adaptation, Loc location, double intensity) {
    int count = (int) Math.max(4, Math.min(24, Math.round(8.0D * intensity)));
    Fx.now(adaptation, location, FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, count, 0.4D)
        .dustBurst(Math.max(3, count / 2), 0.5D, 1.0F)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 0.9F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
  }

  public static void successShimmer(Adaptation<?> adaptation, Loc location) {
    Fx.now(adaptation, location, FxPriority.AMBIENT)
        .column(Particles.END_ROD, 5, 1.2D)
        .particle(Particles.VILLAGER_HAPPY, 4, 0, 0.8D, 0, 0.3D, 0)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.8F);
  }

  public static void failFizzle(Adaptation<?> adaptation, Loc location) {
    Fx.now(adaptation, location, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 8, 0.25D)
        .chord(Sounds.BLOCK_FIRE_EXTINGUISH, 0.4F, 1.2F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
  }

  public static void failFizzle(Adaptation<?> adaptation, PlayerRef player) {
    if (player == null) {
      return;
    }
    failFizzle(adaptation, player.location());
  }

  public static void streakTrail(Adaptation<?> adaptation, EntityRef entity) {
    if (entity == null) {
      return;
    }
    Vec3 velocity = entity.velocity();
    streakTrail(adaptation, entity.location(), -velocity.x(), -velocity.y(), -velocity.z(), 1.6D);
  }

  public static void streakTrail(Adaptation<?> adaptation, Loc location, double dirX, double dirY, double dirZ, double length) {
    Fx.now(adaptation, location, FxPriority.TRAIL)
        .trail(Particles.FIREWORK, dirX, dirY, dirZ, length, 6);
  }

  public static void readyPing(Adaptation<?> adaptation, PlayerRef player) {
    if (player == null) {
      return;
    }
    Fx.now(adaptation, player.location(), FxPriority.TRANSITION)
        .dustRing(0.7D, 12, 0.8F)
        .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.6F, 1.6F);
  }

  public static void levelUpBurst(Skill<?> skill, PlayerRef player, int level) {
    if (player == null) {
      return;
    }
    float pitch = (float) Math.min(1.8D, 1.1D + (level * 0.01D));
    FxTimeline.follow(skill, player)
        .duration(18)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.dustHelix(0.9D, 2.2D, 12, progress * Math.PI * 2.0D, 1.0F);
          if (tick == 0 && AdaptConfig.get().isProgressionSoundsEnabled()) {
            fx.chord(Sounds.ENTITY_PLAYER_LEVELUP, 0.6F, pitch, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.45F);
          }
        })
        .start();
  }

  public static void learnCelebration(Adaptation<?> adaptation, PlayerRef player) {
    if (player == null) {
      return;
    }
    FxTimeline.follow(adaptation, player)
        .duration(14)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.dome(Particles.ENCHANTMENT_TABLE, 2.4D - (2.1D * progress), 24);
          fx.dustRing(1.6D - (1.3D * progress), 12, 0.8F);
          if (tick == 0) {
            fx.chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 0.9F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
          }
        })
        .start();
  }
}
