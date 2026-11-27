package art.arcane.adapt.bukkit.platform.item;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.bukkit.platform.data.PdcDataHolder;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.EquipmentSlotGroup;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.ShieldDesign;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.util.common.compat.PaperApi;
import art.arcane.adapt.util.reflect.registries.ItemFlags;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.compat.PaperCompat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Color;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import org.bukkit.Material;
import org.bukkit.FireworkEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Container;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Item handle over a live Bukkit stack. Meta edits are written back to the same stack immediately. */
public final class BukkitItemRef implements ItemRef {
  private final ItemStack stack;
  private DataHolder data;

  public BukkitItemRef(ItemStack stack) {
    this.stack = Objects.requireNonNull(stack, "stack");
  }

  public ItemStack stack() {
    return stack;
  }

  @Override
  public Id type() {
    return BukkitResolve.id(stack.getType());
  }

  @Override
  public int amount() {
    return isEmpty() ? 0 : stack.getAmount();
  }

  @Override
  public void setAmount(int amount) {
    if (!BukkitResolve.isAir(stack.getType())) {
      stack.setAmount(amount);
    }
  }

  @Override
  public boolean isEmpty() {
    return BukkitResolve.isAir(stack.getType()) || stack.getAmount() <= 0;
  }

  @Override
  public int maxStackSize() {
    return stack.getMaxStackSize();
  }

  @Override
  public ItemRef copy() {
    return new BukkitItemRef(stack.clone());
  }

  @Override
  public ItemRef copyWithAmount(int amount) {
    ItemStack copy = stack.clone();
    if (!BukkitResolve.isAir(copy.getType())) {
      copy.setAmount(amount);
    }
    return new BukkitItemRef(copy);
  }

  @Override
  public boolean isSimilar(ItemRef other) {
    return other instanceof BukkitItemRef that && stack.isSimilar(that.stack);
  }

  @Override
  public String displayName() {
    ItemMeta meta = stack.getItemMeta();
    if (meta != null && meta.hasDisplayName()) {
      return meta.getDisplayName();
    }
    return BukkitItemVersion.effectiveName(stack);
  }

  @Override
  public boolean hasDisplayName() {
    ItemMeta meta = stack.getItemMeta();
    return meta != null && meta.hasDisplayName();
  }

  @Override
  public String displayNameJson() {
    Component name = PaperCompat.displayName(stack);
    return name == null ? null : GsonComponentSerializer.gson().serialize(name);
  }

  @Override
  public void setDisplayName(String markup) {
    edit((ItemMeta meta) -> meta.setDisplayName(BukkitText.legacy(markup)));
  }

  @Override
  public List<String> lore() {
    ItemMeta meta = stack.getItemMeta();
    if (meta == null || !meta.hasLore()) {
      return List.of();
    }
    List<String> lore = meta.getLore();
    return lore == null ? List.of() : List.copyOf(lore);
  }

  @Override
  public void setLore(List<String> markupLines) {
    if (markupLines == null || markupLines.isEmpty()) {
      edit((ItemMeta meta) -> meta.setLore(null));
      return;
    }
    List<String> lines = new ArrayList<>(markupLines.size());
    for (String line : markupLines) {
      lines.add(BukkitText.legacy(line));
    }
    edit((ItemMeta meta) -> meta.setLore(lines));
  }

  @Override
  public List<String> loreJson() {
    ItemMeta meta = stack.getItemMeta();
    List<Component> lore = meta == null ? null : PaperApi.lore(meta);
    if (lore == null || lore.isEmpty()) {
      return List.of();
    }
    List<String> json = new ArrayList<>(lore.size());
    for (Component line : lore) {
      json.add(GsonComponentSerializer.gson().serialize(line));
    }
    return json;
  }

  @Override
  public void setLoreJson(List<String> jsonLines) {
    if (jsonLines == null || jsonLines.isEmpty()) {
      edit((ItemMeta meta) -> PaperApi.setLore(meta, null));
      return;
    }
    List<Component> lines = new ArrayList<>(jsonLines.size());
    for (String line : jsonLines) {
      try {
        lines.add(GsonComponentSerializer.gson().deserialize(line));
      } catch (RuntimeException unreadable) {
        throw new IllegalArgumentException("Not a text component: " + line, unreadable);
      }
    }
    edit((ItemMeta meta) -> PaperApi.setLore(meta, lines));
  }

  @Override
  public Map<Id, Integer> enchantments() {
    ItemMeta meta = stack.getItemMeta();
    return meta == null ? Map.of() : levels(meta.getEnchants());
  }

  @Override
  public int enchantmentLevel(Id enchantment) {
    Enchantment resolved = BukkitResolve.enchantment(enchantment);
    ItemMeta meta = resolved == null ? null : stack.getItemMeta();
    return meta == null ? 0 : meta.getEnchantLevel(resolved);
  }

  @Override
  public void setEnchantment(Id enchantment, int level) {
    Enchantment resolved = BukkitResolve.enchantment(enchantment);
    if (resolved == null) {
      return;
    }
    if (level <= 0) {
      edit((ItemMeta meta) -> meta.removeEnchant(resolved));
    } else {
      edit((ItemMeta meta) -> meta.addEnchant(resolved, level, true));
    }
  }

  @Override
  public void removeEnchantment(Id enchantment) {
    setEnchantment(enchantment, 0);
  }

  @Override
  public Map<Id, Integer> storedEnchantments() {
    return stack.getItemMeta() instanceof EnchantmentStorageMeta storage ? levels(storage.getStoredEnchants()) : Map.of();
  }

  @Override
  public void setStoredEnchantment(Id enchantment, int level) {
    Enchantment resolved = BukkitResolve.enchantment(enchantment);
    if (resolved == null) {
      return;
    }
    edit((ItemMeta meta) -> {
      if (meta instanceof EnchantmentStorageMeta storage) {
        if (level <= 0) {
          storage.removeStoredEnchant(resolved);
        } else {
          storage.addStoredEnchant(resolved, level, true);
        }
      }
    });
  }

  @Override
  public void removeStoredEnchantment(Id enchantment) {
    setStoredEnchantment(enchantment, 0);
  }

  @Override
  public boolean hasGlint() {
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return false;
    }
    Boolean glint = BukkitItem120Version.glintOverride(meta);
    if (glint != null) {
      return glint;
    }
    return meta.hasEnchants() || (meta instanceof EnchantmentStorageMeta storage && storage.hasStoredEnchants());
  }

  @Override
  public void setGlintOverride(Boolean glint) {
    edit((ItemMeta meta) -> BukkitItem120Version.setGlintOverride(meta, glint));
  }

  @Override
  public void clearComponents() {
    stack.setItemMeta(null);
  }

  @Override
  public void setHideTooltip(boolean hidden) {
    edit((ItemMeta meta) -> BukkitItem120Version.setHideTooltip(meta, hidden));
  }

  @Override
  public void hideTooltip(TooltipPart part) {
    ItemFlag flag = switch (part) {
      case ATTRIBUTES -> ItemFlag.HIDE_ATTRIBUTES;
      case ENCHANTMENTS -> ItemFlag.HIDE_ENCHANTS;
      case POTION_EFFECTS -> ItemFlags.HIDE_POTION_EFFECTS;
      case UNBREAKABLE -> ItemFlag.HIDE_UNBREAKABLE;
      case ADDITIONAL -> ItemFlags.HIDE_ADDITIONAL_TOOLTIP;
      case DYE -> ItemFlag.HIDE_DYE;
    };
    if (flag != null) {
      edit((ItemMeta meta) -> meta.addItemFlags(flag));
    }
  }

  @Override
  public void setMaxStackSize(int maxStackSize) {
    edit((ItemMeta meta) -> BukkitItem120Version.setMaxStackSize(meta, maxStackSize));
  }

  @Override
  public void setFireworkStarColor(Rgb color) {
    edit((ItemMeta meta) -> {
      if (meta instanceof FireworkEffectMeta firework) {
        firework.setEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL)
            .withColor(Color.fromRGB(color.red(), color.green(), color.blue())).build());
      }
    });
  }

  @Override
  public ShieldDesign shieldDesign() {
    return BukkitItem120Version.shieldDesign(stack.getItemMeta());
  }

  @Override
  public void setShieldDesign(ShieldDesign design) {
    edit((ItemMeta meta) -> BukkitItem120Version.setShieldDesign(meta, design));
  }

  @Override
  public int damage() {
    return stack.getItemMeta() instanceof Damageable damageable ? damageable.getDamage() : 0;
  }

  @Override
  public void setDamage(int damage) {
    edit((ItemMeta meta) -> {
      if (meta instanceof Damageable damageable) {
        damageable.setDamage(Math.max(0, damage));
      }
    });
  }

  @Override
  public int maxDamage() {
    return BukkitItem120Version.maxDamage(stack.getItemMeta(), stack.getType());
  }

  @Override
  public void setMaxDamage(int maxDamage) {
    edit((ItemMeta meta) -> BukkitItem120Version.setMaxDamage(meta, Math.max(1, maxDamage)));
  }

  @Override
  public boolean isUnbreakable() {
    ItemMeta meta = stack.getItemMeta();
    return meta != null && meta.isUnbreakable();
  }

  @Override
  public void setUnbreakable(boolean unbreakable) {
    edit((ItemMeta meta) -> meta.setUnbreakable(unbreakable));
  }

  @Override
  public Id itemModel() {
    ItemMeta meta = stack.getItemMeta();
    return meta == null ? null : BukkitItemVersion.itemModel(meta);
  }

  @Override
  public void setItemModel(Id model) {
    NamespacedKey key = BukkitConversions.key(model);
    edit((ItemMeta meta) -> BukkitItemVersion.setItemModel(meta, key));
  }

  @Override
  public void setAttributeModifier(Id attribute, AttributeMod modifier, EquipmentSlot slot) {
    Attribute type = BukkitResolve.attribute(attribute);
    if (type == null) {
      return;
    }
    NamespacedKey key = BukkitConversions.key(modifier.key());
    edit((ItemMeta meta) -> {
      removeModifiers(meta, type, key);
      if (!meta.hasAttributeModifiers()) {
        for (Map.Entry<Attribute, AttributeModifier> entry : BukkitItem120Version.defaultModifiers(stack.getType()).entries()) {
          meta.addAttributeModifier(entry.getKey(), entry.getValue());
        }
      }
      meta.addAttributeModifier(type, BukkitItem120Version.modifier(key, modifier.amount(),
          BukkitConversions.operation(modifier.operation()), BukkitConversions.slot(slot)));
    });
  }

  @Override
  public void setAttributeModifier(Id attribute, AttributeMod modifier, EquipmentSlotGroup group) {
    Attribute type = BukkitResolve.attribute(attribute);
    if (type == null) {
      return;
    }
    NamespacedKey key = BukkitConversions.key(modifier.key());
    edit((ItemMeta meta) -> {
      removeModifiers(meta, type, key);
      if (!meta.hasAttributeModifiers()) {
        for (Map.Entry<Attribute, AttributeModifier> entry : BukkitItem120Version.defaultModifiers(stack.getType()).entries()) {
          meta.addAttributeModifier(entry.getKey(), entry.getValue());
        }
      }
      meta.addAttributeModifier(type, BukkitItem120Version.modifier(key, modifier.amount(),
          BukkitConversions.operation(modifier.operation()), group));
    });
  }

  @Override
  public void removeAttributeModifier(Id attribute, Id key) {
    Attribute type = BukkitResolve.attribute(attribute);
    if (type == null) {
      return;
    }
    NamespacedKey modifierKey = BukkitConversions.key(key);
    edit((ItemMeta meta) -> removeModifiers(meta, type, modifierKey));
  }

  private static void removeModifiers(ItemMeta meta, Attribute type, NamespacedKey key) {
    Collection<AttributeModifier> existing = meta.getAttributeModifiers(type);
    if (existing == null) {
      return;
    }
    for (AttributeModifier current : new ArrayList<>(existing)) {
      if (BukkitItem120Version.hasKey(current, key)) {
        meta.removeAttributeModifier(type, current);
      }
    }
  }

  @Override
  public Optional<PotionContents> potionContents() {
    if (!(stack.getItemMeta() instanceof PotionMeta meta)) {
      return Optional.empty();
    }
    List<EffectInstance> effects = new ArrayList<>();
    for (PotionEffect effect : meta.getCustomEffects()) {
      effects.add(EffectInstance.of(BukkitResolve.id(effect.getType()), effect.isInfinite() ? EffectInstance.INFINITE : effect.getDuration(), effect.getAmplifier()).withAmbient(effect.isAmbient()).withParticles(effect.hasParticles()).withIcon(effect.hasIcon()));
    }
    Color color = meta.hasColor() ? meta.getColor() : null;
    Component customName = BukkitItemVersion.customName(meta);
    String name = customName == null ? null : BukkitText.markup(customName);
    return Optional.of(PotionContents.of(BukkitItem120Version.basePotion(meta)).withColor(color == null ? null : Rgb.of(color.asRGB())).withCustomEffects(effects).withName(name));
  }

  @Override
  public void setPotionContents(PotionContents contents) {
    edit((ItemMeta meta) -> {
      if (!(meta instanceof PotionMeta potion)) {
        return;
      }
      potion.clearCustomEffects();
      for (EffectInstance effect : contents.customEffects()) {
        PotionEffectType type = BukkitResolve.effect(effect.type());
        if (type != null) {
          int duration = effect.isInfinite() ? PotionEffect.INFINITE_DURATION : effect.durationTicks();
          potion.addCustomEffect(new PotionEffect(type, duration, effect.amplifier(), effect.ambient(), effect.particles(),
              effect.icon()), true);
        }
      }
      potion.setColor(contents.color() == null ? null : Color.fromRGB(contents.color().packed()));
      BukkitItem120Version.setBasePotion(potion, contents.potion());
      Component current = BukkitItemVersion.customName(potion);
      if (!Objects.equals(current == null ? null : BukkitText.markup(current), contents.name())) {
        BukkitItemVersion.setCustomName(potion, contents.name() == null ? null : potionName(contents.name()));
      }
    });
  }

  private static Component potionName(String markup) {
    if (markup.indexOf('<') < 0 && markup.indexOf('§') < 0 && markup.indexOf('&') < 0) {
      return Component.text(markup).decoration(TextDecoration.ITALIC, false);
    }
    return BukkitText.itemText(markup);
  }

  @Override
  public ItemRef copyAs(Id type) {
    Material material = BukkitResolve.material(type);
    ItemStack copy = stack.clone();
    if (material != null) {
      copy.setType(material);
    }
    return new BukkitItemRef(copy);
  }

  @Override
  public DataHolder data() {
    DataHolder current = data;
    if (current == null) {
      current = new PdcDataHolder(new BukkitItemStore(stack));
      data = current;
    }
    return current;
  }

  @Override
  public Object unwrap() {
    return stack;
  }

  @Override
  public String toString() {
    return "BukkitItemRef[" + stack + "]";
  }

  @Override
  public boolean clearStoredItems() {
    if (BukkitResolve.isAir(stack.getType())) {
      return false;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
      bundle.setItems(List.of());
      stack.setItemMeta(bundle);
      return true;
    }
    if (meta instanceof BlockStateMeta state && state.hasBlockState() && state.getBlockState() instanceof Container container) {
      container.getInventory().clear();
      state.setBlockState(container);
      stack.setItemMeta(state);
      return true;
    }
    return false;
  }

  @Override
  public List<ItemRef> storedItems() {
    if (BukkitResolve.isAir(stack.getType())) {
      return List.of();
    }
    ItemMeta meta = stack.getItemMeta();
    List<ItemRef> stored = new ArrayList<>();
    if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
      for (ItemStack inner : bundle.getItems()) {
        stored.add(new BukkitItemRef(inner.clone()));
      }
    }
    if (meta instanceof BlockStateMeta state && state.hasBlockState() && state.getBlockState() instanceof Container container) {
      for (ItemStack inner : container.getInventory().getContents()) {
        stored.add(inner == null ? new BukkitItemRef(new ItemStack(Material.AIR)) : new BukkitItemRef(inner.clone()));
      }
    }
    return stored;
  }

  @Override
  public Optional<List<ItemRef>> bundleContents() {
    if (BukkitResolve.isAir(stack.getType()) || !(stack.getItemMeta() instanceof BundleMeta bundle)) {
      return Optional.empty();
    }
    List<ItemRef> items = new ArrayList<>();
    for (ItemStack inner : bundle.getItems()) {
      items.add(new BukkitItemRef(inner.clone()));
    }
    return Optional.of(items);
  }

  @Override
  public void setBundleContents(List<ItemRef> items) {
    if (BukkitResolve.isAir(stack.getType()) || !(stack.getItemMeta() instanceof BundleMeta bundle)) {
      return;
    }
    List<ItemStack> stacks = new ArrayList<>(items.size());
    for (ItemRef item : items) {
      stacks.add(BukkitRefs.item(item));
    }
    bundle.setItems(stacks);
    stack.setItemMeta(bundle);
  }

  @Override
  public Optional<List<ItemRef>> containerContents() {
    if (BukkitResolve.isAir(stack.getType()) || !(stack.getItemMeta() instanceof BlockStateMeta meta)
        || !(meta.getBlockState() instanceof Container container)) {
      return Optional.empty();
    }
    List<ItemRef> slots = new ArrayList<>();
    for (ItemStack inner : container.getInventory().getContents()) {
      slots.add(inner == null ? new BukkitItemRef(new ItemStack(Material.AIR)) : new BukkitItemRef(inner.clone()));
    }
    return Optional.of(slots);
  }

  @Override
  public void setContainerContents(List<ItemRef> slots) {
    if (BukkitResolve.isAir(stack.getType()) || !(stack.getItemMeta() instanceof BlockStateMeta meta)
        || !(meta.getBlockState() instanceof Container container)) {
      return;
    }
    int size = container.getInventory().getSize();
    for (int slot = 0; slot < size; slot++) {
      ItemRef item = slot < slots.size() ? slots.get(slot) : null;
      container.getInventory().setItem(slot, item == null || item.isEmpty() ? null : BukkitRefs.item(item));
    }
    meta.setBlockState(container);
    stack.setItemMeta(meta);
  }

  private void edit(Consumer<ItemMeta> change) {
    if (BukkitResolve.isAir(stack.getType())) {
      return;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return;
    }
    change.accept(meta);
    stack.setItemMeta(meta);
  }

  private static Map<Id, Integer> levels(Map<Enchantment, Integer> enchants) {
    if (enchants == null || enchants.isEmpty()) {
      return Map.of();
    }
    Map<Id, Integer> levels = new LinkedHashMap<>();
    for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
      levels.put(BukkitResolve.id(entry.getKey()), entry.getValue());
    }
    return levels;
  }
}
