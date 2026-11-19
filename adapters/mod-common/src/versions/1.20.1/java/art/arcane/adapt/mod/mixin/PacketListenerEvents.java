package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.ClientMovement;
import art.arcane.adapt.mod.event.EntityInteractClicks;
import art.arcane.adapt.mod.event.InteractHooks;
import art.arcane.adapt.mod.event.InventoryHooks;
import art.arcane.adapt.mod.event.MoveState;
import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.mod.platform.ModInteractPacket;
import art.arcane.adapt.platform.entity.TeleportCause;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player input from the network. Every hook sits after vanilla re-scheduled the packet onto the
 * server thread, so it never runs on a network thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PacketListenerEvents implements MoveState.Holder, ClientMovement {
    @Shadow
    public ServerPlayer player;

    @Unique
    private final MoveState adapt$moveState = new MoveState();

    @Shadow
    private int receivedMovePacketCount;

    @Shadow
    private int knownMovePacketCount;

    @Unique
    private Vec3 adapt$clientMovement = Vec3.ZERO;

    @Unique
    private Vec3 adapt$lastTickPosition;

    @Override
    public MoveState adapt$moveState() {
        return adapt$moveState;
    }

    @Override
    public Vec3 adapt$clientMovement() {
        return adapt$clientMovement;
    }

    /**
     * The server keeps no client movement on this version: the distance moved since the previous tick, counted only when
     * the client sent movement packets since then (a client standing still sends nothing).
     */
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void adapt$tickMovement(CallbackInfo callback) {
        Vec3 position = player.position();
        boolean moved = receivedMovePacketCount != knownMovePacketCount && adapt$lastTickPosition != null;
        adapt$clientMovement = moved ? position.subtract(adapt$lastTickPosition) : Vec3.ZERO;
        adapt$lastTickPosition = position;
    }

    /** Sneak key presses arrive as player commands before 1.21.2. */
    @Inject(method = "handlePlayerCommand", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;setShiftKeyDown(Z)V"))
    private void adapt$sneaking(ServerboundPlayerCommandPacket packet, CallbackInfo callback) {
        if (PlayerHooks.sneaking(player, packet.getAction() == ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY)) {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerCommand", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;setSprinting(Z)V"))
    private void adapt$sprinting(ServerboundPlayerCommandPacket packet, CallbackInfo callback) {
        if (PlayerHooks.sprinting(player, packet.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING)) {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerAbilities", cancellable = true, at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z", opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    private void adapt$flying(ServerboundPlayerAbilitiesPacket packet, CallbackInfo callback) {
        if (PlayerHooks.flying(player, packet.isFlying())) {
            callback.cancel();
        }
    }

    @Inject(method = "handleSetCarriedItem", cancellable = true, at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", opcode = org.objectweb.asm.Opcodes.GETFIELD, ordinal = 0))
    private void adapt$holding(ServerboundSetCarriedItemPacket packet, CallbackInfo callback) {
        if (PlayerHooks.holding(player, packet.getSlot())) {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift = At.Shift.AFTER))
    private void adapt$swapping(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND && PlayerHooks.swapping(player)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleUseItem", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItem(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private void adapt$rightClickAir(ServerboundUseItemPacket packet, CallbackInfo callback, @Local InteractionHand hand) {
        if (InteractHooks.rightClickAir(player, hand)) {
            callback.cancel();
        }
    }

    /**
     * Interactions run inside a dispatch handler before 26.x, so the hook sits on the dispatch once vanilla checked the
     * world border and reach. One click arrives as a positioned and a plain packet (EntityInteractClicks); attacks pass
     * through.
     */
    @Inject(method = "handleInteract", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;dispatch(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket$Handler;)V"))
    private void adapt$interactingEntity(ServerboundInteractPacket packet, CallbackInfo callback) {
        ModInteractPacket read = ModInteractPacket.read(packet);
        if (read.hand() != null
            && EntityInteractClicks.interacting(player, packet.getTarget(player.serverLevel()), read.hand(), read.location())) {
            callback.cancel();
        }
    }

    @Inject(method = "handleContainerClick", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;suppressRemoteUpdates()V"))
    private void adapt$clicking(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (InventoryHooks.clicking(player, packet.getSlotNum(), packet.getButtonNum(), packet.getClickType().name())) {
            callback.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
        shift = At.Shift.AFTER))
    private void adapt$clicked(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        InventoryHooks.clicked(player);
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;isPassenger()Z", ordinal = 0))
    private void adapt$movePrepared(CallbackInfo callback) {
        PlayerHooks.movePrepared(player, adapt$moveState);
    }

    /** Vanilla's server-side jump detection; the event itself waits for the accepted move below. */
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;jumpFromGround()V"))
    private void adapt$jumping(CallbackInfo callback) {
        PlayerHooks.jumping(player, adapt$moveState);
    }

    /**
     * The accepted-move branch, once vanilla placed the player where the packet says and took the client's ground
     * state, so handlers see a landing on the move that lands as Paper's collision does.
     */
    @Inject(method = "handleMovePlayer", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;setOnGroundWithKnownMovement(ZLnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.AFTER))
    private void adapt$moved(CallbackInfo callback) {
        if (PlayerHooks.moved((ServerGamePacketListenerImpl) (Object) this, player, adapt$moveState)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleAnimate", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void adapt$swung(CallbackInfo callback) {
        InteractHooks.swung(player);
        if (art.arcane.adapt.mod.event.player.PlayerGapHooks.animating(player)) {
            callback.cancel();
        }
    }

    @Inject(method = "handleTeleportToEntityPacket", at = @At("HEAD"))
    private void adapt$spectating(ServerboundTeleportToEntityPacket packet, CallbackInfo callback) {
        PlayerHooks.teleportCause(TeleportCause.SPECTATE);
    }

    @Inject(method = "handleTeleportToEntityPacket", at = @At("RETURN"))
    private void adapt$spectated(ServerboundTeleportToEntityPacket packet, CallbackInfo callback) {
        PlayerHooks.teleportCauseEnd();
    }
}
