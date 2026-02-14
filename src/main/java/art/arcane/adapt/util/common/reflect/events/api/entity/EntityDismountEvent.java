package art.arcane.adapt.util.reflect.events.api.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.NotNull;

public interface EntityDismountEvent extends EntityEvent, Cancellable {
    @NotNull
    Entity getDismounted();
}
