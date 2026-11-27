package art.arcane.adapt.engine.preference;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerPreferenceData {
  private volatile Map<String, Map<String, String>> adaptations = Map.of();
  private volatile Map<String, Map<String, String>> skills = Map.of();

  public String getSkill(String skillId, String preferenceId) {
    Map<String, Map<String, String>> current = skills;
    Map<String, String> values = current == null ? null : current.get(skillId);
    return values == null ? null : values.get(preferenceId);
  }

  public void setSkill(String skillId, String preferenceId, String value) {
    Map<String, Map<String, String>> current = skills;
    Map<String, Map<String, String>> updated = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
    Map<String, String> previous = updated.get(skillId);
    Map<String, String> values = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
    values.put(preferenceId, value);
    updated.put(skillId, Map.copyOf(values));
    skills = Map.copyOf(updated);
  }

  /** Forgets one saved skill choice; false when none was saved. */
  public boolean removeSkill(String skillId, String preferenceId) {
    Map<String, Map<String, String>> updated = without(skills, skillId, preferenceId);
    if (updated == null) {
      return false;
    }
    skills = updated;
    return true;
  }

  /** Forgets one saved adaptation choice; false when none was saved. */
  public boolean remove(String adaptationId, String preferenceId) {
    Map<String, Map<String, String>> updated = without(adaptations, adaptationId, preferenceId);
    if (updated == null) {
      return false;
    }
    adaptations = updated;
    return true;
  }

  private static Map<String, Map<String, String>> without(Map<String, Map<String, String>> current, String owner,
                                                           String preferenceId) {
    Map<String, String> values = current == null ? null : current.get(owner);
    if (values == null || !values.containsKey(preferenceId)) {
      return null;
    }
    Map<String, String> remaining = new LinkedHashMap<>(values);
    remaining.remove(preferenceId);
    Map<String, Map<String, String>> updated = new LinkedHashMap<>(current);
    if (remaining.isEmpty()) {
      updated.remove(owner);
    } else {
      updated.put(owner, Map.copyOf(remaining));
    }
    return Map.copyOf(updated);
  }

  public boolean resetSkill(String skillId) {
    Map<String, Map<String, String>> current = skills;
    if (current == null || !current.containsKey(skillId)) {
      return false;
    }
    Map<String, Map<String, String>> updated = new LinkedHashMap<>(current);
    updated.remove(skillId);
    skills = Map.copyOf(updated);
    return true;
  }

  public String get(String adaptationId, String preferenceId) {
    Map<String, Map<String, String>> current = adaptations;
    Map<String, String> values = current == null ? null : current.get(adaptationId);
    return values == null ? null : values.get(preferenceId);
  }

  public void set(String adaptationId, String preferenceId, String value) {
    Map<String, Map<String, String>> current = adaptations;
    Map<String, Map<String, String>> updated = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
    Map<String, String> previous = updated.get(adaptationId);
    Map<String, String> values = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
    values.put(preferenceId, value);
    updated.put(adaptationId, Map.copyOf(values));
    adaptations = Map.copyOf(updated);
  }

  public boolean reset(String adaptationId) {
    Map<String, Map<String, String>> current = adaptations;
    if (current == null || !current.containsKey(adaptationId)) {
      return false;
    }
    Map<String, Map<String, String>> updated = new LinkedHashMap<>(current);
    updated.remove(adaptationId);
    adaptations = Map.copyOf(updated);
    return true;
  }
}
