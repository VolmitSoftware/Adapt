package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.EngineFeature;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

/**
 * Engine features of Minecraft 1.20.1: NBT items (no per-stack limits, item model or cooldown group), hardcoded
 * enchantments, displays without teleport interpolation, no tick rate manager and no player input packet. Bundles exist behind the experimental bundle data pack.
 */
final class ModEngineFeatures {
    private ModEngineFeatures() {
    }

    static boolean supports(MinecraftServer server, EngineFeature feature) {
        return switch (feature) {
            case ITEM_DATA_COMPONENTS, DATA_DRIVEN_ENCHANTMENTS, DISPLAY_TELEPORT_INTERPOLATION, TICK_RATE_MANAGER, ITEM_STACK_LIMITS,
                 ITEM_MODEL, ITEM_COOLDOWN_GROUPS, PLAYER_INPUT -> false;
            case BUNDLES -> bundlesEnabled(server.getWorldData().enabledFeatures());
        };
    }

    /** The bundle item carries no feature flag here; the world's {@code bundle} experiment decides, as Paper reports it. */
    static boolean bundlesEnabled(FeatureFlagSet enabled) {
        return enabled.contains(FeatureFlags.BUNDLE);
    }
}
