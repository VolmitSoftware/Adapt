package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.AiHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** A piglin done admiring gold: the barter loot it throws. Cancelled: nothing is thrown, as Paper. */
@Mixin(PiglinAi.class)
public abstract class PiglinBarterEvents {
    @WrapOperation(method = "stopHoldingOffHandItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/monster/piglin/PiglinAi;getBarterResponseItems(Lnet/minecraft/world/entity/monster/piglin/Piglin;)Ljava/util/List;"))
    private static List<ItemStack> adapt$bartering(Piglin piglin, Operation<List<ItemStack>> original, @Local(ordinal = 0) ItemStack input) {
        return AiHooks.bartering(piglin, input, original.call(piglin));
    }
}
