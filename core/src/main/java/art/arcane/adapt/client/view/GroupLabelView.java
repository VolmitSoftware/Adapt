package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/** The names of a skill's adaptation groups, in the order of its catalog {@code rowGroups}. */
public record GroupLabelView(String skillId, List<String> labels) {
  public GroupLabelView {
    Objects.requireNonNull(skillId, "skillId");
    labels = List.copyOf(labels);
  }
}
