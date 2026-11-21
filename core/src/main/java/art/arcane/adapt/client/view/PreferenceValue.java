package art.arcane.adapt.client.view;

import java.util.Objects;

/** The value a player's preference resolves to, as the registered uppercase choice name. */
public record PreferenceValue(PreferenceKey key, String value) {
  public PreferenceValue {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
  }
}
