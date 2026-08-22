package art.arcane.adapt.localization;

import art.arcane.volmlib.util.localization.TextKey;

import java.util.Objects;

public record SkillPresentation(TextKey name, TextKey icon, TextKey description) {
  public SkillPresentation {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(description, "description");
  }

  public static SkillPresentation of(TextKey name, TextKey icon, TextKey description) {
    return new SkillPresentation(name, icon, description);
  }
}
