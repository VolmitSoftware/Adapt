package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.EquipmentSlotGroup;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import org.jetbrains.annotations.ApiStatus;

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
 * <p>Text setters (display name, lore) take Adapt markup: the strings the localization layer already
 * produces (legacy section colours and MiniMessage tags). Adapters render it. Text reads return legacy section-coloured
 * text on every platform, which is valid markup again.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface ItemRef {
  /** Item type id, for example {@code minecraft:diamond_sword}; {@code minecraft:air} for the empty stack. */
  Id type();

  /** Number of items in the stack. */
  int amount();

  /** Sets the number of items; no-op on the empty stack. */
  void setAmount(int amount);

  /** True for air or a stack of zero items. */
  boolean isEmpty();

  /** Stack size limit of this stack, including any override. */
  int maxStackSize();

  /** Independent copy; changes to the copy never reach this stack. */
  ItemRef copy();

  /** Independent copy with another amount. */
  ItemRef copyWithAmount(int amount);

  /** Same type and components, ignoring amount. */
  boolean isSimilar(ItemRef other);

  /**
   * Name shown for the stack: the custom name when it has one, otherwise the item's default name, with legacy section
   * colours on every platform. Never null.
   */
  String displayName();

  /** Custom name as vanilla text-component JSON, or null when the item has no custom name. */
  String displayNameJson();

  /** True when the stack has a custom name. */
  boolean hasDisplayName();

  /** Sets the custom name from Adapt markup; null removes it. */
  void setDisplayName(String markup);

  /** Lore lines with legacy section colours, as {@link #displayName()}; empty when the stack has none. */
  List<String> lore();

  /** Replaces the lore with Adapt markup lines; null or empty clears it. */
  void setLore(List<String> markupLines);

  /** Lore lines as vanilla text-component JSON, one entry per line; empty when the item has no lore. */
  List<String> loreJson();

  /**
   * Replaces the lore with text-component JSON lines; null or empty clears it. Throws {@link IllegalArgumentException}
   * for a line that is not a text component, leaving the lore unchanged.
   */
  void setLoreJson(List<String> jsonLines);

  /** Enchantments applied to the stack, by id and level; empty when none. */
  Map<Id, Integer> enchantments();

  /** Level of the enchantment on the stack; 0 when absent or unknown. */
  int enchantmentLevel(Id enchantment);

  /** Adds or replaces an enchantment, ignoring vanilla level caps and conflicts. */
  void setEnchantment(Id enchantment, int level);

  /** Removes the enchantment; no-op when absent. */
  void removeEnchantment(Id enchantment);

  /** Enchantments stored in an enchanted book; empty for other items. */
  Map<Id, Integer> storedEnchantments();

  /** Adds or replaces an enchantment stored in an enchanted book; level 0 or less removes it. */
  void setStoredEnchantment(Id enchantment, int level);

  /** Removes an enchantment stored in an enchanted book. */
  void removeStoredEnchantment(Id enchantment);

  /** True when the stack renders with the enchantment glint, from enchantments or an override. */
  boolean hasGlint();

  /** Forces the glint on or off; null removes the override so enchantments decide. */
  void setGlintOverride(Boolean glint);

  /** Hides one section of the hover tooltip. */
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

  /** Sets the damage taken; no-op when the item is not damageable. */
  void setDamage(int damage);

  /** Maximum damage, 0 when the item is not damageable. */
  int maxDamage();

  /** Overrides the stack's maximum damage (the max damage component). */
  void setMaxDamage(int maxDamage);

  /** True when the stack never loses durability. */
  boolean isUnbreakable();

  /** Makes the stack unbreakable or breakable again. */
  void setUnbreakable(boolean unbreakable);

  /** Item model id override, or null for the default model. */
  Id itemModel();

  /** Overrides the item model; null restores the default model. */
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
  @ApiStatus.Internal
  Object unwrap();
}
