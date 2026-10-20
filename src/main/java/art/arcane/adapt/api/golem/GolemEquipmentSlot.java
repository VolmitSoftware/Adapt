package art.arcane.adapt.api.golem;

import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public enum GolemEquipmentSlot {
  HELMET("helmet", EquipmentSlot.HEAD),
  CHESTPLATE("chestplate", EquipmentSlot.CHEST),
  LEGGINGS("leggings", EquipmentSlot.LEGS),
  BOOTS("boots", EquipmentSlot.FEET),
  MAIN_HAND("main_hand", EquipmentSlot.HAND),
  OFF_HAND("off_hand", EquipmentSlot.OFF_HAND);

  private static final GolemEquipmentSlot[] VALUES = values();

  private final String id;
  private final EquipmentSlot slot;

  GolemEquipmentSlot(String id, EquipmentSlot slot) {
    this.id = id;
    this.slot = slot;
  }

  public static Optional<GolemEquipmentSlot> fromId(String id) {
    for (GolemEquipmentSlot candidate : VALUES) {
      if (candidate.id.equals(id)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  public static boolean isAir(Material material) {
    return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
  }

  public static void strip(LivingEntity entity) {
    EntityEquipment equipment = entity.getEquipment();
    if (equipment == null) {
      return;
    }
    for (GolemEquipmentSlot candidate : VALUES) {
      equipment.setItem(candidate.slot, null);
      equipment.setDropChance(candidate.slot, 0F);
    }
  }

  public String id() {
    return id;
  }

  public EquipmentSlot slot() {
    return slot;
  }

  public boolean accepts(Material material) {
    if (isAir(material)) {
      return false;
    }
    String name = material.name();
    return switch (this) {
      case HELMET -> name.endsWith("_HELMET") || name.endsWith("_HEAD") || name.endsWith("_SKULL") || material == Material.CARVED_PUMPKIN;
      case CHESTPLATE -> name.endsWith("_CHESTPLATE") || material == Material.ELYTRA;
      case LEGGINGS -> name.endsWith("_LEGGINGS");
      case BOOTS -> name.endsWith("_BOOTS");
      case MAIN_HAND, OFF_HAND -> true;
    };
  }

  public TextKey displayName() {
    return switch (this) {
      case HELMET -> GolemancyMessages.GUI_EQUIPMENT_HELMET;
      case CHESTPLATE -> GolemancyMessages.GUI_EQUIPMENT_CHESTPLATE;
      case LEGGINGS -> GolemancyMessages.GUI_EQUIPMENT_LEGGINGS;
      case BOOTS -> GolemancyMessages.GUI_EQUIPMENT_BOOTS;
      case MAIN_HAND -> GolemancyMessages.GUI_EQUIPMENT_MAIN_HAND;
      case OFF_HAND -> GolemancyMessages.GUI_EQUIPMENT_OFF_HAND;
    };
  }
}
