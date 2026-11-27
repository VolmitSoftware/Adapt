package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Optional;

public enum GolemEquipmentSlot {
  HELMET("helmet", EquipmentSlot.HEAD),
  CHESTPLATE("chestplate", EquipmentSlot.CHEST),
  LEGGINGS("leggings", EquipmentSlot.LEGS),
  BOOTS("boots", EquipmentSlot.FEET),
  MAIN_HAND("main_hand", EquipmentSlot.MAIN_HAND),
  OFF_HAND("off_hand", EquipmentSlot.OFF_HAND);

  private static final GolemEquipmentSlot[] VALUES = values();
  private static final Id CARVED_PUMPKIN = Id.minecraft("carved_pumpkin");
  private static final Id ELYTRA = Id.minecraft("elytra");

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

  public static boolean isAir(Id material) {
    return material == null || material.isAir();
  }

  public static void strip(LivingRef entity) {
    for (GolemEquipmentSlot candidate : VALUES) {
      entity.setEquipment(candidate.slot, null);
      entity.setEquipmentDropChance(candidate.slot, 0F);
    }
  }

  public String id() {
    return id;
  }

  public EquipmentSlot slot() {
    return slot;
  }

  public boolean accepts(Id material) {
    if (isAir(material)) {
      return false;
    }
    String name = material.path();
    return switch (this) {
      case HELMET -> name.endsWith("_helmet") || name.endsWith("_head") || name.endsWith("_skull") || material.equals(CARVED_PUMPKIN);
      case CHESTPLATE -> name.endsWith("_chestplate") || material.equals(ELYTRA);
      case LEGGINGS -> name.endsWith("_leggings");
      case BOOTS -> name.endsWith("_boots");
      case MAIN_HAND, OFF_HAND -> true;
    };
  }

  public Text displayName() {
    return switch (this) {
      case HELMET -> Text.key("adapt.golemancy.gui.equipment_helmet");
      case CHESTPLATE -> Text.key("adapt.golemancy.gui.equipment_chestplate");
      case LEGGINGS -> Text.key("adapt.golemancy.gui.equipment_leggings");
      case BOOTS -> Text.key("adapt.golemancy.gui.equipment_boots");
      case MAIN_HAND -> Text.key("adapt.golemancy.gui.equipment_main_hand");
      case OFF_HAND -> Text.key("adapt.golemancy.gui.equipment_off_hand");
    };
  }
}
