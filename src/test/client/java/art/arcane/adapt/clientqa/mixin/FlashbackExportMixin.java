package art.arcane.adapt.clientqa.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.moulberry.flashback.exporting.ExportJob", remap = false)
public abstract class FlashbackExportMixin {
    @Unique
    private static final boolean HIDDEN = Boolean.parseBoolean(System.getProperty("adapt.qa.hidden", "true"));

    @Redirect(method = "finishFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;isMinimized()Z"), remap = false)
    private boolean skipPresentation(Window window) {
        return HIDDEN || window.isMinimized();
    }
}
