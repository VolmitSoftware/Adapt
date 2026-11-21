package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the chest menus would show this player, decided by the server so the client never guesses. {@code skills} are
 * the skills the skills menu lists, in its order. {@code adaptationRows} has an entry for every skill whose page the
 * player may open: the adaptations that page shows, row by row in its order. A skill missing from it is disabled or
 * denied by permission. {@code refunds} is false when unlearning returns nothing, {@code preferences} false when player
 * settings are off, and {@code debug} true while the player's debug mode makes learning free.
 */
public record PlayerAccess(boolean debug, boolean refunds, boolean preferences, List<String> skills,
                           Map<String, List<List<String>>> adaptationRows) {
  public static final PlayerAccess NONE = new PlayerAccess(false, true, true, List.of(), Map.of());

  public PlayerAccess {
    skills = List.copyOf(skills);
    Map<String, List<List<String>>> rows = new TreeMap<>();
    for (Map.Entry<String, List<List<String>>> entry : adaptationRows.entrySet()) {
      rows.put(entry.getKey(), AdaptationView.copyLines(entry.getValue()));
    }
    adaptationRows = Map.copyOf(rows);
  }

  /** Whether the player may open the page of {@code skillId}. */
  public boolean canOpen(String skillId) {
    return adaptationRows.containsKey(skillId);
  }

  /** The rows of adaptation ids the page of {@code skillId} shows; empty when the player may not open it. */
  public List<List<String>> rows(String skillId) {
    List<List<String>> rows = adaptationRows.get(skillId);
    return rows == null ? List.of() : rows;
  }

  /** Whether the page of the adaptation's skill shows {@code adaptationId}. */
  public boolean shows(String skillId, String adaptationId) {
    for (List<String> row : rows(skillId)) {
      if (row.contains(adaptationId)) {
        return true;
      }
    }
    return false;
  }
}
