package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.ExhaustionHooks;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exhaustion natural regeneration costs. */
@Mixin(FoodData.class)
public abstract class FoodDataExhaustionEvents {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"))
    private void adapt$regenExhaustion(FoodData data, float amount, Operation<Void> original, @Local(argsOnly = true) ServerPlayer player) {
        float chosen = ExhaustionHooks.exhausting(player, EntityExhaustionEvent.Reason.REGEN, amount);
        if (!Float.isNaN(chosen)) {
            original.call(data, chosen);
        }
    }
}
