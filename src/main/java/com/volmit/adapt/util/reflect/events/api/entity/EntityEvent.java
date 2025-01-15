package com.volmit.adapt.util.reflect.events.api.entity;

import com.volmit.adapt.util.reflect.events.api.Event;
import org.bukkit.entity.EntityType;

public interface EntityEvent extends Event {
    EntityType getType();
}
