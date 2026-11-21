package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.ScreenHarness;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiInput;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiScreen;

/** A {@link UiHost} that attaches screens at a fixed size and records clicks and the clipboard. */
public final class FakeHost implements UiHost, UiInput {
    private final int width;
    private final int height;
    private UiScreen current;
    private String clipboard = "";
    private int clicks;
    private int opened;
    private UiModifiers modifiers = UiModifiers.NONE;

    public FakeHost(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void open(UiScreen screen) {
        UiScreen previous = current;
        current = screen;
        if (previous != null) {
            previous.removed();
        }
        opened++;
        ScreenHarness.attach(screen, this, this, width, height);
    }

    @Override
    public void close() {
        UiScreen previous = current;
        current = null;
        if (previous != null) {
            previous.removed();
        }
    }

    @Override
    public UiScreen current() {
        return current;
    }

    @Override
    public void playClick() {
        clicks++;
    }

    @Override
    public String clipboard() {
        return clipboard;
    }

    @Override
    public void setClipboard(String text) {
        clipboard = text;
    }

    @Override
    public String translate(String key) {
        return key;
    }

    public int clicks() {
        return clicks;
    }

    public int opened() {
        return opened;
    }

    public void setModifiers(UiModifiers held) {
        modifiers = held;
    }

    @Override
    public double mouseX() {
        return 0;
    }

    @Override
    public double mouseY() {
        return 0;
    }

    @Override
    public boolean isMouseDown(UiMouseButton button) {
        return false;
    }

    @Override
    public UiModifiers modifiers() {
        return modifiers;
    }
}
