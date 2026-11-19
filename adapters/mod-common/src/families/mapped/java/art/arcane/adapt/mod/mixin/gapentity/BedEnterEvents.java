package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bed use: the successful attempt before the player lies down, and every refusal with its reason. */
@Mixin(ServerPlayer.class)
public abstract class BedEnterEvents {
    @Inject(method = "startSleepInBed(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;startSleepInBed(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;"))
    private void adapt$sleeping(CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> callback,
                                @Local(argsOnly = true) BlockPos pos) {
        if (PlayerGapHooks.sleeping((ServerPlayer) (Object) this, pos)) {
            callback.setReturnValue(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM));
        }
    }

    @Inject(method = "startSleepInBed(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;", at = @At("RETURN"))
    private void adapt$sleepAttempted(CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> callback,
                                      @Local(argsOnly = true) BlockPos pos) {
        Either<Player.BedSleepingProblem, Unit> result = callback.getReturnValue();
        PlayerGapHooks.sleepAttempted((ServerPlayer) (Object) this, pos, result == null ? null : result.left().orElse(null));
    }
}
