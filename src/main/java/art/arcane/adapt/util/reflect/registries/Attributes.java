package art.arcane.adapt.util.reflect.registries;

import org.bukkit.attribute.Attribute;

import java.util.ArrayList;
import java.util.List;

public class Attributes {
  public static final Attribute AIR_DRAG_MODIFIER = RegistryUtil.findNullable(Attribute.class, "air_drag_modifier");
  public static final Attribute ARMOR = RegistryUtil.findNullable(Attribute.class, "armor", "generic_armor");
  public static final Attribute ARMOR_TOUGHNESS = RegistryUtil.findNullable(Attribute.class, "armor_toughness", "generic_armor_toughness");
  public static final Attribute ATTACK_DAMAGE = RegistryUtil.findNullable(Attribute.class, "attack_damage", "generic_attack_damage");
  public static final Attribute ATTACK_KNOCKBACK = RegistryUtil.findNullable(Attribute.class, "attack_knockback", "generic_attack_knockback");
  public static final Attribute ATTACK_SPEED = RegistryUtil.findNullable(Attribute.class, "attack_speed", "generic_attack_speed");
  public static final Attribute BELOW_NAME_DISTANCE = RegistryUtil.findNullable(Attribute.class, "below_name_distance");
  public static final Attribute BLOCK_BREAK_SPEED = RegistryUtil.findNullable(Attribute.class, "block_break_speed", "player_block_break_speed");
  public static final Attribute BLOCK_INTERACTION_RANGE = RegistryUtil.findNullable(Attribute.class, "block_interaction_range", "player_block_interaction_range");
  public static final Attribute BOUNCINESS = RegistryUtil.findNullable(Attribute.class, "bounciness");
  public static final Attribute BURNING_TIME = RegistryUtil.findNullable(Attribute.class, "burning_time", "generic_burning_time");
  public static final Attribute CAMERA_DISTANCE = RegistryUtil.findNullable(Attribute.class, "camera_distance");
  public static final Attribute ENTITY_INTERACTION_RANGE = RegistryUtil.findNullable(Attribute.class, "entity_interaction_range", "player_entity_interaction_range");
  public static final Attribute EXPLOSION_KNOCKBACK_RESISTANCE = RegistryUtil.findNullable(Attribute.class, "explosion_knockback_resistance", "generic_explosion_knockback_resistance");
  public static final Attribute FALL_DAMAGE_MULTIPLIER = RegistryUtil.findNullable(Attribute.class, "fall_damage_multiplier", "generic_fall_damage_multiplier");
  public static final Attribute FLYING_SPEED = RegistryUtil.findNullable(Attribute.class, "flying_speed", "generic_flying_speed");
  public static final Attribute FOLLOW_RANGE = RegistryUtil.findNullable(Attribute.class, "follow_range", "generic_follow_range");
  public static final Attribute FRICTION_MODIFIER = RegistryUtil.findNullable(Attribute.class, "friction_modifier");
  public static final Attribute GRAVITY = RegistryUtil.findNullable(Attribute.class, "gravity", "generic_gravity");
  public static final Attribute JUMP_STRENGTH = RegistryUtil.findNullable(Attribute.class, "jump_strength", "generic_jump_strength", "horse_jump_strength");
  public static final Attribute KNOCKBACK_RESISTANCE = RegistryUtil.findNullable(Attribute.class, "knockback_resistance", "generic_knockback_resistance");
  public static final Attribute LUCK = RegistryUtil.findNullable(Attribute.class, "luck", "generic_luck");
  public static final Attribute MAX_ABSORPTION = RegistryUtil.findNullable(Attribute.class, "max_absorption", "generic_max_absorption");
  public static final Attribute MAX_HEALTH = RegistryUtil.findNullable(Attribute.class, "max_health", "generic_max_health");
  public static final Attribute MINING_EFFICIENCY = RegistryUtil.findNullable(Attribute.class, "mining_efficiency", "player_mining_efficiency");
  public static final Attribute MOVEMENT_EFFICIENCY = RegistryUtil.findNullable(Attribute.class, "movement_efficiency", "generic_movement_efficiency");
  public static final Attribute MOVEMENT_SPEED = RegistryUtil.findNullable(Attribute.class, "movement_speed", "generic_movement_speed");
  public static final Attribute NAME_TAG_DISTANCE = RegistryUtil.findNullable(Attribute.class, "name_tag_distance");
  public static final Attribute OXYGEN_BONUS = RegistryUtil.findNullable(Attribute.class, "oxygen_bonus", "generic_oxygen_bonus");
  public static final Attribute SAFE_FALL_DISTANCE = RegistryUtil.findNullable(Attribute.class, "safe_fall_distance", "generic_safe_fall_distance");
  public static final Attribute SCALE = RegistryUtil.findNullable(Attribute.class, "scale", "generic_scale");
  public static final Attribute SNEAKING_SPEED = RegistryUtil.findNullable(Attribute.class, "sneaking_speed", "player_sneaking_speed");
  public static final Attribute SPAWN_REINFORCEMENTS = RegistryUtil.findNullable(Attribute.class, "spawn_reinforcements", "zombie_spawn_reinforcements");
  public static final Attribute STEP_HEIGHT = RegistryUtil.findNullable(Attribute.class, "step_height", "generic_step_height");
  public static final Attribute SUBMERGED_MINING_SPEED = RegistryUtil.findNullable(Attribute.class, "submerged_mining_speed", "player_submerged_mining_speed");
  public static final Attribute SWEEPING_DAMAGE_RATIO = RegistryUtil.findNullable(Attribute.class, "sweeping_damage_ratio", "player_sweeping_damage_ratio");
  public static final Attribute TEMPT_RANGE = RegistryUtil.findNullable(Attribute.class, "tempt_range", "generic_tempt_range");
  public static final Attribute WATER_MOVEMENT_EFFICIENCY = RegistryUtil.findNullable(Attribute.class, "water_movement_efficiency", "generic_water_movement_efficiency");
  public static final Attribute WAYPOINT_RECEIVE_RANGE = RegistryUtil.findNullable(Attribute.class, "waypoint_receive_range");
  public static final Attribute WAYPOINT_TRANSMIT_RANGE = RegistryUtil.findNullable(Attribute.class, "waypoint_transmit_range");

  public static final List<Attribute> ALL = buildAll();

  private static List<Attribute> buildAll() {
    Attribute[] resolved = new Attribute[]{
        AIR_DRAG_MODIFIER, ARMOR, ARMOR_TOUGHNESS, ATTACK_DAMAGE, ATTACK_KNOCKBACK, ATTACK_SPEED,
        BELOW_NAME_DISTANCE, BLOCK_BREAK_SPEED, BLOCK_INTERACTION_RANGE, BOUNCINESS, BURNING_TIME,
        CAMERA_DISTANCE, ENTITY_INTERACTION_RANGE, EXPLOSION_KNOCKBACK_RESISTANCE, FALL_DAMAGE_MULTIPLIER,
        FLYING_SPEED, FOLLOW_RANGE, FRICTION_MODIFIER, GRAVITY, JUMP_STRENGTH, KNOCKBACK_RESISTANCE,
        LUCK, MAX_ABSORPTION, MAX_HEALTH, MINING_EFFICIENCY, MOVEMENT_EFFICIENCY, MOVEMENT_SPEED,
        NAME_TAG_DISTANCE, OXYGEN_BONUS, SAFE_FALL_DISTANCE, SCALE, SNEAKING_SPEED, SPAWN_REINFORCEMENTS,
        STEP_HEIGHT, SUBMERGED_MINING_SPEED, SWEEPING_DAMAGE_RATIO, TEMPT_RANGE, WATER_MOVEMENT_EFFICIENCY,
        WAYPOINT_RECEIVE_RANGE, WAYPOINT_TRANSMIT_RANGE
    };
    List<Attribute> all = new ArrayList<>(resolved.length);
    for (Attribute attribute : resolved) {
      if (attribute != null) {
        all.add(attribute);
      }
    }
    return List.copyOf(all);
  }
}
