package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/** Durability loss of a player's tool or armour, after Unbreaking, before it is applied. */
@Mixin(ItemStack.class)
public abstract class ItemStackEvents {
    @WrapOperation(method = {"hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
        "hurtWithoutBreaking"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)I"))
    private int adapt$damaging(ItemStack stack, int amount, ServerLevel level, ServerPlayer player, Operation<Integer> original) {
        int damage = original.call(stack, amount, level, player);
        return PlayerHooks.itemDamaging(player, stack, damage, amount);
    }

    /** NeoForge moves the work into a LivingEntity overload; absent elsewhere. */
    @WrapOperation(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
        require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)I"))
    private int adapt$damagingLiving(ItemStack stack, int amount, ServerLevel level, LivingEntity owner, Operation<Integer> original) {
        int damage = original.call(stack, amount, level, owner);
        return owner instanceof ServerPlayer player ? PlayerHooks.itemDamaging(player, stack, damage, amount) : damage;
    }

    /** Forge passes its break callback through a five-argument variant; absent elsewhere. */
    @WrapOperation(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
        require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;ZLjava/util/function/Consumer;)I"))
    private int adapt$damagingForge(ItemStack stack, int amount, ServerLevel level, ServerPlayer player, boolean breaks,
                                    Consumer<ItemStack> onBreak, Operation<Integer> original) {
        int damage = original.call(stack, amount, level, player, breaks, onBreak);
        return PlayerHooks.itemDamaging(player, stack, damage, amount);
    }
}
