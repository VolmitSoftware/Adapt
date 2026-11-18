package art.arcane.adapt.mod.feature.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Posts NeoForge's cancellable action events without performing the action, so claim mods can veto. */
public final class LoaderProtectionEvents {
    private LoaderProtectionEvents() {
    }

    public static boolean canBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        BreakBlockEvent event = new BreakBlockEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    /**
     * Right-click events read the player's own level, so a block in another dimension is asked through the break
     * event instead, which carries the level and needs at least the same claim rights.
     */
    public static boolean canUse(ServerLevel level, ServerPlayer player, BlockHitResult hit) {
        if (player.level() != level) {
            return canBreak(level, player, hit.getBlockPos(), level.getBlockState(hit.getBlockPos()));
        }
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND,
                hit.getBlockPos(), hit);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled() && event.getUseBlock() != TriState.FALSE;
    }

    public static boolean canPlace(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState against) {
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos),
                against, player);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    public static boolean canPickup(ServerPlayer player, ItemEntity item) {
        ItemEntityPickupEvent.Pre event = new ItemEntityPickupEvent.Pre(player, item);
        NeoForge.EVENT_BUS.post(event);
        return event.canPickup() != TriState.FALSE;
    }
}
