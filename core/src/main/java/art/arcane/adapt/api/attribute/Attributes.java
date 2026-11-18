package art.arcane.adapt.api.attribute;

import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** Vanilla attribute ids (26.x names). Check {@code LivingRef.hasAttribute} before relying on ids newer than the running version. */
public final class Attributes {
  public static final Id AIR_DRAG_MODIFIER = Id.minecraft("air_drag_modifier");
  public static final Id ARMOR = Id.minecraft("armor");
  public static final Id ARMOR_TOUGHNESS = Id.minecraft("armor_toughness");
  public static final Id ATTACK_DAMAGE = Id.minecraft("attack_damage");
  public static final Id ATTACK_KNOCKBACK = Id.minecraft("attack_knockback");
  public static final Id ATTACK_SPEED = Id.minecraft("attack_speed");
  public static final Id BELOW_NAME_DISTANCE = Id.minecraft("below_name_distance");
  public static final Id BLOCK_BREAK_SPEED = Id.minecraft("block_break_speed");
  public static final Id BLOCK_INTERACTION_RANGE = Id.minecraft("block_interaction_range");
  public static final Id BOUNCINESS = Id.minecraft("bounciness");
  public static final Id BURNING_TIME = Id.minecraft("burning_time");
  public static final Id CAMERA_DISTANCE = Id.minecraft("camera_distance");
  public static final Id ENTITY_INTERACTION_RANGE = Id.minecraft("entity_interaction_range");
  public static final Id EXPLOSION_KNOCKBACK_RESISTANCE = Id.minecraft("explosion_knockback_resistance");
  public static final Id FALL_DAMAGE_MULTIPLIER = Id.minecraft("fall_damage_multiplier");
  public static final Id FLYING_SPEED = Id.minecraft("flying_speed");
  public static final Id FOLLOW_RANGE = Id.minecraft("follow_range");
  public static final Id FRICTION_MODIFIER = Id.minecraft("friction_modifier");
  public static final Id GRAVITY = Id.minecraft("gravity");
  public static final Id JUMP_STRENGTH = Id.minecraft("jump_strength");
  public static final Id KNOCKBACK_RESISTANCE = Id.minecraft("knockback_resistance");
  public static final Id LUCK = Id.minecraft("luck");
  public static final Id MAX_ABSORPTION = Id.minecraft("max_absorption");
  public static final Id MAX_HEALTH = Id.minecraft("max_health");
  public static final Id MINING_EFFICIENCY = Id.minecraft("mining_efficiency");
  public static final Id MOVEMENT_EFFICIENCY = Id.minecraft("movement_efficiency");
  public static final Id MOVEMENT_SPEED = Id.minecraft("movement_speed");
  public static final Id NAME_TAG_DISTANCE = Id.minecraft("name_tag_distance");
  public static final Id OXYGEN_BONUS = Id.minecraft("oxygen_bonus");
  public static final Id SAFE_FALL_DISTANCE = Id.minecraft("safe_fall_distance");
  public static final Id SCALE = Id.minecraft("scale");
  public static final Id SNEAKING_SPEED = Id.minecraft("sneaking_speed");
  public static final Id SPAWN_REINFORCEMENTS = Id.minecraft("spawn_reinforcements");
  public static final Id STEP_HEIGHT = Id.minecraft("step_height");
  public static final Id SUBMERGED_MINING_SPEED = Id.minecraft("submerged_mining_speed");
  public static final Id SWEEPING_DAMAGE_RATIO = Id.minecraft("sweeping_damage_ratio");
  public static final Id TEMPT_RANGE = Id.minecraft("tempt_range");
  public static final Id WATER_MOVEMENT_EFFICIENCY = Id.minecraft("water_movement_efficiency");
  public static final Id WAYPOINT_RECEIVE_RANGE = Id.minecraft("waypoint_receive_range");
  public static final Id WAYPOINT_TRANSMIT_RANGE = Id.minecraft("waypoint_transmit_range");

  public static final List<Id> ALL = List.of(
      AIR_DRAG_MODIFIER, ARMOR, ARMOR_TOUGHNESS, ATTACK_DAMAGE, ATTACK_KNOCKBACK, ATTACK_SPEED,
      BELOW_NAME_DISTANCE, BLOCK_BREAK_SPEED, BLOCK_INTERACTION_RANGE, BOUNCINESS, BURNING_TIME,
      CAMERA_DISTANCE, ENTITY_INTERACTION_RANGE, EXPLOSION_KNOCKBACK_RESISTANCE, FALL_DAMAGE_MULTIPLIER,
      FLYING_SPEED, FOLLOW_RANGE, FRICTION_MODIFIER, GRAVITY, JUMP_STRENGTH, KNOCKBACK_RESISTANCE,
      LUCK, MAX_ABSORPTION, MAX_HEALTH, MINING_EFFICIENCY, MOVEMENT_EFFICIENCY, MOVEMENT_SPEED,
      NAME_TAG_DISTANCE, OXYGEN_BONUS, SAFE_FALL_DISTANCE, SCALE, SNEAKING_SPEED, SPAWN_REINFORCEMENTS,
      STEP_HEIGHT, SUBMERGED_MINING_SPEED, SWEEPING_DAMAGE_RATIO, TEMPT_RANGE, WATER_MOVEMENT_EFFICIENCY,
      WAYPOINT_RECEIVE_RANGE, WAYPOINT_TRANSMIT_RANGE
  );

  private Attributes() {
  }
}
