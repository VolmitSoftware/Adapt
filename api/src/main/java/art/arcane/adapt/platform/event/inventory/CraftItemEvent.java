package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * Click on a crafting result slot. Click listeners receive it too. Shift-clicks may craft several
 * times; {@link ItemCraftedEvent} reports what was actually made. currentItem() is the result item. Mutable: result
 * item, cancelled (nothing is crafted).
 */
@ApiStatus.AvailableSince("1.0.0")
public class CraftItemEvent extends InventoryClickEvent {
  private final Id recipeKey;
  private final RecipeKind recipeKind;
  private final List<ItemRef> matrix;

  public CraftItemEvent(PlayerRef player, InventoryKind viewKind, int slot, int rawSlot, ClickKind click,
                        InventoryAction action, int hotbarButton, ItemRef result, ItemRef cursor, Id recipeKey,
                        RecipeKind recipeKind, List<ItemRef> matrix) {
    super(player, viewKind, viewKind, slot, rawSlot, SlotKind.RESULT, click, action, hotbarButton, result, cursor);
    this.recipeKey = recipeKey;
    this.recipeKind = Objects.requireNonNull(recipeKind, "recipeKind");
    this.matrix = List.copyOf(matrix);
  }

  /** Recipe id, or null for special recipes without one. */
  public Id recipeKey() {
    return recipeKey;
  }

  /** Whether the recipe is shaped, shapeless or another kind. */
  public RecipeKind recipeKind() {
    return recipeKind;
  }

  /** Grid contents in slot order; empty refs for empty cells. Read-only. */
  public List<ItemRef> matrix() {
    return matrix;
  }

  /**
   * Shape of a crafting recipe.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum RecipeKind {
    SHAPED,
    SHAPELESS,
    /** Any recipe that is neither shaped nor shapeless, including special recipes. */
    OTHER
  }
}
