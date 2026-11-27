package art.arcane.adapt.client.view;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.engine.skill.LegacyContentNames;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where the client mod looks for icon art. A sprite id is a texture file id, {@code <namespace>:textures/gui/<kind>/<file>.png}.
 * Skill, adaptation, preference, emblem and trim art lives in the namespace of the content it belongs to, named by
 * {@link #file(Id)}; the rest lives in {@value #NAMESPACE}:
 * <ul>
 *   <li>{@code skill/<skill file>.png}, e.g. {@code adapt:textures/gui/skill/agility.png} or
 *   {@code alchemist:textures/gui/skill/alchemy.png}</li>
 *   <li>{@code adaptation/<adaptation file>.png}, e.g. {@code adapt:textures/gui/adaptation/agility-wall-jump.png} or
 *   {@code alchemist:textures/gui/adaptation/alchemy/transmute.png}</li>
 *   <li>{@code toast/<kind>.png}, kind one of {@code level_up unlock discovery knowledge generic challenge signature}</li>
 *   <li>{@code hud/<status>.png}, the HUD status name such as {@code rift_gate}</li>
 *   <li>{@code preference/<owner file>/<preference id>/<choice>.png} for a preference choice, falling back to
 *   {@code adapt:textures/gui/preference/common/choice/<choice>.png}; the owner is the adaptation, or the skill for skill
 *   preferences</li>
 *   <li>{@code preference/<owner file>/<preference id>.png} for a toggle group member and
 *   {@code preference/<owner file>/group-<group id>.png} for a toggle group heading</li>
 *   <li>{@code emblem/<lead file>.png} under {@code trim/<partner file>.png} for a mutated skill, each in its own skill's
 *   namespace, see {@link MutationIcons}</li>
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

  public static String skill(Id skill) {
    return content(skill, "skill");
  }

  public static String adaptation(Id adaptation) {
    return content(adaptation, "adaptation");
  }

  /** A mutation tool's icon. */
  public static String tool(Id tool) {
    return content(tool, "tool");
  }

  /** A mutated skill's emblem drawn for its lead skill. */
  public static String emblem(Id lead) {
    return content(lead, "emblem");
  }

  /** A mutated skill's trim drawn for its partner skill. */
  public static String trim(Id partner) {
    return content(partner, "trim");
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

  public static String preferenceChoice(Id owner, String preferenceId, String value) {
    return preference(owner, file(owner) + '/' + pathSegment(preferenceId) + '/' + pathSegment(value));
  }

  public static String commonChoice(String value) {
    return NAMESPACE + ':' + ROOT + "preference/common/choice/" + pathSegment(value) + ".png";
  }

  public static String preferenceMember(Id owner, String preferenceId) {
    return preference(owner, file(owner) + '/' + pathSegment(preferenceId));
  }

  public static String preferenceGroup(Id owner, String groupId) {
    return preference(owner, file(owner) + "/group-" + pathSegment(groupId));
  }

  private static String preference(Id owner, String path) {
    return owner.namespace() + ':' + ROOT + "preference/" + path + ".png";
  }

  private static String content(Id id, String kind) {
    return id.namespace() + ':' + ROOT + kind + '/' + file(id) + ".png";
  }

  /**
   * The file name content art is filed under, below its kind's folder in the id's namespace: the flat legacy name for
   * {@code adapt} content (see {@link LegacyContentNames}), the id's path for any other, each segment made a valid
   * resource path segment.
   */
  public static String file(Id id) {
    if (Id.ADAPT.equals(id.namespace())) {
      return pathSegment(LegacyContentNames.of(id));
    }
    String[] segments = id.path().split("/", -1);
    StringBuilder file = new StringBuilder(id.path().length());
    for (String segment : segments) {
      if (!file.isEmpty()) {
        file.append('/');
      }
      file.append(pathSegment(segment));
    }
    return file.toString();
  }

  /**
   * The id of content a view names by its id string or line key: a mutated skill's {@code <lead>+<partner>} names
   * its mutated skill id, and a bare path is in {@value #NAMESPACE}. Null when the key is neither.
   */
  public static Id contentId(String key) {
    if (key == null) {
      return null;
    }
    SkillPair mutation = PairKeys.parse(key);
    return mutation != null ? mutation.id() : Id.tryParseAdapt(key);
  }

  /** {@code icon} with {@code spriteId} when it names none yet; an explicit sprite id is kept. */
  public static IconRef withSprite(IconRef icon, String spriteId) {
    if (icon == null || icon.spriteId() != null || spriteId == null) {
      return icon;
    }
    return new IconRef(icon.itemId(), icon.customModelData(), icon.itemModelId(), spriteId);
  }

  /** {@code adapt:textures/gui/<kind>/<name>.png} for Adapt's own art. */
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
