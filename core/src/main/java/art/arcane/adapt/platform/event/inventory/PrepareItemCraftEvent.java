package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;

/**
 * A crafting grid changed and its result is about to be shown. Mutable: result (null or empty
 * clears it).
 */
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

  public PlayerRef player() {
    return player;
  }

  public List<ItemRef> matrix() {
    return matrix;
  }

  public Id recipeKey() {
    return recipeKey;
  }

  public CraftItemEvent.RecipeKind recipeKind() {
    return recipeKind;
  }

  public boolean repair() {
    return repair;
  }

  public ItemRef result() {
    return result;
  }

  public void setResult(ItemRef result) {
    this.result = result;
  }
}
