package art.arcane.adapt.bukkit.platform.item;

import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.entity.EquipmentSlotGroup;
import art.arcane.adapt.platform.item.BannerLayer;
import art.arcane.adapt.platform.item.ShieldDesign;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import com.google.common.collect.Multimap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The parts of {@link BukkitItemRef} and {@link BukkitItemOps} bound to the item components Minecraft 1.20.5
 * introduced.
 */
final class BukkitItem120Version {
  private static volatile boolean enchantWithLevelsSupported = true;

  private BukkitItem120Version() {
  }

  @SuppressWarnings("deprecation")
  static ItemStack enchantWithLevels(ItemStack stack, int levels, boolean treasure, Random random) {
    if (enchantWithLevelsSupported) {
      try {
        return Bukkit.getItemFactory().enchantWithLevels(stack, levels, treasure, random);
      } catch (NoSuchMethodError absent) {
        enchantWithLevelsSupported = false;
      }
    }
    return Bukkit.getItemFactory().enchantItem(stack, levels, treasure);
  }

  static Boolean glintOverride(ItemMeta meta) {
    return meta.hasEnchantmentGlintOverride() ? Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()) : null;
  }

  static void setGlintOverride(ItemMeta meta, Boolean glint) {
    meta.setEnchantmentGlintOverride(glint);
  }

  static void setHideTooltip(ItemMeta meta, boolean hidden) {
    meta.setHideTooltip(hidden);
  }

  static void setMaxStackSize(ItemMeta meta, int maxStackSize) {
    meta.setMaxStackSize(maxStackSize);
  }

  static int maxDamage(ItemMeta meta, Material type) {
    if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
      return damageable.getMaxDamage();
    }
    return type.getMaxDurability();
  }

  static void setMaxDamage(ItemMeta meta, int maxDamage) {
    if (meta instanceof Damageable damageable) {
      damageable.setMaxDamage(maxDamage);
    }
  }

  static AttributeModifier modifier(NamespacedKey key, double amount, AttributeModifier.Operation operation,
                                    EquipmentSlot slot) {
    return new AttributeModifier(key, amount, operation, slot.getGroup());
  }

  static AttributeModifier modifier(NamespacedKey key, double amount, AttributeModifier.Operation operation,
                                    EquipmentSlotGroup group) {
    return new AttributeModifier(key, amount, operation, slotGroup(group));
  }

  static boolean hasKey(AttributeModifier modifier, NamespacedKey key) {
    return key.equals(modifier.getKey());
  }

  static Multimap<Attribute, AttributeModifier> defaultModifiers(Material type) {
    return type.getDefaultAttributeModifiers();
  }

  static Id basePotion(PotionMeta meta) {
    PotionType base = meta.getBasePotionType();
    return base == null ? null : BukkitResolve.id(base);
  }

  static void setBasePotion(PotionMeta meta, Id potion) {
    meta.setBasePotionType(BukkitResolve.potion(potion));
  }

  static ShieldDesign shieldDesign(ItemMeta meta) {
    if (!(meta instanceof ShieldMeta shield)) {
      return null;
    }
    List<BannerLayer> layers = new ArrayList<>();
    for (Pattern pattern : shield.getPatterns()) {
      Id type = BukkitResolve.id(pattern.getPattern());
      if (type != null) {
        layers.add(new BannerLayer(DyeColor.valueOf(pattern.getColor().name()), type));
      }
    }
    org.bukkit.DyeColor base = shield.getBaseColor();
    return new ShieldDesign(base == null ? null : DyeColor.valueOf(base.name()), layers);
  }

  static void setShieldDesign(ItemMeta meta, ShieldDesign design) {
    if (!(meta instanceof ShieldMeta shield)) {
      return;
    }
    List<Pattern> patterns = new ArrayList<>();
    for (BannerLayer layer : design.patterns()) {
      PatternType type = BukkitResolve.bannerPattern(layer.pattern());
      if (type != null) {
        patterns.add(new Pattern(org.bukkit.DyeColor.valueOf(layer.color().name()), type));
      }
    }
    shield.setBaseColor(design.baseColor() == null ? null : org.bukkit.DyeColor.valueOf(design.baseColor().name()));
    shield.setPatterns(patterns);
  }

  private static org.bukkit.inventory.EquipmentSlotGroup slotGroup(EquipmentSlotGroup group) {
    org.bukkit.inventory.EquipmentSlotGroup resolved = org.bukkit.inventory.EquipmentSlotGroup.getByName(group.name().toLowerCase(Locale.ROOT));
    if (resolved == null) {
      throw new IllegalArgumentException("No " + group + " slot group on this server");
    }
    return resolved;
  }
}
