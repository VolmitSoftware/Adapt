package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileAction;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Panel;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.platform.menu.ClickKind;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** The native panel's frame, title bar, page label and footer chips. */
final class PanelChrome {
    private PanelChrome() {
    }

    /** The name of the tile behind a chrome {@code target}, or {@code fallback} when it has none. */
    static String tooltip(PanelView view, Map<String, Integer> chromeSlots, String target, String fallback) {
        Integer slot = chromeSlots.get(target);
        if (slot == null) {
            return fallback;
        }
        for (PanelTile tile : view.tiles()) {
            if (tile.slot() == slot && !LegacyText.isBlank(tile.name())) {
                return tile.name();
            }
        }
        return fallback;
    }

    /** The panel surface with ruled dividers under the title bar and above the footer. */
    static final class Frame extends Panel {
        private int divider = Integer.MIN_VALUE;
        private int footerDivider = Integer.MIN_VALUE;

        Frame() {
            super("panel", null);
            setLayout((List<UiNode> children, UiRect area, UiDraw metrics) -> {
            });
        }

        /** Top rows of the ruled dividers under the title bar and above the footer; {@link Integer#MIN_VALUE} leaves one out. */
        void dividers(int title, int footer) {
            divider = title;
            footerDivider = footer;
        }

        @Override
        protected void paint(UiFrame frame) {
            super.paint(frame);
            UiRect box = bounds();
            for (int line : new int[] {divider, footerDivider}) {
                if (line != Integer.MIN_VALUE) {
                    UiTome.divider(frame.draw(), box.x() + frame.theme().frame(), box.right() - frame.theme().frame(), line);
                }
            }
        }
    }

    /** Breadcrumb and title on the title ribbon, the breadcrumb muted and shortened from the left when space runs out. */
    static final class TitleBar extends UiNode {
        private static final String SEPARATOR = " / ";
        private static final int RIBBON_PAD = 4;
        private final Supplier<PanelView> view;

        TitleBar(Supplier<PanelView> view) {
            super("title");
            this.view = view;
        }

        @Override
        public String kind() {
            return "title";
        }

        @Override
        public String label() {
            return LegacyText.strip(view.get().title());
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("breadcrumb", String.join(SEPARATOR, view.get().breadcrumb()));
        }

        /** The width the ribbon needs for the whole breadcrumb and title. */
        int naturalWidth(UiDraw metrics) {
            PanelView current = view.get();
            String trail = current.breadcrumb().isEmpty() ? "" : String.join(SEPARATOR, current.breadcrumb()) + SEPARATOR;
            return metrics.textWidth(trail + LegacyText.strip(current.title())) + RIBBON_PAD * 4;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            PanelView current = view.get();
            int y = UiControl.textY(box);
            String title = LegacyText.strip(current.title());
            int titleWidth = draw.textWidth(title);
            List<String> crumbs = current.breadcrumb();
            int room = box.width() - titleWidth - draw.textWidth(SEPARATOR) - RIBBON_PAD * 3;
            String trail = crumbs.isEmpty() || room <= draw.textWidth(LegacyText.ELLIPSIS) + 8 ? ""
                    : LegacyText.truncate(draw, String.join(SEPARATOR, crumbs), room) + SEPARATOR;
            int x = box.x() + RIBBON_PAD;
            String shownTitle = LegacyText.truncate(draw, title, Math.max(0, box.right() - x - draw.textWidth(trail) - RIBBON_PAD * 2));
            int ribbonRight = Math.min(box.right(), x + draw.textWidth(trail) + draw.textWidth(shownTitle) + RIBBON_PAD * 2);
            UiTome.ribbon(draw, UiRect.of(box.x(), box.y(), ribbonRight, box.bottom()), theme);
            if (!trail.isEmpty()) {
                draw.text(trail, x, y, UiEase.mix(theme.textInverse(), theme.ribbon(), 0.25F), true);
                x += draw.textWidth(trail);
            }
            draw.text(shownTitle, x, y, UiTome.GILT, true);
        }
    }

    /** The page number between the page buttons. */
    static final class PageLabel extends UiNode {
        private String text = "";

        PageLabel() {
            super("page.label");
        }

        void setText(String text) {
            this.text = text;
        }

        @Override
        public String kind() {
            return "label";
        }

        @Override
        public String label() {
            return LegacyText.strip(text);
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            draw.centeredText(text, box.centerX(), UiControl.textY(box), frame.theme().textMuted(), false);
        }
    }

    /** A summary tile shown small in the footer, its lore in the tooltip; clicking it sends a left click when it has one. */
    static final class InfoChip extends UiNode {
        private final int slot;
        private final PanelControls.Sink sink;
        private PanelTile data;

        InfoChip(int slot, PanelControls.Sink sink) {
            super("info:" + slot);
            this.slot = slot;
            this.sink = sink;
        }

        void update(PanelTile tile, UiHost host) {
            data = tile;
            setTooltip(PanelRow.tooltip(tile, PanelRow.alternates(tile), host));
        }

        @Override
        public String kind() {
            return "info";
        }

        @Override
        public String label() {
            return LegacyText.strip(data.name());
        }

        @Override
        public boolean hittable() {
            return true;
        }

        @Override
        public boolean mousePressed(UiMouseEvent event) {
            ClickKind kind = PanelClicks.mouse(event.button(), event.modifiers());
            for (TileAction action : data.actions()) {
                if (action.click() == kind) {
                    services().playClick();
                    sink.click(slot, kind);
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            if (frame.isHovered(this)) {
                UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), UiTome.HOVER);
            }
            UiIcons.paintOn(draw, data.icon(), box.x() + (box.width() - UiIcons.SIZE) / 2, box.y() + (box.height() - UiIcons.SIZE) / 2,
                    UiIcons.SIZE, data.glint(), frame.theme().backdrop());
        }
    }
}
