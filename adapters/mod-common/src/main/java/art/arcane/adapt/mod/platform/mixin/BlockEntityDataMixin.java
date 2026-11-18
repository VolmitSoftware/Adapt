package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptBlockEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityDataMixin implements AdaptBlockEntityData {
    @Unique
    private CompoundTag adapt$data;

    @Inject(method = "saveWithoutMetadata(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("TAIL"))
    private void adapt$saveData(ValueOutput output, CallbackInfo callback) {
        if (adapt$data != null && !adapt$data.isEmpty()) {
            output.store(AdaptBlockEntityData.KEY, CompoundTag.CODEC, adapt$data);
        }
    }

    @Inject(method = "loadWithComponents", at = @At("TAIL"))
    private void adapt$loadData(ValueInput input, CallbackInfo callback) {
        adapt$data = input.read(AdaptBlockEntityData.KEY, CompoundTag.CODEC).orElse(null);
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
