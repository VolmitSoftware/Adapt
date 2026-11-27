package art.arcane.adapt.api.preference;

import art.arcane.adapt.api.config.ConfigDoc;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * What the server allows for one player preference. A config field: every skill and adaptation config has a map of
 * these keyed by preference id, filled with defaults for each preference the content declares.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class PreferencePolicy {
  /** Whether players may choose; false holds everyone at {@link #defaultValue} and keeps their saved choices. */
  @ConfigDoc(value = "Allows players to choose an unlocked permitted value.", impact = "False forces the server default without deleting saved player choices.")
  public boolean playerEditable = true;
  /** The constant name of the server's default value. */
  @ConfigDoc(value = "Default preference value, using its registered uppercase name.", impact = "Applies when locked or when a saved choice is unavailable; level requirements still apply.")
  public String defaultValue;
  /** The constant names players may choose from; must include {@link #defaultValue}. */
  @ConfigDoc(value = "Preference values permitted by the server.", impact = "Only listed values can be selected; the default must be included.")
  public List<String> allowedValues = List.of();

  /** An empty policy for the config loader; content uses {@link #defaults(PlayerPreference)}. */
  public PreferencePolicy() {
  }

  /** The policy a preference has until an admin edits it: its own default, every choice permitted. */
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
