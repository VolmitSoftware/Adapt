package art.arcane.adapt.platform.entity.display;

import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

/** Display entity that shows an item stack. */
@ApiStatus.AvailableSince("1.0.0")
public interface ItemDisplayRef extends DisplayRef {
  /** Item shown. */
  ItemRef item();

  /** Replaces the item shown. */
  void setItem(ItemRef item);

  /** Item model transform used to render the item. */
  ItemDisplayMode displayMode();

  /** Picks the item model transform used to render the item. */
  void setDisplayMode(ItemDisplayMode mode);
}
