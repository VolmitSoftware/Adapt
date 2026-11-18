package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityDataMixin implements AdaptEntityData {
    @Unique
    private CompoundTag adapt$data;
    @Unique
    private boolean adapt$transient;

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void adapt$saveData(ValueOutput output, CallbackInfo callback) {
        if (adapt$data != null && !adapt$data.isEmpty()) {
            output.store(AdaptEntityData.KEY, CompoundTag.CODEC, adapt$data);
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void adapt$loadData(ValueInput input, CallbackInfo callback) {
        adapt$data = input.read(AdaptEntityData.KEY, CompoundTag.CODEC).orElse(null);
    }

    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void adapt$skipTransient(CallbackInfoReturnable<Boolean> callback) {
        if (adapt$transient) {
            callback.setReturnValue(Boolean.FALSE);
        }
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

    @Override
    public boolean adapt$isTransient() {
        return adapt$transient;
    }

    @Override
    public void adapt$setTransient(boolean value) {
        adapt$transient = value;
    }
}
