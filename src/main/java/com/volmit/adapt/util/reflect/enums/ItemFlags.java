package com.volmit.adapt.util.reflect.enums;

import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.inventory.ItemFlag;

public class ItemFlags {

    public static ItemFlag HIDE_POTION_EFFECTS = Reflect.getEnum(ItemFlag.class, "HIDE_POTION_EFFECTS", "HIDE_ADDITIONAL_TOOLTIP");
}
