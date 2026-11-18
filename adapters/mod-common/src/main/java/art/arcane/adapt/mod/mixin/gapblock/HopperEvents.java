package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hoppers (blocks and minecarts) pushing into and pulling from containers, and taking item
 * entities. A cancelled move returns the one-item stack, which vanilla puts back.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperEvents {
    @WrapOperation(method = "ejectItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack adapt$pushing(Container from, Container into, ItemStack stack, Direction direction, Operation<ItemStack> original) {
        if (!ContainerHooks.moving(from, into, stack, true)) {
            return stack;
        }
        return original.call(from, into, stack, direction);
    }

    @WrapOperation(method = "tryTakeInItemFromSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack adapt$pulling(Container from, Container into, ItemStack stack, Direction direction, Operation<ItemStack> original) {
        if (!ContainerHooks.moving(from, into, stack, false)) {
            return stack;
        }
        return original.call(from, into, stack, direction);
    }

    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void adapt$pickingUp(Container container, ItemEntity item, CallbackInfoReturnable<Boolean> callback) {
        if (!ContainerHooks.pickingUp(container, item)) {
            callback.setReturnValue(false);
        }
    }
}
