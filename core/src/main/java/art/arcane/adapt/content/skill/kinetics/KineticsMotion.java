package art.arcane.adapt.content.skill.kinetics;

import art.arcane.adapt.platform.value.Id;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KineticsMotion {
  private static final List<String> BED_COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
      "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
  private static final Set<Id> BOUNCY_SURFACES = buildBouncySurfaces();
  private static final Set<Id> SOFT_LANDINGS = buildSoftLandings();

  private KineticsMotion() {
  }

  public static boolean isBouncySurface(Id material) {
    return material != null && BOUNCY_SURFACES.contains(material);
  }

  public static boolean isSoftLanding(Id material) {
    return material != null && SOFT_LANDINGS.contains(material);
  }

  public static double breakFallXp(double fallDistance, double xpPerBlock, double cap) {
    if (!Double.isFinite(fallDistance) || !Double.isFinite(xpPerBlock) || !Double.isFinite(cap)
        || xpPerBlock <= 0D || cap <= 0D || fallDistance < 3D) {
      return 0D;
    }
    return Math.max(0D, Math.min(cap, fallDistance * xpPerBlock));
  }

  public static boolean isLaunch(double deltaY, double previousDeltaY, double minDeltaY) {
    return Double.isFinite(deltaY) && Double.isFinite(previousDeltaY) && Double.isFinite(minDeltaY)
        && minDeltaY > 0D && deltaY >= minDeltaY && previousDeltaY <= 0D;
  }

  public static int nextBounceChain(int currentChain, long nowMs, long lastBounceAtMs, long chainWindowMs) {
    if (nowMs - lastBounceAtMs <= chainWindowMs) {
      return Math.max(1, currentChain + 1);
    }
    return 1;
  }

  public static double bounceXp(double baseXp, int chain, double chainBonus, double cap) {
    if (!Double.isFinite(baseXp) || !Double.isFinite(chainBonus) || !Double.isFinite(cap)
        || baseXp <= 0D || chainBonus < 0D || cap <= 0D) {
      return 0D;
    }
    return Math.max(0D, Math.min(cap, baseXp + Math.max(0, chain - 1) * chainBonus));
  }

  private static Set<Id> buildBouncySurfaces() {
    Set<Id> surfaces = new HashSet<>();
    surfaces.add(Id.minecraft("slime_block"));
    surfaces.add(Id.minecraft("honey_block"));
    for (String color : BED_COLORS) {
      surfaces.add(Id.minecraft(color + "_bed"));
    }
    return Set.copyOf(surfaces);
  }

  private static Set<Id> buildSoftLandings() {
    Set<Id> landings = new HashSet<>(BOUNCY_SURFACES);
    landings.add(Id.minecraft("hay_block"));
    landings.add(Id.minecraft("powder_snow"));
    landings.add(Id.minecraft("sponge"));
    landings.add(Id.minecraft("wet_sponge"));
    return Set.copyOf(landings);
  }

  public record MotionState(long lastBounceAtMs, int bounceChain, double lastDeltaY, long lastLaunchAtMs) {
  }
}
