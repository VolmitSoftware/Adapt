package art.arcane.adapt;

import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.generator.WorldInfo;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Bukkit material and world views over {@link AdaptConfig}, and the Bukkit {@link AdaptConfig.Binding}. */
public final class BukkitAdaptConfig implements AdaptConfig.Binding {
  @Override
  public String blockName(String configured) {
    Material material = configured == null ? null : Material.matchMaterial(configured.trim());
    return material != null && isServerBlock(material) ? material.name() : null;
  }

  @Override
  public void reloaded() {
    if (Adapt.instance != null) {
      Adapt.instance.reconfigureUpdates();
    }
  }

  public static boolean isGrowthBlacklisted(Material material) {
    return material != null && AdaptConfig.get().isGrowthBlacklisted(material.name());
  }

  public static Set<Material> growthBlacklist() {
    EnumSet<Material> materials = EnumSet.noneOf(Material.class);
    for (String block : AdaptConfig.get().getBlacklistedGrowableBlocks()) {
      Material material = Material.getMaterial(block);
      if (material != null) {
        materials.add(material);
      }
    }
    return Collections.unmodifiableSet(materials);
  }

  public static boolean isWorldBlacklisted(WorldInfo world) {
    return world != null && AdaptConfig.get().isWorldBlacklisted(WorldIdentity.serialize(world));
  }

  static boolean isServerBlock(Material material) {
    return Bukkit.getServer() == null || material.isBlock();
  }
}
