package art.arcane.adapt.api.content;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Another adaptation a player must hold before learning this one. Written in config as {@code <id>@<level>}, for
 * example {@code alchemist:alchemy/distill@2}. The level follows the last {@code @}; a colon cannot separate it because
 * ids contain one. An id written without a namespace is in the namespace of the adaptation whose config lists it, so
 * {@code alchemy/distill@2} in an {@code alchemist} adaptation's config means {@code alchemist:alchemy/distill}.
 *
 * @param adaptation the id of the adaptation the player must hold
 * @param level      the lowest level of it that satisfies the requirement, at least 1
 */
@ApiStatus.AvailableSince("1.0.0")
public record AdaptationRequirement(Id adaptation, int level) {
  /** Separates the id from the level in the written form. */
  public static final char LEVEL_SEPARATOR = '@';

  public AdaptationRequirement {
    ContentIds.require(adaptation, "required adaptation");
    if (level < 1) {
      throw new IllegalArgumentException("A requirement level is at least 1");
    }
  }

  /**
   * Parses one config entry of an adaptation in {@code namespace}; empty when the entry is not {@code <id>@<level>}
   * with a level of at least 1.
   */
  public static Optional<AdaptationRequirement> parse(String entry, String namespace) {
    Objects.requireNonNull(namespace, "namespace");
    if (entry == null) {
      return Optional.empty();
    }
    String trimmed = entry.trim().toLowerCase(Locale.ROOT);
    int split = trimmed.lastIndexOf(LEVEL_SEPARATOR);
    if (split <= 0) {
      return Optional.empty();
    }
    String levelText = trimmed.substring(split + 1).trim();
    if (levelText.isEmpty() || levelText.length() > 9) {
      return Optional.empty();
    }
    for (int index = 0; index < levelText.length(); index++) {
      char digit = levelText.charAt(index);
      if (digit < '0' || digit > '9') {
        return Optional.empty();
      }
    }
    String written = trimmed.substring(0, split).trim();
    Optional<Id> id = ContentIds.tryParse(written.indexOf(':') < 0 ? namespace + ":" + written : written);
    int level = Integer.parseInt(levelText);
    if (id.isEmpty() || level < 1) {
      return Optional.empty();
    }
    return Optional.of(new AdaptationRequirement(id.get(), level));
  }

  /** The written form, such as {@code adapt:herbalism/luck@2}. */
  public String entry() {
    return adaptation + String.valueOf(LEVEL_SEPARATOR) + level;
  }
}
