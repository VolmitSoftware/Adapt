package art.arcane.adapt.api.skill;

import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Objects;

/** A named run of a skill's adaptations, by id, that its pages show together under {@code label}. */
public record AdaptationGroup(TextKey label, List<String> adaptations) {
  public AdaptationGroup {
    Objects.requireNonNull(label, "label");
    adaptations = List.copyOf(adaptations);
  }

  public static AdaptationGroup of(TextKey label, String... adaptations) {
    return new AdaptationGroup(label, List.of(adaptations));
  }
}
