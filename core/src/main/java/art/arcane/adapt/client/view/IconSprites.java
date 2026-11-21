package art.arcane.adapt.client.view;

import java.util.Locale;

/**
 * Where the client mod looks for icon art. A sprite id is a texture file id, {@code adapt:textures/gui/<kind>/<name>.png}:
 * <ul>
 *   <li>{@code skill/<skill id>.png}, e.g. {@code skill/agility.png}</li>
 *   <li>{@code adaptation/<adaptation id>.png}, e.g. {@code adaptation/agility-wall-jump.png}</li>
 *   <li>{@code toast/<kind>.png}, kind one of {@code level_up unlock discovery knowledge generic challenge}</li>
 *   <li>{@code hud/<status>.png}, the HUD status name such as {@code rift_gate}</li>
 * </ul>
 * Icons are square PNGs, 16x16 or a larger power of two up to 512 for high-resolution art. Names are lower case;
 * characters a resource path cannot hold become {@code _}. A resource pack, including a server resource pack, replaces
 * or adds files at the same paths. When no file exists the client draws the icon's item instead.
 */
public final class IconSprites {
  public static final String NAMESPACE = "adapt";
  public static final String ROOT = "textures/gui/";

  private IconSprites() {
  }

  public static String skill(String skillId) {
    return texture("skill", skillId);
  }

  public static String adaptation(String adaptationId) {
    return texture("adaptation", adaptationId);
  }

  public static String toast(String kind) {
    return texture("toast", kind);
  }

  public static String hud(String name) {
    return texture("hud", name);
  }

  /** {@code icon} with {@code spriteId} when it names none yet; an explicit sprite id is kept. */
  public static IconRef withSprite(IconRef icon, String spriteId) {
    if (icon == null || icon.spriteId() != null || spriteId == null) {
      return icon;
    }
    return new IconRef(icon.itemId(), icon.customModelData(), icon.itemModelId(), spriteId);
  }

  /** {@code adapt:textures/gui/<kind>/<name>.png} for a catalog id or kind name. */
  public static String texture(String kind, String name) {
    return NAMESPACE + ':' + ROOT + kind + '/' + pathSegment(name) + ".png";
  }

  /** {@code name} lower-cased with every character outside {@code [a-z0-9_.-]} replaced by {@code _}. */
  public static String pathSegment(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    StringBuilder segment = new StringBuilder(lower.length());
    for (int index = 0; index < lower.length(); index++) {
      char character = lower.charAt(index);
      boolean allowed = (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') || character == '_'
          || character == '-' || character == '.';
      segment.append(allowed ? character : '_');
    }
    return segment.isEmpty() ? "_" : segment.toString();
  }
}
