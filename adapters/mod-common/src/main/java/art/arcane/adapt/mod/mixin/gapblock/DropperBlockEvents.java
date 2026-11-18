package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A dropper dropping into the world (dispense) or into a container (item move). */
@Mixin(DropperBlock.class)
public abstract class DropperBlockEvents {
    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/dispenser/DispenseItemBehavior;dispense(Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack adapt$dropping(DispenseItemBehavior behavior, BlockSource source, ItemStack stack, Operation<ItemStack> original) {
        if (!BlockHooks.dispense(source.level(), source.pos(), stack, source.state().getValue(DispenserBlock.FACING))) {
            return stack;
        }
        return original.call(behavior, source, stack);
    }

    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack adapt$transferring(Container from, Container into, ItemStack stack, Direction direction, Operation<ItemStack> original) {
        if (!ContainerHooks.moving(from, into, stack, true)) {
            return stack;
        }
        return original.call(from, into, stack, direction);
    }
}
