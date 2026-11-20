package art.arcane.adapt.gameplay.fabric;

import art.arcane.adapt.mod.feature.mixin.RecipeManagerAccess;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.impl.recipe.sync.ClientboundRecipeSyncPayload;
import net.fabricmc.fabric.impl.recipe.sync.RecipeSyncImpl;
import net.fabricmc.fabric.impl.recipe.sync.SyncedSerializerAwarePreparedRecipe;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the Fabric API recipe sync payload exactly as Fabric API does for a client that registered the vanilla
 * serializers (JEI, EMI), round-trips it through the payload codec and reports what that client would decode.
 */
final class RecipeSyncProbe {
    private final AtomicInteger syncs = new AtomicInteger();

    void install() {
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((ServerPlayer player, boolean joined) -> syncs.incrementAndGet());
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("adaptqa-recipe-sync")
                .executes((CommandContext<CommandSourceStack> context) -> report(context.getSource())));
    }

    private int report(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        RecipeMap map = ((RecipeManagerAccess) server.getRecipeManager()).adapt$recipes();
        List<ClientboundRecipeSyncPayload.Entry> entries = new ArrayList<>();
        for (RecipeSerializer<?> serializer : RecipeSyncImpl.getSyncedSerializers()) {
            List<RecipeHolder<?>> recipes = ((SyncedSerializerAwarePreparedRecipe) map).fabric_getRecipesBySyncedSerializer(serializer);
            if (recipes != null && !recipes.isEmpty()) {
                entries.add(new ClientboundRecipeSyncPayload.Entry(serializer, recipes));
            }
        }
        RegistryFriendlyByteBuf buffer = RegistryFriendlyByteBuf.decorator(server.registryAccess()).apply(Unpooled.buffer());
        ClientboundRecipeSyncPayload.CODEC.encode(buffer, new ClientboundRecipeSyncPayload(entries));
        int bytes = buffer.readableBytes();
        ClientboundRecipeSyncPayload decoded = ClientboundRecipeSyncPayload.CODEC.decode(buffer);
        JsonObject json = new JsonObject();
        json.addProperty("mechanism", "fabric:recipe_sync");
        json.addProperty("syncedSerializers", RecipeSyncImpl.getSyncedSerializers().size());
        json.addProperty("syncEvents", syncs.get());
        json.addProperty("bytes", bytes);
        int total = 0;
        int adapt = 0;
        JsonObject serializers = new JsonObject();
        JsonArray sample = new JsonArray();
        for (ClientboundRecipeSyncPayload.Entry entry : decoded.entries()) {
            for (RecipeHolder<?> holder : entry.recipes()) {
                total++;
                if (holder.id().identifier().getNamespace().equals("adapt")) {
                    adapt++;
                    String serializer = String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(entry.serializer()));
                    serializers.addProperty(serializer, serializers.has(serializer) ? serializers.get(serializer).getAsInt() + 1 : 1);
                    if (sample.size() < 3) {
                        sample.add(holder.id().identifier().toString());
                    }
                }
            }
        }
        int registered = 0;
        for (RecipeHolder<?> holder : map.values()) {
            if (holder.id().identifier().getNamespace().equals("adapt")) {
                registered++;
            }
        }
        json.addProperty("total", total);
        json.addProperty("adapt", adapt);
        json.addProperty("registered", registered);
        json.add("serializers", serializers);
        json.add("sample", sample);
        source.sendSystemMessage(Component.literal("ADAPT_QA RECIPE_SYNC 0 1 " + json));
        return 1;
    }
}
