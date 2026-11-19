package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptBlockEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On 1.20.1 subclasses override {@code load}, and a few vanilla ones (chiseled bookshelf, brushable blocks, sculk
 * catalyst) never call the base method, so chunk loads also restore the data from {@code loadStatic}.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityDataMixin implements AdaptBlockEntityData {
    @Unique
    private CompoundTag adapt$data;

    @Inject(method = "saveWithoutMetadata()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void adapt$saveData(CallbackInfoReturnable<CompoundTag> callback) {
        if (adapt$data != null && !adapt$data.isEmpty()) {
            callback.getReturnValue().put(AdaptBlockEntityData.KEY, adapt$data.copy());
        }
    }

    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void adapt$loadData(CompoundTag tag, CallbackInfo callback) {
        adapt$data = adapt$read(tag);
    }

    @Inject(method = "loadStatic(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
        at = @At("RETURN"))
    private static void adapt$loadedStatic(BlockPos pos, BlockState state, CompoundTag tag, CallbackInfoReturnable<BlockEntity> callback) {
        BlockEntity loaded = callback.getReturnValue();
        if (loaded != null) {
            ((AdaptBlockEntityData) loaded).adapt$setData(adapt$read(tag));
        }
    }

    @Unique
    private static CompoundTag adapt$read(CompoundTag tag) {
        return tag.contains(AdaptBlockEntityData.KEY, Tag.TAG_COMPOUND) ? tag.getCompound(AdaptBlockEntityData.KEY).copy() : null;
    }

    @Override
    public CompoundTag adapt$data() {
        return adapt$data;
    }

    @Override
    public CompoundTag adapt$dataOrCreate() {
        if (adapt$data == null) {
            adapt$data = new CompoundTag();
        }
        return adapt$data;
    }

    @Override
    public void adapt$setData(CompoundTag tag) {
        adapt$data = tag;
    }
}
