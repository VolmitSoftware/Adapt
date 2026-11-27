package art.arcane.adapt.mod.feature.recipe;

import art.arcane.adapt.engine.platform.EngineRecipeView;
import art.arcane.adapt.engine.value.MaterialRecipeIndex;
import art.arcane.adapt.mod.feature.mixin.ShapedRecipeAccess;
import art.arcane.adapt.mod.feature.mixin.ShapelessRecipeAccess;
import art.arcane.adapt.mod.platform.ModHosts;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.api.internal.HostRecipes;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adapt recipes merged into the server recipe manager. Specs stay registered here; every registry tag update
 * (startup and {@code /reload}, once the reloaded tags are bound) and each change made through this host merges
 * them into the loaded recipes, so they survive datapack reloads.
 */
public final class ModRecipeHost implements HostRecipes {
    private static final Logger LOGGER = LoggerFactory.getLogger("Adapt");
    private static volatile ModRecipeHost active;

    private final Map<Id, RecipeSpec> specs = new LinkedHashMap<>();
    private final MaterialRecipeIndex<Item, RecipeHolder<?>> index = new MaterialRecipeIndex<>(ModRecipeHost::output);
    private final Set<ResourceLocation> injected = new HashSet<>();
    private volatile boolean dirty;

    public ModRecipeHost() {
        active = this;
        ModHosts.onServerTick((MinecraftServer server) -> {
            if (dirty) {
                dirty = false;
                apply(server);
            }
        });
    }

    public static void inject(RecipeManager manager) {
        ModRecipeHost host = active;
        if (host != null) {
            host.merge(manager);
        }
    }

    @Override
    public boolean add(RecipeSpec recipe) {
        try {
            RecipeFactory.build(recipe);
        } catch (RuntimeException rejected) {
            LOGGER.warn("Rejected recipe {}: {}", recipe.key(), rejected.getMessage());
            return false;
        }
        synchronized (specs) {
            specs.put(recipe.key(), recipe);
        }
        refresh();
        return true;
    }

    @Override
    public void unregister(Id key) {
        RecipeSpec removed;
        synchronized (specs) {
            removed = specs.remove(key);
        }
        if (removed == null) {
            return;
        }
        MinecraftServer server = ModPlatform.server();
        if (server != null) {
            Optional<RecipeHolder<?>> holder = server.getRecipeManager().byKey(RecipeFactory.key(key));
            if (holder.isPresent()) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.getRecipeBook().remove(holder.get());
                }
            }
        }
        refresh();
    }

    @Override
    public Collection<Id> registered() {
        synchronized (specs) {
            return List.copyOf(specs.keySet());
        }
    }

    @Override
    public void discover(PlayerRef player, Id key) {
        ServerPlayer handle = ModRefs.player(player);
        if (handle == null) {
            return;
        }
        flushIfPending();
        handle.awardRecipesByKey(List.of(RecipeFactory.key(key)));
    }

    @Override
    public void undiscover(PlayerRef player, Id key) {
        ServerPlayer handle = ModRefs.player(player);
        MinecraftServer server = ModPlatform.server();
        if (handle == null || server == null) {
            return;
        }
        flushIfPending();
        Optional<RecipeHolder<?>> holder = server.getRecipeManager().byKey(RecipeFactory.key(key));
        if (holder.isPresent()) {
            handle.resetRecipes(List.of(holder.get()));
        }
    }

    @Override
    public Optional<RecipeView> recipe(Id key) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return Optional.empty();
        }
        flushIfPending();
        return server.getRecipeManager().byKey(RecipeFactory.key(key)).map(ModRecipeHost::view);
    }

    @Override
    public List<RecipeView> recipesFor(ItemRef stack) {
        MinecraftServer server = ModPlatform.server();
        ItemStack wanted = ModRefs.item(stack);
        if (server == null || wanted.isEmpty()) {
            return List.of();
        }
        flushIfPending();
        RecipeManager manager = server.getRecipeManager();
        List<RecipeView> views = new ArrayList<>();
        for (RecipeHolder<?> holder : index.recipes(manager.getOrderedRecipes(), () -> ordered(manager), wanted.getItem())) {
            if (result(holder.value()).getDamageValue() == wanted.getDamageValue()) {
                views.add(view(holder));
            }
        }
        return views;
    }

    private static Item output(RecipeHolder<?> holder) {
        ItemStack result = result(holder.value());
        return result.isEmpty() ? null : result.getItem();
    }

    /** Every recipe grouped by recipe type, the order Paper's recipe iterator walks. */
    public static Collection<RecipeHolder<?>> ordered(RecipeManager manager) {
        return manager.getOrderedRecipes();
    }

    private static RecipeView view(RecipeHolder<?> holder) {
        Id key = ModRefs.id(holder.id());
        Recipe<?> recipe = holder.value();
        ItemRef result = ModRefs.item(result(recipe));
        ExactMatcher exact = recipe instanceof ExactRecipes.Exact narrowed ? narrowed.exact() : null;
        if (recipe instanceof ShapedRecipe shaped) {
            NonNullList<Ingredient> cells = shaped.getIngredients();
            List<RecipeChoice> distinct = new ArrayList<>();
            Map<Character, RecipeChoice> choices = new LinkedHashMap<>();
            List<String> shape = new ArrayList<>(shaped.getHeight());
            for (int row = 0; row < shaped.getHeight(); row++) {
                StringBuilder line = new StringBuilder(shaped.getWidth());
                for (int column = 0; column < shaped.getWidth(); column++) {
                    Ingredient cell = cells.get((row * shaped.getWidth()) + column);
                    if (cell.isEmpty()) {
                        line.append(' ');
                        continue;
                    }
                    RecipeChoice choice = choice(cell, exact);
                    int index = distinct.indexOf(choice);
                    if (index < 0) {
                        index = distinct.size();
                        distinct.add(choice);
                    }
                    char symbol = (char) ('a' + index);
                    choices.putIfAbsent(symbol, choice);
                    line.append(symbol);
                }
                shape.add(line.toString());
            }
            return new EngineRecipeView(key, RecipeView.Kind.SHAPED, shape, choices, List.of(), result);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> choices = new ArrayList<>();
            for (Ingredient ingredient : shapeless.getIngredients()) {
                choices.add(choice(ingredient, exact));
            }
            return new EngineRecipeView(key, RecipeView.Kind.SHAPELESS, List.of(), Map.of(), choices, result);
        }
        return new EngineRecipeView(key, RecipeView.Kind.OTHER, List.of(), Map.of(), List.of(), result);
    }

    private static ItemStack result(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return ((ShapedRecipeAccess) shaped).adapt$result().copy();
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return ((ShapelessRecipeAccess) shapeless).adapt$result().copy();
        }
        return ItemStack.EMPTY;
    }

    private static RecipeChoice choice(Ingredient ingredient, ExactMatcher exact) {
        ItemStack shown = exact == null ? null : exact.shown(ingredient);
        if (shown != null) {
            return RecipeChoice.ofExact(List.of(ModRefs.item(shown.copy())));
        }
        List<Id> types = new ArrayList<>();
        for (ItemStack item : ingredient.getItems()) {
            types.add(ModRefs.id(BuiltInRegistries.ITEM.getKey(item.getItem())));
        }
        return RecipeChoice.ofTypes(types);
    }

    private void refresh() {
        dirty = true;
    }

    private void flushIfPending() {
        MinecraftServer server = ModPlatform.server();
        if (dirty && server != null && server.isSameThread()) {
            dirty = false;
            apply(server);
        }
    }

    private void apply(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        merge(manager);
        Collection<RecipeHolder<?>> recipes = manager.getOrderedRecipes();
        settleIngredients(recipes);
        ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(recipes);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
            player.getRecipeBook().sendInitialRecipeBook(player);
        }
    }

    /**
     * Forge resolves an ingredient's items lazily and throws them away whenever its global invalidation counter moved
     * since the ingredient was last marked valid, which only getStackingIds does. Such an ingredient recomputes on
     * every read, so a server-thread read can null the items while the network thread encodes the recipe packet
     * (Forge 52: "Failed to encode packet 'clientbound/minecraft:update_recipes'", NPE in the ingredient codec).
     * Marking every ingredient valid before the packet is built keeps its items fixed. Harmless where items are eager.
     */
    private static void settleIngredients(Collection<RecipeHolder<?>> recipes) {
        for (RecipeHolder<?> recipe : recipes) {
            for (Ingredient ingredient : recipe.value().getIngredients()) {
                ingredient.getStackingIds();
            }
        }
    }

    private void merge(RecipeManager manager) {
        List<RecipeSpec> current;
        synchronized (specs) {
            current = List.copyOf(specs.values());
        }
        List<RecipeHolder<?>> ours = new ArrayList<>(current.size());
        Set<ResourceLocation> ourKeys = new HashSet<>();
        for (RecipeSpec spec : current) {
            try {
                RecipeHolder<?> holder = RecipeFactory.build(spec);
                ours.add(holder);
                ourKeys.add(holder.id());
            } catch (RuntimeException failure) {
                LOGGER.warn("Skipped recipe {}: {}", spec.key(), failure.getMessage());
            }
        }
        boolean changed = !ours.isEmpty();
        Collection<RecipeHolder<?>> loaded = manager.getOrderedRecipes();
        List<RecipeHolder<?>> merged = new ArrayList<>(loaded.size() + ours.size());
        for (RecipeHolder<?> holder : loaded) {
            if (ourKeys.contains(holder.id()) || injected.contains(holder.id())) {
                changed = true;
            } else {
                merged.add(holder);
            }
        }
        injected.clear();
        injected.addAll(ourKeys);
        if (!changed) {
            return;
        }
        merged.addAll(ours);
        manager.replaceRecipes(merged);
    }
}
