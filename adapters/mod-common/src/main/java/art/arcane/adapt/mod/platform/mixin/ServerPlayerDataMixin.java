package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDataMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void adapt$keepData(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo callback) {
        CompoundTag data = ((AdaptEntityData) oldPlayer).adapt$data();
        ((AdaptEntityData) this).adapt$setData(data == null ? null : data.copy());
    }
}
