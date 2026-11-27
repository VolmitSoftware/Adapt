package art.arcane.adapt.platform.menu;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * What a menu slot shows. Either a type with display fields, or a prepared {@link ItemRef}. Chest menus render a
 * prepared item as it is and ignore the other fields; clients that draw menus natively read the display fields
 * first and fall back to the item. Text uses Adapt markup. Start from {@link #of(Id, String, List)} or
 * {@link #of(ItemRef)}; each {@code with} method returns a new icon.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class MenuIcon {
  private final Id type;
  private final int amount;
  private final String name;
  private final List<String> lore;
  private final boolean glint;
  private final Id itemModel;
  private final int customModelData;
  private final ItemRef item;

  private MenuIcon(Id type, int amount, String name, List<String> lore, boolean glint, Id itemModel,
                   int customModelData, ItemRef item) {
    this.type = Objects.requireNonNull(type, "type");
    if (amount < 1 || amount > 99) {
      throw new IllegalArgumentException("icon amount must be 1..99: " + amount);
    }
    this.amount = amount;
    this.name = name;
    this.lore = lore == null ? List.of() : List.copyOf(lore);
    this.glint = glint;
    this.itemModel = itemModel;
    this.customModelData = customModelData;
    this.item = item;
  }

  /** An icon of one item with a name and lore; a null name keeps the type's own name, null lore becomes empty. */
  public static MenuIcon of(Id type, String name, List<String> lore) {
    return new MenuIcon(type, 1, name, lore, false, null, 0, null);
  }

  /** An icon that shows a prepared item; the amount is clamped to 1..99. */
  public static MenuIcon of(ItemRef item) {
    Objects.requireNonNull(item, "item");
    return new MenuIcon(item.type(), Math.max(1, Math.min(99, item.amount())), null, List.of(), false, null, 0, item);
  }

  /** This icon with another stack size, 1 to 99. */
  public MenuIcon withAmount(int newAmount) {
    return new MenuIcon(type, newAmount, name, lore, glint, itemModel, customModelData, item);
  }

  /** This icon with or without the enchantment glint. */
  public MenuIcon withGlint(boolean newGlint) {
    return new MenuIcon(type, amount, name, lore, newGlint, itemModel, customModelData, item);
  }

  /** This icon with another item model, or null for none. */
  public MenuIcon withItemModel(Id model) {
    return new MenuIcon(type, amount, name, lore, glint, model, customModelData, item);
  }

  /** This icon with custom model data for native clients, 0 for none. */
  public MenuIcon withCustomModelData(int value) {
    return new MenuIcon(type, amount, name, lore, glint, itemModel, value, item);
  }

  /** This icon with a prepared item chest menus show as it is, or null for none. */
  public MenuIcon withItem(ItemRef prepared) {
    return new MenuIcon(type, amount, name, lore, glint, itemModel, customModelData, prepared);
  }

  /** Item type shown. */
  public Id type() {
    return type;
  }

  /** Stack size shown, 1 to 99. */
  public int amount() {
    return amount;
  }

  /** Display name, or null for the type's own name. */
  public String name() {
    return name;
  }

  /** Lines under the name, never null. */
  public List<String> lore() {
    return lore;
  }

  /** Whether the enchantment glint shows. */
  public boolean glint() {
    return glint;
  }

  /** Item model id, or null. */
  public Id itemModel() {
    return itemModel;
  }

  /** Custom model data for native clients, 0 for none; it only describes the icon for native clients. */
  public int customModelData() {
    return customModelData;
  }

  /** A prepared item to show as it is, or null. */
  public ItemRef item() {
    return item;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof MenuIcon icon && type.equals(icon.type) && amount == icon.amount
        && Objects.equals(name, icon.name) && lore.equals(icon.lore) && glint == icon.glint
        && Objects.equals(itemModel, icon.itemModel) && customModelData == icon.customModelData
        && Objects.equals(item, icon.item);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, amount, name, lore, glint, itemModel, customModelData, item);
  }

  @Override
  public String toString() {
    return "MenuIcon[type=" + type + ", amount=" + amount + ", name=" + name + ", lore=" + lore + ", glint=" + glint
        + ", itemModel=" + itemModel + ", customModelData=" + customModelData + ", item=" + item + "]";
  }
}
