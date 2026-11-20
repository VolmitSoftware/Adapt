package art.arcane.adapt.mod.feature.loader;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Since 1.21.2 the vanilla join packet no longer carries recipes, so recipe viewers on a Fabric client (JEI, EMI) read
 * the recipes Fabric API synchronizes: those whose serializer both sides registered. Adapt registers the vanilla
 * serializers its recipes use on the server and replays the datapack sync whenever its recipes change after players
 * joined, which is when Fabric API sends that payload.
 */
public final class LoaderRecipeSync {
    private LoaderRecipeSync() {
    }

    public static void install() {
        for (RecipeSerializer<?> serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
            Identifier id = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
            if (id != null && id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
                RecipeSynchronization.synchronizeRecipeSerializer(serializer);
            }
        }
    }

    public static void resend(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, false);
        }
    }
}
