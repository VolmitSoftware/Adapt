package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A crafter crafting, after the result is assembled and before anything is consumed or dispensed. */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockEvents {
    @Inject(method = "dispenseFrom", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/CrafterBlockEntity;setCraftingTicksRemaining(I)V"))
    private void adapt$crafting(CallbackInfo callback, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos,
                                @Local RecipeHolder<?> recipe, @Local ItemStack result) {
        if (!BlockHooks.crafterCraft(level, pos, recipe, result)) {
            callback.cancel();
        }
    }
}
