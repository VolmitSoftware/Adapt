package art.arcane.adapt.client.protocol;

import art.arcane.adapt.client.view.IconRef;

import java.util.List;
import java.util.Objects;

/**
 * One chest-menu button. {@code hint} is null for a plain button. {@code section} names the group the tile belongs to
 * and {@code value} is a short current value shown at the row's right; both are empty when unset. {@code description}
 * is the text a row shows under its name, or null to show {@code lore}. {@code actions} lists the clicks the tile
 * answers to, so the native panel can offer the ones a plain click cannot reach.
 */
public record PanelTile(int slot, IconRef icon, int amount, String name, List<String> lore, boolean glint,
                        TileHint hint, TileRole role, String section, String value, List<String> description,
                        List<TileAction> actions) {
  public PanelTile {
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(section, "section");
    Objects.requireNonNull(value, "value");
    lore = List.copyOf(lore);
    description = description == null ? null : List.copyOf(description);
    actions = List.copyOf(actions);
  }

  /** The lines a row shows under the name. */
  public List<String> shownDescription() {
    return description == null ? lore : description;
  }
}
