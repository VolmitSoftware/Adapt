package art.arcane.adapt.pack.manifest;

import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.platform.value.Id;

/**
 * Sprite ids in the pack's form. A sprite {@code ns:gui/skill/x} lives at {@code assets/ns/textures/gui/skill/x.png} and
 * is drawn by item model {@code ns:adapt_ui/gui/skill/x}; stacked sprites use their bottom layer.
 */
public final class PackSprites {
  private static final String TEXTURES = "textures/";
  private static final String PNG = ".png";
  private static final String MODEL_ROOT = "adapt_ui/";

  private PackSprites() {
  }

  /** {@code ns:gui/<kind>/<name>} for a sprite id with or without the texture path and extension, or null. */
  public static String packId(String spriteId) {
    if (spriteId == null || spriteId.isBlank()) {
      return null;
    }
    String bottom = IconSprites.layers(spriteId).get(0).spriteId();
    int colon = bottom.indexOf(':');
    String namespace = colon < 0 ? IconSprites.NAMESPACE : bottom.substring(0, colon);
    String path = colon < 0 ? bottom : bottom.substring(colon + 1);
    if (path.startsWith(TEXTURES)) {
      path = path.substring(TEXTURES.length());
    }
    if (path.endsWith(PNG)) {
      path = path.substring(0, path.length() - PNG.length());
    }
    return path.isEmpty() ? null : namespace + ':' + path;
  }

  /** The item model that draws {@code spriteId}, or null when it names no sprite. */
  public static Id itemModel(String spriteId) {
    String packId = packId(spriteId);
    if (packId == null) {
      return null;
    }
    int colon = packId.indexOf(':');
    return Id.of(packId.substring(0, colon), MODEL_ROOT + packId.substring(colon + 1));
  }
}
