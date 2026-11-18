package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Recipe host that keeps registered specs and each player's discovered recipe keys. */
public final class FakeRecipes implements RecipeHost {
  private final Map<Id, RecipeSpec> registered = new LinkedHashMap<>();
  private final Map<UUID, Set<Id>> discovered = new ConcurrentHashMap<>();
  private final Map<Id, RecipeView> server = new LinkedHashMap<>();

  @Override
  public synchronized boolean register(RecipeSpec recipe) {
    registered.put(recipe.key(), recipe);
    return true;
  }

  @Override
  public synchronized void unregister(Id key) {
    registered.remove(key);
  }

  @Override
  public synchronized Collection<Id> registered() {
    return List.copyOf(registered.keySet());
  }

  public synchronized RecipeSpec spec(Id key) {
    return registered.get(key);
  }

  @Override
  public void discover(PlayerRef player, Id key) {
    discovered.computeIfAbsent(player.uuid(), (UUID id) -> ConcurrentHashMap.newKeySet()).add(key);
  }

  @Override
  public void undiscover(PlayerRef player, Id key) {
    Set<Id> keys = discovered.get(player.uuid());
    if (keys != null) {
      keys.remove(key);
    }
  }

  /** Keys the player has in their recipe book. */
  public Set<Id> discovered(PlayerRef player) {
    Set<Id> keys = discovered.get(player.uuid());
    return keys == null ? Set.of() : new LinkedHashSet<>(keys);
  }

  /** Adds a recipe the fake server holds besides Adapt's own, for {@link #recipe} and {@link #recipesFor}. */
  public synchronized FakeRecipes serverRecipe(RecipeView view) {
    server.put(view.key(), view);
    return this;
  }

  @Override
  public synchronized Optional<RecipeView> recipe(Id key) {
    RecipeView view = server.get(key);
    if (view != null) {
      return Optional.of(view);
    }
    RecipeSpec spec = registered.get(key);
    return spec == null ? Optional.empty() : Optional.of(view(spec));
  }

  @Override
  public synchronized List<RecipeView> recipesFor(ItemRef stack) {
    List<RecipeView> views = new ArrayList<>();
    if (stack == null || stack.isEmpty()) {
      return views;
    }
    for (RecipeView view : server.values()) {
      if (view.result().type().equals(stack.type()) && view.result().damage() == stack.damage()) {
        views.add(view);
      }
    }
    for (RecipeSpec spec : registered.values()) {
      if (spec.result().type().equals(stack.type()) && spec.result().damage() == stack.damage()) {
        views.add(view(spec));
      }
    }
    return views;
  }

  private static RecipeView view(RecipeSpec spec) {
    if (spec instanceof RecipeSpec.Shaped shaped) {
      Map<Character, RecipeChoice> choices = new LinkedHashMap<>();
      shaped.ingredients().forEach((Character symbol, Ingredient ingredient) -> choices.put(symbol, choice(ingredient)));
      return new RecipeView(spec.key(), RecipeView.Kind.SHAPED, shaped.shape(), choices, List.of(), spec.result());
    }
    if (spec instanceof RecipeSpec.Shapeless shapeless) {
      List<RecipeChoice> choices = new ArrayList<>();
      for (Ingredient ingredient : shapeless.ingredients()) {
        choices.add(choice(ingredient));
      }
      return new RecipeView(spec.key(), RecipeView.Kind.SHAPELESS, List.of(), Map.of(), choices, spec.result());
    }
    return new RecipeView(spec.key(), RecipeView.Kind.OTHER, List.of(), Map.of(), List.of(), spec.result());
  }

  private static RecipeChoice choice(Ingredient ingredient) {
    return switch (ingredient) {
      case Ingredient.AnyOf any -> RecipeChoice.ofTypes(List.copyOf(any.types()));
      case Ingredient.Exact exact -> RecipeChoice.ofExact(List.of(exact.item()));
      case Ingredient.Matching matching -> RecipeChoice.ofExact(List.of(matching.example()));
      case Ingredient.Tag tag -> RecipeChoice.ofTypes(List.of());
    };
  }
}
