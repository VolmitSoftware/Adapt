package com.volmit.adapt.util.reflect.enums;

import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.potion.PotionEffectType;

public class PotionEffectTypes {

    public static final PotionEffectType FAST_DIGGING = Reflect.getField(PotionEffectType.class, "FAST_DIGGING", "HASTE");
    public static final PotionEffectType DAMAGE_RESISTANCE = Reflect.getField(PotionEffectType.class, "DAMAGE_RESISTANCE", "RESISTANCE");
    public static final PotionEffectType JUMP = Reflect.getField(PotionEffectType.class, "JUMP", "JUMP_BOOST");
    public static final PotionEffectType SLOW_DIGGING = Reflect.getField(PotionEffectType.class, "SLOW_DIGGING", "MINING_FATIGUE");
    public static final PotionEffectType CONFUSION = Reflect.getField(PotionEffectType.class, "CONFUSION", "NAUSEA");
    public static final PotionEffectType INCREASE_DAMAGE = Reflect.getField(PotionEffectType.class, "INCREASE_DAMAGE", "STRENGTH");
}
