package art.arcane.adapt.mod.feature.loader;

import art.arcane.adapt.mod.feature.mixin.RecipeManagerAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Since 1.21.2 the vanilla join packet no longer carries recipes, so recipe viewers on a NeoForge client (JEI, EMI)
 * read the recipes the server sends through NeoForge's recipe sync, which carries only the types some server mod
 * requested. Adapt requests every vanilla recipe type (JEI replaces its whole fallback with the synced map once any type
 * arrives) and sends the same types again whenever its recipes change after players joined.
 */
public final class LoaderRecipeSync {
    private static volatile Set<RecipeType<?>> requested = Set.of();

    private LoaderRecipeSync() {
    }

    public static void install() {
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> event.sendRecipes(vanillaTypes()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (OnDatapackSyncEvent event) ->
                requested = Set.copyOf(event.getRecipeTypesToSend()));
    }

    public static void resend(MinecraftServer server) {
        Set<RecipeType<?>> types = new HashSet<>(requested);
        types.addAll(vanillaTypes());
        RecipeMap recipes = ((RecipeManagerAccess) server.getRecipeManager()).adapt$recipes();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CommonHooks.sendRecipes(player, types, recipes);
        }
    }

    private static Set<RecipeType<?>> vanillaTypes() {
        Set<RecipeType<?>> types = new HashSet<>();
        for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE) {
            Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
            if (id != null && id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
                types.add(type);
            }
        }
        return types;
    }
}
