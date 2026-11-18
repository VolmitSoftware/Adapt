package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Server recipe registration. Register during startup or reload on the server/global thread;
 * adapters resend recipe books to online players after changes. Brewing stays in Adapt's own
 * brewing manager, which reads stands through {@code BlockRef.container()}.
 */
public interface RecipeHost {
  /** Registers or replaces the recipe. Returns false when the platform rejected it. */
  boolean register(RecipeSpec recipe);

  void unregister(Id key);

  Collection<Id> registered();

  void discover(PlayerRef player, Id key);

  void undiscover(PlayerRef player, Id key);

  /** The server recipe with this key, Adapt's or any other, as the server holds it now. */
  Optional<RecipeView> recipe(Id key);

  /**
   * Every server recipe whose result has the stack's type and the stack's damage, like Bukkit's
   * {@code Server#getRecipesFor}.
   */
  List<RecipeView> recipesFor(ItemRef stack);
}
