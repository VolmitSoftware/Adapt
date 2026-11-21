package art.arcane.adapt.client.view;

import java.util.Objects;

public record PreferenceChoiceView(String value, String label, IconRef icon, int minimumLevel) {
  public PreferenceChoiceView {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(icon, "icon");
  }
}
