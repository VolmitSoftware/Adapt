package art.arcane.adapt.content.skill.kinetics;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

public final class KineticsMotion {
  private static final Set<Material> BOUNCY_SURFACES = buildBouncySurfaces();
  private static final Set<Material> SOFT_LANDINGS = buildSoftLandings();

  private KineticsMotion() {
  }

  public static boolean isBouncySurface(Material material) {
    return material != null && BOUNCY_SURFACES.contains(material);
  }

  public static boolean isSoftLanding(Material material) {
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

  private static Set<Material> buildBouncySurfaces() {
    EnumSet<Material> surfaces = EnumSet.of(Material.SLIME_BLOCK, Material.HONEY_BLOCK);
    for (Material material : Material.values()) {
      String name = material.name();
      if (name.endsWith("_BED") && !name.startsWith("LEGACY_")) {
        surfaces.add(material);
      }
    }
    return surfaces;
  }

  private static Set<Material> buildSoftLandings() {
    EnumSet<Material> landings = EnumSet.copyOf(BOUNCY_SURFACES);
    landings.add(Material.HAY_BLOCK);
    landings.add(Material.POWDER_SNOW);
    landings.add(Material.SPONGE);
    landings.add(Material.WET_SPONGE);
    return landings;
  }

  public record MotionState(long lastBounceAtMs, int bounceChain, double lastDeltaY, long lastLaunchAtMs) {
  }
}
