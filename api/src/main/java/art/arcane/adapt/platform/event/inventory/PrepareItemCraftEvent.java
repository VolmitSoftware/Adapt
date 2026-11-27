package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * A crafting grid changed and its result is about to be shown. Mutable: result (null or empty
 * clears it).
 */
@ApiStatus.AvailableSince("1.0.0")
public class PrepareItemCraftEvent extends AdaptEvent {
  private final PlayerRef player;
  private final List<ItemRef> matrix;
  private final Id recipeKey;
  private final CraftItemEvent.RecipeKind recipeKind;
  private final boolean repair;
  private ItemRef result;

  public PrepareItemCraftEvent(PlayerRef player, List<ItemRef> matrix, Id recipeKey, CraftItemEvent.RecipeKind recipeKind, boolean repair, ItemRef result) {
    this.player = Objects.requireNonNull(player, "player");
    this.matrix = List.copyOf(matrix);
    this.recipeKey = recipeKey;
    this.recipeKind = Objects.requireNonNull(recipeKind, "recipeKind");
    this.repair = repair;
    this.result = result;
  }

  /** The player viewing the grid. */
  public PlayerRef player() {
    return player;
  }

  /** Read-only grid contents in slot order; empty refs for empty cells. */
  public List<ItemRef> matrix() {
    return matrix;
  }

  /** Id of the matched recipe, or null when none matched or the recipe has no id. */
  public Id recipeKey() {
    return recipeKey;
  }

  /** Whether the matched recipe is shaped, shapeless or another kind. */
  public CraftItemEvent.RecipeKind recipeKind() {
    return recipeKind;
  }

  /** True when the grid combines two damaged items into a repair. */
  public boolean repair() {
    return repair;
  }

  /** The result about to be shown, or null when there is none. */
  public ItemRef result() {
    return result;
  }

  /** Replaces the result; null or an empty item clears it. */
  public void setResult(ItemRef result) {
    this.result = result;
  }
}
