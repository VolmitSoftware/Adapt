package art.arcane.adapt.engine.skill;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.platform.value.Id;

import java.util.Arrays;
import java.util.List;

/**
 * The flat name a built-in skill, adaptation or mutation tool had before ids were namespaced, such as
 * {@code herbalism-luck} for {@code adapt:herbalism/luck}. Sprite files, item looks and the comments written into
 * content config files are still named this way; nothing else may use it.
 */
public final class LegacyContentNames {
  private LegacyContentNames() {
  }

  /**
   * The flat name of an id or line key in its string form, as views carry them: a mutation key names its mutated skill.
   * A string that is neither is returned unchanged.
   */
  public static String of(String key) {
    SkillPair mutation = PairKeys.parse(key);
    if (mutation != null) {
      return of(mutation.id());
    }
    Id id = key.indexOf(':') < 0 ? null : Id.tryParse(key);
    return id == null ? key : of(id);
  }

  /** The flat name of a built-in id, or the id's string form outside the {@code adapt} namespace. */
  public static String of(Id id) {
    if (!Id.ADAPT.equals(id.namespace())) {
      return id.toString();
    }
    List<String> parts = Arrays.asList(id.path().split("/"));
    if (parts.size() == 1) {
      return parts.get(0);
    }
    if (parts.size() == 3) {
      String kind = parts.get(1);
      String owned = parts.get(0) + "-" + flat(parts.get(2));
      switch (kind) {
        case "upgrade":
          return "upgraded-" + owned;
        case "block":
          return "block-" + owned;
        case "tool":
          return "tool-" + owned;
        default:
          break;
      }
    }
    if (parts.size() >= 4 && parts.get(0).equals("mutated") && parts.get(2).equals(Id.ADAPT)) {
      String pair = parts.get(1) + "-" + parts.get(3);
      List<String> rest = parts.subList(4, parts.size());
      if (rest.isEmpty()) {
        return "mutated-" + pair;
      }
      if (rest.size() == 1) {
        return "mutated-" + pair + "-" + flat(rest.get(0));
      }
      if (rest.size() == 2 && rest.get(0).equals("signature")) {
        return "signature-" + pair + "-" + flat(rest.get(1));
      }
      if (rest.size() == 2 && rest.get(0).equals("tool")) {
        return "tool-signature-" + pair + "-" + flat(rest.get(1));
      }
    }
    return flat(String.join("-", parts));
  }

  private static String flat(String segment) {
    return segment.replace('_', '-');
  }
}
