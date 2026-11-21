package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/** One preference with the choices the server allows, in display order. */
public record PreferenceView(String id, String label, String defaultValue, List<PreferenceChoiceView> choices,
                             boolean playerEditable) {
  public PreferenceView {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(defaultValue, "defaultValue");
    choices = List.copyOf(choices);
  }
}
