package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.KnockbackHooks;
import art.arcane.adapt.mod.event.player.PlayerMotionHooks;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's melee attack: the pre-attack event, the push of a non-living target, and the knockback
 * velocity sent straight to a player target.
 */
@Mixin(Player.class)
public abstract class PlayerAttackEvents {
    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void adapt$preAttack(Entity target, CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayer player && PlayerMotionHooks.preAttack(player, target)) {
            callback.cancel();
        }
    }

    @WrapOperation(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"))
    private void adapt$attackPush(Entity target, double x, double y, double z, Operation<Void> original) {
        if (!KnockbackHooks.active()) {
            original.call(target, x, y, z);
            return;
        }
        Vec3 impulse = KnockbackHooks.push(target, (Player) (Object) this, EntityKnockbackEvent.Cause.ENTITY_ATTACK, new Vec3(x, y, z));
        if (impulse != null) {
            original.call(target, impulse.x, impulse.y, impulse.z);
        }
    }

    @WrapOperation(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void adapt$attackVelocity(ServerGamePacketListenerImpl connection, Packet<?> packet, Operation<Void> original) {
        if (!(packet instanceof ClientboundSetEntityMotionPacket)) {
            original.call(connection, packet);
            return;
        }
        ServerPlayer player = connection.player;
        Vec3 before = player.getDeltaMovement();
        Vec3 velocity = PlayerMotionHooks.velocity(player, before);
        if (velocity == null) {
            return;
        }
        original.call(connection, velocity == before ? packet : new ClientboundSetEntityMotionPacket(player.getId(), velocity));
    }
}
