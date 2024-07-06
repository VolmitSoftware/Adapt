package com.volmit.adapt.util.reflect.enums;

import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.entity.EntityType;

public class EntityTypes {

    public static EntityType MINECART_CHEST = Reflect.getEnum(EntityType.class, "MINECART_CHEST", "CHEST_MINECART");
    public static EntityType MINECART_COMMAND = Reflect.getEnum(EntityType.class, "MINECART_COMMAND", "COMMAND_BLOCK_MINECART");
    public static EntityType MINECART_FURNACE = Reflect.getEnum(EntityType.class, "MINECART_FURNACE", "FURNACE_MINECART");
    public static EntityType MINECART_HOPPER = Reflect.getEnum(EntityType.class, "MINECART_HOPPER", "HOPPER_MINECART");
    public static EntityType MINECART_MOB_SPAWNER = Reflect.getEnum(EntityType.class, "MINECART_MOB_SPAWNER", "SPAWNER_MINECART");
    public static EntityType MINECART_TNT = Reflect.getEnum(EntityType.class, "MINECART_TNT", "TNT_MINECART");

    public static EntityType LEASH_HITCH = Reflect.getEnum(EntityType.class, "LEASH_HITCH", "LEASH_KNOT");
}
