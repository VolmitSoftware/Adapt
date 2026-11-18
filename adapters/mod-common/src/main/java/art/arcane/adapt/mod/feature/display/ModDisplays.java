package art.arcane.adapt.mod.feature.display;

import art.arcane.adapt.mod.feature.mixin.BlockDisplayDataAccess;
import art.arcane.adapt.mod.feature.mixin.InteractionDataAccess;
import art.arcane.adapt.mod.feature.mixin.ItemDisplayDataAccess;
import art.arcane.adapt.mod.feature.viewer.ViewerStates;
import art.arcane.adapt.mod.feature.mixin.TextDisplayDataAccess;
import art.arcane.adapt.mod.platform.ModDisplayFactory;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.mod.platform.world.ModBlockStateRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.DisplayRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockStateRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Interaction;
import art.arcane.adapt.mod.feature.FeatureEntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Real vanilla display entities. The prepare callback runs before the entity joins the level, so settings such as
 * visible-by-default apply before any player is paired with it.
 */
public final class ModDisplays implements ModDisplayFactory {
    @Override
    public DisplayRef wrap(Display display) {
        if (display instanceof Display.ItemDisplay item) {
            return new ModItemDisplayRef(item);
        }
        if (display instanceof Display.BlockDisplay block) {
            return new ModBlockDisplayRef(block);
        }
        if (display instanceof Display.TextDisplay text) {
            return new ModTextDisplayRef(text);
        }
        return new ModDisplayRef<>(display);
    }

    @Override
    public ItemDisplayRef spawnItem(ServerLevel level, Loc location, ItemRef item, Consumer<ItemDisplayRef> prepare) {
        Display.ItemDisplay entity = new Display.ItemDisplay(FeatureEntityTypes.itemDisplay(), level);
        place(entity, location);
        ItemStack stack = item == null ? null : ModRefs.item(item);
        entity.getEntityData().set(ItemDisplayDataAccess.adapt$itemStack(), stack == null ? ItemStack.EMPTY : stack.copy());
        ModItemDisplayRef ref = new ModItemDisplayRef(entity);
        return spawn(level, entity, ref, prepare);
    }

    @Override
    public BlockDisplayRef spawnBlock(ServerLevel level, Loc location, BlockStateRef block, Consumer<BlockDisplayRef> prepare) {
        Display.BlockDisplay entity = new Display.BlockDisplay(FeatureEntityTypes.blockDisplay(), level);
        place(entity, location);
        Object handle = block == null ? null : block.unwrap();
        BlockState state = handle instanceof BlockState blockState ? blockState : Blocks.AIR.defaultBlockState();
        entity.getEntityData().set(BlockDisplayDataAccess.adapt$blockState(), state);
        ModBlockDisplayRef ref = new ModBlockDisplayRef(entity);
        return spawn(level, entity, ref, prepare);
    }

    @Override
    public TextDisplayRef spawnText(ServerLevel level, Loc location, String markup, Consumer<TextDisplayRef> prepare) {
        Display.TextDisplay entity = new Display.TextDisplay(FeatureEntityTypes.textDisplay(), level);
        place(entity, location);
        entity.getEntityData().set(TextDisplayDataAccess.adapt$text(), ModText.component(markup == null ? "" : markup));
        ModTextDisplayRef ref = new ModTextDisplayRef(entity);
        return spawn(level, entity, ref, prepare);
    }

    @Override
    public EntityRef spawnInteraction(ServerLevel level, Loc location, float width, float height, boolean visibleByDefault,
                                      Consumer<EntityRef> prepare) {
        Interaction entity = new Interaction(FeatureEntityTypes.interaction(), level);
        entity.snapTo(new Vec3(location.x(), location.y(), location.z()), location.yaw(), location.pitch());
        entity.getEntityData().set(InteractionDataAccess.adapt$width(), width);
        entity.getEntityData().set(InteractionDataAccess.adapt$height(), height);
        if (!visibleByDefault) {
            ViewerStates.setHiddenByDefault(entity.getId(), true);
        }
        EntityRef ref = ModRefs.entity(entity);
        if (prepare != null) {
            prepare.accept(ref);
        }
        level.addFreshEntity(entity);
        return ref;
    }

    static BlockStateRef state(BlockState state) {
        return new ModBlockStateRef(state);
    }

    private static void place(Display entity, Loc location) {
        entity.snapTo(new Vec3(location.x(), location.y(), location.z()), location.yaw(), location.pitch());
    }

    private static <R extends DisplayRef> R spawn(ServerLevel level, Display entity, R ref, Consumer<R> prepare) {
        if (prepare != null) {
            prepare.accept(ref);
        }
        level.addFreshEntity(entity);
        return ref;
    }
}
