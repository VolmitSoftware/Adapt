package art.arcane.adapt.client.ui;

import java.util.Objects;

/**
 * Base of every Adapt screen. Written once against {@link UiDraw} and {@link UiInput}; each Minecraft version wraps
 * it in its own native screen. Input handlers return true when they used the event; unused events reach vanilla, so
 * Escape closes the screen unless {@link #closesOnEscape()} says otherwise.
 */
public abstract class UiScreen {
    private final String title;
    private UiHost host;
    private UiInput input;
    private int width;
    private int height;

    protected UiScreen(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public final String title() {
        return title;
    }

    public final int width() {
        return width;
    }

    public final int height() {
        return height;
    }

    protected final UiHost host() {
        return host;
    }

    protected final UiInput input() {
        return input;
    }

    final void attach(UiHost host, UiInput input, int width, int height) {
        this.host = host;
        this.input = input;
        this.width = width;
        this.height = height;
        init();
    }

    /** Called when the screen opens and again whenever its size changes; lay out here. */
    protected void init() {
    }

    public void tick() {
    }

    public abstract void render(UiDraw draw, int mouseX, int mouseY, float delta);

    public boolean mouseClicked(UiMouseEvent event) {
        return false;
    }

    public boolean mouseReleased(UiMouseEvent event) {
        return false;
    }

    public boolean mouseDragged(UiMouseEvent event, double dragX, double dragY) {
        return false;
    }

    public void mouseMoved(double mouseX, double mouseY) {
    }

    public boolean mouseScrolled(UiScrollEvent event) {
        return false;
    }

    public boolean keyPressed(UiKeyEvent event) {
        return false;
    }

    public boolean keyReleased(UiKeyEvent event) {
        return false;
    }

    public boolean charTyped(UiCharEvent event) {
        return false;
    }

    public boolean closesOnEscape() {
        return true;
    }

    public boolean pausesGame() {
        return false;
    }

    /** Whether the world behind the screen is dimmed and blurred like vanilla menus. */
    public boolean dimsBackground() {
        return true;
    }

    /** Called once when the screen stops being shown. */
    public void removed() {
    }

    public final void close() {
        if (host != null && host.current() == this) {
            host.close();
        }
    }
}
