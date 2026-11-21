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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The body of a GRID panel: tiles at their chest slot positions on the nine-column grid, background filler left out,
 * with each tile's name under it when the screen has room for labels.
 */
final class PanelGrid extends UiNode {
    static final int MIN_LABELLED_CELL = 40;
    static final int MAX_LABELLED_CELL = 56;

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
        }
    }

    @Override
    protected void paintOver(UiFrame frame) {
        if (labelHeight <= 0) {
            return;
        }
        UiDraw draw = frame.draw();
        for (SlotTile node : tiles.values()) {
            UiRect box = node.bounds();
            String name = LegacyText.truncate(draw, node.data().name(), cellWidth - 2);
            draw.centeredText(name, box.centerX(), box.bottom() + 2, frame.theme().text(), true);
        }
    }

    /** One grid tile; hinted tiles draw their control state on the tile. */
    static final class SlotTile extends IconTile {
        private final int slot;
        private PanelTile data;

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
