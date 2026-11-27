package art.arcane.adapt.api.content;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * A labelled row of adaptations on a skill's pages. Adaptations in no group follow the last group, in registration
 * order; that is where adaptations other addons add to the skill land.
 *
 * @param label       the row's heading
 * @param adaptations the ids shown in the row, in order
 */
@ApiStatus.AvailableSince("1.0.0")
public record AdaptationGroup(Text label, List<Id> adaptations) {
  public AdaptationGroup {
    Objects.requireNonNull(label, "label");
    adaptations = List.copyOf(adaptations);
  }
}
