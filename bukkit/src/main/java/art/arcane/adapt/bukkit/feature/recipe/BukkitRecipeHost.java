package art.arcane.adapt.bukkit.feature.recipe;

import art.arcane.adapt.api.value.MaterialRecipeIndex;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Recipes through {@code Server.addRecipe}/{@code removeRecipe} and player recipe discovery, as {@code AdaptRecipe} does. */
public final class BukkitRecipeHost implements RecipeHost {
  private final Server server;
  private final RecipeTranslator translator;
  private final BukkitRecipeRegistry registry;
  private final Set<Id> registered = ConcurrentHashMap.newKeySet();
  private final MaterialRecipeIndex<Material, Recipe> index = new MaterialRecipeIndex<>(BukkitRecipeHost::output);

  public BukkitRecipeHost(Server server, BukkitRecipeRegistry registry) {
    this(server, new RecipeTranslator(BukkitRefs::material, BukkitRefs::item,
        (Id tag) -> server.getTag(Tag.REGISTRY_ITEMS, RecipeTranslator.key(tag), Material.class)), registry);
  }

  BukkitRecipeHost(Server server, RecipeTranslator translator, BukkitRecipeRegistry registry) {
    this.server = server;
    this.translator = translator;
    this.registry = registry;
  }

  @Override
  public boolean register(RecipeSpec recipe) {
    Recipe translated;
    try {
      translated = translator.translate(recipe);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
    if (translated == null) {
      return false;
    }
    if (registered.contains(recipe.key()) || server.getRecipe(RecipeTranslator.key(recipe.key())) != null) {
      server.removeRecipe(RecipeTranslator.key(recipe.key()));
    }
    boolean added;
    try {
      added = BukkitRecipeVersion.add(server, translated);
    } catch (IllegalStateException | IllegalArgumentException rejected) {
      added = false;
    }
    if (added) {
      registered.add(recipe.key());
    } else {
      registered.remove(recipe.key());
    }
    registry.invalidate();
    return added;
  }

  @Override
  public void unregister(Id key) {
    registered.remove(key);
    BukkitRecipeVersion.remove(server, RecipeTranslator.key(key));
    registry.invalidate();
  }

  @Override
  public Collection<Id> registered() {
    return List.copyOf(registered);
  }

  @Override
  public void discover(PlayerRef player, Id key) {
    Player target = BukkitRefs.player(player);
    if (target != null && target.isOnline()) {
      target.discoverRecipe(RecipeTranslator.key(key));
    }
  }

  @Override
  public void undiscover(PlayerRef player, Id key) {
    Player target = BukkitRefs.player(player);
    if (target != null && target.isOnline()) {
      target.undiscoverRecipe(RecipeTranslator.key(key));
    }
  }

  @Override
  public Optional<RecipeView> recipe(Id key) {
    Recipe recipe = server.getRecipe(RecipeTranslator.key(key));
    return recipe == null ? Optional.empty() : Optional.of(view(recipe));
  }

  @Override
  public List<RecipeView> recipesFor(ItemRef stack) {
    if (stack == null || stack.isEmpty()) {
      return List.of();
    }
    ItemStack wanted = BukkitRefs.item(stack);
    short damage = wanted.getDurability();
    List<RecipeView> views = new ArrayList<>();
    for (Recipe recipe : index.recipes(registry.generation(), registry::recipes, wanted.getType())) {
      if (damage == -1 || recipe.getResult().getDurability() == damage) {
        views.add(view(recipe));
      }
    }
    return views;
  }

  private static Material output(Recipe recipe) {
    ItemStack result = recipe.getResult();
    return result == null ? null : result.getType();
  }

  public static RecipeView view(Recipe recipe) {
    Id key = recipe instanceof Keyed keyed ? BukkitRefs.id(keyed.getKey()) : null;
    ItemRef result = BukkitRefs.item(recipe.getResult());
    if (recipe instanceof ShapedRecipe shaped) {
      Map<Character, RecipeChoice> choices = new LinkedHashMap<>();
      for (Map.Entry<Character, org.bukkit.inventory.RecipeChoice> entry : shaped.getChoiceMap().entrySet()) {
        RecipeChoice choice = choice(entry.getValue());
        if (choice != null) {
          choices.put(entry.getKey(), choice);
        }
      }
      return new RecipeView(key, RecipeView.Kind.SHAPED, List.of(shaped.getShape()), choices, List.of(), result);
    }
    if (recipe instanceof ShapelessRecipe shapeless) {
      List<RecipeChoice> choices = new ArrayList<>();
      for (org.bukkit.inventory.RecipeChoice choice : shapeless.getChoiceList()) {
        RecipeChoice converted = choice(choice);
        choices.add(converted == null ? RecipeChoice.ofTypes(List.of()) : converted);
      }
      return new RecipeView(key, RecipeView.Kind.SHAPELESS, List.of(), Map.of(), choices, result);
    }
    return new RecipeView(key, RecipeView.Kind.OTHER, List.of(), Map.of(), List.of(), result);
  }

  private static RecipeChoice choice(org.bukkit.inventory.RecipeChoice choice) {
    if (choice instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice materials) {
      List<Id> types = new ArrayList<>();
      for (Material material : materials.getChoices()) {
        types.add(BukkitRefs.id(material));
      }
      return RecipeChoice.ofTypes(types);
    }
    if (choice instanceof org.bukkit.inventory.RecipeChoice.ExactChoice exact && BukkitRecipeVersion.comparesExactly(exact)) {
      List<ItemRef> stacks = new ArrayList<>();
      for (ItemStack stack : exact.getChoices()) {
        stacks.add(BukkitRefs.item(stack.clone()));
      }
      return RecipeChoice.ofExact(stacks);
    }
    if (choice == null || choice == BukkitRecipeVersion.emptyChoice()) {
      return null;
    }
    ItemStack example = choice.getItemStack();
    if (example == null) {
      return null;
    }
    return RecipeChoice.ofMatching(BukkitRefs.item(example.clone()),
        (ItemRef item) -> choice.test(BukkitRefs.item(item)));
  }
}
