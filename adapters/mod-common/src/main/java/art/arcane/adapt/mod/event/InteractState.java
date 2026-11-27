package art.arcane.adapt.mod.event;

import art.arcane.adapt.platform.event.player.UseResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Per-player outcome of the last right-click on a block, kept on the player's game mode as Paper
 * does, so the item-use packet the client sends after a passed block click reuses it.
 */
public final class InteractState {
    UseResult useBlock = UseResult.DEFAULT;
    UseResult useItem = UseResult.DEFAULT;
    boolean active;
    boolean fired;
    InteractionHand hand;
    BlockPos pos;
    ItemStack stack = ItemStack.EMPTY;

    public boolean denyBlock() {
        return active && useBlock == UseResult.DENY;
    }

    /** True while the item must not be used on the clicked block; see {@link UseRules#usesItemOnBlock}. */
    public boolean denyItem() {
        return active && !UseRules.usesItemOnBlock(useBlock, useItem);
    }

    public void finish() {
        active = false;
    }

    /** Implemented by the game mode mixin. */
    public interface Holder {
        InteractState adapt$interactState();
    }
}
