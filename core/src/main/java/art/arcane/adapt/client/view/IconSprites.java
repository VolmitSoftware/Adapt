package art.arcane.adapt.client.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where the client mod looks for icon art. A sprite id is a texture file id, {@code adapt:textures/gui/<kind>/<name>.png}:
 * <ul>
 *   <li>{@code skill/<skill id>.png}, e.g. {@code skill/agility.png}</li>
 *   <li>{@code adaptation/<adaptation id>.png}, e.g. {@code adaptation/agility-wall-jump.png}</li>
 *   <li>{@code toast/<kind>.png}, kind one of {@code level_up unlock discovery knowledge generic challenge signature}</li>
 *   <li>{@code hud/<status>.png}, the HUD status name such as {@code rift_gate}</li>
 *   <li>{@code preference/<owner>/<preference id>/<choice>.png} for a preference choice, falling back to
 *   {@code preference/common/choice/<choice>.png}; the owner is the adaptation id, or the skill id for skill
 *   preferences</li>
 *   <li>{@code preference/<owner>/<preference id>.png} for a toggle group member and
 *   {@code preference/<owner>/group-<group id>.png} for a toggle group heading</li>
 *   <li>{@code emblem/<lead id>.png} under {@code trim/<partner id>.png} for a mutated skill, see {@link MutationIcons}</li>
 *   <li>{@code glyph/<recipe>.png} with {@code glyph/<recipe>-lead.png} and {@code glyph/<recipe>-partner.png} tinted
 *   with the parents' colours for a mutated adaptation, and {@code signature/<name>.png} for a signature</li>
 *   <li>{@code tool/<tool id>.png} for a mutation tool and {@code mark/<name>.png} for the book's resource and button
 *   marks</li>
 * </ul>
 * Icons are square PNGs, 16x16 or a larger power of two up to 512 for high-resolution art. Names are lower case;
 * characters a resource path cannot hold become {@code _}. A resource pack, including a server resource pack, replaces
 * or adds files at the same paths. When no file exists the client draws the icon's item instead.
 *
 * <p>A sprite id may stack several textures, bottom first, joined by {@value #LAYER_SEPARATOR}; a layer followed by
 * {@value #TINT_MARK} and six hex digits is multiplied by that colour. The client draws the stack only when every layer
 * exists.
 */
public final class IconSprites {
  public static final String NAMESPACE = "adapt";
  public static final String ROOT = "textures/gui/";
  public static final char LAYER_SEPARATOR = '|';
  public static final char TINT_MARK = '#';
  public static final int NO_TINT = 0xFFFFFF;
  private static final Pattern LAYERS = Pattern.compile(Pattern.quote(String.valueOf(LAYER_SEPARATOR)));

  private IconSprites() {
  }

  /** One texture of a stacked sprite id and the RGB colour it is multiplied by. */
  public record Layer(String spriteId, int tint) {
  }

  /** {@code spriteId} multiplied by the RGB {@code tint}, as one layer of {@link #stack}. */
  public static String tinted(String spriteId, int tint) {
    return spriteId + TINT_MARK + String.format(Locale.ROOT, "%06x", tint & NO_TINT);
  }

  /** The sprite id that draws {@code layers} on top of each other, the first at the bottom. */
  public static String stack(String... layers) {
    return String.join(String.valueOf(LAYER_SEPARATOR), layers);
  }

  /** The layers {@code spriteId} names, one untinted layer for a plain id. */
  public static List<Layer> layers(String spriteId) {
    List<Layer> layers = new ArrayList<>(3);
    for (String part : LAYERS.split(spriteId, -1)) {
      int mark = part.lastIndexOf(TINT_MARK);
      boolean tinted = mark >= 0 && part.length() - mark == 7;
      layers.add(new Layer(tinted ? part.substring(0, mark) : part,
          tinted ? Integer.parseInt(part.substring(mark + 1), 16) : NO_TINT));
    }
    return List.copyOf(layers);
  }

  public static String skill(String skillId) {
    return texture("skill", skillId);
  }

  public static String adaptation(String adaptationId) {
    return texture("adaptation", adaptationId);
  }

  /** The book's drawn mark {@code name}, such as {@code knowledge}, {@code power} or {@code swap}. */
  public static String mark(String name) {
    return texture("mark", name);
  }

  public static String toast(String kind) {
    return texture("toast", kind);
  }

  public static String hud(String name) {
    return texture("hud", name);
  }

  public static String preferenceChoice(String ownerId, String preferenceId, String value) {
    return preference(pathSegment(ownerId) + '/' + pathSegment(preferenceId) + '/' + pathSegment(value));
  }

  public static String commonChoice(String value) {
    return preference("common/choice/" + pathSegment(value));
  }

  public static String preferenceMember(String ownerId, String preferenceId) {
    return preference(pathSegment(ownerId) + '/' + pathSegment(preferenceId));
  }

  public static String preferenceGroup(String ownerId, String groupId) {
    return preference(pathSegment(ownerId) + "/group-" + pathSegment(groupId));
  }

  private static String preference(String path) {
    return NAMESPACE + ':' + ROOT + "preference/" + path + ".png";
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
