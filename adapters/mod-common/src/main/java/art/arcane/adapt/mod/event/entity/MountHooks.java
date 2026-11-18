package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.EntityDismountEvent;
import art.arcane.adapt.platform.event.entity.EntityMountEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Mounting and dismounting, posted where Bukkit posts them: after the ride checks pass, before the link changes. */
public final class MountHooks {
    private MountHooks() {
    }

    /** True when a handler cancelled the mount. */
    public static boolean mounting(Entity entity, Entity vehicle) {
        if (!(entity.level() instanceof ServerLevel) || !ModEvents.listening(EntityMountEvent.class)) {
            return false;
        }
        return ModEvents.post(new EntityMountEvent(ModRefs.entity(entity), ModRefs.entity(vehicle))).isCancelled();
    }

    /** True when a handler cancelled a dismount that may be cancelled. */
    public static boolean dismounting(Entity entity, Entity vehicle) {
        if (vehicle == null || !(entity.level() instanceof ServerLevel) || !ModEvents.listening(EntityDismountEvent.class)) {
            return false;
        }
        boolean cancellable = !entity.isRemoved() && !vehicle.isRemoved();
        EntityDismountEvent event = ModEvents.post(new EntityDismountEvent(ModRefs.entity(entity), ModRefs.entity(vehicle), cancellable));
        return cancellable && event.isCancelled();
    }
}
