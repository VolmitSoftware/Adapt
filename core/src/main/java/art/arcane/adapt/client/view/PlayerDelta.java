package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Map;

/**
 * Changes between two {@link PlayerView}s. {@code header} and {@code access} are null when unchanged. An adaptation
 * level of 0 means it was unlearned; {@code clearedPreferences} lists values that no longer apply.
 */
public record PlayerDelta(PlayerHeader header, List<SkillLineView> skills, Map<String, Integer> adaptationLevels,
                          List<PreferenceValue> preferences, List<PreferenceKey> clearedPreferences,
                          PlayerAccess access) {
  public PlayerDelta {
    skills = List.copyOf(skills);
    adaptationLevels = Map.copyOf(adaptationLevels);
    preferences = List.copyOf(preferences);
    clearedPreferences = List.copyOf(clearedPreferences);
  }

  public boolean isEmpty() {
    return header == null && skills.isEmpty() && adaptationLevels.isEmpty() && preferences.isEmpty()
        && clearedPreferences.isEmpty() && access == null;
  }
}
