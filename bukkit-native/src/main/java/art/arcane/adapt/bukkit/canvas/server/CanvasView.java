package art.arcane.adapt.bukkit.canvas.server;

import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

/** The Bukkit view of a canvas: its bottom inventory is the stand-in container, never the player's inventory. */
final class CanvasView extends CraftInventoryView<CanvasMenu, Inventory> {
  private final Inventory bottom;

  CanvasView(HumanEntity viewer, Inventory top, CanvasMenu menu, Inventory bottom) {
    super(viewer, top, menu);
    this.bottom = bottom;
  }

  @Override
  public Inventory getBottomInventory() {
    return bottom;
  }
}
