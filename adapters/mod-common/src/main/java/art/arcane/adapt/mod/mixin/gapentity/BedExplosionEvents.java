package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ExplodingBlocks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The removed bed behind a bed explosion in a dimension where beds explode. */
@Mixin(BedBlock.class)
public abstract class BedExplosionEvents {
    @Inject(method = {"destroyOnUse", "useWithoutItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;explode"))
    private void adapt$exploding(CallbackInfoReturnable<InteractionResult> callback, @Local(argsOnly = true) BlockState state) {
        ExplodingBlocks.exploding(state);
    }

    @Inject(method = {"destroyOnUse", "useWithoutItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;explode",
        shift = At.Shift.AFTER))
    private void adapt$exploded(CallbackInfoReturnable<InteractionResult> callback) {
        ExplodingBlocks.exploded();
    }
}
