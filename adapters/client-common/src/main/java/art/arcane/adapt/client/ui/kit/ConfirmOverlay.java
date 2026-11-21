package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.List;
import java.util.Objects;

/**
 * A modal confirmation: a dimmed screen with a centred panel holding a title, a wrapped message and Cancel/Confirm
 * buttons. Escape or Cancel runs {@code onCancel}; Confirm runs {@code onConfirm}. Destructive confirmations start
 * with Cancel focused.
 */
public class ConfirmOverlay extends UiOverlay {
    private static final int MAX_WIDTH = 240;
    private static final int MARGIN = 8;

    private final Panel panel;
    private final Label message;
    private final Button cancel;
    private final Button confirm;
    private final Runnable onCancel;

    public ConfirmOverlay(String id, String title, String text, String confirmLabel, String cancelLabel, Button.Style confirmStyle,
                          Runnable onConfirm, Runnable onCancel) {
        super(id);
        Objects.requireNonNull(onConfirm, "onConfirm");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
        panel = add(new Panel(id + ".panel", title));
        message = panel.add(new Label(id + ".message", text).setWrap(true));
        cancel = panel.add(new Button(id + ".cancel", cancelLabel, Button.Style.NORMAL, () -> {
            close();
            this.onCancel.run();
        }));
        confirm = panel.add(new Button(id + ".confirm", confirmLabel, confirmStyle, () -> {
            close();
            onConfirm.run();
        }));
    }

    @Override
    public String kind() {
        return "confirm";
    }

    @Override
    public String label() {
        return panel.label();
    }

    @Override
    public boolean modal() {
        return true;
    }

    @Override
    public UiNode initialFocus() {
        return confirm.style() == Button.Style.DESTRUCTIVE ? cancel : confirm;
    }

    public Button confirmButton() {
        return confirm;
    }

    public Button cancelButton() {
        return cancel;
    }

    @Override
    protected void dismissed() {
        onCancel.run();
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiTheme theme = services().theme();
        UiRect screen = bounds();
        int width = Math.min(MAX_WIDTH, screen.width() - MARGIN * 2);
        int inner = width - 2 - theme.padding() * 2;
        int messageHeight = message.preferredHeight(inner, metrics);
        int height = panel.chromeHeight() + messageHeight + theme.padding() + theme.controlHeight();
        UiRect box = UiLayout.center(screen, width, height);
        panel.place(box, metrics);
        UiRect content = panel.contentBounds();
        message.place(content.top(messageHeight), metrics);
        UiRect buttons = content.bottomStrip(theme.controlHeight());
        List<UiRect> cells = UiLayout.row(buttons, theme.gap() * 2, 0, 0);
        cancel.place(cells.get(0), metrics);
        confirm.place(cells.get(1), metrics);
    }
}
