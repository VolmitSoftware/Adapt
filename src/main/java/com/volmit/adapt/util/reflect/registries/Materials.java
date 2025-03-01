package com.volmit.adapt.util.reflect.registries;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

public class Materials {
    public static final Material GRASS = RegistryUtil.find(Material.class, "grass", "short_grass");
    public static final NamespacedKey MACE_KEY = NamespacedKey.minecraft("mace");
}
