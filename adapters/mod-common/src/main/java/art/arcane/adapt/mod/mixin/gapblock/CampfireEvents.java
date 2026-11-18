package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A campfire finishing an item, before it drops the result. A cancel stops this tick like Bukkit. */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireEvents {
    @Inject(method = "cookTick", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"))
    private static void adapt$cooking(CallbackInfo callback, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos,
                                      @Local(ordinal = 0) ItemStack source, @Local(ordinal = 1) LocalRef<ItemStack> result) {
        ItemStack cooked = BlockHooks.campfireCook(level, pos, source, result.get());
        if (cooked == null) {
            callback.cancel();
        } else if (cooked != result.get()) {
            result.set(cooked);
        }
    }
}
