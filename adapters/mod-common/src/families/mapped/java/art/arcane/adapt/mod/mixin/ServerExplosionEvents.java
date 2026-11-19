package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import art.arcane.adapt.mod.event.ExplosionYield;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Explosion block damage on the server, after entities were hurt and before blocks break, with the block list and
 * yield editable; a cancel leaves every block, and so every fire, in place.
 */
@Mixin(Explosion.class)
public abstract class ServerExplosionEvents implements ExplosionYield {
    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private double x;

    @Shadow
    @Final
    private double y;

    @Shadow
    @Final
    private double z;

    @Shadow
    @Final
    private float radius;

    @Unique
    private float adapt$yield = Float.NaN;

    @Override
    public float adapt$yield() {
        return adapt$yield;
    }

    @Override
    public void adapt$yield(float yield) {
        adapt$yield = yield;
    }

    @ModifyExpressionValue(method = "finalizeExplosion(Z)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Explosion;interactsWithBlocks()Z"))
    private boolean adapt$exploding(boolean interacts) {
        Explosion self = (Explosion) (Object) this;
        if (interacts && level instanceof ServerLevel server && EntityHooks.exploding(self, server, new Vec3(x, y, z), radius, self.getToBlow())) {
            return false;
        }
        return interacts;
    }
}
