package art.arcane.adapt.api.fx;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.util.reflect.registries.Particles;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class FxPresets {
  private FxPresets() {
  }

  public static void chargeRing(Adaptation<?> adaptation, Location location, int durationTicks) {
    FxTimeline.at(adaptation, location)
        .duration(Math.max(4, durationTicks))
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.dustRing(1.8D - (1.5D * progress), 16, 0.9F);
          if ((tick & 3) == 0) {
            fx.sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5F, (float) (0.7D + (progress * 0.9D)));
          }
        })
        .start();
  }

  public static void shockwave(Adaptation<?> adaptation, Location location, double radius) {
    double maxRadius = Math.max(0.5D, radius);
    FxTimeline.at(adaptation, location)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.dustRing(0.3D + ((maxRadius - 0.3D) * progress), 24, 1.1F);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.6F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 0.7F);
          }
        })
        .start();
  }

  public static void impact(Adaptation<?> adaptation, Location location, double intensity) {
    int count = (int) Math.max(4, Math.min(24, Math.round(8.0D * intensity)));
    Fx.now(adaptation, location, FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, count, 0.4D)
        .dustBurst(Math.max(3, count / 2), 0.5D, 1.0F)
        .chord(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 0.9F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
  }

  public static void successShimmer(Adaptation<?> adaptation, Location location) {
    Fx.now(adaptation, location, FxPriority.AMBIENT)
        .column(Particles.END_ROD, 5, 1.2D)
        .particle(Particles.VILLAGER_HAPPY, 4, 0, 0.8D, 0, 0.3D, 0)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.8F);
  }

  public static void failFizzle(Adaptation<?> adaptation, Location location) {
    Fx.now(adaptation, location, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 8, 0.25D)
        .chord(Sound.BLOCK_FIRE_EXTINGUISH, 0.4F, 1.2F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
  }

  public static void failFizzle(Adaptation<?> adaptation, Player player) {
    if (player == null) {
      return;
    }
    failFizzle(adaptation, player.getLocation());
  }

  public static void streakTrail(Adaptation<?> adaptation, Entity entity) {
    if (entity == null) {
      return;
    }
    Vector velocity = entity.getVelocity();
    streakTrail(adaptation, entity.getLocation(), -velocity.getX(), -velocity.getY(), -velocity.getZ(), 1.6D);
  }

  public static void streakTrail(Adaptation<?> adaptation, Location location, double dirX, double dirY, double dirZ, double length) {
    Fx.now(adaptation, location, FxPriority.TRAIL)
        .trail(Particles.FIREWORK, dirX, dirY, dirZ, length, 6);
  }

  public static void readyPing(Adaptation<?> adaptation, Player player) {
    if (player == null) {
      return;
    }
    Fx.now(adaptation, player.getLocation(), FxPriority.TRANSITION)
        .dustRing(0.7D, 12, 0.8F)
        .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.6F, 1.6F);
  }

  public static void levelUpBurst(Skill<?> skill, Player player, int level) {
    if (player == null) {
      return;
    }
    float pitch = (float) Math.min(1.8D, 1.1D + (level * 0.01D));
    FxTimeline.follow(skill, player)
        .duration(18)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.dustHelix(0.9D, 2.2D, 12, progress * Math.PI * 2.0D, 1.0F);
          if (tick == 0) {
            fx.chord(Sound.ENTITY_PLAYER_LEVELUP, 0.6F, pitch, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.45F);
          }
        })
        .start();
  }

  public static void learnCelebration(Adaptation<?> adaptation, Player player) {
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
            fx.chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 0.9F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
          }
        })
        .start();
  }
}
