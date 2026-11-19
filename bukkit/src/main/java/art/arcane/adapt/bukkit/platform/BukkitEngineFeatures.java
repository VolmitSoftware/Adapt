package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.EngineFeature;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

/** Engine features of the servers this jar is built for, apart from bundles, which are probed in the item registry. */
final class BukkitEngineFeatures {
  private BukkitEngineFeatures() {
  }

  static boolean supports(EngineFeature feature) {
    return switch (feature) {
      case ITEM_DATA_COMPONENTS -> RegistryAccess.registryAccess().getRegistry(RegistryKey.DATA_COMPONENT_TYPE).iterator().hasNext();
      case BUNDLES, DATA_DRIVEN_ENCHANTMENTS, DISPLAY_TELEPORT_INTERPOLATION, TICK_RATE_MANAGER, ITEM_STACK_LIMITS, ITEM_MODEL,
          ITEM_COOLDOWN_GROUPS, PLAYER_INPUT -> true;
    };
  }
}
