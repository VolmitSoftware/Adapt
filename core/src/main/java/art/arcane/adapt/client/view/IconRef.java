package art.arcane.adapt.client.view;

import java.util.Objects;

/**
 * An icon as the client draws it: the sprite when the client ships one, otherwise the item with its custom model data
 * and item model. {@code itemModelId} and {@code spriteId} may be null.
 */
public record IconRef(String itemId, int customModelData, String itemModelId, String spriteId) {
  public IconRef {
    Objects.requireNonNull(itemId, "itemId");
  }

  public static IconRef item(String itemId) {
    return new IconRef(itemId, 0, null, null);
  }
}
