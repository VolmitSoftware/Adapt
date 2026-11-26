package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.UiChrome;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.IconSprites;

import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The foot of the mutation spread, drawn across both pages: the hold control, centred on the fold, its two halves
 * filling from the outer ends toward the spine, each in its parent's colour, and meeting there. Under its label it
 * says that a mutation cannot be undone.
 */
final class SpreadSeal extends UiNode {
    private static final int HOLD_WIDTH = 132;
    /** Room either side of the longer line inside the hold. */
    private static final int HOLD_INSET = 8;

    private final Hold hold;
    private int fold;

    SpreadSeal(String id, Hold hold) {
        super(id);
        this.hold = add(hold);
    }

    Hold hold() {
        return hold;
    }

    void place(UiRect box, int foldX, UiDraw metrics) {
        fold = foldX;
        place(box, metrics);
    }

    @Override
    public String kind() {
        return "seal";
    }

    static int height(UiTheme theme) {
        return Hold.height(theme);
    }

    @Override
    protected void paint(UiFrame frame) {
        frame.draw().nextLayer();
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiRect box = bounds();
        int widest = Math.max(HOLD_WIDTH, hold.textWidth(metrics) + HOLD_INSET * 2);
        int holdWidth = Math.min(widest, box.width());
        int holdHeight = Math.min(box.height(), Hold.height(services().theme()));
        int holdX = Math.max(box.x(), Math.min(box.right() - holdWidth, fold - holdWidth / 2));
        hold.place(new UiRect(holdX, box.bottom() - holdHeight, holdWidth, holdHeight), metrics);
    }

    /**
     * Press and hold to confirm: the fill grows from both ends while held and drains back when let go early. The
     * warning sits under the label in the destructive ink and stays through the hold.
     */
    static final class Hold extends UiNode {
        private static final String MARK = IconSprites.mark("confirm");
        private static final int MARK_GAP = 3;
        /** Space between the label's capitals and the warning's. */
        private static final int LINE_GAP = 3;
        private final HoldToConfirm gesture;
        private final LongSupplier clock;
        private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
        private String text = "";
        private String warning = "";
        private int leadColor;
        private int partnerColor;
        private boolean byMouse;

        Hold(String id, HoldToConfirm gesture, LongSupplier clock) {
            super(id);
            this.gesture = gesture;
            this.clock = clock;
        }

        void set(String label, int lead, int partner) {
            text = label;
            leadColor = lead;
            partnerColor = partner;
        }

        /** Shows {@code permanent} under the label, or nothing there when it is empty. */
        void setWarning(String permanent) {
            warning = permanent;
        }

        static int height(UiTheme theme) {
            return theme.controlHeight() + UiControl.CAP_HEIGHT + LINE_GAP;
        }

        /** The width of the longer of the label, with its mark, and the warning. */
        int textWidth(UiDraw metrics) {
            return Math.max(Button.MARK + MARK_GAP + metrics.textWidth(text), metrics.textWidth(warning));
        }

        /** Whether the hold in progress was started with the mouse, which must stay over the control. */
        boolean byMouse() {
            return byMouse;
        }

        @Override
        public String kind() {
            return "hold";
        }

        @Override
        public String label() {
            return text;
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("warning", warning);
            state.put("holding", Boolean.toString(gesture.holding()));
            state.put("fill", String.format(Locale.ROOT, "%.2f", gesture.shown(clock.getAsLong())));
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
        public boolean mousePressed(UiMouseEvent event) {
            byMouse = true;
            start();
            return true;
        }

        @Override
        public boolean mouseReleased(UiMouseEvent event) {
            gesture.release(clock.getAsLong());
            return true;
        }

        @Override
        public boolean keyPressed(UiKeyEvent event) {
            if (event.key() != UiKey.ENTER && event.key() != UiKey.SPACE) {
                return false;
            }
            if (!gesture.holding()) {
                byMouse = false;
                start();
            }
            return true;
        }

        private void start() {
            if (!enabled() || gesture.fired()) {
                return;
            }
            gesture.press(clock.getAsLong());
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            boolean active = enabled();
            float lift = hover.update(frame.motion(), active && (frame.isHovered(this) || frame.showsFocus(this)));
            UiControl.State state = UiControl.State.of(active, gesture.holding(), lift);
            if (!active) {
                UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), theme.backdrop());
                UiControl.stitchedEdge(draw, box.x(), box.y(), box.width(), box.height(), theme.textDisabled());
            } else if (!UiChrome.paint(draw, UiChrome.BUTTON + "_primary", state == UiControl.State.HOVER ? "hover" : null, box)) {
                UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), theme.surface());
                UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), theme.accent());
            }
            float fill = gesture.shown(clock.getAsLong());
            int reach = 0;
            if (active && fill > 0.0F) {
                int half = (box.width() - 4) / 2;
                reach = Math.round(half * fill);
                int top = box.y() + 2;
                int bottom = box.bottom() - 2;
                draw.fill(box.x() + 2, top, box.x() + 2 + reach, bottom, (0xB0 << 24) | (leadColor & 0xFFFFFF));
                draw.fill(box.right() - 2 - reach, top, box.right() - 2, bottom, (0xB0 << 24) | (partnerColor & 0xFFFFFF));
                if (reach > 0 && reach < half) {
                    draw.fill(box.x() + 1 + reach, top, box.x() + 2 + reach, bottom, UiTome.GILT);
                    draw.fill(box.right() - 2 - reach, top, box.right() - 1 - reach, bottom, UiTome.GILT);
                }
            }
            if (frame.showsFocus(this)) {
                UiControl.focusRing(draw, box, theme);
            }
            boolean marked = draw.sprites().texture(MARK) != null;
            int markRoom = marked ? Button.MARK + MARK_GAP : 0;
            String line = LegacyText.truncate(draw, text, box.width() - 6 - markRoom);
            int ink = !active ? theme.textDisabled() : UiControl.ink(state, theme);
            boolean warned = !warning.isEmpty();
            int labelY = UiControl.textY(box.y(), box.height() - (warned ? UiControl.CAP_HEIGHT + LINE_GAP : 0))
                    + (state == UiControl.State.PRESSED ? UiControl.SINK : 0);
            int left = box.centerX() - (markRoom + draw.textWidth(line)) / 2;
            if (marked) {
                UiIcons.mark(draw, MARK, left, labelY + (UiControl.CAP_HEIGHT - Button.MARK) / 2, Button.MARK);
            }
            inked(draw, line, left + markRoom, labelY, ink, reach);
            if (warned) {
                String note = LegacyText.truncate(draw, warning, box.width() - 6);
                inked(draw, note, box.centerX() - draw.textWidth(note) / 2, labelY + UiControl.CAP_HEIGHT + LINE_GAP,
                        active ? theme.destructive() : theme.textDisabled(), reach);
            }
        }

        /** Draws {@code line} in {@code ink}, white with a shadow where the fill, {@code reach} in from each end, lies under it. */
        private void inked(UiDraw draw, String line, int x, int y, int ink, int reach) {
            draw.text(line, x, y, ink, false);
            if (reach <= 0) {
                return;
            }
            UiRect box = bounds();
            draw.pushScissor(box.x() + 2, box.y(), box.x() + 2 + reach, box.bottom());
            draw.text(line, x, y, 0xFFFFFFFF, true);
            draw.popScissor();
            draw.pushScissor(box.right() - 2 - reach, box.y(), box.right() - 2, box.bottom());
            draw.text(line, x, y, 0xFFFFFFFF, true);
            draw.popScissor();
        }
    }
}
