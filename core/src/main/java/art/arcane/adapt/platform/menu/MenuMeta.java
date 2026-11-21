package art.arcane.adapt.platform.menu;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How clients that draw menus natively present a button. {@code role} is null to let the client infer it;
 * {@code label} replaces the icon name natively (null keeps it, for names that carry chest-only markers);
 * {@code section} groups buttons under a heading; {@code value} is a short current value; {@code description}
 * replaces the lore under the name (null keeps the lore); {@code actions} names what each click kind does, an empty
 * label when unnamed. Chest menus ignore it.
 */
public record MenuMeta(MenuRole role, String label, String section, String value, List<String> description,
                       Map<ClickKind, String> actions) {
  public static final MenuMeta NONE = new MenuMeta(null, null, "", "", null, Map.of());

  public MenuMeta {
    section = section == null ? "" : section;
    value = value == null ? "" : value;
    description = description == null ? null : List.copyOf(description);
    EnumMap<ClickKind, String> copy = new EnumMap<>(ClickKind.class);
    if (actions != null) {
      for (Map.Entry<ClickKind, String> entry : actions.entrySet()) {
        copy.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
      }
    }
    actions = Collections.unmodifiableMap(copy);
  }

  public static MenuMeta role(MenuRole role) {
    return new MenuMeta(role, null, "", "", null, Map.of());
  }
}
