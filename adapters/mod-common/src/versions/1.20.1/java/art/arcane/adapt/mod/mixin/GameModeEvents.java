package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import art.arcane.adapt.mod.event.InteractState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Left and right clicks on blocks, mining start and player block breaks. */
@Mixin(ServerPlayerGameMode.class)
public abstract class GameModeEvents implements InteractState.Holder {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    protected ServerLevel level;

    @Unique
    private final InteractState adapt$interactState = new InteractState();

    @Override
    public InteractState adapt$interactState() {
        return adapt$interactState;
    }

    /**
     * Left click on a block, after spawn protection and before creative breaking, where Paper posts it. A denied click
     * reads as failed spawn protection, so vanilla resends the block and stops.
     */
    @ModifyExpressionValue(method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean adapt$leftClickBlock(boolean allowed, @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) Direction direction) {
        return allowed && !InteractHooks.leftClickBlock(player, pos, direction);
    }

    @Inject(method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
        ordinal = 0))
    private void adapt$blockDamaging(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY, int sequence,
                                     CallbackInfo callback) {
        if (InteractHooks.blockDamaging(player, pos, level.getBlockState(pos)) < 0) {
            callback.cancel();
        }
    }

    @ModifyExpressionValue(method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
        ordinal = 0))
    private float adapt$destroyProgress(float progress) {
        return InteractHooks.destroyProgress(progress);
    }

    /** Paper posts the break at the start, with the block in place. */
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void adapt$breaking(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (InteractHooks.breaking(player, pos)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"))
    private void adapt$broken(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        InteractHooks.broken(player, callback.getReturnValueZ());
    }

    @Inject(method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void adapt$rightClickBlock(ServerPlayer user, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<InteractionResult> callback) {
        if (InteractHooks.rightClickBlock(user, (ServerPlayerGameMode) (Object) this, stack, hand, hit)) {
            callback.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
    private void adapt$rightClickBlockDone(ServerPlayer user, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hit,
                                           CallbackInfoReturnable<InteractionResult> callback) {
        adapt$interactState.finish();
    }

    @WrapOperation(method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult adapt$useBlock(BlockState state, Level world, Player user, InteractionHand hand, BlockHitResult hit,
                                             Operation<InteractionResult> original) {
        if (adapt$interactState.denyBlock()) {
            return InteractionResult.PASS;
        }
        return original.call(state, world, user, hand, hit);
    }

    @WrapOperation(method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult adapt$useItem(ItemStack stack, UseOnContext context, Operation<InteractionResult> original) {
        if (adapt$interactState.denyItem()) {
            return InteractionResult.PASS;
        }
        return original.call(stack, context);
    }
}
