package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A silverfish hiding in a stone block. Cancelled: the block stays and the silverfish stays out. */
@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishMergeWithStoneGoal")
public abstract class SilverfishMergeEvents extends RandomStrollGoal {
    private SilverfishMergeEvents(PathfinderMob mob) {
        super(mob, 1.0, 10);
    }

    @Inject(method = "start", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/LevelAccessor;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
        cancellable = true)
    private void adapt$merging(CallbackInfo callback, @Local BlockPos pos, @Local BlockState host) {
        if (ChangeBlockHooks.changing(mob, pos, InfestedBlock.infestedStateByHost(host))) {
            callback.cancel();
        }
    }
}
