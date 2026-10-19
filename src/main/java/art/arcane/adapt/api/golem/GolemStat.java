package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.reflect.registries.Attributes;
import org.bukkit.attribute.Attribute;

public enum GolemStat {
  MAX_HEALTH("max_health"),
  ARMOR("armor"),
  ARMOR_TOUGHNESS("armor_toughness"),
  ATTACK_DAMAGE("attack_damage"),
  MOVEMENT_SPEED("movement_speed"),
  KNOCKBACK_RESISTANCE("knockback_resistance"),
  FOLLOW_RANGE("follow_range"),
  WATER_MOVEMENT_EFFICIENCY("water_movement_efficiency");

  private final String id;

  GolemStat(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  public Attribute attribute() {
    return switch (this) {
      case MAX_HEALTH -> Attributes.MAX_HEALTH;
      case ARMOR -> Attributes.ARMOR;
      case ARMOR_TOUGHNESS -> Attributes.ARMOR_TOUGHNESS;
      case ATTACK_DAMAGE -> Attributes.ATTACK_DAMAGE;
      case MOVEMENT_SPEED -> Attributes.MOVEMENT_SPEED;
      case KNOCKBACK_RESISTANCE -> Attributes.KNOCKBACK_RESISTANCE;
      case FOLLOW_RANGE -> Attributes.FOLLOW_RANGE;
      case WATER_MOVEMENT_EFFICIENCY -> Attributes.WATER_MOVEMENT_EFFICIENCY;
    };
  }
}
