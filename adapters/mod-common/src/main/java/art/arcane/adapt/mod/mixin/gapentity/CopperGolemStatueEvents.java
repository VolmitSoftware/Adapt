package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A fully oxidized copper golem turning into a statue block. Cancelled: the golem stays a golem. */
@Mixin(CopperGolem.class)
public abstract class CopperGolemStatueEvents {
    @WrapOperation(method = "turnToStatue", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean adapt$statue(ServerLevel level, BlockPos pos, BlockState to, Operation<Boolean> original) {
        if (ChangeBlockHooks.changing((CopperGolem) (Object) this, pos, to)) {
            return false;
        }
        return original.call(level, pos, to);
    }
}
