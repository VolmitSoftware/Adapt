package art.arcane.adapt.client.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** What changed from one {@link PlayerView} of a player to the next. */
public final class PlayerViewDiff {
  private PlayerViewDiff() {
  }

  public static PlayerDelta diff(PlayerView before, PlayerView after) {
    Objects.requireNonNull(before, "before");
    Objects.requireNonNull(after, "after");
    PlayerHeader header = before.header().equals(after.header()) ? null : after.header();

    Map<String, SkillLineView> previousLines = new HashMap<>();
    for (SkillLineView line : before.skills()) {
      previousLines.put(line.skillId(), line);
    }
    List<SkillLineView> skills = new ArrayList<>();
    for (SkillLineView line : after.skills()) {
      if (!line.equals(previousLines.get(line.skillId()))) {
        skills.add(line);
      }
    }

    Map<String, Integer> levels = new TreeMap<>();
    for (Map.Entry<String, Integer> entry : after.adaptationLevels().entrySet()) {
      if (!entry.getValue().equals(before.adaptationLevels().get(entry.getKey()))) {
        levels.put(entry.getKey(), entry.getValue());
      }
    }
    for (String unlearned : before.adaptationLevels().keySet()) {
      if (!after.adaptationLevels().containsKey(unlearned)) {
        levels.put(unlearned, 0);
      }
    }

    Map<PreferenceKey, String> previousValues = new HashMap<>();
    for (PreferenceValue value : before.preferences()) {
      previousValues.put(value.key(), value.value());
    }
    List<PreferenceValue> preferences = new ArrayList<>();
    for (PreferenceValue value : after.preferences()) {
      if (!value.value().equals(previousValues.remove(value.key()))) {
        preferences.add(value);
      }
    }
    List<PreferenceKey> cleared = new ArrayList<>(previousValues.keySet());
    cleared.sort(null);

    PlayerAccess access = before.access().equals(after.access()) ? null : after.access();
    return new PlayerDelta(header, skills, levels, preferences, cleared, access);
  }
}
