package art.arcane.adapt.bukkit.platform.item;

import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.entity.EquipmentSlotGroup;
import art.arcane.adapt.platform.item.BannerLayer;
import art.arcane.adapt.platform.item.ShieldDesign;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The parts of {@link BukkitItemRef} bound to the item components Minecraft 1.20.5 introduced, emulated on 1.20.1. A
 * forced glint is a hidden marker enchantment; a hidden tooltip hides every item flag; stack size and durability caps
 * keep the material's own values; an attribute modifier is identified by a UUID derived from its key and applies to a
 * single slot (a group of several slots applies everywhere); a shield's design is its banner block state.
 */
final class BukkitItem120Version {
  private static final NamespacedKey GLINT = new NamespacedKey("adapt", "glint_override");
  private static final Enchantment GLINT_ENCHANTMENT = Enchantment.LUCK;
  private static final byte GLINT_ENCHANT = 1;
  private static final byte GLINT_FLAG = 2;

  private BukkitItem120Version() {
  }

  static ItemStack enchantWithLevels(ItemStack stack, int levels, boolean treasure, Random random) {
    return Bukkit.getItemFactory().enchantWithLevels(stack, levels, treasure, random);
  }

  static Boolean glintOverride(ItemMeta meta) {
    return meta.getPersistentDataContainer().has(GLINT, PersistentDataType.BYTE) ? Boolean.TRUE : null;
  }

  static void setGlintOverride(ItemMeta meta, Boolean glint) {
    Byte marker = meta.getPersistentDataContainer().get(GLINT, PersistentDataType.BYTE);
    if (Boolean.TRUE.equals(glint)) {
      if (marker != null || meta.hasEnchants()) {
        return;
      }
      byte added = GLINT_ENCHANT;
      meta.addEnchant(GLINT_ENCHANTMENT, 1, true);
      if (!meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        added |= GLINT_FLAG;
      }
      meta.getPersistentDataContainer().set(GLINT, PersistentDataType.BYTE, added);
      return;
    }
    if (marker == null) {
      return;
    }
    if ((marker & GLINT_ENCHANT) != 0) {
      meta.removeEnchant(GLINT_ENCHANTMENT);
    }
    if ((marker & GLINT_FLAG) != 0) {
      meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    meta.getPersistentDataContainer().remove(GLINT);
  }

  static void setHideTooltip(ItemMeta meta, boolean hidden) {
    if (hidden) {
      meta.addItemFlags(ItemFlag.values());
    } else {
      meta.removeItemFlags(ItemFlag.values());
    }
  }

  static void setMaxStackSize(ItemMeta meta, int maxStackSize) {
  }

  static int maxDamage(ItemMeta meta, Material type) {
    return type.getMaxDurability();
  }

  static void setMaxDamage(ItemMeta meta, int maxDamage) {
  }

  static AttributeModifier modifier(NamespacedKey key, double amount, AttributeModifier.Operation operation,
                                    EquipmentSlot slot) {
    return new AttributeModifier(uuid(key), key.toString(), amount, operation, slot);
  }

  static AttributeModifier modifier(NamespacedKey key, double amount, AttributeModifier.Operation operation,
                                    EquipmentSlotGroup group) {
    EquipmentSlot slot = switch (group) {
      case ANY, HAND, ARMOR -> null;
      case MAINHAND -> EquipmentSlot.HAND;
      case OFFHAND -> EquipmentSlot.OFF_HAND;
      case FEET -> EquipmentSlot.FEET;
      case LEGS -> EquipmentSlot.LEGS;
      case CHEST -> EquipmentSlot.CHEST;
      case HEAD -> EquipmentSlot.HEAD;
      case BODY, SADDLE -> throw new IllegalArgumentException("No " + group + " slot group on this server");
    };
    return new AttributeModifier(uuid(key), key.toString(), amount, operation, slot);
  }

  static boolean hasKey(AttributeModifier modifier, NamespacedKey key) {
    return uuid(key).equals(modifier.getUniqueId());
  }

  static Multimap<Attribute, AttributeModifier> defaultModifiers(Material type) {
    Multimap<Attribute, AttributeModifier> modifiers = LinkedHashMultimap.create();
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      modifiers.putAll(type.getDefaultAttributeModifiers(slot));
    }
    return modifiers;
  }

  static Id basePotion(PotionMeta meta) {
    return BukkitResolve.id(meta.getBasePotionData());
  }

  static void setBasePotion(PotionMeta meta, Id potion) {
    PotionData data = BukkitResolve.potionData(potion);
    meta.setBasePotionData(data == null ? new PotionData(PotionType.UNCRAFTABLE) : data);
  }

  static ShieldDesign shieldDesign(ItemMeta meta) {
    if (!(meta instanceof BlockStateMeta state) || !(state.getBlockState() instanceof Banner banner)) {
      return null;
    }
    if (!state.hasBlockState()) {
      return new ShieldDesign(null, List.of());
    }
    List<BannerLayer> layers = new ArrayList<>();
    for (Pattern pattern : banner.getPatterns()) {
      Id type = BukkitResolve.id(pattern.getPattern());
      if (type != null) {
        layers.add(new BannerLayer(DyeColor.valueOf(pattern.getColor().name()), type));
      }
    }
    org.bukkit.DyeColor base = banner.getBaseColor();
    return new ShieldDesign(base == null ? null : DyeColor.valueOf(base.name()), layers);
  }

  static void setShieldDesign(ItemMeta meta, ShieldDesign design) {
    if (!(meta instanceof BlockStateMeta state) || !(state.getBlockState() instanceof Banner banner)) {
      return;
    }
    List<Pattern> patterns = new ArrayList<>();
    for (BannerLayer layer : design.patterns()) {
      PatternType type = BukkitResolve.bannerPattern(layer.pattern());
      if (type != null) {
        patterns.add(new Pattern(org.bukkit.DyeColor.valueOf(layer.color().name()), type));
      }
    }
    if (design.baseColor() != null) {
      banner.setBaseColor(org.bukkit.DyeColor.valueOf(design.baseColor().name()));
    }
    banner.setPatterns(patterns);
    state.setBlockState(banner);
  }

  private static UUID uuid(NamespacedKey key) {
    return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
  }
}
