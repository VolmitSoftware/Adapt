package com.volmit.adapt.util.reflect.enums;

import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.Material;

public class Materials {

    public static final Material GRASS = Reflect.getEnum(Material.class, "GRASS", "SHORT_GRASS");
}
