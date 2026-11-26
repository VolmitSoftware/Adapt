package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/**
 * What one parent brings to a mutation and what it loses. {@code lost} lists its originals that go away and every
 * adaptation of it the merge unlearns; {@code surplus} names the levels and knowledge it gives up, empty when it is the
 * lower parent. An empty {@code skillId} is a side with no skill chosen.
 */
public record MutationSide(String skillId, int level, List<MutationEntry> upgrades, List<MutationEntry> kept,
                           List<MutationEntry> tools, List<MutationEntry> lost, String surplus) {
  public static final MutationSide NONE = new MutationSide("", 0, List.of(), List.of(), List.of(), List.of(), "");

  public MutationSide {
    Objects.requireNonNull(skillId, "skillId");
    Objects.requireNonNull(surplus, "surplus");
    upgrades = List.copyOf(upgrades);
    kept = List.copyOf(kept);
    tools = List.copyOf(tools);
    lost = List.copyOf(lost);
  }
}
