package art.arcane.adapt.clientqa.mixin;

import art.arcane.adapt.clientqa.ClientBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
    private void extractCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if (!ClientBridge.hasCursor()) {
            return;
        }
        graphics.nextStratum();
        for (int row = 0; row < 10; row++) {
            int width = Math.min(row + 1, 7);
            graphics.fill(mouseX, mouseY + row, mouseX + width, mouseY + row + 1, 0xFF111111);
            if (row > 1 && row < 8 && width > 2) {
                graphics.fill(mouseX + 1, mouseY + row, mouseX + width - 1, mouseY + row + 1, 0xFFFFFFFF);
            }
        }
        graphics.fill(mouseX + 3, mouseY + 8, mouseX + 6, mouseY + 12, 0xFF111111);
        graphics.fill(mouseX + 4, mouseY + 8, mouseX + 5, mouseY + 11, 0xFFFFFFFF);
    }
}
