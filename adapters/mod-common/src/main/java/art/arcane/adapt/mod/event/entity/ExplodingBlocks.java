package art.arcane.adapt.mod.event.entity;

import net.minecraft.world.level.block.state.BlockState;

/** The state of a bed or respawn anchor that vanilla removed just before it explodes, for the block explode event. */
public final class ExplodingBlocks {
    private static BlockState exploding;

    private ExplodingBlocks() {
    }

    public static void exploding(BlockState state) {
        exploding = state;
    }

    public static void exploded() {
        exploding = null;
    }

    /** The removed block of the explosion being built, or null for explosions no block started. */
    public static BlockState current() {
        return exploding;
    }
}
