package com.volmit.adapt.util.reflect.enums;

import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.potion.PotionType;

public class PotionTypes {

    public static final PotionType INSTANT_HEAL = Reflect.getEnum(PotionType.class, "INSTANT_HEAL", "HEALING");
    public static final PotionType SPEED = Reflect.getEnum(PotionType.class, "SPEED", "SWIFTNESS");
    public static final PotionType REGEN = Reflect.getEnum(PotionType.class, "REGEN", "REGENERATION");
    public static final PotionType JUMP = Reflect.getEnum(PotionType.class, "JUMP", "LEAPING");
}
