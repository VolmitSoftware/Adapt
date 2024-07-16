package com.volmit.adapt.util.reflect.enums;

import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.Particle;

public class Particles {
    public static final Particle REDSTONE = Reflect.getEnum(Particle.class, "REDSTONE", "DUST");
    public static final Particle ENCHANTMENT_TABLE = Reflect.getEnum(Particle.class, "ENCHANTMENT_TABLE", "ENCHANT");
    public static final Particle CRIT_MAGIC = Reflect.getEnum(Particle.class, "CRIT_MAGIC", "CRIT");
    public static final Particle TOTEM = Reflect.getEnum(Particle.class, "TOTEM", "TOTEM_OF_UNDYING");
    public static final Particle BLOCK_CRACK = Reflect.getEnum(Particle.class, "BLOCK_CRACK", "BLOCK");
    public static final Particle VILLAGER_HAPPY = Reflect.getEnum(Particle.class, "VILLAGER_HAPPY", "HAPPY_VILLAGER");
    public static final Particle ITEM_CRACK = Reflect.getEnum(Particle.class, "ITEM_CRACK", "ITEM");
}
