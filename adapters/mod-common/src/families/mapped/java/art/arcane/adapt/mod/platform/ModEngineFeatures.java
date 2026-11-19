package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.EngineFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

/**
 * Engine features of Minecraft 1.21.1: item components with per-stack limits, data-driven enchantments, display teleport
 * interpolation; no per-stack item model, cooldown group or player input packet (all 1.21.2).
 */
final class ModEngineFeatures {
    private ModEngineFeatures() {
    }

    static boolean supports(MinecraftServer server, EngineFeature feature) {
        return switch (feature) {
            case ITEM_DATA_COMPONENTS, DISPLAY_TELEPORT_INTERPOLATION, TICK_RATE_MANAGER, ITEM_STACK_LIMITS -> true;
            case ITEM_MODEL, ITEM_COOLDOWN_GROUPS, PLAYER_INPUT -> false;
            case DATA_DRIVEN_ENCHANTMENTS -> server.registryAccess().registry(Registries.ENCHANTMENT).isPresent();
            case BUNDLES -> bundlesEnabled(server.getWorldData().enabledFeatures());
        };
    }

    /**
     * The bundle item is registered without a feature flag before 1.21.2; the world's {@code bundle} experiment is what
     * makes bundles a supported item (recipe, creative tab), as Paper reports it.
     */
    static boolean bundlesEnabled(FeatureFlagSet enabled) {
        return enabled.contains(FeatureFlags.BUNDLE);
    }
}
