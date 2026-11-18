package art.arcane.adapt.mod.event.block;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockFertilizeEvent;
import art.arcane.adapt.platform.event.block.StructureGrowEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bone meal and tree, mushroom and fungus growth. Each grows inside a {@link BlockCapture} frame
 * opened at the start of the vanilla method and closed at its end; a cancel undoes the changes
 * and, for bone meal, gives the consumed item back.
 */
public final class GrowthHooks {
    private static final Object FERTILIZE = new Object();
    private static final Object WATER_PLANT = new Object();
    private static final Object STRUCTURE = new Object();
    private static ServerPlayer fertilizer;
    private static long fertilizerTick;
    private static int bonemealDepth;
    private static boolean keepBonemeal;
    private static int waterPlantCount;

    private GrowthHooks() {
    }

    /** BoneMealItem#useOn HEAD and RETURN: the player using bone meal, or null when done. */
    public static void fertilizer(Player player) {
        fertilizer = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        fertilizerTick = fertilizer == null ? 0 : fertilizer.level().getGameTime();
    }

    private static PlayerRef player(ServerLevel level) {
        ServerPlayer current = fertilizer;
        return current != null && current.level() == level && fertilizerTick == level.getGameTime() ? ModRefs.player(current) : null;
    }

    /** Before BonemealableBlock#performBonemeal in the bone meal item. */
    public static void fertilizing(Level level) {
        keepBonemeal = false;
        if (level instanceof ServerLevel serverLevel) {
            bonemealDepth++;
            BlockCapture.open(serverLevel, FERTILIZE);
        }
    }

    /** After BonemealableBlock#performBonemeal in the bone meal item. */
    public static void fertilized(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        bonemealDepth = Math.max(0, bonemealDepth - 1);
        BlockCapture.Frame frame = BlockCapture.close(FERTILIZE);
        if (frame == null || !ModEvents.listening(BlockFertilizeEvent.class) || !frame.changed()) {
            return;
        }
        BlockFertilizeEvent event = ModEvents.post(new BlockFertilizeEvent(player(serverLevel), ModRefs.block(serverLevel, pos), frame.changes()));
        if (event.isCancelled()) {
            frame.revert();
            keepBonemeal = true;
        }
    }

    /** True when the bone meal of a cancelled fertilize must not be used up. Clears the flag. */
    public static boolean keepBonemeal() {
        boolean keep = keepBonemeal;
        keepBonemeal = false;
        return keep;
    }

    /** BoneMealItem#growWaterPlant HEAD. */
    public static void plantingWater(ItemStack stack, Level level) {
        if (level instanceof ServerLevel serverLevel) {
            waterPlantCount = stack.getCount();
            bonemealDepth++;
            BlockCapture.open(serverLevel, WATER_PLANT);
        }
    }

    /** BoneMealItem#growWaterPlant RETURN. */
    public static void plantedWater(ItemStack stack, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        bonemealDepth = Math.max(0, bonemealDepth - 1);
        BlockCapture.Frame frame = BlockCapture.close(WATER_PLANT);
        if (frame == null || !ModEvents.listening(BlockFertilizeEvent.class) || !frame.changed()) {
            return;
        }
        BlockFertilizeEvent event = ModEvents.post(new BlockFertilizeEvent(player(serverLevel), ModRefs.block(serverLevel, pos), frame.changes()));
        if (event.isCancelled()) {
            frame.revert();
            stack.setCount(waterPlantCount);
        }
    }

    /** HEAD of a tree, huge mushroom or huge fungus placement. */
    public static void growing(ServerLevel level) {
        BlockCapture.open(level, STRUCTURE);
    }

    /** RETURN of a tree, huge mushroom or huge fungus placement. False when the growth was cancelled and undone. */
    public static boolean grown(ServerLevel level, BlockPos origin) {
        BlockCapture.Frame frame = BlockCapture.close(STRUCTURE);
        if (frame == null || !ModEvents.listening(StructureGrowEvent.class) || !frame.changed()) {
            return true;
        }
        boolean bonemeal = bonemealDepth > 0;
        StructureGrowEvent event = ModEvents.post(new StructureGrowEvent(ModRefs.block(level, origin), bonemeal ? player(level) : null, bonemeal,
            frame.changes()));
        if (!event.isCancelled()) {
            return true;
        }
        frame.revert();
        return false;
    }
}
