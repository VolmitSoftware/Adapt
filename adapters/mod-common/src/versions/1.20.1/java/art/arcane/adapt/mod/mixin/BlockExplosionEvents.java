package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.Explosion;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drops of exploded blocks: a yield set by a handler replaces the radius the loot tables' survival chance reads.
 * The first radius read in finalizeExplosion picks the particle, the second fills the loot parameter.
 */
@Mixin(Explosion.class)
public abstract class BlockExplosionEvents {
    @ModifyExpressionValue(method = "finalizeExplosion(Z)V", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/Explosion;radius:F", opcode = Opcodes.GETFIELD, ordinal = 1))
    private float adapt$dropRadius(float radius) {
        return EntityHooks.dropRadius((Explosion) (Object) this, radius);
    }
}
