package art.arcane.adapt.platform.menu;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How clients that draw menus natively present a button. Chest menus ignore it. Start from {@link #NONE} or
 * {@link #role(MenuRole)}; each {@code with} method returns a new value.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class MenuMeta {
  /** No native presentation hints. */
  public static final MenuMeta NONE = new MenuMeta(null, null, "", "", null, Map.of());

  private final MenuRole role;
  private final String label;
  private final String section;
  private final String value;
  private final List<String> description;
  private final Map<ClickKind, String> actions;

  private MenuMeta(MenuRole role, String label, String section, String value, List<String> description,
                   Map<ClickKind, String> actions) {
    this.role = role;
    this.label = label;
    this.section = section == null ? "" : section;
    this.value = value == null ? "" : value;
    this.description = description == null ? null : List.copyOf(description);
    EnumMap<ClickKind, String> copy = new EnumMap<>(ClickKind.class);
    if (actions != null) {
      for (Map.Entry<ClickKind, String> entry : actions.entrySet()) {
        copy.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
      }
    }
    this.actions = Collections.unmodifiableMap(copy);
  }

  /** Meta that only sets the role; null lets the client infer it. */
  public static MenuMeta role(MenuRole role) {
    return new MenuMeta(role, null, "", "", null, Map.of());
  }

  /** This meta with a native name, or null to keep the icon name (for names that carry chest-only markers). */
  public MenuMeta withLabel(String newLabel) {
    return new MenuMeta(role, newLabel, section, value, description, actions);
  }

  /** This meta under a section heading; null becomes empty. */
  public MenuMeta withSection(String newSection) {
    return new MenuMeta(role, label, newSection, value, description, actions);
  }

  /** This meta with a short current value; null becomes empty. */
  public MenuMeta withValue(String newValue) {
    return new MenuMeta(role, label, section, newValue, description, actions);
  }

  /** This meta with native lore replacing the icon lore, or null to keep the lore. */
  public MenuMeta withDescription(List<String> newDescription) {
    return new MenuMeta(role, label, section, value, newDescription, actions);
  }

  /** This meta naming what each click kind does, an empty label when unnamed; null becomes empty. */
  public MenuMeta withActions(Map<ClickKind, String> newActions) {
    return new MenuMeta(role, label, section, value, description, newActions);
  }

  /** What the button is, or null to let the client infer it. */
  public MenuRole role() {
    return role;
  }

  /** Native name, or null to keep the icon name. */
  public String label() {
    return label;
  }

  /** Section heading, never null. */
  public String section() {
    return section;
  }

  /** Short current value, never null. */
  public String value() {
    return value;
  }

  /** Native lore, or null to keep the icon lore. */
  public List<String> description() {
    return description;
  }

  /** Label per click kind, never null. */
  public Map<ClickKind, String> actions() {
    return actions;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof MenuMeta meta && role == meta.role && Objects.equals(label, meta.label)
        && section.equals(meta.section) && value.equals(meta.value) && Objects.equals(description, meta.description)
        && actions.equals(meta.actions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(role, label, section, value, description, actions);
  }

  @Override
  public String toString() {
    return "MenuMeta[role=" + role + ", label=" + label + ", section=" + section + ", value=" + value
        + ", description=" + description + ", actions=" + actions + "]";
  }
}
