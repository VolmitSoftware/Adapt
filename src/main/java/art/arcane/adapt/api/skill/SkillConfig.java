package art.arcane.adapt.api.skill;

import art.arcane.adapt.api.preference.PreferencePolicy;

import java.util.LinkedHashMap;
import java.util.Map;

public class SkillConfig {
  public Map<String, PreferencePolicy> playerPreferences = new LinkedHashMap<>();
}
