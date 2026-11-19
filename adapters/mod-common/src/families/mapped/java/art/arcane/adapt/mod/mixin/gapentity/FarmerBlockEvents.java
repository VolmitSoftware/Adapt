package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Farmer villagers harvesting grown crops and planting seeds. */
@Mixin(HarvestFarmland.class)
public abstract class FarmerBlockEvents {
    @WrapOperation(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$harvesting(ServerLevel level, BlockPos pos, boolean drop, Entity breaker, Operation<Boolean> original) {
        if (ChangeBlockHooks.clearing(breaker, pos)) {
            return false;
        }
        return original.call(level, pos, drop, breaker);
    }

    @WrapOperation(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean adapt$planting(ServerLevel level, BlockPos pos, BlockState to, Operation<Boolean> original,
                                   @Local(argsOnly = true) Villager body) {
        if (ChangeBlockHooks.changing(body, pos, to)) {
            return false;
        }
        return original.call(level, pos, to);
    }
}
