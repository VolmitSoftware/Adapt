package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.MotionHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.ApplyEntityImpulse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The lunge impulse of a spear: the lunge event decides whether it runs and at which level. */
@Mixin(ApplyEntityImpulse.class)
public abstract class LungeImpulseEvents {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void adapt$lunging(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position,
                               CallbackInfo callback) {
        int power = MotionHooks.lunging(entity, enchantmentLevel);
        if (power == enchantmentLevel) {
            return;
        }
        callback.cancel();
        if (power != MotionHooks.CANCELLED) {
            ApplyEntityImpulse self = (ApplyEntityImpulse) (Object) this;
            MotionHooks.relunge(() -> self.apply(level, power, item, entity, position));
        }
    }
}
