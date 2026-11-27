package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Server recipe registration. Register during startup or reload on the server/global thread;
 * adapters resend recipe books to online players after changes. Brewing stays in Adapt's own
 * brewing manager, which reads stands through {@code BlockRef.container()}.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface RecipeHost {
  /**
   * Registers the recipe. Removing the returned registration is the only way to remove it; empty when the platform
   * rejected the recipe.
   *
   * @throws IllegalArgumentException when a recipe with the same key is already registered through this host
   */
  Optional<RecipeRegistration> register(RecipeSpec recipe);

  /** Keys of the recipes registered through this host, as a snapshot. */
  Collection<Id> registered();

  /** Adds the recipe to the player's recipe book; nothing happens when the player is offline. */
  void discover(PlayerRef player, Id key);

  /** Removes the recipe from the player's recipe book; nothing happens when the player is offline. */
  void undiscover(PlayerRef player, Id key);

  /** The server recipe with this key, Adapt's or any other, as the server holds it now. */
  Optional<RecipeView> recipe(Id key);

  /**
   * Every server recipe whose result has the stack's type and the stack's damage, like Bukkit's
   * {@code Server#getRecipesFor}.
   */
  List<RecipeView> recipesFor(ItemRef stack);
}
