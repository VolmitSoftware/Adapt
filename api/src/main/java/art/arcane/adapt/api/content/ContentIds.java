package art.arcane.adapt.api.content;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The grammar of content ids: skills, adaptations, custom items, recipes and anything else an addon registers.
 *
 * <p>A content id is an {@link Id} with a tighter alphabet than a vanilla resource location, because one id has to
 * serve as a message key, a permission node, a config path and a save key:
 *
 * <ul>
 *   <li>namespace: {@code [a-z][a-z0-9_-]{0,31}}</li>
 *   <li>path: one or more segments of {@code [a-z0-9][a-z0-9_-]*} joined by {@code /}, at most 160 characters</li>
 * </ul>
 *
 * <p>A dot is legal in a vanilla id and illegal here: a path's {@code /} becomes a dot in message keys and
 * permission nodes, so a dot in the id itself would make two ids share one key.
 *
 * <p>A skill id has a single segment ({@code alchemist:alchemy}). An adaptation id starts with its home skill's path
 * by convention ({@code alchemist:alchemy/transmute}); the engine never reads the owning skill out of the id.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class ContentIds {
  /** The longest namespace a content id may have. */
  public static final int MAX_NAMESPACE_LENGTH = 32;
  /** The longest path a content id may have. */
  public static final int MAX_PATH_LENGTH = 160;

  private static final Set<String> RESERVED_NAMESPACES = Set.of(Id.ADAPT, Id.MINECRAFT);

  private ContentIds() {
  }

  /** True for {@code adapt} and {@code minecraft}, which no addon may claim. */
  public static boolean isReserved(String namespace) {
    return RESERVED_NAMESPACES.contains(namespace);
  }

  /** Whether {@code namespace} fits the namespace grammar; false for null. */
  public static boolean validNamespace(String namespace) {
    if (namespace == null || namespace.isEmpty() || namespace.length() > MAX_NAMESPACE_LENGTH) {
      return false;
    }
    char first = namespace.charAt(0);
    if (first < 'a' || first > 'z') {
      return false;
    }
    for (int index = 1; index < namespace.length(); index++) {
      if (!segmentChar(namespace.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  /** Whether {@code path} fits the path grammar, with no empty segment; false for null. */
  public static boolean validPath(String path) {
    if (path == null || path.isEmpty() || path.length() > MAX_PATH_LENGTH) {
      return false;
    }
    boolean segmentStart = true;
    for (int index = 0; index < path.length(); index++) {
      char character = path.charAt(index);
      if (character == '/') {
        if (segmentStart) {
          return false;
        }
        segmentStart = true;
        continue;
      }
      if (segmentStart ? !alphanumeric(character) : !segmentChar(character)) {
        return false;
      }
      segmentStart = false;
    }
    return !segmentStart;
  }

  /** Whether both parts of {@code id} fit the content grammar; false for null. */
  public static boolean valid(Id id) {
    return id != null && validNamespace(id.namespace()) && validPath(id.path());
  }

  /** Returns {@code id}, or throws naming {@code what} when it breaks the content grammar. */
  public static Id require(Id id, String what) {
    Objects.requireNonNull(id, what);
    if (!valid(id)) {
      throw new IllegalArgumentException("Invalid " + what + " id '" + id + "': namespace must match "
          + "[a-z][a-z0-9_-]*, path must be [a-z0-9][a-z0-9_-]* segments joined by '/'");
    }
    return id;
  }

  /** Like {@link #require(Id, String)} for an id that must have a single path segment, such as a skill. */
  public static Id requireSingleSegment(Id id, String what) {
    require(id, what);
    if (id.path().indexOf('/') >= 0) {
      throw new IllegalArgumentException("A " + what + " id has one path segment, got '" + id + "'");
    }
    return id;
  }

  /**
   * Parses what an admin types in a command or config file. A bare path gets the {@code adapt} namespace, the way
   * vanilla gives a bare path {@code minecraft}.
   */
  public static Id parse(String value) {
    Objects.requireNonNull(value, "value");
    int colon = value.indexOf(':');
    Id id = colon < 0 ? Id.adapt(value) : Id.of(value.substring(0, colon), value.substring(colon + 1));
    return require(id, "content");
  }

  /** As {@link #parse(String)}, empty for null, an empty string or an id that breaks the grammar. */
  public static Optional<Id> tryParse(String value) {
    if (value == null || value.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(parse(value));
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }

  /** The conventional id of an adaptation whose home is {@code skill}: {@code <namespace>:<skill>/<name>}. */
  public static Id child(Id skill, String name) {
    return require(Id.of(skill.namespace(), skill.path() + "/" + name), "content");
  }

  /** The id with its path's {@code /} turned into dots: {@code alchemist.alchemy.transmute}. */
  public static String dotted(Id id) {
    return id.namespace() + "." + id.path().replace('/', '.');
  }

  /** The permission node that lets a player use the content: {@code adapt.use.<namespace>.<path>}. */
  public static String usePermission(Id id) {
    return "adapt.use." + dotted(id);
  }

  /** The config file of the content below its kind's folder: {@code <namespace>/<path>.toml}. */
  public static String configPath(Id id) {
    return id.namespace() + "/" + id.path() + ".toml";
  }

  /** What an admin sees and types: the bare path for {@code adapt} content, the full id otherwise. */
  public static String display(Id id) {
    return Id.ADAPT.equals(id.namespace()) ? id.path() : id.toString();
  }

  /**
   * {@code preferred}, or {@code fallback} when the running server has no item {@code preferred} but has
   * {@code fallback}; for icons that use an item older versions lack. {@code preferred} while no core runs, so
   * field initializers and config defaults can call it.
   */
  @ApiStatus.Experimental
  public static Id itemOr(Id preferred, Id fallback) {
    Objects.requireNonNull(preferred, "preferred");
    Objects.requireNonNull(fallback, "fallback");
    return Engine.isRunning() ? Engine.bridge().progress().itemOr(preferred, fallback) : preferred;
  }

  /**
   * The material name {@code preferred}, or {@code fallback} when the running server has neither an item nor a block
   * {@code preferred} but has {@code fallback}; for recipe keys that use a material older versions lack.
   * {@code preferred} while no core runs, so field initializers and config defaults can call it.
   */
  @ApiStatus.Experimental
  public static String materialOr(String preferred, String fallback) {
    Objects.requireNonNull(preferred, "preferred");
    Objects.requireNonNull(fallback, "fallback");
    return Engine.isRunning() ? Engine.bridge().progress().materialOr(preferred, fallback) : preferred;
  }

  private static boolean alphanumeric(char character) {
    return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
  }

  private static boolean segmentChar(char character) {
    return alphanumeric(character) || character == '_' || character == '-';
  }
}
