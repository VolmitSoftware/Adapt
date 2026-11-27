package art.arcane.adapt.content.golem.gui;

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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One slot of a {@link GolemWindow}, with the same builder calls as the inventorygui elements the Bukkit menus used:
 * name, wrapped lore, glint, count and per-click actions. Left and right handlers fire for plain left and right
 * clicks only, shift and middle clicks have their own handlers. An optional {@link MenuHint} and the presentation
 * fields (role, section, value, description, click labels; see {@link MenuMeta}) tell native clients what the element
 * means; chest menus ignore them.
 */
public final class GolemElement {
  private static final int LORE_WRAP_VISIBLE_WIDTH = 42;
  private static final Id FALLBACK_TYPE = Id.minecraft("barrier");

  private final String id;
  private final List<String> lore = new ArrayList<>();
  private final Map<ClickKind, MenuAction> actions = new EnumMap<>(ClickKind.class);
  private Id material;
  private ItemRef model;
  private ItemRef item;
  private String name;
  private boolean enchanted;
  private int count = 1;
  private MenuHint hint;
  private MenuRole role;
  private String label;
  private String section = "";
  private String value = "";
  private List<String> description;
  private final Map<ClickKind, String> actionLabels = new EnumMap<>(ClickKind.class);

  public GolemElement(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public GolemElement setMaterial(Id material) {
    this.material = material;
    return this;
  }

  public Id getMaterial() {
    if (material != null) {
      return material;
    }
    return model == null ? FALLBACK_TYPE : model.type();
  }

  /** Renders from a copy of this modelled stack, as the element's type when that differs; null clears it. */
  public GolemElement setModel(ItemRef model) {
    this.model = model == null || model.isEmpty() ? null : model.copy();
    if (this.model != null && material == null) {
      material = this.model.type();
    }
    return this;
  }

  /**
   * Renders from a copy of this item instead of a type or model, starting at the item's own count; name, lore,
   * glint and count set on the element still override it. Empty or null clears it.
   */
  public GolemElement setItem(ItemRef item) {
    this.item = item == null || item.isEmpty() ? null : item.copy();
    if (this.item != null) {
      count = Math.max(1, Math.min(64, this.item.amount()));
      if (material == null) {
        material = this.item.type();
      }
    }
    return this;
  }

  public ItemRef getItem() {
    return item == null ? null : item.copy();
  }

  public GolemElement setName(String name) {
    this.name = name;
    return this;
  }

  public String getName() {
    return name;
  }

  public GolemElement setEnchanted(boolean enchanted) {
    this.enchanted = enchanted;
    return this;
  }

  public boolean isEnchanted() {
    return enchanted;
  }

  public GolemElement setCount(int count) {
    this.count = Math.max(1, Math.min(64, count));
    return this;
  }

  public int getCount() {
    return count;
  }

  public GolemElement setHint(MenuHint hint) {
    this.hint = hint;
    return this;
  }

  public MenuHint getHint() {
    return hint;
  }

  public GolemElement setRole(MenuRole role) {
    this.role = role;
    return this;
  }

  public MenuRole getRole() {
    return role;
  }

  /** The name native clients show instead of the icon name, for names that carry chest-only markers. */
  public GolemElement setLabel(String label) {
    this.label = label;
    return this;
  }

  public GolemElement setSection(String section) {
    this.section = section == null ? "" : section;
    return this;
  }

  public GolemElement setValue(String value) {
    this.value = value == null ? "" : value;
    return this;
  }

  /** The lines native rows show under the name instead of the lore; null shows the lore. */
  public GolemElement setDescription(List<String> description) {
    this.description = description == null ? null : List.copyOf(description);
    return this;
  }

  /** Names what a click kind does, for native clients; only kinds with a handler are reported. */
  public GolemElement setActionLabel(ClickKind kind, String label) {
    if (label == null) {
      actionLabels.remove(kind);
    } else {
      actionLabels.put(kind, label);
    }
    return this;
  }

  public GolemElement addLore(String loreLine) {
    if (loreLine == null) {
      lore.add("");
      return this;
    }
    if (loreLine.indexOf('\n') >= 0) {
      for (String line : loreLine.split("\n", -1)) {
        addWrappedLore(line);
      }
      return this;
    }
    addWrappedLore(loreLine);
    return this;
  }

  public GolemElement addLore(List<String> lines) {
    if (lines != null) {
      for (String line : lines) {
        addLore(line);
      }
    }
    return this;
  }

  public List<String> getLore() {
    return List.copyOf(lore);
  }

  public GolemElement onClick(ClickKind kind, MenuAction action) {
    if (action == null) {
      actions.remove(kind);
    } else {
      actions.put(kind, action);
    }
    return this;
  }

  public GolemElement onLeftClick(MenuAction action) {
    return onClick(ClickKind.LEFT, action);
  }

  public GolemElement onRightClick(MenuAction action) {
    return onClick(ClickKind.RIGHT, action);
  }

  public GolemElement onShiftLeftClick(MenuAction action) {
    return onClick(ClickKind.SHIFT_LEFT, action);
  }

  public GolemElement onShiftRightClick(MenuAction action) {
    return onClick(ClickKind.SHIFT_RIGHT, action);
  }

  public GolemElement onMiddleClick(MenuAction action) {
    return onClick(ClickKind.MIDDLE, action);
  }

  public boolean isClickable() {
    return !actions.isEmpty();
  }

  /**
   * A modelled element renders from a copy of its modelled stack so custom model data, head skins and hidden
   * component tooltips carry over; a plain element renders from its type.
   */
  public MenuIcon icon() {
    Id type = getMaterial();
    if (item != null) {
      return MenuIcon.of(type, name, lore).withAmount(count).withGlint(enchanted).withItem(decorate(item.copy()));
    }
    if (model == null) {
      return MenuIcon.of(type, name, lore).withAmount(count).withGlint(enchanted);
    }
    ItemRef rendered = type.equals(model.type()) ? model.copy() : model.copyAs(type);
    return MenuIcon.of(type, name, lore).withAmount(count).withGlint(enchanted).withItemModel(rendered.itemModel()).withItem(decorate(rendered));
  }

  private ItemRef decorate(ItemRef shown) {
    shown.setAmount(count);
    if (name != null) {
      shown.setDisplayName(name);
    }
    if (!lore.isEmpty()) {
      shown.setLore(lore);
    }
    if (enchanted) {
      shown.setGlintOverride(Boolean.TRUE);
    }
    return shown;
  }

  public MenuButton button() {
    MenuIcon icon = icon();
    if (actions.isEmpty()) {
      return new MenuButton(icon, null, hint, meta());
    }
    Map<ClickKind, MenuAction> bound = new EnumMap<>(actions);
    return new MenuButton(icon, (MenuClick click) -> {
      MenuAction action = bound.get(click.kind());
      if (action != null) {
        action.click(click);
      }
    }, hint, meta());
  }

  private MenuMeta meta() {
    Map<ClickKind, String> labels = new EnumMap<>(ClickKind.class);
    for (ClickKind kind : actions.keySet()) {
      labels.put(kind, actionLabels.getOrDefault(kind, ""));
    }
    if (role == null && label == null && section.isEmpty() && value.isEmpty() && description == null && labels.isEmpty()) {
      return MenuMeta.NONE;
    }
    return MenuMeta.role(role).withLabel(label).withSection(section).withValue(value).withDescription(description).withActions(labels);
  }

  private void addWrappedLore(String line) {
    String carry = "";
    String rest = line;
    while (true) {
      String candidate = carry.isEmpty() ? rest : carry + rest;
      int length = candidate.length();
      int visible = 0;
      int breakAt = -1;
      String breakColor = "";
      StringBuilder color = new StringBuilder();
      int i = 0;
      while (i < length) {
        char c = candidate.charAt(i);
        if (c == '§' && i + 1 < length) {
          char code = Character.toLowerCase(candidate.charAt(i + 1));
          if (code == 'x') {
            int end = Math.min(length, i + 14);
            color.setLength(0);
            color.append(candidate, i, end);
            i = end;
            continue;
          }
          if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
            color.setLength(0);
            color.append(c).append(candidate.charAt(i + 1));
          } else if (code == 'r') {
            color.setLength(0);
          } else {
            color.append(c).append(candidate.charAt(i + 1));
          }
          i += 2;
          continue;
        }
        if (c == ' ' && visible <= LORE_WRAP_VISIBLE_WIDTH && i > 0) {
          breakAt = i;
          breakColor = color.toString();
        }
        visible++;
        if (visible > LORE_WRAP_VISIBLE_WIDTH && breakAt >= 0) {
          break;
        }
        i++;
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
