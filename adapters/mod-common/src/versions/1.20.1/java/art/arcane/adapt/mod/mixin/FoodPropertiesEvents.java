package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Eating food: the food level change the eat causes carries the item. The player applies the food first thing in
 * {@code eat}, through a FoodData call whose signature differs between vanilla and Forge, so the window runs from the
 * start of {@code eat} to its first stat award.
 */
@Mixin(Player.class)
public abstract class FoodPropertiesEvents {
    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private void adapt$eating(Level level, ItemStack stack, CallbackInfoReturnable<ItemStack> callback) {
        PlayerHooks.eating(stack);
    }

    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/stats/Stat;)V", ordinal = 0))
    private void adapt$eaten(Level level, ItemStack stack, CallbackInfoReturnable<ItemStack> callback) {
        PlayerHooks.eatingEnd();
    }
}
