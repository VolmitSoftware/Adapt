package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.Map;

/**
 * A framed surface: the tome's cover and page (the kit {@code panel} texture, else a soft page with a leather edge
 * {@link UiTheme#frame} thick) and an optional title bar. Children go in
 * {@link #contentBounds()} through the group's layout policy. The panel itself takes pointer hits so clicks on its
 * background never reach what is behind it.
 */
public class Panel extends UiGroup {
    private String title;
    private int titleLeft;
    private int titleRight;

    public Panel(String id, String title) {
        super(id, UiLayoutPolicy.fill());
        this.title = title;
    }

    @Override
    public String kind() {
        return "panel";
    }

    @Override
    public String label() {
        return LegacyText.strip(title);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("title", title == null ? "" : title);
    }

    public String title() {
        return title;
    }

    public void setTitle(String newTitle) {
        title = newTitle;
    }

    /** Keeps room free on each side of the title for buttons placed in the title bar. */
    public void setTitleInsets(int left, int right) {
        titleLeft = Math.max(0, left);
        titleRight = Math.max(0, right);
    }

    @Override
    public boolean hittable() {
        return true;
    }

    /** The title bar, empty when the panel has no title. */
    public UiRect headerBounds() {
        if (title == null) {
            return new UiRect(bounds().x(), bounds().y(), bounds().width(), 0);
        }
        UiTheme theme = services().theme();
        return bounds().inset(theme.frame()).top(theme.controlHeight());
    }

    @Override
    public UiRect contentBounds() {
        UiTheme theme = services().theme();
        UiRect inner = bounds().inset(theme.frame());
        if (title != null) {
            inner = inner.belowTop(theme.controlHeight());
        }
        return inner.inset(theme.padding());
    }

    @Override
    protected int chromeWidth() {
        UiTheme theme = services().theme();
        return theme.frame() * 2 + theme.padding() * 2;
    }

    @Override
    protected int chromeHeight() {
        UiTheme theme = services().theme();
        return theme.frame() * 2 + theme.padding() * 2 + (title == null ? 0 : theme.controlHeight());
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        if (!UiChrome.paint(draw, UiChrome.PANEL, null, box)) {
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), theme.panelBorder());
            UiRect page = box.inset(theme.frame() - 1);
            draw.fill(page.x(), page.y(), page.right(), page.bottom(), theme.panel());
        }
        if (title != null) {
            UiRect header = headerBounds();
            draw.fill(header.x(), header.y(), header.right(), header.bottom(), theme.header());
            int textY = header.y() + (header.height() - draw.lineHeight()) / 2 + 1;
            int textWidth = header.width() - theme.padding() * 2 - titleLeft - titleRight;
            draw.text(LegacyText.truncate(draw, title, Math.max(0, textWidth)), header.x() + theme.padding() + titleLeft, textY,
                    theme.text(), false);
        }
    }
}
