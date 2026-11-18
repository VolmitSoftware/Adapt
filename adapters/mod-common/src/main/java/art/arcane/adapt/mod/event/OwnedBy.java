package art.arcane.adapt.mod.event;

import net.minecraft.world.entity.LivingEntity;

/**
 * Implemented by mixins on vanilla objects that do not know their entity (a mob's brain, a player's
 * food data), so hooks inside them can post events for it.
 */
public interface OwnedBy {
    LivingEntity adapt$owner();

    void adapt$owner(LivingEntity owner);
}
