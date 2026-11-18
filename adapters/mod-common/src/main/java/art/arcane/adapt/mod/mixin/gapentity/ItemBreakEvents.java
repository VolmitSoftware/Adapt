package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/** A player's item worn out by durability loss. */
@Mixin(ItemStack.class)
public abstract class ItemBreakEvents {
    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"), argsOnly = true)
    private Consumer<Object> adapt$breaking(Consumer<Object> onBreak, @Local(argsOnly = true) ServerPlayer player) {
        return PlayerGapHooks.breaking((ItemStack) (Object) this, player, onBreak);
    }
}
