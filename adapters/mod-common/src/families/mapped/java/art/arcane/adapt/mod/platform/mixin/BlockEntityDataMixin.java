package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptBlockEntityData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
public abstract class BlockEntityDataMixin implements AdaptBlockEntityData {
    @Unique
    private CompoundTag adapt$data;

    @Inject(method = "saveWithoutMetadata(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void adapt$saveData(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> callback) {
        if (adapt$data != null && !adapt$data.isEmpty()) {
            callback.getReturnValue().put(AdaptBlockEntityData.KEY, adapt$data.copy());
        }
    }

    @Inject(method = "loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("TAIL"))
    private void adapt$loadData(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        adapt$data = tag.contains(AdaptBlockEntityData.KEY, Tag.TAG_COMPOUND) ? tag.getCompound(AdaptBlockEntityData.KEY).copy() : null;
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
