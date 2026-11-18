package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerMotionHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The velocity packet a hurt or pushed player gets from its tracker. */
@Mixin(ServerEntity.class)
public abstract class PlayerVelocityEvents {
    @Shadow
    @Final
    private Entity entity;

    @WrapOperation(method = "sendChanges", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerEntity$Synchronizer;sendToTrackingPlayersAndSelf(Lnet/minecraft/network/protocol/Packet;)V"))
    private void adapt$velocity(ServerEntity.Synchronizer synchronizer, Packet<?> packet, Operation<Void> original) {
        if (!(entity instanceof ServerPlayer player) || !(packet instanceof ClientboundSetEntityMotionPacket)) {
            original.call(synchronizer, packet);
            return;
        }
        Vec3 before = player.getDeltaMovement();
        Vec3 velocity = PlayerMotionHooks.velocity(player, before);
        if (velocity == null) {
            return;
        }
        original.call(synchronizer, velocity == before ? packet : new ClientboundSetEntityMotionPacket(player.getId(), velocity));
    }
}
