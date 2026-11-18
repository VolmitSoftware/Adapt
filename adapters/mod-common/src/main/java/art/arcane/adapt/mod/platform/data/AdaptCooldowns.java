package art.arcane.adapt.mod.platform.data;

import net.minecraft.resources.Identifier;

/** Implemented on item cooldown trackers by the platform mixins. */
public interface AdaptCooldowns {
    /** Remaining ticks of the group's cooldown, 0 when none. */
    int adapt$remaining(Identifier group);
}
