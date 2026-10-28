package art.arcane.adapt.api.preference;

import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PreferenceToggleGroup(String id, TextKey label, Material icon, List<Entry> entries) {
  public static final int MAX_ENTRIES = GuiLayout.WIDTH * (GuiLayout.MAX_ROWS - 1);

  public PreferenceToggleGroup {
    Objects.requireNonNull(id);
    Objects.requireNonNull(label);
    Objects.requireNonNull(icon);
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

  public List<PlayerPreference<?>> preferences() {
    List<PlayerPreference<?>> preferences = new ArrayList<>(entries.size());
    for (Entry entry : entries) {
      preferences.add(entry.preference());
    }
    return List.copyOf(preferences);
  }

  public boolean contains(PlayerPreference<?> preference) {
    for (Entry entry : entries) {
      if (entry.preference() == preference) {
        return true;
      }
    }
    return false;
  }

  public record Entry(PlayerPreference<CommonPreferences.Toggle> preference, Material icon) {
    public Entry {
      Objects.requireNonNull(preference);
      Objects.requireNonNull(icon);
    }
  }
}
