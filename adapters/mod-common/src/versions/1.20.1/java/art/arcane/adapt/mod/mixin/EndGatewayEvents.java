package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.entity.TeleportCause;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Teleports through an end gateway carry the gateway cause; before 1.21 the gateway moves the entity itself. */
@Mixin(TheEndGatewayBlockEntity.class)
public abstract class EndGatewayEvents {
    @Inject(method = "teleportEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;)V",
        at = @At("HEAD"))
    private static void adapt$teleporting(Level level, BlockPos pos, BlockState state, Entity entity, TheEndGatewayBlockEntity gateway,
                                          CallbackInfo callback) {
        PlayerHooks.teleportCause(TeleportCause.END_GATEWAY);
    }

    @Inject(method = "teleportEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;)V",
        at = @At("RETURN"))
    private static void adapt$teleported(Level level, BlockPos pos, BlockState state, Entity entity, TheEndGatewayBlockEntity gateway,
                                         CallbackInfo callback) {
        PlayerHooks.teleportCauseEnd();
    }
}
