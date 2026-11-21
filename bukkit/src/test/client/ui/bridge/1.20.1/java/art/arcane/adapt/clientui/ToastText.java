package art.arcane.adapt.clientui;

import art.arcane.adapt.clientui.mixin.AdvancementToastAccess;
import art.arcane.adapt.clientui.mixin.SystemToastAccess;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;

final class ToastText {
    private ToastText() {
    }

    static String[] describe(Toast toast) {
        if (toast instanceof AdvancementToast) {
            DisplayInfo display = ((AdvancementToastAccess) toast).adaptUiAdvancement().getDisplay();
            return display == null ? null : new String[]{Recorder.plain(display.getTitle()), Recorder.plain(display.getDescription())};
        }
        if (toast instanceof SystemToast) {
            SystemToastAccess system = (SystemToastAccess) toast;
            return new String[]{Recorder.plain(system.adaptUiTitle()), Recorder.plain(system.adaptUiMessage())};
        }
        return null;
    }
}
