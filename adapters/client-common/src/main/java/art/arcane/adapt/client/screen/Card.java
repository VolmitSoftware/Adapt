package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.List;

/**
 * The one spacing system of the book's cards, the skills page's seats and a skill page's adaptations alike: {@link #PAD}
 * inside every edge, the icon in the top left corner, two short lines beside it whose capitals line up with the
 * icon's top and bottom, and under that row, each {@link #PAD} below the part above, the parts that span the card (a
 * bar, the name on up to two lines) at the icon's left edge and the padding's right edge. Cards sit {@link #GAP} apart
 * across and down.
 */
final class Card {
    /** Space inside each edge of a card, and between the parts stacked in it. */
    static final int PAD = 3;
    /** Space between two cards, across and down. */
    static final int GAP = 4;
    /** Where the lines beside the icon start. */
    static final int TEXT_LEFT = PAD + UiIcons.SIZE + PAD;
    /** The top of the line beside the icon whose capitals start at the icon's top. */
    static final int UPPER_LINE = PAD;
    /** The top of the line beside the icon whose capitals end at the icon's bottom. */
    static final int LOWER_LINE = PAD + UiIcons.SIZE - UiControl.CAP_HEIGHT;
    /** The bottom of the icon's row. */
    static final int ROW_BOTTOM = PAD + UiIcons.SIZE;
    /** Distance from one line of a name to the next. */
    static final int NAME_PITCH = 9;
    /** The most lines a name takes. */
    static final int NAME_LINES = 2;
    /** The height two name lines take to the second line's baseline; a descender may dip into the padding below. */
    static final int NAME_HEIGHT = NAME_PITCH + UiControl.CAP_HEIGHT;

    private Card() {
    }

    /** The icon's square inside a card at {@code box}. */
    static UiRect icon(UiRect box) {
        return new UiRect(box.x() + PAD, box.y() + PAD, UiIcons.SIZE, UiIcons.SIZE);
    }

    /** The width the parts spanning a card {@code width} wide have between its paddings. */
    static int inner(int width) {
        return Math.max(1, width - 2 * PAD);
    }

    /** The height of a card with the icon's row and a name under it, after {@code middle} more height between them. */
    static int height(int middle) {
        return ROW_BOTTOM + PAD + middle + NAME_HEIGHT + PAD;
    }

    /** Draws {@code name} on up to two lines from {@code top}, at the card's padding, in {@code color}. */
    static void name(UiDraw draw, UiRect box, String name, int top, int color) {
        int y = top;
        for (String line : nameLines(draw, name, box.width())) {
            draw.text(line, box.x() + PAD, y, color, false);
            y += NAME_PITCH;
        }
    }

    static List<String> nameLines(UiDraw metrics, String name, int width) {
        return NameLines.fit(metrics, name, inner(width), NAME_LINES);
    }
}
