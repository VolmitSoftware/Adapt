package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.EquipmentSlotGroup;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mutable view of one item stack (Bukkit item stack plus meta, or a vanilla item stack with data
 * components). Never null where the SPI returns an item: empty slots are an empty ref.
 *
 * <p>Write-back: a ref read from an inventory slot or event may or may not write through on a given
 * platform. After mutating, store it back with the slot setter (for example
 * {@code inventory.setMainHand(item)}) or the event setter. That is correct on every platform.
 *
 * <p>Text (display name, lore) uses Adapt markup: the strings the localization layer already
 * produces (legacy section colours and MiniMessage tags). Adapters render it.
 */
public interface ItemRef {
  Id type();

  int amount();

  void setAmount(int amount);

  boolean isEmpty();

  int maxStackSize();

  /** Independent copy; changes to the copy never reach this stack. */
  ItemRef copy();

  ItemRef copyWithAmount(int amount);

  /** Same type and components, ignoring amount. */
  boolean isSimilar(ItemRef other);

  String displayName();

  /** Custom name as vanilla text-component JSON, or null when the item has no custom name. */
  String displayNameJson();

  boolean hasDisplayName();

  void setDisplayName(String markup);

  List<String> lore();

  void setLore(List<String> markupLines);

  /** Lore lines as vanilla text-component JSON, one entry per line; empty when the item has no lore. */
  List<String> loreJson();

  /**
   * Replaces the lore with text-component JSON lines; null or empty clears it. Throws {@link IllegalArgumentException}
   * for a line that is not a text component, leaving the lore unchanged.
   */
  void setLoreJson(List<String> jsonLines);

  Map<Id, Integer> enchantments();

  int enchantmentLevel(Id enchantment);

  /** Adds or replaces an enchantment, ignoring vanilla level caps and conflicts. */
  void setEnchantment(Id enchantment, int level);

  void removeEnchantment(Id enchantment);

  /** Enchantments stored in an enchanted book; empty for other items. */
  Map<Id, Integer> storedEnchantments();

  void setStoredEnchantment(Id enchantment, int level);

  void removeStoredEnchantment(Id enchantment);

  boolean hasGlint();

  void setGlintOverride(Boolean glint);

  void hideTooltip(TooltipPart part);

  /** Hides the whole hover tooltip, as for filler panes in a container view. */
  void setHideTooltip(boolean hidden);

  /** Overrides the stack size limit of this item (1-99). */
  void setMaxStackSize(int maxStackSize);

  /** Gives a firework star a single small-ball explosion of {@code color}; does nothing on other items. */
  void setFireworkStarColor(Rgb color);

  /** Banner face of a shield, or null when the item is not a shield. */
  ShieldDesign shieldDesign();

  /** Replaces a shield's banner face; does nothing on other items. Unknown pattern ids are skipped. */
  void setShieldDesign(ShieldDesign design);

  /** Damage taken, 0 when undamaged or not damageable. */
  int damage();

  void setDamage(int damage);

  /** Maximum damage, 0 when the item is not damageable. */
  int maxDamage();

  /** Overrides the stack's maximum damage (the max damage component). */
  void setMaxDamage(int maxDamage);

  boolean isUnbreakable();

  void setUnbreakable(boolean unbreakable);

  /** Item model id override, or null for the default model. */
  Id itemModel();

  void setItemModel(Id model);

  /**
   * Adds an attribute modifier active in {@code slot}, replacing one with the same key on the same attribute. When the
   * stack has no explicit modifiers yet, the item type's default modifiers are copied in first so they stay active.
   */
  void setAttributeModifier(Id attribute, AttributeMod modifier, EquipmentSlot slot);

  /** Like {@link #setAttributeModifier(Id, AttributeMod, EquipmentSlot)} for a whole slot group such as all armor. */
  void setAttributeModifier(Id attribute, AttributeMod modifier, EquipmentSlotGroup group);

  /** Removes the stack's modifiers with {@code key} on {@code attribute}. */
  void removeAttributeModifier(Id attribute, Id key);

  /** Potion data, empty when the item cannot hold potion contents. */
  Optional<PotionContents> potionContents();

  /** Replaces the potion data; does nothing on items that cannot hold potion contents. */
  void setPotionContents(PotionContents contents);

  /**
   * Empties the items stored inside this one (bundle contents, the inventory of a shulker box or other container
   * block item). True when something was removed.
   */
  boolean clearStoredItems();

  /** Copies of the items stored inside this one (bundle contents or a container block item's inventory). */
  List<ItemRef> storedItems();

  /** Copies of a bundle's contents in order, or empty when this item is not a bundle. */
  Optional<List<ItemRef>> bundleContents();

  /** Replaces a bundle's contents; does nothing when this item is not a bundle. */
  void setBundleContents(List<ItemRef> items);

  /**
   * Copies of a container block item's slots (shulker box and similar), empty slots included, or empty when this
   * item cannot store a container inventory.
   */
  Optional<List<ItemRef>> containerContents();

  /** Replaces a container block item's slots by index; does nothing when this item has no container inventory. */
  void setContainerContents(List<ItemRef> slots);

  /** A copy of this stack as another item type, keeping amount and components. */
  ItemRef copyAs(Id type);

  /** Custom data (Bukkit persistent data container, vanilla custom data component). */
  DataHolder data();

  /**
   * Drops every change made to this stack (name, lore, enchantments, stored data, repair cost and the rest), back to
   * the item type's defaults, keeping type and amount.
   */
  void clearComponents();

  /** Adapter-internal access to the native stack. Content code must not call this. */
  Object unwrap();
}
