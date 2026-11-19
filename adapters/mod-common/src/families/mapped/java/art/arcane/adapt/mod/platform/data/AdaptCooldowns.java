package art.arcane.adapt.mod.platform.data;

import net.minecraft.resources.ResourceLocation;

/** Implemented on item cooldown trackers by the platform mixins. */
public interface AdaptCooldowns {
    /**
     * Remaining ticks of the group's cooldown, 0 when none. This version has no cooldown groups: the group is the
     * item's registry id.
     */
    int adapt$remaining(ResourceLocation group);
}
