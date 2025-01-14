package com.volmit.adapt.util.reflect.events.api.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.NotNull;

public interface EntityMountEvent extends EntityEvent, Cancellable {
    @NotNull
    Entity getMount();
}
