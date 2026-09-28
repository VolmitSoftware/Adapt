package art.arcane.adapt.api.preference;

import art.arcane.adapt.util.config.ConfigDoc;

import java.util.ArrayList;
import java.util.List;

public final class PreferencePolicy {
  @ConfigDoc(value = "Allows players to choose an unlocked permitted value.", impact = "False forces the server default without deleting saved player choices.")
  public boolean playerEditable = true;
  @ConfigDoc(value = "Default preference value, using its registered uppercase name.", impact = "Applies when locked or when a saved choice is unavailable; level requirements still apply.")
  public String defaultValue;
  @ConfigDoc(value = "Preference values permitted by the server.", impact = "Only listed values can be selected; the default must be included.")
  public List<String> allowedValues = List.of();

  public PreferencePolicy() {
  }

  @SafeVarargs
  public static <E extends Enum<E>> PreferencePolicy of(E defaultValue, E... allowedValues) {
    PreferencePolicy policy = new PreferencePolicy();
    policy.defaultValue = defaultValue.name();
    List<String> names = new ArrayList<>(allowedValues.length);
    for (E value : allowedValues) {
      names.add(value.name());
    }
    policy.allowedValues = List.copyOf(names);
    return policy;
  }

  public static PreferencePolicy defaults(PlayerPreference<?> preference) {
    PreferencePolicy policy = new PreferencePolicy();
    policy.defaultValue = preference.defaultValue().name();
    List<String> values = new ArrayList<>(preference.choices().size());
    for (PlayerPreference.Choice<?> choice : preference.choices()) {
      values.add(choice.value().name());
    }
    policy.allowedValues = List.copyOf(values);
    return policy;
  }
}
