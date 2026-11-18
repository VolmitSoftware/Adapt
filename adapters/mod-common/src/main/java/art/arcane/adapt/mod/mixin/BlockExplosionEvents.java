package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Drops of exploded blocks: a yield set by a handler replaces the radius the loot tables' survival chance reads. */
@Mixin(BlockBehaviour.class)
public abstract class BlockExplosionEvents {
    @ModifyExpressionValue(method = "onExplosionHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Explosion;radius()F"))
    private float adapt$dropRadius(float radius, @Local(argsOnly = true) Explosion explosion) {
        return EntityHooks.dropRadius(explosion, radius);
    }
}
