package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.entity.EntityWorldEvent;
import art.arcane.adapt.platform.event.entity.ItemDespawnEvent;
import art.arcane.adapt.platform.event.entity.ItemMergeEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/** Entities entering and leaving a level's tracking, and item entities that despawn or merge. */
public final class EntityLifeHooks {
    private EntityLifeHooks() {
    }

    public static void tracking(Entity entity, EntityWorldEvent.Phase phase) {
        if (ModEvents.listening(EntityWorldEvent.class)) {
            ModEvents.post(new EntityWorldEvent(ModRefs.entity(entity), phase));
        }
    }

    /** True when a handler cancelled; the caller then keeps the item and restarts its age. */
    public static boolean despawning(ItemEntity item) {
        if (item.getItem().isEmpty() || !(item.level() instanceof ServerLevel level) || !ModEvents.listening(ItemDespawnEvent.class)) {
            return false;
        }
        return ModEvents.post(new ItemDespawnEvent((ItemEntityRef) ModRefs.entity(item),
            ModRefs.loc(level, item.position(), item.getYRot(), item.getXRot()))).isCancelled();
    }

    /** {@code from} is the stack that would be merged away into {@code into}. True when a handler cancelled. */
    public static boolean merging(ItemEntity into, ItemEntity from) {
        if (!(into.level() instanceof ServerLevel) || !ModEvents.listening(ItemMergeEvent.class)) {
            return false;
        }
        return ModEvents.post(new ItemMergeEvent((ItemEntityRef) ModRefs.entity(from), (ItemEntityRef) ModRefs.entity(into))).isCancelled();
    }
}
