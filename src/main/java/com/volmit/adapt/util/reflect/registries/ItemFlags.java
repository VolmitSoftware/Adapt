package com.volmit.adapt.util.reflect.registries;

import org.bukkit.inventory.ItemFlag;

public class ItemFlags {
    public static final ItemFlag HIDE_POTION_EFFECTS = RegistryUtil.find(ItemFlag.class, "hide_potion_effects", "hide_additional_tooltip");
}
