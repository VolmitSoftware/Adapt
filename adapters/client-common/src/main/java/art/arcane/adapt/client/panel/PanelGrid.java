package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.kit.IconTile;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The body of a GRID panel: tiles at their chest slot positions on the nine-column grid, background filler left out,
 * with each tile's name under it, wrapped onto more lines when long, when the screen has room for labels.
 */
final class PanelGrid extends UiNode {
    static final int MIN_LABELLED_CELL = 40;
    static final int MAX_LABELLED_CELL = 56;
    static final int MAX_LABEL_LINES = 2;
    private static final String HYPHEN = "-";

    private final Map<Integer, SlotTile> tiles = new TreeMap<>();
    private final IconTile.Listener listener;
    private int rows = 1;
    private int tile = NativePanelScreen.MIN_TILE;
    private int cellWidth = NativePanelScreen.MIN_TILE;
    private int labelHeight;

    PanelGrid(IconTile.Listener listener) {
        super("grid");
        this.listener = listener;
    }

    @Override
    public String kind() {
        return "grid";
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("tile", Integer.toString(tile));
        state.put("rows", Integer.toString(rows));
        state.put("labels", Boolean.toString(labelHeight > 0));
    }

    SlotTile tile(int slot) {
        return tiles.get(slot);
    }

    Map<Integer, SlotTile> tiles() {
        return tiles;
    }

    int tileSize() {
        return tile;
    }

    /** Sets the tile size, the cell width and whether names are drawn under the tiles. */
    void setMetrics(int tileSize, int cell, int label) {
        tile = tileSize;
        cellWidth = Math.max(tileSize, cell);
        labelHeight = label;
    }

    int columnPitch(int gap) {
        return cellWidth + gap;
    }

    int rowPitch(int gap) {
        return tile + labelHeight + gap;
    }

    static boolean isTile(PanelTile tile) {
        return tile.role() != TileRole.DECORATION && NativePanelScreen.navigationTarget(tile) == null;
    }

    void sync(int rowCount, List<PanelTile> source, UiHost host) {
        rows = rowCount;
        Map<Integer, PanelTile> wanted = new TreeMap<>();
        for (PanelTile data : source) {
            if (isTile(data)) {
                wanted.put(data.slot(), data);
            }
        }
        tiles.entrySet().removeIf((Map.Entry<Integer, SlotTile> entry) -> {
            if (wanted.containsKey(entry.getKey())) {
                return false;
            }
            remove(entry.getValue());
            return true;
        });
        for (PanelTile data : wanted.values()) {
            SlotTile node = tiles.get(data.slot());
            if (node == null) {
                node = new SlotTile(data.slot());
                node.setListener(listener);
                tiles.put(data.slot(), node);
                add(node);
            }
            node.update(data, host);
        }
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return UiLayout.span(rows, tile + labelHeight, services().theme().gap());
    }

    int preferredWidth(int gap) {
        return UiLayout.span(PanelView.COLUMNS, cellWidth, gap);
    }

    @Override
    protected void layout(UiDraw metrics) {
        int gap = services().theme().gap();
        UiRect area = bounds();
        for (SlotTile node : tiles.values()) {
            int column = node.slot() % PanelView.COLUMNS;
            int row = node.slot() / PanelView.COLUMNS;
            int x = area.x() + column * columnPitch(gap) + (cellWidth - tile) / 2;
            node.place(new UiRect(x, area.y() + row * rowPitch(gap), tile, tile), metrics);
            node.label = labelHeight > 0 ? label(metrics, node.data().name(), cellWidth) : List.of();
        }
    }

    /** The most lines any tile's label takes at {@code width}: one or two. */
    int labelLines(UiDraw metrics, int width) {
        int lines = 1;
        for (SlotTile node : tiles.values()) {
            lines = Math.max(lines, label(metrics, node.data().name(), width).size());
        }
        return lines;
    }

    /**
     * A tile's label in at most {@link #MAX_LABEL_LINES} lines: broken at spaces when that is enough, otherwise filled
     * letter by letter; what still does not fit stays in the tooltip.
     */
    static List<String> label(UiDraw metrics, String name, int width) {
        List<String> lines = wrap(metrics, name, width);
        if (lines.size() <= MAX_LABEL_LINES) {
            return lines;
        }
        String codes = lines.get(0).substring(0, codeLength(name));
        String rest = name.substring(codes.length()).strip();
        List<String> filled = new ArrayList<>();
        while (!rest.isEmpty() && filled.size() < MAX_LABEL_LINES) {
            int cut = rest.length();
            while (cut > 1 && metrics.textWidth(rest.substring(0, cut)) > width) {
                cut--;
            }
            filled.add(codes + rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        return filled;
    }

    private static int codeLength(String name) {
        int start = 0;
        while (start + 1 < name.length() && name.charAt(start) == LegacyText.SECTION) {
            start += 2;
        }
        return start;
    }

    /**
     * {@code name} broken at spaces, and with a hyphen inside a word too long for a line, into lines no wider than
     * {@code width}; its leading colour codes carry to every line.
     */
    static List<String> wrap(UiDraw metrics, String name, int width) {
        int start = codeLength(name);
        String codes = name.substring(0, start);
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : name.substring(start).trim().split(" +")) {
            String rest = word;
            while (rest.length() > 1 && metrics.textWidth(rest) > width) {
                if (!line.isEmpty()) {
                    lines.add(codes + line);
                    line = "";
                }
                int cut = rest.length() - 1;
                while (cut > 1 && metrics.textWidth(rest.substring(0, cut) + HYPHEN) > width) {
                    cut--;
                }
                lines.add(codes + rest.substring(0, cut) + HYPHEN);
                rest = rest.substring(cut);
            }
            String longer = line.isEmpty() ? rest : line + ' ' + rest;
            if (!line.isEmpty() && metrics.textWidth(longer) > width) {
                lines.add(codes + line);
                line = rest;
            } else {
                line = longer;
            }
        }
        if (!line.isEmpty()) {
            lines.add(codes + line);
        }
        return lines;
    }

    @Override
    protected void paintOver(UiFrame frame) {
        if (labelHeight <= 0) {
            return;
        }
        UiDraw draw = frame.draw();
        for (SlotTile node : tiles.values()) {
            UiRect box = node.bounds();
            int y = box.bottom() + 2;
            for (String line : node.label) {
                draw.centeredText(line, box.centerX(), y, frame.theme().text(), false);
                y += draw.lineHeight();
            }
        }
    }

    /** One grid tile; hinted tiles draw their control state on the tile. */
    static final class SlotTile extends IconTile {
        private final int slot;
        private PanelTile data;
        private List<String> label = List.of();

        private SlotTile(int slot) {
            super("slot-" + slot);
            this.slot = slot;
        }

        int slot() {
            return slot;
        }

        PanelTile data() {
            return data;
        }

        HintKind hintKind() {
            return data.hint() == null ? null : data.hint().kind();
        }

        /** Whether a plain click opens an editor here instead of reaching the server. */
        boolean edits() {
            HintKind kind = hintKind();
            return kind == HintKind.NUMBER || kind == HintKind.TEXT || kind == HintKind.CHOICE && data.hint().typed();
        }

        void update(PanelTile tile, UiHost host) {
            data = tile;
            HintKind kind = hintKind();
            setIcon(tile.icon()).setText(tile.name()).setAmount(tile.amount()).setGlint(tile.glint());
            setTooltip(PanelRow.tooltip(tile, PanelRow.alternates(tile), host));
            setTone(kind == HintKind.DESTRUCTIVE ? Tone.DESTRUCTIVE : Tone.NORMAL);
            setSwitch(kind == HintKind.TOGGLE ? NativePanelScreen.toggleOn(tile.hint().value()) : null);
            setCornerText(kind == HintKind.NUMBER ? tile.hint().value() : !tile.value().isEmpty() ? tile.value() : null);
            setField(kind == HintKind.TEXT);
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("slot", Integer.toString(slot));
            super.describe(state);
            state.put("role", data.role().name());
            if (data.hint() != null) {
                state.put("hint", data.hint().kind().name());
                state.put("hintValue", data.hint().value());
            }
        }
    }
}
