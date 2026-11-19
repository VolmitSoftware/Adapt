package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.EngineFeature;
import org.bukkit.Bukkit;
import org.bukkit.FeatureFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.List;

/**
 * Engine features of Paper 1.21.1: everything except the per-stack item model, cooldown group and player input packet
 * (all 1.21.2).
 * Bundles are experimental here, so they count only when the main world enables the bundle feature flag.
 */
final class BukkitEngineFeatures {
  private static final NamespacedKey BUNDLE_FLAG = NamespacedKey.minecraft("bundle");

  private BukkitEngineFeatures() {
  }

  static boolean supports(EngineFeature feature) {
    return switch (feature) {
      case BUNDLES -> bundlesEnabled();
      case ITEM_MODEL, ITEM_COOLDOWN_GROUPS, PLAYER_INPUT -> false;
      default -> true;
    };
  }

  static boolean bundlesEnabled() {
    List<World> worlds = Bukkit.getWorlds();
    return !worlds.isEmpty() && worlds.get(0).getFeatureFlags().stream().map(FeatureFlag::getKey).anyMatch(BUNDLE_FLAG::equals);
  }
}
