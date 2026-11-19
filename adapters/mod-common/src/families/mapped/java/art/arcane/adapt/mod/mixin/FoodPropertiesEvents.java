package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Eating food: the food level change the eat causes carries the item. Before 1.21.2 the player applies the food itself. */
@Mixin(Player.class)
public abstract class FoodPropertiesEvents {
    @WrapOperation(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
    private void adapt$eating(FoodData food, FoodProperties properties, Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        PlayerHooks.eating(stack);
        try {
            original.call(food, properties);
        } finally {
            PlayerHooks.eatingEnd();
        }
    }
}
