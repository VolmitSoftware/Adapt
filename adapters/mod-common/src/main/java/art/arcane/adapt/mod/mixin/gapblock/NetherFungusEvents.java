package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.GrowthHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.NetherFungusBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bone-mealed fungi growing into huge fungi. */
@Mixin(NetherFungusBlock.class)
public abstract class NetherFungusEvents {
    @Inject(method = "performBonemeal", at = @At("HEAD"))
    private void adapt$growing(CallbackInfo callback, @Local(argsOnly = true) ServerLevel level) {
        GrowthHooks.growing(level);
    }

    @Inject(method = "performBonemeal", at = @At("RETURN"))
    private void adapt$grown(CallbackInfo callback, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        GrowthHooks.grown(level, pos);
    }
}
