package art.arcane.adapt.platform.menu;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;

/**
 * What a menu slot shows. Either a type with display fields, or a prepared {@link ItemRef} (then the
 * other fields are ignored). Text uses Adapt markup.
 */
public record MenuIcon(Id type, int amount, String name, List<String> lore, boolean glint, Id itemModel, ItemRef item) {
  public MenuIcon {
    Objects.requireNonNull(type, "type");
    if (amount < 1 || amount > 99) {
      throw new IllegalArgumentException("icon amount must be 1..99: " + amount);
    }
    lore = lore == null ? List.of() : List.copyOf(lore);
  }

  public static MenuIcon of(Id type, String name, List<String> lore) {
    return new MenuIcon(type, 1, name, lore, false, null, null);
  }

  public static MenuIcon of(ItemRef item) {
    Objects.requireNonNull(item, "item");
    return new MenuIcon(item.type(), Math.max(1, Math.min(99, item.amount())), null, List.of(), false, null, item);
  }

  public MenuIcon withAmount(int newAmount) {
    return new MenuIcon(type, newAmount, name, lore, glint, itemModel, item);
  }

  public MenuIcon withGlint(boolean newGlint) {
    return new MenuIcon(type, amount, name, lore, newGlint, itemModel, item);
  }

  public MenuIcon withItemModel(Id model) {
    return new MenuIcon(type, amount, name, lore, glint, model, item);
  }
}
