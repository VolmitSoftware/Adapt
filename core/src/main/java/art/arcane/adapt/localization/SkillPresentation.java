package art.arcane.adapt.localization;

import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Objects;

/**
 * A skill's localized text. The quip is the one-liner on the toast when the skill is discovered; an adaptation of the
 * skill unlocks with one of its unlock quips.
 */
public record SkillPresentation(TextKey name, TextKey icon, TextKey description, TextKey quip, List<TextKey> unlockQuips) {
  public SkillPresentation {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(quip, "quip");
    unlockQuips = List.copyOf(unlockQuips);
    if (unlockQuips.isEmpty()) {
      throw new IllegalArgumentException("unlockQuips");
    }
  }

  public static SkillPresentation of(TextKey name, TextKey icon, TextKey description, TextKey quip, TextKey... unlockQuips) {
    return new SkillPresentation(name, icon, description, quip, List.of(unlockQuips));
  }
}
