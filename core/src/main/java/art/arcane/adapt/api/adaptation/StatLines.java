package art.arcane.adapt.api.adaptation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lore lines an adaptation adds to its GUI icon for one level ({@code addStats}). Same call shape as the inventorygui
 * element the Bukkit plugin used, so stat code reads {@code v.addLore(...)} on every platform.
 */
public final class StatLines {
  private final List<String> lines = new ArrayList<>();

  public StatLines addLore(String line) {
    lines.add(line == null ? "" : line);
    return this;
  }

  public StatLines addLore(List<String> more) {
    if (more != null) {
      for (String line : more) {
        addLore(line);
      }
    }
    return this;
  }

  public List<String> lines() {
    return Collections.unmodifiableList(lines);
  }

  public boolean isEmpty() {
    return lines.isEmpty();
  }
}
