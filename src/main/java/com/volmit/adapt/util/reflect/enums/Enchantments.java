package com.volmit.adapt.util.reflect.enums;

import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.enchantments.Enchantment;

public class Enchantments {

    public static Enchantment DURABILITY = Reflect.getField(Enchantment.class, "DURABILITY", "UNBREAKING");
    public static Enchantment ARROW_INFINITE = Reflect.getField(Enchantment.class, "ARROW_INFINITE", "INFINITY");
    public static Enchantment LOOT_BONUS_BLOCKS = Reflect.getField(Enchantment.class, "LOOT_BONUS_BLOCKS", "FORTUNE");
}
