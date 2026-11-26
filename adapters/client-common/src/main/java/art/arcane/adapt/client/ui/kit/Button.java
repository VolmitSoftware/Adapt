package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A push button. Like vanilla buttons it fires on press, plays the click sound and reacts to Enter and Space. A glyph
 * button shows an ink arrow or stepper mark on a paper tab instead of text; a quiet button is ink over a dotted
 * underline until hovered. States follow {@link UiControl}.
 */
public class Button extends UiNode {
    /** The size of a text button's mark, one texel per pixel at GUI scale 2. */
    public static final int MARK = 8;
    private static final int MARK_GAP = 3;
    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
    private final UiTome.Glyph glyph;
    private String text;
    private String mark;
    private boolean markOnly;
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

    /** Draws the mark {@code spriteId} before the text, or none when null. */
    public Button setMark(String spriteId) {
        mark = spriteId;
        return this;
    }

    /**
     * A quiet square button that shows only the mark {@code spriteId}, its text kept as its name and tooltip; the text
     * shows instead when no resource pack provides the mark.
     */
    public static Button mark(String id, String text, String spriteId, Runnable action) {
        Button button = new Button(id, text, Style.QUIET, action).setMark(spriteId);
        button.markOnly = true;
        button.setTooltip(new Tooltip(text, List.of()));
        return button;
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
        if (markOnly && metrics.sprites().texture(mark) != null) {
            return theme.controlHeight();
        }
        return width(metrics, text, theme) + markRoom(metrics);
    }

    /** The width a button labelled {@code label} asks for. */
    public static int width(UiDraw metrics, String label, UiTheme theme) {
        return Math.max(theme.controlHeight(), metrics.textWidth(label) + UiControl.TEXT_INSET * 2);
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
        UiControl.State state = UiControl.State.of(active, frame.isPressed(this), lift);
        if (glyph != null || style == Style.QUIET) {
            paintPlain(frame, draw, theme, box, state);
            return;
        }
        String chrome = switch (style) {
            case PRIMARY -> UiChrome.BUTTON + "_primary";
            case DESTRUCTIVE -> UiChrome.BUTTON + "_destructive";
            case NORMAL, QUIET -> UiChrome.BUTTON;
        };
        String variant = switch (state) {
            case REST, DISABLED -> null;
            case HOVER -> "hover";
            case PRESSED -> "pressed";
        };
        if (state == UiControl.State.DISABLED) {
            UiControl.stitchedEdge(draw, box.x(), box.y(), box.width(), box.height(), theme.textDisabled());
        } else if (!UiChrome.paint(draw, chrome, variant, box)) {
            paintFlat(draw, theme, box, state, lift);
        }
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, box, theme);
        }
        String line = LegacyText.truncate(draw, text, box.width() - 4 - markRoom(draw));
        paintText(draw, box, line, UiControl.textY(box, state), UiControl.ink(state, theme));
    }

    /** The room the mark takes before the text, 0 without one or when no resource pack provides it. */
    private int markRoom(UiDraw draw) {
        return mark != null && draw.sprites().texture(mark) != null ? MARK + MARK_GAP : 0;
    }

    /** Draws {@code line} centred in {@code box} at {@code textY}, after the mark when it has one; returns the text's left. */
    private int paintText(UiDraw draw, UiRect box, String line, int textY, int color) {
        int markRoom = markRoom(draw);
        int left = box.centerX() - (markRoom + draw.textWidth(line)) / 2;
        if (markRoom > 0) {
            UiIcons.mark(draw, mark, left, box.y() + (box.height() - MARK) / 2, MARK);
        }
        draw.text(line, left + markRoom, textY, color, false);
        return left + markRoom;
    }

    private void paintFlat(UiDraw draw, UiTheme theme, UiRect box, UiControl.State state, float lift) {
        int tint = switch (style) {
            case PRIMARY -> theme.accent();
            case DESTRUCTIVE -> theme.destructive();
            case NORMAL, QUIET -> 0;
        };
        int base = tint == 0 ? theme.surface() : UiEase.mix(theme.surface(), tint, 0.18F);
        int raised = tint == 0 ? theme.surfaceHover() : UiEase.mix(theme.surface(), tint, 0.08F);
        int fill = state == UiControl.State.PRESSED ? UiEase.mix(base, theme.surfacePressed(), 0.6F) : UiEase.mix(base, raised, lift);
        int edge = tint == 0 ? theme.surfaceBorder() : tint;
        UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), fill);
        UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), edge);
        if (state == UiControl.State.HOVER) {
            draw.fill(box.x() + 1, box.bottom() - 2, box.right() - 1, box.bottom() - 1, edge);
        } else if (state == UiControl.State.PRESSED) {
            draw.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.y() + 2, edge);
        }
    }

    private void paintPlain(UiFrame frame, UiDraw draw, UiTheme theme, UiRect box, UiControl.State state) {
        if (markOnly && draw.sprites().texture(mark) != null) {
            if (state == UiControl.State.HOVER || state == UiControl.State.PRESSED) {
                UiControl.face(draw, box, state, theme);
            }
            if (frame.showsFocus(this)) {
                UiControl.focusRing(draw, box, theme);
            }
            UiIcons.mark(draw, mark, box.x() + (box.width() - MARK) / 2, box.y() + (box.height() - MARK) / 2, MARK);
            return;
        }
        boolean bare = glyph == null && (state == UiControl.State.REST || state == UiControl.State.DISABLED);
        if (!bare) {
            UiControl.face(draw, box, state, theme);
        }
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, box, theme);
        }
        if (glyph != null) {
            UiControl.glyph(draw, glyph, box, state, theme);
            return;
        }
        String line = LegacyText.truncate(draw, text, box.width() - 4 - markRoom(draw));
        int color = state == UiControl.State.REST ? theme.textMuted() : UiControl.ink(state, theme);
        int left = paintText(draw, box, line, UiControl.textY(box, state), color);
        if (state == UiControl.State.REST) {
            int width = draw.textWidth(line);
            for (int x = left; x < left + width; x += 2) {
                draw.fill(x, UiControl.textY(box) + UiControl.CAP_HEIGHT + 1, x + 1, UiControl.textY(box) + UiControl.CAP_HEIGHT + 2, color);
            }
        }
    }

    public enum Style {
        NORMAL,
        PRIMARY,
        DESTRUCTIVE,
        QUIET
    }
}
