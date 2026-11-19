package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.entity.TeleportCause;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The random teleport of eating chorus fruit, which the item itself runs before 1.21.2. */
@Mixin(ChorusFruitItem.class)
public abstract class ChorusFruitEvents {
    @Inject(method = "finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"))
    private void adapt$teleporting(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> callback) {
        PlayerHooks.teleportCause(TeleportCause.CHORUS_FRUIT);
    }

    @Inject(method = "finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN"))
    private void adapt$teleported(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> callback) {
        PlayerHooks.teleportCauseEnd();
    }
}
