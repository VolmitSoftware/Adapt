package art.arcane.adapt.client.theme;

import art.arcane.adapt.api.client.AdaptClientApi;
import art.arcane.adapt.api.client.FrameCanvas;
import art.arcane.adapt.api.client.FrameRenderer;
import art.arcane.adapt.api.internal.FrameRegistry;

/**
 * Which hover frame a skill's tiles wear: the renderer registered for the skill's id, else its kit's {@code frame} data,
 * else {@link FrameData#GENERIC} in the kit's colours, so every themed skill has one. Adapt's own frames are registered
 * the first time a frame is drawn.
 */
final class SkillFrames {
    static {
        HoverFrames.register(AdaptClientApi.themes());
    }

    private SkillFrames() {
    }

    static void draw(ThemeKit kit, FrameCanvas canvas) {
        FrameRenderer registered = FrameRegistry.INSTANCE.renderer(kit.id());
        if (registered != null) {
            registered.draw(canvas);
            return;
        }
        FrameData data = kit.frame();
        FrameDataRenderer.draw(data != null ? data : FrameData.GENERIC, canvas);
    }
}
