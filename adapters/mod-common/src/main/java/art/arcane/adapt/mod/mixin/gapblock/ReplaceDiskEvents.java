package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.enchantment.effects.ReplaceDisk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Frost walker (and any replace-disk enchantment effect) forming blocks around the wearer. */
@Mixin(ReplaceDisk.class)
public abstract class ReplaceDiskEvents {
    @WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean adapt$forming(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original, @Local(argsOnly = true) Entity entity) {
        return BlockHooks.form(level, pos, state, entity) && original.call(level, pos, state);
    }
}
