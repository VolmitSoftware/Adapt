package art.arcane.adapt.client.icons;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.KitScreen;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Panel;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiMotion;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/** Lists every {@link IconSheet.Entry} with its icon as drawn now and whether it came from a sprite or the item. */
public final class IconSheetScreen extends KitScreen {
    private static final int MARGIN = 8;
    private static final int MAX_WIDTH = 460;
    private static final int SPRITE_COLOR = 0xFF2E5619;
    private static final int INVALID_COLOR = 0xFF8D261D;

    private final Panel panel;
    private final Rows rows;

    public IconSheetScreen(List<IconSheet.Entry> entries, boolean sample, UiTheme theme, UiMotion motion) {
        super("Adapt icons", theme, motion);
        rows = new Rows(entries, sample);
        panel = root().add(new Panel("sheet", sample ? "Adapt icons (sample catalog)" : "Adapt icons"));
        panel.setLayout(UiLayoutPolicy.fill());
        panel.add(new ScrollArea("rows", rows));
    }

    @Override
    protected String probeKind() {
        return "adapt-icons";
    }

    @Override
    protected void describe(Map<String, String> state) {
        rows.describeCounts(state);
    }

    @Override
    protected void layoutScreen(UiRect screen, UiDraw metrics) {
        int width = Math.min(MAX_WIDTH, screen.width() - MARGIN * 2);
        panel.place(UiLayout.center(screen, width, screen.height() - MARGIN * 2), metrics);
    }

    private static final class Rows extends UiNode {
        private static final int ROW = 20;
        private static final int KIND_WIDTH = 64;

        private final List<IconSheet.Entry> entries;
        private final boolean sample;
        private final Map<IconSheet.Kind, Integer> counts = new EnumMap<>(IconSheet.Kind.class);
        private String sprites = "";

        Rows(List<IconSheet.Entry> entries, boolean sample) {
            super("icons");
            this.entries = List.copyOf(entries);
            this.sample = sample;
        }

        @Override
        public String kind() {
            return "icon-sheet";
        }

        void describeCounts(Map<String, String> state) {
            for (IconSheet.Kind kind : IconSheet.Kind.values()) {
                state.put(kind.name().toLowerCase(Locale.ROOT), Integer.toString(counts.getOrDefault(kind, 0)));
            }
            state.put("sprites", sprites);
            state.put("sample", Boolean.toString(sample));
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return header(metrics) + entries.size() * ROW;
        }

        private static int header(UiDraw metrics) {
            return metrics.lineHeight() * 2 + 8;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            UiRect visible = parent() == null ? box : parent().bounds();
            Map<IconSheet.Kind, Integer> tally = new EnumMap<>(IconSheet.Kind.class);
            StringJoiner withSprites = new StringJoiner(",");
            int top = box.y() + header(draw);
            for (int index = 0; index < entries.size(); index++) {
                IconSheet.Entry entry = entries.get(index);
                IconSheet.Source source = IconSheet.source(draw.sprites(), entry);
                tally.merge(source.kind(), 1, Integer::sum);
                if (source.kind() == IconSheet.Kind.SPRITE) {
                    withSprites.add(entry.kind() + ':' + entry.id());
                }
                int y = top + index * ROW;
                if (y + ROW < visible.y() || y > visible.bottom()) {
                    continue;
                }
                paintRow(draw, theme, box, y, entry, source);
            }
            counts.clear();
            counts.putAll(tally);
            sprites = withSprites.toString();
            String summary = tally.getOrDefault(IconSheet.Kind.SPRITE, 0) + " sprite  " + tally.getOrDefault(IconSheet.Kind.ITEM, 0)
                    + " item  " + tally.getOrDefault(IconSheet.Kind.INVALID, 0) + " invalid";
            draw.text(summary, box.x() + 2, box.y() + 2, theme.text(), true);
            draw.text(LegacyText.truncate(draw, "Art: assets/adapt/textures/gui/<kind>/<id>.png", box.width() - 4), box.x() + 2,
                    box.y() + 4 + draw.lineHeight(), theme.textMuted(), true);
        }

        private static void paintRow(UiDraw draw, UiTheme theme, UiRect box, int y, IconSheet.Entry entry, IconSheet.Source source) {
            draw.fill(box.x(), y + ROW - 1, box.right(), y + ROW, theme.tileBorder());
            UiIcons.paint(draw, entry.icon(), box.x() + 2, y + 2, UiIcons.SIZE, false);
            int textY = y + (ROW - draw.lineHeight()) / 2 + 1;
            int left = box.x() + UiIcons.SIZE + 8;
            draw.text(entry.kind(), left, textY, theme.textMuted(), true);
            String status = switch (source.kind()) {
                case SPRITE -> "sprite " + source.detail();
                case ITEM -> "item";
                case INVALID -> "invalid: " + source.detail();
            };
            int color = switch (source.kind()) {
                case SPRITE -> SPRITE_COLOR;
                case ITEM -> theme.textMuted();
                case INVALID -> INVALID_COLOR;
            };
            String fitted = LegacyText.truncate(draw, status, Math.max(0, box.width() / 2 - 8));
            int statusX = box.right() - 4 - draw.textWidth(fitted);
            draw.text(fitted, statusX, textY, color, true);
            int idLeft = left + KIND_WIDTH;
            draw.text(LegacyText.truncate(draw, entry.id(), Math.max(0, statusX - idLeft - 6)), idLeft, textY, theme.text(), true);
        }
    }
}
