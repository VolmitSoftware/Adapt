package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.EntityLifeHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Item entities despawning from age and merging into a neighbouring stack. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityLifeEvents {
    @Shadow
    private int age;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V"))
    private void adapt$despawning(ItemEntity item, Operation<Void> original) {
        if (EntityLifeHooks.despawning(item)) {
            age = 0;
            return;
        }
        original.call(item);
    }

    @WrapOperation(method = "tryToMerge", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/item/ItemEntity;merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void adapt$merging(ItemEntity into, ItemStack intoStack, ItemEntity from, ItemStack fromStack, Operation<Void> original) {
        if (!EntityLifeHooks.merging(into, from)) {
            original.call(into, intoStack, from, fromStack);
        }
    }
}
