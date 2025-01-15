package com.volmit.adapt.util.reflect.events.api.entity;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.NotNull;

public interface EndermanAttackPlayerEvent extends EntityEvent, Cancellable {
    @NotNull
    Player getPlayer();
}
