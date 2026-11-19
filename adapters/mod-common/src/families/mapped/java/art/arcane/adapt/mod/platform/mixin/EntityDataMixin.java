package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
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

    @Inject(method = "saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void adapt$saveData(CompoundTag tag, CallbackInfoReturnable<CompoundTag> callback) {
        if (adapt$data != null && !adapt$data.isEmpty()) {
            tag.put(AdaptEntityData.KEY, adapt$data.copy());
        }
    }

    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void adapt$loadData(CompoundTag tag, CallbackInfo callback) {
        adapt$data = tag.contains(AdaptEntityData.KEY, Tag.TAG_COMPOUND) ? tag.getCompound(AdaptEntityData.KEY).copy() : null;
    }

    @Inject(method = "shouldBeSaved()Z", at = @At("HEAD"), cancellable = true)
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
