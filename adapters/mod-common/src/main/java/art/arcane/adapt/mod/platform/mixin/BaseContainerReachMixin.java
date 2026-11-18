package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.entity.ModRemoteContainers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerReachMixin {
    @Inject(method = "stillValid(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true)
    private void adapt$remoteReach(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (ModRemoteContainers.reachable(player, (BaseContainerBlockEntity) (Object) this)) {
            callback.setReturnValue(true);
        }
    }
}
