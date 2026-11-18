package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.MotionHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the user of a post-piercing (spear) enchantment while its effects run. */
@Mixin(Enchantment.class)
public abstract class LungeEvents {
    @Inject(method = "doPostPiercingAttack", at = @At("HEAD"))
    private void adapt$piercingStarted(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity user, CallbackInfo callback) {
        MotionHooks.piercingStarted(user);
    }

    @Inject(method = "doPostPiercingAttack", at = @At("RETURN"))
    private void adapt$piercingEnded(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity user, CallbackInfo callback) {
        MotionHooks.piercingEnded();
    }
}
