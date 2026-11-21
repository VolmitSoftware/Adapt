package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything the client shows about one player. {@code skills} has a line for every registered skill,
 * {@code adaptationLevels} holds learned adaptations only, {@code preferences} holds the resolved value of every
 * skill preference and of the preferences of learned adaptations, and {@code access} is what the chest menus would
 * show this player.
 */
public record PlayerView(PlayerHeader header, List<SkillLineView> skills, Map<String, Integer> adaptationLevels,
                         List<PreferenceValue> preferences, PlayerAccess access) {
  public PlayerView {
    Objects.requireNonNull(header, "header");
    Objects.requireNonNull(access, "access");
    skills = List.copyOf(skills);
    adaptationLevels = Map.copyOf(adaptationLevels);
    preferences = List.copyOf(preferences);
  }
}
