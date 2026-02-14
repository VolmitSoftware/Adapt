package art.arcane.adapt.util.reflect.registries;

import org.bukkit.attribute.Attribute;

public class Attributes {
    public static final Attribute GENERIC_ARMOR = RegistryUtil.find(Attribute.class, "generic_armor", "armor");
    public static final Attribute GENERIC_ATTACK_DAMAGE = RegistryUtil.find(Attribute.class, "generic_attack_damage", "attack_damage");
    public static final Attribute GENERIC_MAX_HEALTH = RegistryUtil.find(Attribute.class, "generic_max_health", "max_health");
    public static final Attribute GENERIC_MOVEMENT_SPEED = RegistryUtil.find(Attribute.class, "generic_movement_speed", "movement_speed");
}
