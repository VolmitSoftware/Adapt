package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** A rabbit eating a carrot crop down one stage, or clearing it at the last stage. */
@Mixin(targets = "net.minecraft.world.entity.animal.rabbit.Rabbit$RaidGardenGoal")
public abstract class RabbitRaidEvents {
    @Shadow
    @Final
    private Rabbit rabbit;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean adapt$nibbling(Level level, BlockPos pos, BlockState to, int flags, Operation<Boolean> original) {
        boolean cancelled = to.isAir() ? ChangeBlockHooks.raiding(rabbit, pos, to) : ChangeBlockHooks.changing(rabbit, pos, to);
        if (cancelled) {
            return false;
        }
        return original.call(level, pos, to, flags);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$clearing(Level level, BlockPos pos, boolean drop, Entity breaker, Operation<Boolean> original) {
        if (ChangeBlockHooks.raidCancelled()) {
            return false;
        }
        return original.call(level, pos, drop, breaker);
    }
}
