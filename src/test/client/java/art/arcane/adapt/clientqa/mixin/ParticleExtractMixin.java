package art.arcane.adapt.clientqa.mixin;

import art.arcane.adapt.clientqa.ClientBridge;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class ParticleExtractMixin {
    @Shadow
    protected abstract SingleQuadParticle.Layer getLayer();

    @Inject(method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V", at = @At("RETURN"))
    private void extractedQuad(QuadParticleRenderState state, Quaternionf rotation,
            float x, float y, float z, float partialTick, CallbackInfo callback) {
        ClientBridge.particleGeometry((Particle) (Object) this, state, getLayer());
    }
}
