package art.arcane.adapt.client.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Applies a {@link PlayerDelta} to the view it was computed from; the inverse of {@link PlayerViewDiff}. */
public final class PlayerViewPatch {
  private PlayerViewPatch() {
  }

  /**
   * Returns {@code before} with {@code delta} applied. Changed skill lines replace the line with the same id in place
   * and new ones are appended; a level of 0 unlearns; preferences stay sorted by key, as the server builds them.
   */
  public static PlayerView apply(PlayerView before, PlayerDelta delta) {
    Objects.requireNonNull(before, "before");
    Objects.requireNonNull(delta, "delta");
    if (delta.isEmpty()) {
      return before;
    }
    PlayerHeader header = delta.header() == null ? before.header() : delta.header();

    Map<String, SkillLineView> changedLines = new LinkedHashMap<>();
    for (SkillLineView line : delta.skills()) {
      changedLines.put(line.skillId(), line);
    }
    List<SkillLineView> skills = new ArrayList<>(before.skills().size() + changedLines.size());
    for (SkillLineView line : before.skills()) {
      SkillLineView changed = changedLines.remove(line.skillId());
      skills.add(changed == null ? line : changed);
    }
    skills.addAll(changedLines.values());

    Map<String, Integer> levels = new HashMap<>(before.adaptationLevels());
    for (Map.Entry<String, Integer> entry : delta.adaptationLevels().entrySet()) {
      if (entry.getValue() <= 0) {
        levels.remove(entry.getKey());
      } else {
        levels.put(entry.getKey(), entry.getValue());
      }
    }

    Map<PreferenceKey, String> values = new TreeMap<>();
    for (PreferenceValue value : before.preferences()) {
      values.put(value.key(), value.value());
    }
    for (PreferenceKey cleared : delta.clearedPreferences()) {
      values.remove(cleared);
    }
    for (PreferenceValue value : delta.preferences()) {
      values.put(value.key(), value.value());
    }
    List<PreferenceValue> preferences = new ArrayList<>(values.size());
    for (Map.Entry<PreferenceKey, String> entry : values.entrySet()) {
      preferences.add(new PreferenceValue(entry.getKey(), entry.getValue()));
    }

    return new PlayerView(header, skills, levels, preferences,
        delta.access() == null ? before.access() : delta.access());
  }
}
