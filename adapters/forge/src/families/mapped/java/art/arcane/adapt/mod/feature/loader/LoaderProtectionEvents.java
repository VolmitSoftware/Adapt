package art.arcane.adapt.mod.feature.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;

/** Posts Forge's cancellable action events without performing the action, so claim mods can veto. */
public final class LoaderProtectionEvents {
    private LoaderProtectionEvents() {
    }

    public static boolean canBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        boolean cancelled = MinecraftForge.EVENT_BUS.post(event);
        return !cancelled && event.getResult() != Event.Result.DENY;
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
        boolean cancelled = MinecraftForge.EVENT_BUS.post(event);
        return !cancelled && event.getUseBlock() != Event.Result.DENY;
    }

    public static boolean canPlace(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState against) {
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos),
                against, player);
        return !MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean canPickup(ServerPlayer player, ItemEntity item) {
        return !MinecraftForge.EVENT_BUS.post(new EntityItemPickupEvent(player, item));
    }
}
