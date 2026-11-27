package art.arcane.adapt.engine.mutation;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

/**
 * Reads a mutated line key typed by a person or sent by a client, where each side may omit its namespace:
 * {@code swords+stealth} names the same pair as {@code adapt:swords+adapt:stealth}.
 */
public final class PairKeys {
  private static final char SEPARATOR = '+';

  private PairKeys() {
  }

  /** The pair a line key names, or null when it names none. */
  public static @Nullable SkillPair parse(@Nullable String key) {
    if (key == null) {
      return null;
    }
    int split = key.indexOf(SEPARATOR);
    if (split <= 0 || split == key.length() - 1 || key.indexOf(SEPARATOR, split + 1) >= 0) {
      return null;
    }
    Id lead = Id.tryParseAdapt(key.substring(0, split).trim());
    Id partner = Id.tryParseAdapt(key.substring(split + 1).trim());
    if (lead == null || partner == null || lead.equals(partner)) {
      return null;
    }
    try {
      return new SkillPair(lead, partner);
    } catch (IllegalArgumentException notASkill) {
      return null;
    }
  }

  public static boolean isPairKey(@Nullable String key) {
    return parse(key) != null;
  }
}
