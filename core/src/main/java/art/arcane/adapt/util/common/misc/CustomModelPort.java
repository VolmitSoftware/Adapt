package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.platform.item.ItemRef;

import java.net.URL;

/**
 * Item decorations {@link CustomModel} needs beyond the item SPI: custom model data numbers, player
 * head skins and hiding vanilla component tooltips.
 */
public interface CustomModelPort {
  CustomModelPort NONE = new CustomModelPort() {
    @Override
    public void setCustomModelData(ItemRef item, int model) {
    }

    @Override
    public void setHeadTexture(ItemRef item, String texture, URL skin) {
    }

    @Override
    public void hideAdditionalTooltip(ItemRef item) {
    }
  };

  void setCustomModelData(ItemRef item, int model);

  /** Applies the skin when the item is a player head; does nothing for other items. */
  void setHeadTexture(ItemRef item, String texture, URL skin);

  void hideAdditionalTooltip(ItemRef item);
}
