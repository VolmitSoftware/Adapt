package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;

import java.util.Map;
import java.util.Objects;

/**
 * A push button. Like vanilla buttons it fires on press, plays the click sound and reacts to Enter and Space. A glyph
 * button shows an ink arrow or stepper mark on a paper tab instead of text; a quiet button is bare ink until hovered.
 */
public class Button extends UiNode {
    private static final long HOVER_MILLIS = 90L;

    private final UiFade hover = new UiFade(HOVER_MILLIS);
    private final UiTome.Glyph glyph;
    private String text;
    private Style style;
    private Runnable action;

    public Button(String id, String text, Style style, Runnable action) {
        super(id);
        this.glyph = null;
        this.text = Objects.requireNonNull(text, "text");
        this.style = Objects.requireNonNull(style, "style");
        this.action = Objects.requireNonNull(action, "action");
    }

    public Button(String id, String text, Runnable action) {
        this(id, text, Style.NORMAL, action);
    }

    public Button(String id, UiTome.Glyph glyph, Runnable action) {
        super(id);
        this.glyph = Objects.requireNonNull(glyph, "glyph");
        this.text = glyph.text();
        this.style = Style.NORMAL;
        this.action = Objects.requireNonNull(action, "action");
    }

    /** The mark drawn instead of text, or null for a text button. */
    public UiTome.Glyph glyph() {
        return glyph;
    }

    @Override
    public String kind() {
        return "button";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("style", style.name());
    }

    public String text() {
        return text;
    }

    public Button setText(String newText) {
        text = Objects.requireNonNull(newText, "text");
        return this;
    }

    public Style style() {
        return style;
    }

    public Button setStyle(Style newStyle) {
        style = Objects.requireNonNull(newStyle, "style");
        return this;
    }

    public Button setAction(Runnable newAction) {
        action = Objects.requireNonNull(newAction, "action");
        return this;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        UiTheme theme = services().theme();
        if (glyph != null) {
            return Math.max(theme.controlHeight(), glyph.width() + 8);
        }
        return Math.max(theme.controlHeight(), metrics.textWidth(text) + theme.padding() * 2);
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return services().theme().controlHeight();
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        if (event.button() != UiMouseButton.LEFT) {
            return false;
        }
        press();
        return true;
    }

    @Override
    public boolean activate() {
        press();
        return true;
    }

    /** Runs the action with the click sound, if enabled. */
    public void press() {
        if (!enabled()) {
            return;
        }
        services().playClick();
        action.run();
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        boolean active = enabled();
        float lift = hover.update(frame.motion(), active && (frame.isHovered(this) || frame.showsFocus(this)));
        if (glyph != null || style == Style.QUIET) {
            paintPlain(frame, draw, theme, box, active, lift);
            return;
        }
        int base;
        int raised;
        int border;
        switch (style) {
            case PRIMARY -> {
                base = UiEase.mix(theme.surface(), theme.accent(), 0.18F);
                raised = UiEase.mix(theme.surface(), theme.accent(), 0.08F);
                border = theme.accent();
            }
            case DESTRUCTIVE -> {
                base = UiEase.mix(theme.surface(), theme.destructive(), 0.18F);
                raised = UiEase.mix(theme.surface(), theme.destructive(), 0.08F);
                border = theme.destructive();
            }
            default -> {
                base = theme.surface();
                raised = theme.surfaceHover();
                border = theme.surfaceBorder();
            }
        }
        int fill = !active ? UiEase.mix(theme.surface(), theme.panel(), 0.6F)
                : frame.isPressed(this) ? UiEase.mix(base, theme.surfacePressed(), 0.6F) : UiEase.mix(base, raised, lift);
        String chrome = switch (style) {
            case PRIMARY -> UiChrome.BUTTON + "_primary";
            case DESTRUCTIVE -> UiChrome.BUTTON + "_destructive";
            case NORMAL, QUIET -> UiChrome.BUTTON;
        };
        String state = !active ? "disabled" : frame.isPressed(this) ? "pressed" : lift >= 0.5F ? "hover" : null;
        if (!UiChrome.paint(draw, chrome, state, box)) {
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), fill);
            UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), active ? border : theme.tileBorder());
        }
        if (frame.showsFocus(this)) {
            UiTome.softEdge(draw, box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, theme.focus());
        }
        String line = LegacyText.truncate(draw, text, box.width() - 4);
        int textY = box.y() + (box.height() - draw.lineHeight()) / 2 + (frame.isPressed(this) ? 2 : 1);
        draw.centeredText(line, box.centerX(), textY, active ? theme.text() : theme.textDisabled(), false);
    }

    private void paintPlain(UiFrame frame, UiDraw draw, UiTheme theme, UiRect box, boolean active, float lift) {
        UiTome.Tab look = !active ? UiTome.Tab.DISABLED : frame.isPressed(this) ? UiTome.Tab.PRESSED
                : lift >= 0.5F ? UiTome.Tab.HOVER : UiTome.Tab.REST;
        boolean bare = glyph == null && look == UiTome.Tab.REST;
        if (!bare && !(glyph == null && look == UiTome.Tab.DISABLED)) {
            UiTome.tab(draw, box, look, theme);
        }
        if (frame.showsFocus(this)) {
            UiTome.softEdge(draw, box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, theme.focus());
        }
        if (glyph != null) {
            UiTome.glyph(draw, glyph, box, UiTome.glyphColor(look, theme));
            return;
        }
        String line = LegacyText.truncate(draw, text, box.width() - 4);
        int textY = box.y() + (box.height() - draw.lineHeight()) / 2 + 1;
        int color = !active ? theme.textDisabled() : look == UiTome.Tab.REST ? theme.textMuted() : theme.text();
        draw.centeredText(line, box.centerX(), textY, color, false);
    }

    public enum Style {
        NORMAL,
        PRIMARY,
        DESTRUCTIVE,
        QUIET
    }
}
