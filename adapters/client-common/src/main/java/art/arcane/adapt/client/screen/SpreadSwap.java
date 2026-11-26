package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.IconSprites;

/**
 * The lead swap, a small button on the spine between the two parents. It sits on a paper plate with clipped corners
 * so it reads over any theme's spine art, and names itself in its tooltip.
 */
final class SpreadSwap extends Button {
    static final int WIDTH = 20;
    /** How far the plate reaches past the button on every side. */
    static final int PLATE = 2;
    private static final int CORNER = 3;
    private String name = "";

    SpreadSwap(String id, Runnable onSwap) {
        super(id, "", Style.NORMAL, onSwap);
        setMark(IconSprites.mark("swap"));
    }

    void setName(String text) {
        name = text;
        setTooltip(Tooltip.of(text));
    }

    @Override
    public String label() {
        return LegacyText.strip(name);
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        return WIDTH;
    }

    /** The button with its plate, the room it takes on the spine. */
    static UiRect plated(UiRect face) {
        return new UiRect(face.x() - PLATE, face.y() - PLATE, face.width() + PLATE * 2, face.height() + PLATE * 2);
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        draw.nextLayer();
        UiRect plate = plated(bounds());
        lozenge(draw, plate, theme.surfaceBorder());
        lozenge(draw, new UiRect(plate.x() + 1, plate.y() + 1, plate.width() - 2, plate.height() - 2), theme.surface());
        super.paint(frame);
    }

    /** Fills {@code box} with its corners cut on the diagonal, {@link #CORNER} units deep. */
    private static void lozenge(UiDraw draw, UiRect box, int color) {
        for (int row = 0; row < box.height(); row++) {
            int inset = Math.max(0, CORNER - Math.min(row, box.height() - 1 - row));
            draw.fill(box.x() + inset, box.y() + row, box.right() - inset, box.y() + row + 1, color);
        }
    }
}
