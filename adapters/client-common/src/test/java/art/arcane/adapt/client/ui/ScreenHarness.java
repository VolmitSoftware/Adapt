package art.arcane.adapt.client.ui;

/** Attaches screens to a fake host and input outside Minecraft. */
public final class ScreenHarness {
    private ScreenHarness() {
    }

    public static void attach(UiScreen screen, UiHost host, UiInput input, int width, int height) {
        screen.attach(host, input, width, height);
    }
}
