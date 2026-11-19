package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Durability loss of a player's tool or armour, after Unbreaking, before it is applied. Vanilla and Forge do the work
 * in the player overload; NeoForge moves it into a LivingEntity overload. Each handler is absent on the other loader.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEvents {
    @WrapOperation(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
        require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"))
    private int adapt$damaging(ServerLevel level, ItemStack stack, int amount, Operation<Integer> original,
                               @Local(argsOnly = true) ServerPlayer player) {
        int damage = original.call(level, stack, amount);
        return PlayerHooks.itemDamaging(player, stack, damage, amount);
    }

    @WrapOperation(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
        require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"))
    private int adapt$damagingLiving(ServerLevel level, ItemStack stack, int amount, Operation<Integer> original,
                                     @Local(argsOnly = true) LivingEntity owner) {
        int damage = original.call(level, stack, amount);
        return owner instanceof ServerPlayer player ? PlayerHooks.itemDamaging(player, stack, damage, amount) : damage;
    }
}
