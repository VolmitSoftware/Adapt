package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.EngineFeature;
import org.bukkit.Bukkit;
import org.bukkit.FeatureFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.List;

/**
 * Engine features of Paper 1.20.1: NBT items, hardcoded enchantments, no display teleport interpolation, no tick rate
 * manager and no player input packet. Bundles are experimental, so they count only when the main world enables the
 * bundle feature flag.
 */
final class BukkitEngineFeatures {
  private static final NamespacedKey BUNDLE_FLAG = NamespacedKey.minecraft("bundle");

  private BukkitEngineFeatures() {
  }

  static boolean supports(EngineFeature feature) {
    return feature == EngineFeature.BUNDLES && bundlesEnabled();
  }

  static boolean bundlesEnabled() {
    List<World> worlds = Bukkit.getWorlds();
    return !worlds.isEmpty() && worlds.get(0).getFeatureFlags().stream().map(FeatureFlag::getKey).anyMatch(BUNDLE_FLAG::equals);
  }
}
