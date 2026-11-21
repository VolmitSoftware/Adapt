package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The body of a LIST or FORM panel: rows in chest reading order, grouped under their section headings (untitled rows
 * first, then each section where it first appears), filtered by the search text. A section heading folds its rows
 * away on click or Enter and shows how many it hides; a search shows every match whatever is folded. HEADER tiles
 * become headings in place, their lines in the tooltip.
 */
final class PanelList extends UiNode {
    private static final int SECTION_HEIGHT = 13;

    private final PanelRow.Host owner;
    private final Map<Integer, PanelRow> rows = new TreeMap<>();
    private final Map<Integer, Heading> headings = new TreeMap<>();
    private final Map<String, Heading> sections = new LinkedHashMap<>();
    private final Set<String> folded = new HashSet<>();
    private List<PanelTile> lastTiles = List.of();
    private String lastQuery = "";
    private String lastNoMatch = "";
    private String lastEmpty = "";
    private String emptyText = "";

    PanelList(PanelRow.Host owner) {
        super("list");
        this.owner = owner;
    }

    @Override
    public String kind() {
        return "list";
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("rows", Integer.toString(visibleRows()));
        if (!emptyText.isEmpty()) {
            state.put("empty", emptyText);
        }
    }

    PanelRow row(int slot) {
        return rows.get(slot);
    }

    Map<Integer, PanelRow> rows() {
        return rows;
    }

    int visibleRows() {
        int count = 0;
        for (UiNode child : children()) {
            if (child instanceof PanelRow) {
                count++;
            }
        }
        return count;
    }

    boolean folded(String section) {
        return folded.contains(section);
    }

    /** Folds or unfolds {@code section} and lays the list out again. */
    void toggle(String section) {
        if (!folded.remove(section)) {
            folded.add(section);
        }
        sync(lastTiles, lastQuery, lastNoMatch, lastEmpty);
        services().requestLayout();
    }

    /** Whether the tile is drawn as a body row (as opposed to chrome, a footer chip or nothing). */
    static boolean isRow(PanelTile tile) {
        return isBody(tile) && !isChip(tile);
    }

    /** Whether the tile belongs in the body or the footer at all, rather than the chrome or nowhere. */
    static boolean isBody(PanelTile tile) {
        return tile.role() != TileRole.DECORATION && NativePanelScreen.navigationTarget(tile) == null;
    }

    /** A plain summary tile, shown as a footer chip while the body has rows of its own. */
    static boolean isChip(PanelTile tile) {
        return tile.role() == TileRole.INFO && tile.value().isEmpty() && tile.section().isEmpty();
    }

    /** Whether {@code tiles} have body rows besides plain summary tiles; without them the summaries become the rows. */
    static boolean hasRows(List<PanelTile> tiles) {
        for (PanelTile tile : tiles) {
            if (isRow(tile)) {
                return true;
            }
        }
        return false;
    }

    /** Rebuilds the rows for {@code tiles}, keeping the nodes of slots that stay, and shows the ones matching {@code query}. */
    void sync(List<PanelTile> tiles, String query, String noMatch, String empty) {
        lastTiles = List.copyOf(tiles);
        lastQuery = query;
        lastNoMatch = noMatch;
        lastEmpty = empty;
        Map<Integer, PanelTile> wanted = new TreeMap<>();
        Map<Integer, PanelTile> heads = new TreeMap<>();
        boolean chipsAsRows = !hasRows(tiles);
        for (PanelTile tile : tiles) {
            if (!isBody(tile) || isChip(tile) && !chipsAsRows) {
                continue;
            }
            if (tile.role() == TileRole.HEADER) {
                heads.put(tile.slot(), tile);
            } else {
                wanted.put(tile.slot(), tile);
            }
        }
        rows.keySet().removeIf((Integer slot) -> !wanted.containsKey(slot));
        for (PanelTile tile : wanted.values()) {
            PanelRow row = rows.get(tile.slot());
            if (row == null) {
                row = new PanelRow(tile.slot(), owner);
                rows.put(tile.slot(), row);
            }
            row.update(tile);
        }
        headings.keySet().removeIf((Integer slot) -> !heads.containsKey(slot));
        for (PanelTile tile : heads.values()) {
            Heading heading = headings.computeIfAbsent(tile.slot(), (Integer slot) -> new Heading("heading:" + slot, null));
            heading.set(tile.name(), tile.shownDescription(), 0);
        }
        String needle = LegacyText.strip(query).trim().toLowerCase(Locale.ROOT);
        Map<String, List<UiNode>> grouped = new LinkedHashMap<>();
        grouped.put("", new ArrayList<>());
        for (int slot = 0; slot < 54; slot++) {
            Heading heading = headings.get(slot);
            if (heading != null && needle.isEmpty()) {
                grouped.get("").add(heading);
                continue;
            }
            PanelRow row = rows.get(slot);
            if (row == null || !matches(row.data(), needle)) {
                continue;
            }
            grouped.computeIfAbsent(row.data().section(), (String key) -> new ArrayList<>()).add(row);
        }
        List<UiNode> order = new ArrayList<>(grouped.remove(""));
        sections.keySet().removeIf((String name) -> !grouped.containsKey(name));
        folded.retainAll(grouped.keySet());
        boolean anyHidden = false;
        for (Map.Entry<String, List<UiNode>> group : grouped.entrySet()) {
            String name = group.getKey();
            Heading heading = sections.computeIfAbsent(name, (String key) -> new Heading("section:" + key, key));
            boolean hidden = needle.isEmpty() && folded.contains(name);
            heading.set(name, List.of(), hidden ? group.getValue().size() : 0);
            order.add(heading);
            if (hidden) {
                anyHidden = true;
            } else {
                order.addAll(group.getValue());
            }
        }
        clearChildren();
        for (UiNode node : order) {
            add(node);
        }
        boolean anyRow = anyHidden || order.stream().anyMatch((UiNode node) -> node instanceof PanelRow);
        emptyText = anyRow ? "" : needle.isEmpty() ? empty : noMatch;
    }

    static boolean matches(PanelTile tile, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        StringBuilder text = new StringBuilder(LegacyText.strip(tile.name())).append('\n').append(LegacyText.strip(tile.value()));
        for (String line : tile.shownDescription()) {
            text.append('\n').append(LegacyText.strip(line));
        }
        if (tile.hint() != null) {
            text.append('\n').append(LegacyText.strip(tile.hint().value()));
        }
        return text.toString().toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        int height = 0;
        for (UiNode child : children()) {
            height += child.preferredHeight(width, metrics);
        }
        if (!emptyText.isEmpty()) {
            height += metrics.lineHeight() * 3;
        }
        return height;
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiRect area = bounds();
        int y = area.y();
        for (UiNode child : children()) {
            int height = child.preferredHeight(area.width(), metrics);
            child.place(new UiRect(area.x(), y, area.width(), height), metrics);
            y += height;
        }
    }

    @Override
    protected void paint(UiFrame frame) {
        if (emptyText.isEmpty()) {
            return;
        }
        UiDraw draw = frame.draw();
        UiRect area = bounds();
        int top = area.y();
        for (UiNode child : children()) {
            top = Math.max(top, child.bounds().bottom());
        }
        draw.centeredText(LegacyText.truncate(draw, emptyText, area.width() - 8), area.centerX(), top + draw.lineHeight(),
                frame.theme().textMuted(), false);
    }

    /**
     * A section title that folds its section on click or Enter (a caret shows which way, with the hidden row count when
     * folded), or a HEADER tile's name with its lines in the tooltip.
     */
    final class Heading extends UiNode {
        private static final int CARET = 7;

        private final String section;
        private String title = "";
        private int hidden;

        Heading(String id, String section) {
            super(id);
            this.section = section;
        }

        void set(String newTitle, List<String> lines, int hiddenRows) {
            title = newTitle;
            hidden = hiddenRows;
            List<String> shown = new ArrayList<>();
            for (String line : lines) {
                if (!LegacyText.isBlank(line)) {
                    shown.add(line);
                }
            }
            setTooltip(shown.isEmpty() ? null : new Tooltip(title, shown));
        }

        @Override
        public String kind() {
            return "heading";
        }

        @Override
        public String label() {
            return LegacyText.strip(title);
        }

        @Override
        public void describe(Map<String, String> state) {
            if (section != null) {
                state.put("folded", Boolean.toString(folded(section)));
            }
        }

        @Override
        public boolean focusable() {
            return section != null;
        }

        @Override
        public boolean hittable() {
            return section != null || tooltip() != null;
        }

        @Override
        public boolean mousePressed(UiMouseEvent event) {
            return event.button() == UiMouseButton.LEFT && activate();
        }

        @Override
        public boolean activate() {
            if (section == null) {
                return false;
            }
            services().playClick();
            toggle(section);
            return true;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return SECTION_HEIGHT;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            if (section != null && (frame.isHovered(this) || frame.showsFocus(this))) {
                UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), UiTome.HOVER);
            }
            if (frame.showsFocus(this)) {
                UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), theme.focus());
            }
            int x = box.x() + PanelRow.PAD;
            int y = box.y() + (SECTION_HEIGHT - draw.lineHeight()) / 2 + 1;
            if (section != null) {
                paintCaret(draw, x, box.y() + SECTION_HEIGHT / 2, folded(section), theme.textMuted());
                x += CARET + 3;
            }
            boolean plain = title.indexOf(LegacyText.SECTION) < 0;
            String count = hidden > 0 ? " (" + hidden + ")" : "";
            int countWidth = draw.textWidth(count);
            String shown = LegacyText.truncate(draw, title, box.right() - PanelRow.PAD - x - countWidth);
            draw.text(shown, x, y, plain ? theme.accent() : theme.text(), true);
            int after = x + draw.textWidth(shown);
            if (!count.isEmpty()) {
                draw.text(count, after, y, theme.textMuted(), false);
                after += countWidth;
            }
            int lineLeft = after + 4;
            if (lineLeft < box.right() - PanelRow.PAD) {
                draw.fill(lineLeft, y + draw.lineHeight() / 2 - 1, box.right() - PanelRow.PAD, y + draw.lineHeight() / 2, 0x405A3E26);
            }
        }

        private static void paintCaret(UiDraw draw, int x, int centerY, boolean closed, int color) {
            for (int step = 0; step < 4; step++) {
                if (closed) {
                    draw.fill(x + step, centerY - 3 + step, x + step + 1, centerY + 4 - step, color);
                } else {
                    draw.fill(x + step, centerY - 2 + step, x + CARET - step, centerY - 1 + step, color);
                }
            }
        }
    }
}
