package art.arcane.adapt.mod.feature.recipe;

import art.arcane.adapt.mod.feature.loader.LoaderRecipeSync;
import art.arcane.adapt.mod.feature.mixin.RecipeManagerAccess;
import art.arcane.adapt.mod.feature.mixin.RecipeMapAccess;
import art.arcane.adapt.mod.feature.mixin.ShapedRecipeAccess;
import art.arcane.adapt.mod.feature.mixin.ShapelessRecipeAccess;
import art.arcane.adapt.mod.platform.ModHosts;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adapt recipes merged into the server recipe manager. Specs stay registered here; every
 * {@code finalizeRecipeLoading} (startup, {@code /reload}, and each change made through this host) merges
 * them into the freshly loaded recipe map, so they survive datapack reloads.
 */
public final class ModRecipeHost implements RecipeHost {
    private static final Logger LOGGER = LoggerFactory.getLogger("Adapt");
    private static volatile ModRecipeHost active;

    private final Map<Id, RecipeSpec> specs = new LinkedHashMap<>();
    private final Set<ResourceKey<Recipe<?>>> injected = new HashSet<>();
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

    public static RecipeMap inject(RecipeMap loaded) {
        ModRecipeHost host = active;
        return host == null ? loaded : host.merge(loaded);
    }

    public static Collection<RecipeHolder<?>> learnable(RecipeMap recipes) {
        List<RecipeHolder<?>> learnable = new ArrayList<>(recipes.values().size());
        for (RecipeHolder<?> holder : recipes.values()) {
            if (!holder.value().isSpecial()) {
                learnable.add(holder);
            }
        }
        return Collections.unmodifiableList(learnable);
    }

    @Override
    public boolean register(RecipeSpec recipe) {
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
            ResourceKey<Recipe<?>> recipeKey = RecipeFactory.key(key);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getRecipeBook().remove(recipeKey);
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
        List<RecipeView> views = new ArrayList<>();
        for (RecipeHolder<?> holder : ordered(server.getRecipeManager())) {
            ItemStack result = result(holder.value());
            if (!result.isEmpty() && result.is(wanted.getItem()) && result.getDamageValue() == wanted.getDamageValue()) {
                views.add(view(holder));
            }
        }
        return views;
    }

    /** Every recipe grouped by recipe type, the order Paper's recipe iterator walks. */
    public static Collection<RecipeHolder<?>> ordered(RecipeManager manager) {
        RecipeMap map = ((RecipeManagerAccess) manager).adapt$recipes();
        return ((RecipeMapAccess) map).adapt$byType().values();
    }

    private static RecipeView view(RecipeHolder<?> holder) {
        Id key = ModRefs.id(holder.id().identifier());
        Recipe<?> recipe = holder.value();
        ItemRef result = ModRefs.item(result(recipe));
        ExactMatcher exact = recipe instanceof ExactRecipes.Exact narrowed ? narrowed.exact() : null;
        if (recipe instanceof ShapedRecipe shaped) {
            List<Optional<Ingredient>> cells = shaped.getIngredients();
            List<RecipeChoice> distinct = new ArrayList<>();
            Map<Character, RecipeChoice> choices = new LinkedHashMap<>();
            List<String> shape = new ArrayList<>(shaped.getHeight());
            for (int row = 0; row < shaped.getHeight(); row++) {
                StringBuilder line = new StringBuilder(shaped.getWidth());
                for (int column = 0; column < shaped.getWidth(); column++) {
                    Optional<Ingredient> cell = cells.get((row * shaped.getWidth()) + column);
                    if (cell.isEmpty() || cell.get().isEmpty()) {
                        line.append(' ');
                        continue;
                    }
                    RecipeChoice choice = choice(cell.get(), exact);
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
            return new RecipeView(key, RecipeView.Kind.SHAPED, shape, choices, List.of(), result);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> choices = new ArrayList<>();
            for (Ingredient ingredient : ((ShapelessRecipeAccess) shapeless).adapt$ingredients()) {
                choices.add(choice(ingredient, exact));
            }
            return new RecipeView(key, RecipeView.Kind.SHAPELESS, List.of(), Map.of(), choices, result);
        }
        return new RecipeView(key, RecipeView.Kind.OTHER, List.of(), Map.of(), List.of(), result);
    }

    private static ItemStack result(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return ((ShapedRecipeAccess) shaped).adapt$result().create();
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return ((ShapelessRecipeAccess) shapeless).adapt$result().create();
        }
        return ItemStack.EMPTY;
    }

    private static RecipeChoice choice(Ingredient ingredient, ExactMatcher exact) {
        ItemStack shown = exact == null ? null : exact.shown(ingredient);
        if (shown != null) {
            return RecipeChoice.ofExact(List.of(ModRefs.item(shown.copy())));
        }
        List<Id> types = new ArrayList<>();
        ingredient.items().forEach((Holder<Item> item) -> item.unwrapKey()
            .ifPresent((ResourceKey<Item> itemKey) -> types.add(ModRefs.id(itemKey.identifier()))));
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
        manager.finalizeRecipeLoading(server.getWorldData().enabledFeatures());
        ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(
                manager.getSynchronizedItemProperties(), manager.getSynchronizedStonecutterRecipes());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
            player.getRecipeBook().sendInitialRecipeBook(player);
        }
        LoaderRecipeSync.resend(server);
    }

    private RecipeMap merge(RecipeMap loaded) {
        List<RecipeSpec> current;
        synchronized (specs) {
            current = List.copyOf(specs.values());
        }
        List<RecipeHolder<?>> ours = new ArrayList<>(current.size());
        Set<ResourceKey<Recipe<?>>> ourKeys = new HashSet<>();
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
        List<RecipeHolder<?>> merged = new ArrayList<>(loaded.values().size() + ours.size());
        for (RecipeHolder<?> holder : loaded.values()) {
            if (ourKeys.contains(holder.id()) || injected.contains(holder.id())) {
                changed = true;
            } else {
                merged.add(holder);
            }
        }
        injected.clear();
        injected.addAll(ourKeys);
        if (!changed) {
            return loaded;
        }
        merged.addAll(ours);
        return RecipeMaps.create(merged);
    }
}
