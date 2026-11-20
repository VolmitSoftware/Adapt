package art.arcane.adapt.gameplay.neoforge;

import art.arcane.adapt.mod.feature.mixin.RecipeManagerAccess;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.payload.RecipeContentPayload;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records the recipe types server mods asked NeoForge to sync on the last datapack sync, builds the recipe content
 * payload a NeoForge client receives from them, round-trips it through the payload codec and reports what that client
 * (and a recipe viewer reading RecipesReceivedEvent) would decode.
 */
final class RecipeSyncProbe {
    private final AtomicInteger syncs = new AtomicInteger();
    private volatile Set<RecipeType<?>> requested = Set.of();

    void install() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (OnDatapackSyncEvent event) -> {
            requested = Set.copyOf(event.getRecipeTypesToSend());
            syncs.incrementAndGet();
        });
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("adaptqa-recipe-sync")
                .executes((CommandContext<CommandSourceStack> context) -> report(context.getSource())));
    }

    private int report(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        RecipeMap map = ((RecipeManagerAccess) server.getRecipeManager()).adapt$recipes();
        RegistryFriendlyByteBuf buffer = RegistryFriendlyByteBuf.decorator(server.registryAccess()).apply(Unpooled.buffer());
        RecipeContentPayload.STREAM_CODEC.encode(buffer, RecipeContentPayload.create(requested, map));
        int bytes = buffer.readableBytes();
        RecipeContentPayload decoded = RecipeContentPayload.STREAM_CODEC.decode(buffer);
        JsonObject json = new JsonObject();
        json.addProperty("mechanism", "neoforge:recipe_content");
        JsonArray types = new JsonArray();
        for (RecipeType<?> type : decoded.recipeTypes()) {
            types.add(String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(type)));
        }
        json.add("types", types);
        json.addProperty("syncEvents", syncs.get());
        json.addProperty("bytes", bytes);
        int adapt = 0;
        JsonObject serializers = new JsonObject();
        JsonArray sample = new JsonArray();
        for (RecipeHolder<?> holder : decoded.recipes()) {
            if (holder.id().identifier().getNamespace().equals("adapt")) {
                adapt++;
                String serializer = String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer()));
                serializers.addProperty(serializer, serializers.has(serializer) ? serializers.get(serializer).getAsInt() + 1 : 1);
                if (sample.size() < 3) {
                    sample.add(holder.id().identifier().toString());
                }
            }
        }
        int registered = 0;
        for (RecipeHolder<?> holder : map.values()) {
            if (holder.id().identifier().getNamespace().equals("adapt")) {
                registered++;
            }
        }
        json.addProperty("total", decoded.recipes().size());
        json.addProperty("adapt", adapt);
        json.addProperty("registered", registered);
        json.add("serializers", serializers);
        json.add("sample", sample);
        source.sendSystemMessage(Component.literal("ADAPT_QA RECIPE_SYNC 0 1 " + json));
        return 1;
    }
}
