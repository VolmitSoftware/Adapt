package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/**
 * One adaptation. {@code name} is the plain localized name without colour codes. The per-level lists hold
 * {@code maxLevel} entries where index 0 is level 1: knowledge to buy that level, knowledge refunded when it is
 * unlearned, power it uses and its stat lines.
 */
public record AdaptationView(
    String id,
    String skillId,
    String name,
    String description,
    IconRef icon,
    boolean enabled,
    int maxLevel,
    boolean permanent,
    List<Integer> knowledgeCosts,
    List<Integer> refunds,
    List<Integer> powerCosts,
    List<RequirementView> requirements,
    List<List<String>> statLines,
    List<PreferenceView> preferences,
    List<PreferenceGroupView> preferenceGroups
) {
  public AdaptationView {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(skillId, "skillId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(icon, "icon");
    knowledgeCosts = List.copyOf(knowledgeCosts);
    refunds = List.copyOf(refunds);
    powerCosts = List.copyOf(powerCosts);
    requirements = List.copyOf(requirements);
    statLines = copyLines(statLines);
    preferences = List.copyOf(preferences);
    preferenceGroups = List.copyOf(preferenceGroups);
    if (maxLevel < 0 || knowledgeCosts.size() != maxLevel || refunds.size() != maxLevel
        || powerCosts.size() != maxLevel || statLines.size() != maxLevel) {
      throw new IllegalArgumentException("per-level lists of " + id + " must hold " + maxLevel + " entries");
    }
  }

  static List<List<String>> copyLines(List<List<String>> lines) {
    return lines.stream().map(List::copyOf).toList();
  }
}
