package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import art.arcane.adapt.mod.event.ExplosionYield;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Explosion block damage, after entities were hurt and before blocks break, with the block list and yield editable. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionEvents implements ExplosionYield {
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

    @Inject(method = "interactWithBlocks", at = @At("HEAD"), cancellable = true)
    private void adapt$exploding(List<BlockPos> blocks, CallbackInfo callback) {
        if (EntityHooks.exploding((ServerExplosion) (Object) this, blocks)) {
            callback.cancel();
        }
    }
}
