package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A dispenser using an item; a cancel keeps the stack in its slot. */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockEvents {
    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/dispenser/DispenseItemBehavior;dispense(Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack adapt$dispensing(DispenseItemBehavior behavior, BlockSource source, ItemStack stack, Operation<ItemStack> original) {
        if (!BlockHooks.dispense(source.level(), source.pos(), stack, source.state().getValue(DispenserBlock.FACING))) {
            return stack;
        }
        return original.call(behavior, source, stack);
    }
}
