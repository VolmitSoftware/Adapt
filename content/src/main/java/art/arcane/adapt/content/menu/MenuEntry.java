package art.arcane.adapt.content.menu;

import art.arcane.adapt.api.item.ItemArt;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuAction;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuMeta;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One button of a built-in menu: name, wrapped lore, glint, per-click actions and the native presentation fields.
 * Left and right handlers fire for plain left and right clicks only; shift clicks have their own.
 */
public final class MenuEntry {
  private static final int LORE_WRAP_VISIBLE_WIDTH = 42;

  private final Id material;
  private final @Nullable ItemRef item;
  private final List<String> lore = new ArrayList<>();
  private final Map<ClickKind, MenuAction> actions = new EnumMap<>(ClickKind.class);
  private final Map<ClickKind, String> actionLabels = new EnumMap<>(ClickKind.class);
  private @Nullable String name;
  private boolean glint;
  private @Nullable MenuHint hint;
  private @Nullable MenuRole role;
  private String value = "";

  private MenuEntry(Id material, @Nullable ItemRef item) {
    this.material = material;
    this.item = item;
  }

  /** A plain button drawn as {@code material}. */
  public static MenuEntry of(Id material) {
    return new MenuEntry(material, null);
  }

  /** A button drawn the way the server's models file sets for {@code path}, or as {@code fallback}. */
  public static MenuEntry modelled(Id fallback, String... path) {
    ItemRef shown = ItemArt.modelled(fallback, path);
    return shown == null || shown.isEmpty() ? of(fallback) : new MenuEntry(shown.type(), shown);
  }

  public MenuEntry name(String text) {
    this.name = text;
    return this;
  }

  /** Adds a lore line, split at new lines and wrapped to the chest tooltip width with its colour carried over. */
  public MenuEntry lore(@Nullable String line) {
    if (line == null) {
      lore.add("");
      return this;
    }
    for (String part : line.split("\n", -1)) {
      wrap(part);
    }
    return this;
  }

  public MenuEntry glint(boolean on) {
    this.glint = on;
    return this;
  }

  public MenuEntry hint(MenuHint newHint) {
    this.hint = newHint;
    return this;
  }

  public MenuEntry role(MenuRole newRole) {
    this.role = newRole;
    return this;
  }

  /** The value native rows show beside the name. */
  public MenuEntry value(@Nullable String text) {
    this.value = text == null ? "" : text;
    return this;
  }

  public MenuEntry on(ClickKind kind, MenuAction action) {
    actions.put(kind, action);
    return this;
  }

  /** Names what a click kind does, for native clients; only kinds with a handler are reported. */
  public MenuEntry label(ClickKind kind, String text) {
    actionLabels.put(kind, text);
    return this;
  }

  public MenuButton button() {
    MenuIcon icon = icon();
    MenuMeta meta = meta();
    if (actions.isEmpty()) {
      return new MenuButton(icon, null, hint, meta);
    }
    Map<ClickKind, MenuAction> bound = new EnumMap<>(actions);
    return new MenuButton(icon, (MenuClick click) -> {
      MenuAction action = bound.get(click.kind());
      if (action != null) {
        action.click(click);
      }
    }, hint, meta);
  }

  private MenuIcon icon() {
    if (item == null) {
      return MenuIcon.of(material, name, lore).withGlint(glint);
    }
    ItemRef shown = item.copy();
    shown.setAmount(1);
    if (name != null) {
      shown.setDisplayName(name);
    }
    if (!lore.isEmpty()) {
      shown.setLore(lore);
    }
    if (glint) {
      shown.setGlintOverride(Boolean.TRUE);
    }
    return MenuIcon.of(material, name, lore).withGlint(glint).withItemModel(shown.itemModel()).withItem(shown);
  }

  private MenuMeta meta() {
    Map<ClickKind, String> labels = new EnumMap<>(ClickKind.class);
    for (ClickKind kind : actions.keySet()) {
      labels.put(kind, actionLabels.getOrDefault(kind, ""));
    }
    if (role == null && value.isEmpty() && labels.isEmpty()) {
      return MenuMeta.NONE;
    }
    return MenuMeta.role(role).withValue(value).withActions(labels);
  }

  private void wrap(String line) {
    String carry = "";
    String rest = line;
    while (true) {
      String candidate = carry.isEmpty() ? rest : carry + rest;
      int length = candidate.length();
      int visible = 0;
      int breakAt = -1;
      String breakColor = "";
      StringBuilder color = new StringBuilder();
      int index = 0;
      while (index < length) {
        char character = candidate.charAt(index);
        if (character == '§' && index + 1 < length) {
          char code = Character.toLowerCase(candidate.charAt(index + 1));
          if (code == 'x') {
            int end = Math.min(length, index + 14);
            color.setLength(0);
            color.append(candidate, index, end);
            index = end;
            continue;
          }
          if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
            color.setLength(0);
            color.append(character).append(candidate.charAt(index + 1));
          } else if (code == 'r') {
            color.setLength(0);
          } else {
            color.append(character).append(candidate.charAt(index + 1));
          }
          index += 2;
          continue;
        }
        if (character == ' ' && visible <= LORE_WRAP_VISIBLE_WIDTH && index > 0) {
          breakAt = index;
          breakColor = color.toString();
        }
        visible++;
        if (visible > LORE_WRAP_VISIBLE_WIDTH && breakAt >= 0) {
          break;
        }
        index++;
      }
      if (visible <= LORE_WRAP_VISIBLE_WIDTH || breakAt < 0) {
        lore.add(candidate);
        return;
      }
      lore.add(candidate.substring(0, breakAt));
      carry = breakColor;
      rest = candidate.substring(breakAt + 1);
    }
  }
}
