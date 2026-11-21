package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.IconRef;

import java.util.List;
import java.util.Objects;

/**
 * A row tile of the skill and adaptation grids, {@link TileGrid#HEIGHT} tall, bare on the page and washed warm under
 * the pointer: the icon at the left, then the name with a number at its right and a thin bar or a row of pips under
 * it. A tile may add one short line below, led by a small ink glyph; one without that line centres its name and bar
 * on the icon. The grid gives the tile room for all of its text ({@link #naturalWidth}); only a screen too narrow for
 * that cuts it. Clicking or Enter opens its page.
 */
abstract class RowTile extends UiNode {
    static final int FIRST_LINE = 2;
    static final int BAR_HEIGHT = 2;
    static final int SECOND_LINE = 14;
    private static final int ICON_LEFT = 3;
    private static final int BAR_TOP = 11;
    private static final int TEXT_GAP = 4;
    private static final int RIGHT_PAD = 3;
    private static final int GLYPH_GAP = 3;

    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
    private final Runnable open;

    RowTile(String id, Runnable open) {
        super(id);
        this.open = Objects.requireNonNull(open, "open");
    }

    abstract IconRef icon();

    abstract int nameColor(UiTheme theme);

    /** The number at the right of the name, or empty for none. */
    abstract String number();

    abstract int numberColor(UiTheme theme);

    /** Where the name's line starts below the tile's top. */
    int nameTop() {
        return FIRST_LINE;
    }

    /** Where the bar starts below the tile's top. */
    int barTop() {
        return BAR_TOP;
    }

    /** Paints the bar inside {@code left..right} from {@code top} down. */
    abstract void paintBar(UiDraw draw, UiTheme theme, int left, int top, int right);

    /** The line under the bar, or empty for none. */
    abstract String second();

    abstract int secondColor(UiTheme theme);

    /** The ink glyph before the line under the bar, drawn in the line's colour, or null for none. */
    UiTome.Glyph secondGlyph() {
        return null;
    }

    private int glyphWidth() {
        UiTome.Glyph glyph = secondGlyph();
        return glyph == null || second().isEmpty() ? 0 : glyph.width() + GLYPH_GAP;
    }

    /** Draws the icon in {@code icon}; {@code backdrop} is the opaque colour under it. */
    void paintIcon(UiDraw draw, UiTheme theme, UiRect icon, int backdrop) {
        UiIcons.paintOn(draw, icon(), icon.x(), icon.y(), UiIcons.SIZE, glint(), backdrop);
    }

    /** The ink underline under the pointer, the tile's accent. */
    abstract int accent(UiTheme theme);

    /** Whether the icon shows dimmed behind a lock. */
    abstract boolean locked();

    boolean glint() {
        return false;
    }

    /** Where the icon sits. */
    final UiRect iconBounds() {
        UiRect box = bounds();
        return new UiRect(box.x() + ICON_LEFT, box.y() + (box.height() - UiIcons.SIZE) / 2, UiIcons.SIZE, UiIcons.SIZE);
    }

    /** The width this tile needs to show all of its text on its two lines. */
    final int naturalWidth(UiDraw metrics) {
        int number = metrics.textWidth(number());
        int first = metrics.textWidth(label()) + (number > 0 ? number + TEXT_GAP : 0);
        int second = glyphWidth() + metrics.textWidth(second());
        return ICON_LEFT + UiIcons.SIZE + TEXT_GAP + Math.max(first, second) + RIGHT_PAD;
    }

    /** The widest {@link #naturalWidth} of {@code tiles}. */
    static int need(List<? extends RowTile> tiles, UiDraw metrics) {
        int need = 0;
        for (RowTile tile : tiles) {
            need = Math.max(need, tile.naturalWidth(metrics));
        }
        return need;
    }

    /** Where the text goes: right of the icon to the tile's right padding. */
    final UiRect textBounds() {
        UiRect box = bounds();
        int left = box.x() + ICON_LEFT + UiIcons.SIZE + TEXT_GAP;
        return UiRect.of(left, box.y(), Math.max(left, box.right() - RIGHT_PAD), box.bottom());
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
        return TileGrid.WIDTH;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return TileGrid.HEIGHT;
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
        open.run();
        return true;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
        int well = UiEase.fade(UiTome.HOVER, lift);
        if (lift > 0.0F) {
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), well);
        }
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, box.inset(1), theme);
        }
        paintIcon(draw, theme, iconBounds(), UiContrast.over(well, theme.backdrop()));
        UiRect text = textBounds();
        String number = number();
        int numberWidth = draw.textWidth(number);
        int nameY = box.y() + nameTop();
        if (numberWidth > 0) {
            draw.text(number, text.right() - numberWidth, nameY, numberColor(theme), false);
        }
        int nameRoom = Math.max(0, text.width() - (numberWidth > 0 ? numberWidth + TEXT_GAP : 0));
        draw.text(LegacyText.truncate(draw, label(), nameRoom), text.x(), nameY, nameColor(theme), false);
        paintBar(draw, theme, text.x(), box.y() + barTop(), text.right());
        if (lift > 0.0F) {
            draw.fill(text.x(), box.bottom() - 1, text.right(), box.bottom(), UiEase.fade(accent(theme), lift));
        }
        String second = second();
        if (!second.isEmpty()) {
            int lead = glyphWidth();
            int color = secondColor(theme);
            if (lead > 0) {
                UiTome.Glyph glyph = secondGlyph();
                UiTome.glyph(draw, glyph, text.x(), box.y() + SECOND_LINE + UiControl.CAP_HEIGHT - glyph.height(), color);
            }
            draw.text(LegacyText.truncate(draw, second, Math.max(0, text.width() - lead)), text.x() + lead, box.y() + SECOND_LINE, color, false);
        }
    }

    @Override
    protected void paintOver(UiFrame frame) {
        if (!locked()) {
            return;
        }
        UiDraw draw = frame.draw();
        UiRect icon = iconBounds();
        draw.pushPose();
        draw.depth(200.0F);
        draw.fill(icon.x(), icon.y(), icon.right(), icon.bottom(), UiEase.fade(frame.theme().panel(), 0.6F));
        UiTome.lock(draw, icon.right() - 6, icon.y() + 1);
        draw.popPose();
    }
}
