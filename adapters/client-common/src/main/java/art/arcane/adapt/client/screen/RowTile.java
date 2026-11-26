package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.TileFrame;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.LegacyText;
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

import java.util.Objects;

/**
 * A tile of the skill and adaptation grids, bare on the page and framed under the pointer or keyboard focus, in gold
 * filigree or, when its skill has a page theme, in that skill's own hover frame: the icon at the left, then the name with a number at its right, and on the line below a short value led by a
 * small ink glyph with a small framed bar after it where the line has room for one. A tile too narrow for the number beside the name puts it at the end
 * of the line below. The grid gives the tile room for all of its text ({@link #naturalWidth}); only a screen too
 * narrow for that cuts it. Clicking or Enter opens its page.
 */
abstract class RowTile extends UiNode {
    static final int FIRST_LINE = 3;
    static final int BAR_HEIGHT = 5;
    static final int SECOND_LINE = 13;
    /** The least width the bar on the second line keeps. */
    static final int BAR_LEAST = 20;
    private static final int ICON_LEFT = 3;
    private static final int TEXT_GAP = 4;
    private static final int RIGHT_PAD = 3;
    private static final int GLYPH_GAP = 3;

    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
    private final TileFrame themeFrame = new TileFrame();
    private final Runnable open;
    private TileTheming theming;
    private String themeSkill;
    private boolean frameWanted;

    RowTile(String id, Runnable open) {
        super(id);
        this.open = Objects.requireNonNull(open, "open");
    }

    abstract IconRef icon();

    /** Frames the tile in {@code skillId}'s theme on hover, as {@code tileTheming} finds it; null keeps the filigree. */
    void theme(TileTheming tileTheming, String skillId) {
        theming = tileTheming;
        themeSkill = skillId;
    }

    /** The theme the tile is framed in, or null for the plain filigree. */
    PageTheme theme(UiDraw draw) {
        return theming == null ? null : theming.theme(draw.sprites(), themeSkill);
    }

    TileTheming theming() {
        return theming;
    }

    /** Draws the tile's themed hover frame, advanced to the themes' clock. */
    void paintFrame(UiDraw draw) {
        PageTheme skillTheme = theme(draw);
        if (skillTheme != null) {
            long millis = theming.millis();
            themeFrame.paint(draw, skillTheme, bounds(), frameWanted, millis, millis / 1000.0, theming.still());
        }
    }

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
        return SECOND_LINE + 1;
    }

    /** Paints the bar inside {@code left..right} from {@code top} down; nothing by default. */
    void paintBar(UiDraw draw, UiTheme theme, int left, int top, int right) {
    }

    /** The line under the bar, or empty for none. */
    abstract String second();

    abstract int secondColor(UiTheme theme);

    /** The ink glyph before the line under the bar, drawn in the line's colour, or null for none. */
    UiTome.Glyph secondGlyph() {
        return null;
    }

    /** Room kept clear at the right end of the line below the name, for a control of the tile's own; none by default. */
    int trailing(UiDraw metrics) {
        return 0;
    }

    private int glyphWidth() {
        UiTome.Glyph glyph = secondGlyph();
        return glyph == null || second().isEmpty() ? 0 : glyph.width() + GLYPH_GAP;
    }

    /** Draws the icon in {@code icon}; {@code backdrop} is the opaque colour under it. */
    void paintIcon(UiDraw draw, UiTheme theme, UiRect icon, int backdrop) {
        UiIcons.paintOn(draw, icon(), icon.x(), icon.y(), UiIcons.SIZE, glint(), backdrop);
    }

    /** Whether the icon shows dimmed behind a lock. */
    abstract boolean locked();

    boolean glint() {
        return false;
    }

    /** Where the icon sits. */
    UiRect iconBounds() {
        UiRect box = bounds();
        return new UiRect(box.x() + ICON_LEFT, box.y() + (box.height() - UiIcons.SIZE) / 2, UiIcons.SIZE, UiIcons.SIZE);
    }

    /**
     * The width this tile needs to show all of its text on its two lines, with the number after the name or, when that
     * is narrower, after the line below.
     */
    final int naturalWidth(UiDraw metrics) {
        int number = numberRoom(metrics);
        int name = metrics.textWidth(label());
        int second = secondWidth(metrics);
        int text = Math.min(Math.max(name + number, second), Math.max(name, second + number));
        return ICON_LEFT + UiIcons.SIZE + TEXT_GAP + text + RIGHT_PAD;
    }

    private int numberRoom(UiDraw metrics) {
        int number = metrics.textWidth(number());
        return number > 0 ? number + TEXT_GAP : 0;
    }

    private int secondWidth(UiDraw metrics) {
        int trail = trailing(metrics);
        return glyphWidth() + metrics.textWidth(second()) + (trail > 0 ? trail + TEXT_GAP : 0);
    }

    /** Whether the number shares the name's line in text {@code room} wide, rather than the line below. */
    private boolean numberBesideName(int room, UiDraw metrics) {
        int number = numberRoom(metrics);
        return metrics.textWidth(label()) + number <= room || secondWidth(metrics) + number > room;
    }

    /** Where the text goes: right of the icon to the tile's right padding. */
    UiRect textBounds() {
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
        return TileGrid.MIN_WIDTH;
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
        boolean wanted = frame.isHovered(this) || frame.showsFocus(this) || holdsPointer(frame);
        float lift = hover.update(frame.motion(), wanted);
        PageTheme skillTheme = theme(draw);
        if (skillTheme != null) {
            frameWanted = wanted;
            theming.defer(this);
        } else {
            UiTome.filigree(draw, box, frame.showsFocus(this) ? 1.0F : lift);
        }
        paintCell(draw, theme, theme.backdrop());
    }

    /** Whether the pointer or focus is on one of the tile's own controls, which keeps the tile framed. */
    private boolean holdsPointer(UiFrame frame) {
        for (UiNode child : children()) {
            if (child.visible() && (frame.isHovered(child) || frame.showsFocus(child))) {
                return true;
            }
        }
        return false;
    }

    /** Paints the tile's icon, text and bar; {@code backdrop} is the opaque colour under the icon. */
    void paintCell(UiDraw draw, UiTheme theme, int backdrop) {
        UiRect box = bounds();
        paintIcon(draw, theme, iconBounds(), backdrop);
        UiRect text = textBounds();
        String number = number();
        int numberWidth = draw.textWidth(number);
        int nameY = box.y() + nameTop();
        boolean beside = numberBesideName(text.width(), draw);
        int trail = trailing(draw);
        int lineEnd = text.right() - (trail > 0 ? trail + TEXT_GAP : 0);
        if (numberWidth > 0) {
            draw.text(number, (beside ? text.right() : lineEnd) - numberWidth, beside ? nameY : box.y() + SECOND_LINE, numberColor(theme), false);
        }
        int numberRoom = numberWidth > 0 ? numberWidth + TEXT_GAP : 0;
        int nameRoom = Math.max(0, text.width() - (beside ? numberRoom : 0));
        draw.text(LegacyText.truncate(draw, label(), nameRoom), text.x(), nameY, nameColor(theme), false);
        String second = second();
        int barLeft = text.x();
        if (!second.isEmpty()) {
            int lead = glyphWidth();
            int color = secondColor(theme);
            if (lead > 0) {
                UiTome.Glyph glyph = secondGlyph();
                UiTome.glyph(draw, glyph, text.x(), box.y() + SECOND_LINE + UiControl.CAP_HEIGHT - glyph.height(), color);
            }
            draw.text(second, text.x() + lead, box.y() + SECOND_LINE, color, false);
            barLeft = text.x() + lead + draw.textWidth(second) + TEXT_GAP;
        }
        int barRight = lineEnd - (beside ? 0 : numberRoom);
        if (barRight - barLeft >= BAR_LEAST / 2) {
            paintBar(draw, theme, barLeft, box.y() + barTop(), barRight);
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
