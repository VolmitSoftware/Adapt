package com.volmit.adapt.util.reflect.enums;

import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.attribute.Attribute;

public class Attributes {
    public static final Attribute GENERIC_ARMOR = Reflect.getField(Attribute.class, "GENERIC_ARMOR", "ARMOR");
    public static final Attribute GENERIC_ATTACK_DAMAGE = Reflect.getField(Attribute.class, "GENERIC_ATTACK_DAMAGE", "ATTACK_DAMAGE");
    public static final Attribute GENERIC_MAX_HEALTH = Reflect.getField(Attribute.class, "GENERIC_MAX_HEALTH", "MAX_HEALTH");
    public static final Attribute GENERIC_MOVEMENT_SPEED = Reflect.getField(Attribute.class, "GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED");
}
