package art.arcane.adapt.mod.event.block;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.block.BlockCookEvent;
import art.arcane.adapt.platform.event.block.BlockDispenseEvent;
import art.arcane.adapt.platform.event.block.BlockFadeEvent;
import art.arcane.adapt.platform.event.block.BlockFormEvent;
import art.arcane.adapt.platform.event.block.BlockFromToEvent;
import art.arcane.adapt.platform.event.block.BlockGrowEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.block.BlockReceiveGameEvent;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.block.FurnaceSmeltEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Natural block changes, pistons, dispensers, crafters and cooking blocks. Every hook returns
 * whether vanilla may go on; nothing is allocated unless a listener exists for the event.
 */
public final class BlockHooks {
    private static BlockPos burningFire;
    private static boolean smeltCancelled;

    private BlockHooks() {
    }

    static ServerLevel server(LevelAccessor level) {
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    static boolean allowed(CancellableEvent event) {
        return !ModEvents.post(event).isCancelled();
    }

    public static boolean grow(LevelAccessor level, BlockPos pos, BlockState state) {
        ServerLevel serverLevel = server(level);
        if (serverLevel == null || !ModEvents.listening(BlockGrowEvent.class)) {
            return true;
        }
        return allowed(new BlockGrowEvent(ModRefs.block(serverLevel, pos), ModRefs.state(state)));
    }

    public static boolean fade(LevelAccessor level, BlockPos pos, BlockState state) {
        ServerLevel serverLevel = server(level);
        if (serverLevel == null || !ModEvents.listening(BlockFadeEvent.class)) {
            return true;
        }
        return allowed(new BlockFadeEvent(ModRefs.block(serverLevel, pos), ModRefs.state(state)));
    }

    public static boolean fadeToAir(LevelAccessor level, BlockPos pos) {
        ServerLevel serverLevel = server(level);
        if (serverLevel == null || !ModEvents.listening(BlockFadeEvent.class)) {
            return true;
        }
        return fade(serverLevel, pos, serverLevel.getFluidState(pos).createLegacyBlock());
    }

    public static boolean form(LevelAccessor level, BlockPos pos, BlockState state, Entity entity) {
        ServerLevel serverLevel = server(level);
        if (serverLevel == null || !ModEvents.listening(BlockFormEvent.class)) {
            return true;
        }
        return allowed(new BlockFormEvent(ModRefs.block(serverLevel, pos), ModRefs.state(state), entity == null ? null : ModRefs.entity(entity)));
    }

    public static void burning(BlockPos fire) {
        burningFire = fire;
    }

    public static boolean burn(LevelAccessor level, BlockPos pos) {
        ServerLevel serverLevel = server(level);
        if (serverLevel == null || !ModEvents.listening(BlockBurnEvent.class)) {
            return true;
        }
        BlockPos fire = burningFire;
        return allowed(new BlockBurnEvent(ModRefs.block(serverLevel, pos), fire == null ? null : ModRefs.block(serverLevel, fire)));
    }

    public static boolean flow(LevelAccessor level, BlockPos to, Direction direction) {
        ServerLevel serverLevel = server(level);
        if (serverLevel == null || !ModEvents.listening(BlockFromToEvent.class)) {
            return true;
        }
        return allowed(new BlockFromToEvent(ModRefs.block(serverLevel, to.relative(direction.getOpposite())), ModRefs.block(serverLevel, to),
            ModConversions.face(direction)));
    }

    public static boolean eggTeleport(LevelAccessor level, BlockPos from, BlockPos to) {
        ServerLevel serverLevel = server(level);
        if (serverLevel == null || !ModEvents.listening(BlockFromToEvent.class)) {
            return true;
        }
        return allowed(new BlockFromToEvent(ModRefs.block(serverLevel, from), ModRefs.block(serverLevel, to), BlockFace.SELF));
    }

    public static boolean vibration(ServerLevel level, BlockPos listener, Holder<GameEvent> gameEvent, Entity source) {
        if (!ModEvents.listening(BlockReceiveGameEvent.class)) {
            return true;
        }
        ResourceKey<GameEvent> key = gameEvent.unwrapKey().orElse(null);
        if (key == null) {
            return true;
        }
        return allowed(new BlockReceiveGameEvent(ModRefs.block(level, listener), ModRefs.id(key.location()),
            source == null ? null : ModRefs.entity(source)));
    }

    public static boolean pistonsListened() {
        return ModEvents.listening(BlockPistonEvent.class);
    }

    /** True when the piston at this position may move the given blocks. */
    public static boolean piston(LevelAccessor level, BlockPos piston, Direction direction, boolean extending, List<BlockPos> moved) {
        ServerLevel serverLevel = server(level);
        if (serverLevel == null || !ModEvents.listening(BlockPistonEvent.class)) {
            return true;
        }
        List<BlockRef> blocks = new ArrayList<>(moved.size());
        for (BlockPos pos : moved) {
            blocks.add(ModRefs.block(serverLevel, pos));
        }
        return allowed(new BlockPistonEvent(ModRefs.block(serverLevel, piston), ModConversions.face(direction), extending, blocks));
    }

    public static boolean dispense(ServerLevel level, BlockPos pos, ItemStack stack, Direction facing) {
        if (!ModEvents.listening(BlockDispenseEvent.class)) {
            return true;
        }
        return allowed(new BlockDispenseEvent(ModRefs.block(level, pos), ModRefs.item(stack.copyWithCount(1)),
            new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ())));
    }

    public static boolean crafterCraft(ServerLevel level, BlockPos pos, RecipeHolder<?> recipe, ItemStack result) {
        if (!ModEvents.listening(CrafterCraftEvent.class)) {
            return true;
        }
        return allowed(new CrafterCraftEvent(ModRefs.block(level, pos), ModRefs.id(recipe.id()), ModRefs.item(result.copy())));
    }

    /** The stack a campfire drops for a finished item, or null when the cook is cancelled. */
    public static ItemStack campfireCook(ServerLevel level, BlockPos pos, ItemStack source, ItemStack result) {
        if (!ModEvents.listening(BlockCookEvent.class)) {
            return result;
        }
        ItemRef before = ModRefs.item(result);
        BlockCookEvent event = ModEvents.post(new BlockCookEvent(ModRefs.block(level, pos), ModRefs.item(source.copyWithCount(1)), before));
        if (event.isCancelled()) {
            return null;
        }
        return event.result() == before ? result : ModRefs.item(event.result());
    }

    /**
     * A furnace finishing one item. Returns the stack to put into the output slot, or null when the
     * smelt is cancelled or the replaced result does not fit the output slot.
     */
    public static ItemStack smelt(ServerLevel level, BlockPos pos, NonNullList<ItemStack> items, ItemStack source, ItemStack result) {
        smeltCancelled = false;
        if (!ModEvents.listening(FurnaceSmeltEvent.class)) {
            return result;
        }
        ItemRef before = ModRefs.item(result);
        FurnaceSmeltEvent event = ModEvents.post(new FurnaceSmeltEvent(ModRefs.block(level, pos), ModRefs.item(source.copyWithCount(1)), before));
        if (event.isCancelled()) {
            smeltCancelled = true;
            return null;
        }
        if (event.result() == before) {
            return result;
        }
        ItemStack replaced = event.result() == null ? ItemStack.EMPTY : ModRefs.item(event.result());
        ItemStack output = items.get(2);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, replaced)
            || output.getCount() + replaced.getCount() > output.getMaxStackSize())) {
            smeltCancelled = true;
            return null;
        }
        return replaced;
    }

    /** True once after a cancelled smelt, so vanilla does not credit the recipe. */
    public static boolean consumeSmeltCancel() {
        boolean cancelled = smeltCancelled;
        smeltCancelled = false;
        return cancelled;
    }
}
