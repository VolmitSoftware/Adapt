package art.arcane.adapt.platform.entity.display;

import art.arcane.adapt.platform.item.ItemRef;

public interface ItemDisplayRef extends DisplayRef {
  ItemRef item();

  void setItem(ItemRef item);

  ItemDisplayMode displayMode();

  void setDisplayMode(ItemDisplayMode mode);
}
