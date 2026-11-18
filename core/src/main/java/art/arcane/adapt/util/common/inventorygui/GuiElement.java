package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuAction;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One slot of a {@link GuiWindow}, with the same builder calls as the inventorygui elements the Bukkit menus used:
 * name, wrapped lore, glint, count and per-click actions. Left and right handlers fire for plain left and right
 * clicks only, shift and middle clicks have their own handlers.
 */
public final class GuiElement {
  private static final int LORE_WRAP_VISIBLE_WIDTH = 42;
  private static final Id FALLBACK_TYPE = Id.minecraft("barrier");

  private final String id;
  private final List<String> lore = new ArrayList<>();
  private final Map<ClickKind, MenuAction> actions = new EnumMap<>(ClickKind.class);
  private Id material;
  private CustomModel model;
  private ItemRef item;
  private String name;
  private boolean enchanted;
  private int count = 1;

  public GuiElement(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public GuiElement setMaterial(Id material) {
    this.material = material;
    return this;
  }

  public Id getMaterial() {
    if (material != null) {
      return material;
    }
    return model == null ? FALLBACK_TYPE : model.material();
  }

  public GuiElement setModel(CustomModel model) {
    this.model = model;
    if (model != null && material == null) {
      material = model.material();
    }
    return this;
  }

  public CustomModel getModel() {
    return model;
  }

  /**
   * Renders from a copy of this item instead of a type or model, starting at the item's own count; name, lore,
   * glint and count set on the element still override it. Empty or null clears it.
   */
  public GuiElement setItem(ItemRef item) {
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

  public GuiElement setName(String name) {
    this.name = name;
    return this;
  }

  public String getName() {
    return name;
  }

  public GuiElement setEnchanted(boolean enchanted) {
    this.enchanted = enchanted;
    return this;
  }

  public boolean isEnchanted() {
    return enchanted;
  }

  public GuiElement setCount(int count) {
    this.count = Math.max(1, Math.min(64, count));
    return this;
  }

  public int getCount() {
    return count;
  }

  public GuiElement addLore(String loreLine) {
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

  public GuiElement addLore(List<String> lines) {
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

  public GuiElement onClick(ClickKind kind, MenuAction action) {
    if (action == null) {
      actions.remove(kind);
    } else {
      actions.put(kind, action);
    }
    return this;
  }

  public GuiElement onLeftClick(MenuAction action) {
    return onClick(ClickKind.LEFT, action);
  }

  public GuiElement onRightClick(MenuAction action) {
    return onClick(ClickKind.RIGHT, action);
  }

  public GuiElement onShiftLeftClick(MenuAction action) {
    return onClick(ClickKind.SHIFT_LEFT, action);
  }

  public GuiElement onShiftRightClick(MenuAction action) {
    return onClick(ClickKind.SHIFT_RIGHT, action);
  }

  public GuiElement onMiddleClick(MenuAction action) {
    return onClick(ClickKind.MIDDLE, action);
  }

  public boolean isClickable() {
    return !actions.isEmpty();
  }

  /**
   * A modelled element renders from {@link CustomModel#toItemStack()} so custom model data, head skins and hidden
   * component tooltips carry over; a plain element renders from its type.
   */
  public MenuIcon icon() {
    Id type = getMaterial();
    if (item != null) {
      return MenuIcon.of(decorate(item.copy()));
    }
    if (model == null) {
      return new MenuIcon(type, count, name, lore, enchanted, null, null);
    }
    CustomModel base = type.equals(model.material()) ? model : new CustomModel(type, model.model(), model.modelKey(), model.headTexture());
    ItemRef rendered = base.toItemStack();
    if (rendered == null || rendered.isEmpty()) {
      return new MenuIcon(type, count, name, lore, enchanted, null, null);
    }
    return MenuIcon.of(decorate(rendered));
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
      return MenuButton.display(icon);
    }
    Map<ClickKind, MenuAction> bound = new EnumMap<>(actions);
    return new MenuButton(icon, (MenuClick click) -> {
      MenuAction action = bound.get(click.kind());
      if (action != null) {
        action.click(click);
      }
    });
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
