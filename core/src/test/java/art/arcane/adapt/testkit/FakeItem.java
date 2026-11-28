package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.EquipmentSlotGroup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.ShieldDesign;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Item stack value: type, amount, damage, max damage, enchantments and name. Copies are deep. */
public final class FakeItem implements ItemRef {
  private static final Set<String> POTION_HOLDERS = Set.of("potion", "splash_potion", "lingering_potion", "tipped_arrow");
  private static final Id SHIELD = Id.minecraft("shield");
  private final Id type;
  private int amount;
  private int damage;
  private int maxDamage;
  private int maxStackSize;
  private String displayName;
  private String displayNameJson;
  private List<String> lore = new ArrayList<>();
  private List<String> loreJson = new ArrayList<>();
  private final Map<Id, Integer> enchantments = new LinkedHashMap<>();
  private final Map<Id, Integer> storedEnchantments = new LinkedHashMap<>();
  private Boolean glint;
  private final Set<TooltipPart> hiddenTooltip = EnumSet.noneOf(TooltipPart.class);
  private boolean unbreakable;
  private Id itemModel;
  private Id spawnedType;
  private PotionContents potionContents;
  private Rgb fireworkStarColor;
  private ShieldDesign shieldDesign = new ShieldDesign(null, List.of());
  private final MemoryData data = new MemoryData();
  private final Map<Id, Map<Id, AttributeMod>> attributeModifiers = new LinkedHashMap<>();
  private final Map<Id, EquipmentSlot> attributeModifierSlots = new LinkedHashMap<>();
  private final Map<Id, EquipmentSlotGroup> attributeModifierGroups = new LinkedHashMap<>();
  private final List<ItemRef> storedItems = new ArrayList<>();
  private boolean tooltipHidden;
  private List<ItemRef> bundle;
  private List<ItemRef> container;
  private final Set<ToolKind> toolActions = EnumSet.noneOf(ToolKind.class);
  private final Set<Id> correctFor = new HashSet<>();

  public FakeItem(Id type, int amount) {
    this.type = Objects.requireNonNull(type, "type");
    this.amount = type.isAir() ? 0 : amount;
    this.maxStackSize = 64;
    String path = type.path();
    if (path.equals("bundle") || path.endsWith("_bundle")) {
      bundle = new ArrayList<>();
    } else if (path.endsWith("shulker_box")) {
      container = new ArrayList<>();
      for (int slot = 0; slot < 27; slot++) {
        container.add(new FakeItem(Id.minecraft("air"), 0));
      }
    }
  }

  public static FakeItem of(String vanillaPath) {
    return new FakeItem(Id.minecraft(vanillaPath), 1);
  }

  public static FakeItem empty() {
    return new FakeItem(Id.minecraft("air"), 0);
  }

  /** A damageable tool, for example {@code tool("iron_pickaxe", 250)}. */
  public static FakeItem tool(String vanillaPath, int maxDamage) {
    return tool(Id.minecraft(vanillaPath), maxDamage);
  }

  /** A damageable tool of any namespace, for example a modded pickaxe. */
  public static FakeItem tool(Id type, int maxDamage) {
    FakeItem item = new FakeItem(type, 1);
    item.maxDamage = maxDamage;
    item.maxStackSize = 1;
    return item;
  }

  /** Makes the stack answer the loader digging action of each kind, as a Forge paxel does. */
  public FakeItem withToolActions(ToolKind... kinds) {
    toolActions.addAll(List.of(kinds));
    return this;
  }

  /** Makes the stack the correct tool for these blocks, as a modded tool's mining rules would. */
  public FakeItem correctFor(Id... blocks) {
    correctFor.addAll(List.of(blocks));
    return this;
  }

  public Set<ToolKind> toolActions() {
    return Set.copyOf(toolActions);
  }

  public boolean isCorrectFor(Id block) {
    return correctFor.contains(block);
  }

  public FakeItem enchant(String vanillaPath, int level) {
    enchantments.put(Id.minecraft(vanillaPath), level);
    return this;
  }

  /** Entity a spawner item places, as set by {@link FakeItems#spawner}. */
  public Id spawnedType() {
    return spawnedType;
  }

  public FakeItem withSpawnedType(Id type) {
    spawnedType = type;
    return this;
  }

  public FakeItem withDamage(int value) {
    damage = value;
    return this;
  }

  @Override
  public Id type() {
    return type;
  }

  @Override
  public int amount() {
    return isEmpty() ? 0 : amount;
  }

  @Override
  public void setAmount(int amount) {
    this.amount = amount;
  }

  @Override
  public boolean isEmpty() {
    return type.isAir() || amount <= 0;
  }

  @Override
  public int maxStackSize() {
    return maxStackSize;
  }

  @Override
  public ItemRef copy() {
    return copyWithAmount(amount);
  }

  @Override
  public ItemRef copyWithAmount(int amount) {
    return copyOf(type, amount);
  }

  @Override
  public boolean clearStoredItems() {
    boolean had = !storedItems.isEmpty();
    storedItems.clear();
    return had;
  }

  @Override
  public Optional<List<ItemRef>> bundleContents() {
    return bundle == null ? Optional.empty() : Optional.of(copies(bundle));
  }

  @Override
  public void setBundleContents(List<ItemRef> items) {
    if (bundle != null) {
      bundle = copies(items);
    }
  }

  @Override
  public Optional<List<ItemRef>> containerContents() {
    return container == null ? Optional.empty() : Optional.of(copies(container));
  }

  @Override
  public void setContainerContents(List<ItemRef> slots) {
    if (container == null) {
      return;
    }
    List<ItemRef> next = new ArrayList<>(container.size());
    for (int slot = 0; slot < container.size(); slot++) {
      ItemRef item = slot < slots.size() ? slots.get(slot) : null;
      next.add(item == null ? empty() : item.copy());
    }
    container = next;
  }

  private static List<ItemRef> copies(List<ItemRef> items) {
    List<ItemRef> copies = new ArrayList<>(items.size());
    for (ItemRef item : items) {
      copies.add(item.copy());
    }
    return copies;
  }

  @Override
  public List<ItemRef> storedItems() {
    List<ItemRef> copies = new ArrayList<>(storedItems.size());
    for (ItemRef item : storedItems) {
      copies.add(item.copy());
    }
    return copies;
  }

  /** Puts items inside this one, like bundle contents or a shulker box inventory. */
  public FakeItem store(ItemRef... items) {
    storedItems.addAll(List.of(items));
    return this;
  }

  @Override
  public ItemRef copyAs(Id type) {
    return copyOf(type, amount);
  }

  private FakeItem copyOf(Id type, int amount) {
    FakeItem copy = new FakeItem(type, amount);
    copy.potionContents = potionContents;
    copy.toolActions.addAll(toolActions);
    copy.correctFor.addAll(correctFor);
    copy.fireworkStarColor = fireworkStarColor;
    copy.shieldDesign = shieldDesign;
    copy.damage = damage;
    copy.maxDamage = maxDamage;
    copy.maxStackSize = maxStackSize;
    copy.displayName = displayName;
    copy.displayNameJson = displayNameJson;
    copy.lore = new ArrayList<>(lore);
    copy.loreJson = new ArrayList<>(loreJson);
    copy.enchantments.putAll(enchantments);
    copy.storedEnchantments.putAll(storedEnchantments);
    copy.glint = glint;
    copy.hiddenTooltip.addAll(hiddenTooltip);
    copy.unbreakable = unbreakable;
    copy.itemModel = itemModel;
    copy.spawnedType = spawnedType;
    attributeModifiers.forEach((Id attribute, Map<Id, AttributeMod> modifiers) ->
        copy.attributeModifiers.put(attribute, new LinkedHashMap<>(modifiers)));
    copy.attributeModifierSlots.putAll(attributeModifierSlots);
    copy.attributeModifierGroups.putAll(attributeModifierGroups);
    for (ItemRef stored : storedItems) {
      copy.storedItems.add(stored.copy());
    }
    copy.bundle = bundle == null ? null : copies(bundle);
    copy.container = container == null ? null : copies(container);
    data.copyInto(copy.data);
    return copy;
  }

  @Override
  public boolean isSimilar(ItemRef other) {
    return other instanceof FakeItem that && type.equals(that.type) && damage == that.damage
        && maxDamage == that.maxDamage && Objects.equals(displayName, that.displayName) && lore.equals(that.lore)
        && loreJson.equals(that.loreJson)
        && enchantments.equals(that.enchantments) && unbreakable == that.unbreakable
        && Objects.equals(spawnedType, that.spawnedType) && Objects.equals(potionContents, that.potionContents);
  }

  @Override
  public Optional<PotionContents> potionContents() {
    if (!POTION_HOLDERS.contains(type.path()) || !type.namespace().equals("minecraft")) {
      return Optional.empty();
    }
    return Optional.of(potionContents == null ? PotionContents.EMPTY : potionContents);
  }

  @Override
  public void setPotionContents(PotionContents contents) {
    if (POTION_HOLDERS.contains(type.path()) && type.namespace().equals("minecraft")) {
      potionContents = contents;
    }
  }

  @Override
  public String displayName() {
    return displayName;
  }

  @Override
  public boolean hasDisplayName() {
    return displayName != null;
  }

  @Override
  public String displayNameJson() {
    return displayNameJson;
  }

  /** Sets the custom name JSON {@link #displayNameJson()} reports. */
  public FakeItem withDisplayNameJson(String json) {
    displayNameJson = json;
    return this;
  }

  @Override
  public void setDisplayName(String markup) {
    displayName = markup;
  }

  @Override
  public List<String> lore() {
    return List.copyOf(lore);
  }

  @Override
  public void setLore(List<String> markupLines) {
    lore = new ArrayList<>(markupLines);
  }

  @Override
  public List<String> loreJson() {
    return List.copyOf(loreJson);
  }

  @Override
  public void setLoreJson(List<String> jsonLines) {
    loreJson = jsonLines == null ? new ArrayList<>() : new ArrayList<>(jsonLines);
  }

  @Override
  public Map<Id, Integer> enchantments() {
    return Map.copyOf(enchantments);
  }

  @Override
  public int enchantmentLevel(Id enchantment) {
    return enchantments.getOrDefault(enchantment, 0);
  }

  @Override
  public void setEnchantment(Id enchantment, int level) {
    enchantments.put(enchantment, level);
  }

  @Override
  public void removeEnchantment(Id enchantment) {
    enchantments.remove(enchantment);
  }

  @Override
  public Map<Id, Integer> storedEnchantments() {
    return Map.copyOf(storedEnchantments);
  }

  @Override
  public void setStoredEnchantment(Id enchantment, int level) {
    storedEnchantments.put(enchantment, level);
  }

  @Override
  public void removeStoredEnchantment(Id enchantment) {
    storedEnchantments.remove(enchantment);
  }

  @Override
  public boolean hasGlint() {
    return glint != null ? glint : !enchantments.isEmpty();
  }

  @Override
  public void setGlintOverride(Boolean glint) {
    this.glint = glint;
  }

  @Override
  public void hideTooltip(TooltipPart part) {
    hiddenTooltip.add(part);
  }

  @Override
  public void clearComponents() {
    damage = 0;
    displayName = null;
    displayNameJson = null;
    lore = new ArrayList<>();
    loreJson = new ArrayList<>();
    enchantments.clear();
    storedEnchantments.clear();
    glint = null;
    hiddenTooltip.clear();
    unbreakable = false;
    itemModel = null;
    potionContents = null;
    fireworkStarColor = null;
    shieldDesign = new ShieldDesign(null, List.of());
    for (Id key : List.copyOf(data.keys())) {
      data.remove(key);
    }
    attributeModifiers.clear();
    attributeModifierSlots.clear();
    attributeModifierGroups.clear();
    storedItems.clear();
    tooltipHidden = false;
    if (bundle != null) {
      bundle.clear();
    }
  }

  @Override
  public void setHideTooltip(boolean hidden) {
    this.tooltipHidden = hidden;
  }

  /** True when {@link #setHideTooltip} hid the whole tooltip. */
  public boolean tooltipHidden() {
    return tooltipHidden;
  }

  @Override
  public void setMaxStackSize(int maxStackSize) {
    this.maxStackSize = maxStackSize;
  }

  @Override
  public void setFireworkStarColor(Rgb color) {
    this.fireworkStarColor = color;
  }

  @Override
  public ShieldDesign shieldDesign() {
    return SHIELD.equals(type) ? shieldDesign : null;
  }

  @Override
  public void setShieldDesign(ShieldDesign design) {
    if (SHIELD.equals(type)) {
      shieldDesign = design;
    }
  }

  public Rgb fireworkStarColor() {
    return fireworkStarColor;
  }

  public Set<TooltipPart> hiddenTooltip() {
    return Set.copyOf(hiddenTooltip);
  }

  @Override
  public int damage() {
    return damage;
  }

  @Override
  public void setDamage(int damage) {
    if (maxDamage > 0) {
      this.damage = Math.max(0, damage);
    }
  }

  @Override
  public int maxDamage() {
    return maxDamage;
  }

  @Override
  public void setMaxDamage(int maxDamage) {
    if (this.maxDamage > 0) {
      this.maxDamage = Math.max(1, maxDamage);
    }
  }

  @Override
  public boolean isUnbreakable() {
    return unbreakable;
  }

  @Override
  public void setUnbreakable(boolean unbreakable) {
    this.unbreakable = unbreakable;
  }

  @Override
  public Id itemModel() {
    return itemModel;
  }

  @Override
  public void setItemModel(Id model) {
    itemModel = model;
  }

  @Override
  public void setAttributeModifier(Id attribute, AttributeMod modifier, EquipmentSlot slot) {
    attributeModifiers.computeIfAbsent(attribute, (Id ignored) -> new LinkedHashMap<>()).put(modifier.key(), modifier);
    attributeModifierSlots.put(modifier.key(), slot);
  }

  @Override
  public void setAttributeModifier(Id attribute, AttributeMod modifier, EquipmentSlotGroup group) {
    attributeModifiers.computeIfAbsent(attribute, (Id ignored) -> new LinkedHashMap<>()).put(modifier.key(), modifier);
    attributeModifierGroups.put(modifier.key(), group);
  }

  /** Slot group of the modifier with {@code key}, or null. */
  public EquipmentSlotGroup attributeModifierGroup(Id key) {
    return attributeModifierGroups.get(key);
  }

  @Override
  public void removeAttributeModifier(Id attribute, Id key) {
    Map<Id, AttributeMod> modifiers = attributeModifiers.get(attribute);
    if (modifiers != null && modifiers.remove(key) != null) {
      attributeModifierSlots.remove(key);
    }
  }

  /** Modifier with {@code key} on {@code attribute}, or null. */
  public AttributeMod attributeModifier(Id attribute, Id key) {
    Map<Id, AttributeMod> modifiers = attributeModifiers.get(attribute);
    return modifiers == null ? null : modifiers.get(key);
  }

  /** Slot the modifier with {@code key} is active in, or null. */
  public EquipmentSlot attributeModifierSlot(Id key) {
    return attributeModifierSlots.get(key);
  }

  @Override
  public DataHolder data() {
    return data;
  }

  @Override
  public Object unwrap() {
    return this;
  }

  @Override
  public String toString() {
    return type + " x" + amount + (maxDamage > 0 ? " " + damage + "/" + maxDamage : "");
  }
}
