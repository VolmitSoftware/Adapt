package com.volmit.adapt.util.reflect.enums;

import com.volmit.adapt.util.reflect.Reflect;
import org.bukkit.entity.EntityType;

public class EntityTypes {
    public static final EntityType ENDER_CRYSTAL = Reflect.getEnum(EntityType.class, "ENDER_CRYSTAL", "END_CRYSTAL");
}
