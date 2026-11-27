package art.arcane.adapt.mod.event;

import art.arcane.adapt.platform.event.player.UseResult;

/** How the two outcomes of a right click on a block combine, the way Paper combines them. */
public final class UseRules {
    private UseRules() {
    }

    /**
     * Whether the item is used on the clicked block: not when its use was denied, and not when the block use was
     * denied, whatever the item use says. {@link UseResult#ALLOW} forces nothing, as on Paper.
     */
    public static boolean usesItemOnBlock(UseResult block, UseResult item) {
        return item != UseResult.DENY && block != UseResult.DENY;
    }
}
