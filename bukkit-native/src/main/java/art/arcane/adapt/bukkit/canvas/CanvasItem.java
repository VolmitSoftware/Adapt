package art.arcane.adapt.bukkit.canvas;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * An item for a canvas slot: the stack, and its name and lore as text component JSON that replace the stack's own
 * (null name and empty lore keep them).
 */
public record CanvasItem(ItemStack stack, String nameJson, List<String> loreJson) {
  public CanvasItem {
    loreJson = loreJson == null ? List.of() : List.copyOf(loreJson);
  }
}
