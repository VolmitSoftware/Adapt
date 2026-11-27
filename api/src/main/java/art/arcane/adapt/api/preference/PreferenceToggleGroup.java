package art.arcane.adapt.api.preference;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Several toggles shown behind one button in the preference menu, for content with many on/off switches (one per
 * block type, one per mob). Returned from the content's {@code getPlayerPreferenceGroups()}.
 *
 * @param id      unique within the content; lower case letters, digits and {@code -}, starting with a letter
 * @param label   the name on the group's button
 * @param icon    the item on the group's button
 * @param entries the toggles and the icon each is shown with, one to {@link #MAX_ENTRIES}, no preference twice
 */
@ApiStatus.AvailableSince("1.0.0")
public record PreferenceToggleGroup(String id, Text label, Id icon, List<Entry> entries) {
  /** The most toggles one group holds. */
  public static final int MAX_ENTRIES = 45;

  public PreferenceToggleGroup {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(icon, "icon");
    entries = List.copyOf(entries);
    if (!id.matches("[a-z][a-z0-9-]*") || entries.isEmpty() || entries.size() > MAX_ENTRIES) {
      throw new IllegalArgumentException("Invalid preference group: " + id);
    }
    Set<String> ids = new HashSet<>();
    for (Entry entry : entries) {
      if (!ids.add(entry.preference().id())) {
        throw new IllegalArgumentException("Duplicate preference " + entry.preference().id() + " in group " + id);
      }
    }
  }

  /** The group's preferences, in entry order. */
  public List<PlayerPreference<?>> preferences() {
    List<PlayerPreference<?>> preferences = new ArrayList<>(entries.size());
    for (Entry entry : entries) {
      preferences.add(entry.preference());
    }
    return List.copyOf(preferences);
  }

  /** Whether the group holds this very preference instance. */
  public boolean contains(PlayerPreference<?> preference) {
    for (Entry entry : entries) {
      if (entry.preference() == preference) {
        return true;
      }
    }
    return false;
  }

  /**
   * One toggle of a group.
   *
   * @param preference the on/off preference
   * @param icon       the item it is shown with inside the group
   */
  public record Entry(PlayerPreference<CommonPreferences.Toggle> preference, Id icon) {
    public Entry {
      Objects.requireNonNull(preference, "preference");
      Objects.requireNonNull(icon, "icon");
    }
  }
}
