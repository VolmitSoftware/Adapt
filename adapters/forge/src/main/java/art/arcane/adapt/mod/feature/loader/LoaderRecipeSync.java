package art.arcane.adapt.mod.feature.loader;

import net.minecraft.server.MinecraftServer;

/**
 * Forge has no recipe sync since 1.21.2 (its datapack sync event cannot carry recipes), so a recipe viewer on a Forge
 * client sees Adapt recipes only through its own server-side component.
 */
public final class LoaderRecipeSync {
    private LoaderRecipeSync() {
    }

    public static void resend(MinecraftServer server) {
    }
}
