package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Location;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class FrostFrameRules {
  private static final double SNOWBALL_ARC = 0.2D;
  private static final double SNOWBALL_SPEED = 1.6D;

  private FrostFrameRules() {
  }

  static double atLevel(List<Integer> values, int level) {
    return Math.max(0, AnimusBindingRules.atLevel(values, level, 0));
  }

  static double hitDamage(double vanillaDamage, List<Integer> damageByLevel, int level) {
    return Math.max(vanillaDamage, atLevel(damageByLevel, level));
  }

  static boolean affects(UUID target, UUID victim, boolean hostile) {
    return hostile || victim.equals(target);
  }

  static boolean resetsImmunity(boolean extra, UUID target, UUID victim, boolean hostile) {
    return extra && affects(target, victim, hostile);
  }

  static double weatherDamage(DamageCause cause, double damage, int level, List<Double> warmMultiplierByLevel) {
    if (level < 1) {
      return damage;
    }
    return switch (cause) {
      case DROWNING -> 0D;
      case MELTING -> damage * warmMultiplier(warmMultiplierByLevel, level);
      default -> damage;
    };
  }

  static boolean unlocked(int level, int unlockLevel) {
    return level >= Math.max(1, unlockLevel);
  }

  static Vector snowballVelocity(Location from, Location to) {
    double dx = to.getX() - from.getX();
    double dy = to.getY() - from.getY();
    double dz = to.getZ() - from.getZ();
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    Vector direction = new Vector(dx, dy + horizontal * SNOWBALL_ARC, dz);
    if (direction.lengthSquared() < 1.0E-6D) {
      return new Vector(0D, SNOWBALL_SPEED, 0D);
    }
    return direction.normalize().multiply(SNOWBALL_SPEED);
  }

  static boolean extraShot(int level, int bonusLevel, double chance, double roll) {
    return unlocked(level, bonusLevel) && roll < chance;
  }

  static FrostbiteState frostbite(FrostbiteState current, long now, int hits, long windowMillis, long cooldownMillis) {
    long cooldownUntil = current == null ? Long.MIN_VALUE : current.cooldownUntil();
    if (now < cooldownUntil) {
      return new FrostbiteState(List.of(), cooldownUntil, false);
    }
    List<Long> recent = new ArrayList<>(Math.max(1, hits));
    if (current != null) {
      for (long hit : current.hits()) {
        if (hit > now - windowMillis) {
          recent.add(hit);
        }
      }
    }
    recent.add(now);
    if (recent.size() >= Math.max(1, hits)) {
      return new FrostbiteState(List.of(), now + Math.max(0L, cooldownMillis), true);
    }
    return new FrostbiteState(List.copyOf(recent), cooldownUntil, false);
  }

  static int frostbiteFreezeTicks(int current, int fullyFrozenTicks, int durationTicks) {
    long frozen = fullyFrozenTicks + 2L * Math.max(1, durationTicks);
    return (int) Math.max(current, Math.min(Integer.MAX_VALUE, frozen));
  }

  static boolean expired(FrostbiteState state, long now, long windowMillis) {
    if (now < state.cooldownUntil()) {
      return false;
    }
    return state.hits().isEmpty() || state.hits().getLast() <= now - windowMillis;
  }

  static double warmMultiplier(List<Double> multiplierByLevel, int level) {
    double multiplier = AnimusBindingRules.atLevel(multiplierByLevel, level, 1D);
    return Double.isNaN(multiplier) ? 1D : Math.max(0D, multiplier);
  }

  record FrostbiteState(List<Long> hits, long cooldownUntil, boolean triggered) {
  }
}
