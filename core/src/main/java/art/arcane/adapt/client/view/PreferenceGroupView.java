package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/** Toggle preferences shown together under one icon; entries name preferences of the same adaptation. */
public record PreferenceGroupView(String id, String label, IconRef icon, List<Entry> entries) {
  public PreferenceGroupView {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(icon, "icon");
    entries = List.copyOf(entries);
  }

  public record Entry(String preferenceId, IconRef icon) {
    public Entry {
      Objects.requireNonNull(preferenceId, "preferenceId");
      Objects.requireNonNull(icon, "icon");
    }
  }
}
