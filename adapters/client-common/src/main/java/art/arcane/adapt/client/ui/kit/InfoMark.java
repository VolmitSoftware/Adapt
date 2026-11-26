package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.client.view.IconSprites;

import java.util.Objects;

/**
 * A small (i) that opens more about what it sits beside: a click, Enter or Space runs its action. The drawn info mark,
 * faded at rest and full under the pointer or focus; an inked glyph, muted then accented, when no resource pack has the
 * mark. The focus ring sits outside its box.
 */
public class InfoMark extends UiNode {
    /** The square it takes, room enough for the glyph and a fingertip. */
    public static final int SIZE = 9;
    private static final String MARK = IconSprites.mark("info");
    private static final int REST_TINT = 0xB4FFFFFF;

    private final String name;
    private final Runnable action;
    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);

    public InfoMark(String id, String name, Runnable action) {
        super(id);
        this.name = Objects.requireNonNull(name, "name");
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public String kind() {
        return "info";
    }

    @Override
    public String label() {
        return name;
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
        return SIZE;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return SIZE;
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        if (event.button() != UiMouseButton.LEFT) {
            return false;
        }
        return activate();
    }

    @Override
    public boolean activate() {
        services().playClick();
        action.run();
        return true;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
        UiTexture texture = draw.sprites().texture(MARK);
        if (texture != null) {
            UiRect box = bounds();
            int size = Button.MARK;
            draw.blit(texture, box.x() + (box.width() - size) / 2, box.y() + (box.height() - size) / 2, size, size, 0, 0,
                    texture.width(), texture.height(), lift >= 0.5F ? 0xFFFFFFFF : REST_TINT);
        } else {
            UiTome.glyph(draw, UiTome.Glyph.INFO, bounds(), lift >= 0.5F ? theme.accent() : theme.textMuted());
        }
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, bounds(), theme);
        }
    }
}
