package art.arcane.adapt.mod.event.player;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.PlayerHarvestBlockEvent;
import art.arcane.adapt.platform.item.ItemRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Sweet berry and glow berry picking. The drops of the interact loot table are held back while the
 * event decides, so it reports the real items before they spawn and before the block changes.
 */
public final class HarvestHooks {
    private static Frame frame;

    private HarvestHooks() {
    }

    public static void opening(Player player, Level level, BlockPos pos) {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel
            && ModEvents.listening(PlayerHarvestBlockEvent.class)) {
            frame = new Frame(frame, serverPlayer, serverLevel, pos);
        }
    }

    /** True when a drop was held back for the open harvest. */
    public static boolean popping(Level level, BlockPos pos, ItemStack stack) {
        Frame current = frame;
        if (current == null || !current.collecting || current.level != level || !current.pos.equals(pos)) {
            return false;
        }
        current.items.add(stack);
        return true;
    }

    public static void lootStarting() {
        Frame current = frame;
        if (current != null && !current.posted) {
            current.collecting = true;
        }
    }

    /** The loot rolled: posts the harvest, then spawns the drops unless a handler cancelled. */
    public static void lootDone() {
        Frame current = frame;
        if (current == null || !current.collecting) {
            return;
        }
        current.collecting = false;
        current.posted = true;
        List<ItemRef> refs = new ArrayList<>(current.items.size());
        for (ItemStack stack : current.items) {
            refs.add(ModRefs.item(stack.copy()));
        }
        PlayerHarvestBlockEvent event = ModEvents.post(new PlayerHarvestBlockEvent(ModRefs.player(current.player),
            ModRefs.block(current.level, current.pos), Hand.MAIN_HAND, refs));
        current.cancelled = event.isCancelled();
        if (!current.cancelled) {
            for (ItemStack stack : current.items) {
                Block.popResource(current.level, current.pos, stack);
            }
        }
    }

    /** True when the closed harvest was cancelled; the block was then put back. */
    public static boolean closing(Level level, BlockPos pos, BlockState original) {
        Frame current = frame;
        if (current == null || current.level != level || !current.pos.equals(pos)) {
            return false;
        }
        frame = current.outer;
        if (current.cancelled) {
            current.level.setBlock(current.pos, original, Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }

    private static final class Frame {
        private final Frame outer;
        private final ServerPlayer player;
        private final ServerLevel level;
        private final BlockPos pos;
        private final List<ItemStack> items = new ArrayList<>();
        private boolean collecting;
        private boolean posted;
        private boolean cancelled;

        private Frame(Frame outer, ServerPlayer player, ServerLevel level, BlockPos pos) {
            this.outer = outer;
            this.player = player;
            this.level = level;
            this.pos = pos.immutable();
        }
    }
}
