package art.arcane.adapt.clientqa.mixin;

import art.arcane.adapt.clientqa.ClientBridge;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(QuadParticleRenderState.class)
public abstract class ParticleRenderMixin {
    @Inject(method = "buildLayer", at = @At("RETURN"))
    private void renderedLayer(SingleQuadParticle.Layer layer, VertexConsumer consumer, CallbackInfo callback) {
        ClientBridge.particleLayer((QuadParticleRenderState) (Object) this, layer);
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void clearedGeometry(CallbackInfo callback) {
        ClientBridge.clearParticleGeometry((QuadParticleRenderState) (Object) this);
    }
}
