package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/**
 * One skill. {@code name} is the plain localized name (no symbol, no colour codes); {@code color} is 0xRRGGBB.
 * {@code rowGroups} lists adaptation ids per row when the skill lays its adaptations out in groups, and is empty
 * otherwise.
 */
public record SkillView(
    String id,
    String name,
    String description,
    IconRef icon,
    int color,
    boolean enabled,
    List<List<String>> rowGroups,
    List<PreferenceView> preferences,
    List<AdaptationView> adaptations
) {
  public SkillView {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(icon, "icon");
    rowGroups = AdaptationView.copyLines(rowGroups);
    preferences = List.copyOf(preferences);
    adaptations = List.copyOf(adaptations);
  }
}
