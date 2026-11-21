package art.arcane.adapt.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.sounds.SoundEvents;

public final class MinecraftUiHost implements UiHost {
    private final MinecraftUiInput input = new MinecraftUiInput();
    private MinecraftUiScreen shown;

    @Override
    public void open(UiScreen screen) {
        MinecraftScreens.show(new MinecraftUiScreen(this, input, screen));
    }

    @Override
    public void close() {
        if (shown != null) {
            shown.onClose();
        }
    }

    @Override
    public UiScreen current() {
        return shown == null ? null : shown.content();
    }

    @Override
    public void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public String clipboard() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    @Override
    public void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    @Override
    public String translate(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    void shown(MinecraftUiScreen screen) {
        shown = screen;
    }

    void hidden(MinecraftUiScreen screen) {
        if (shown == screen) {
            shown = null;
        }
    }
}
