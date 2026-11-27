package art.arcane.adapt.client.ui;

/** A host that attaches the client's screens at a fixed GUI size with the pointer far off the page. */
public final class PackScreenHost implements UiHost, UiInput {
    private final int width;
    private final int height;
    private UiScreen current;

    public PackScreenHost(int width, int height) {
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
        screen.attach(this, this, width, height);
    }

    @Override
    public void close() {
        current = null;
    }

    @Override
    public UiScreen current() {
        return current;
    }

    @Override
    public void playClick() {
    }

    @Override
    public String clipboard() {
        return "";
    }

    @Override
    public void setClipboard(String text) {
    }

    @Override
    public String translate(String key) {
        return key;
    }

    @Override
    public double mouseX() {
        return -1000;
    }

    @Override
    public double mouseY() {
        return -1000;
    }

    @Override
    public boolean isMouseDown(UiMouseButton button) {
        return false;
    }

    @Override
    public UiModifiers modifiers() {
        return UiModifiers.NONE;
    }
}
