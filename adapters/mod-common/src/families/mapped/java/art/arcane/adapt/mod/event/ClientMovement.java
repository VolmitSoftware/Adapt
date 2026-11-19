package art.arcane.adapt.mod.event;

import net.minecraft.world.phys.Vec3;

/**
 * Movement a player's client reported over the last server tick. Servers before 1.21.2 receive no movement keys, so
 * core infers held directions from it. Implemented by the packet listener mixin, which takes it at the start of each
 * connection tick, after the movement packets of the tick before ran.
 */
public interface ClientMovement {
    Vec3 adapt$clientMovement();
}
