package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player empties a bucket by using it. Each loader calls a different emptyContents variant from use, so each
 * handler is optional; a cancelled empty places nothing and the use fails.
 */
@Mixin(BucketItem.class)
public abstract class BucketEmptyEvents {
    @WrapOperation(method = "use", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/BucketItem;emptyContents(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;)Z"))
    private boolean adapt$emptying(BucketItem bucket, LivingEntity user, Level level, BlockPos pos, BlockHitResult hit,
                                   Operation<Boolean> original) {
        return !InteractHooks.emptyingBucket(user, level, pos, bucket) && original.call(bucket, user, level, pos, hit);
    }

    /** NeoForge passes the bucket stack through a five-argument variant. */
    @WrapOperation(method = "use", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/BucketItem;emptyContents(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean adapt$emptyingStack(BucketItem bucket, LivingEntity user, Level level, BlockPos pos, BlockHitResult hit,
                                        ItemStack stack, Operation<Boolean> original) {
        return !InteractHooks.emptyingBucket(user, level, pos, bucket) && original.call(bucket, user, level, pos, hit, stack);
    }

    /** Forge's five-argument variant takes the player. */
    @WrapOperation(method = "use", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/BucketItem;emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean adapt$emptyingForge(BucketItem bucket, Player user, Level level, BlockPos pos, BlockHitResult hit,
                                        ItemStack stack, Operation<Boolean> original) {
        return !InteractHooks.emptyingBucket(user, level, pos, bucket) && original.call(bucket, user, level, pos, hit, stack);
    }
}
