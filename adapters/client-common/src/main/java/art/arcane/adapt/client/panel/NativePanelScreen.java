package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelPaging;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileHint;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.IconTile;
import art.arcane.adapt.client.ui.kit.KitScreen;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Popover;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiBreakpoint;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiMotion;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.platform.menu.ClickKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A server chest menu ({@link PanelView}) drawn as a native screen. LIST and FORM menus become a scrolling list of
 * rows with real controls, section headings and a search field for long lists; GRID menus keep the chest's slot
 * geometry with names under the tiles when there is room. Background filler is never drawn; back sits in the title
 * bar next to the breadcrumb, page controls and summary tiles in the footer. Every input reaches the server as the
 * click a chest would send for the same slot, or as typed text where the server takes it.
 */
public final class NativePanelScreen extends KitScreen {
    public static final int MIN_TILE = 18;
    public static final int SEARCH_MIN_ROWS = 8;
    static final Set<String> CHROME_TARGETS = Set.of("back", "first", "previous", "next", "last", "page-info");
    private static final int WIDE_WIDTH = 400;
    private static final int REGULAR_WIDTH = 360;
    private static final int MIN_BODY = 48;
    private static final int MIN_LIST_WIDTH = 220;
    private static final int LIST_STEP = 8;
    private static final int MIN_SEARCH = 70;
    private static final int MARGIN = 2;
    /**
     * Screen a panel leaves free on every side beyond the margin, so it does not run edge to edge: unless the screen
     * is small, or the last of that room is what saves its content from scrolling.
     */
    private static final int CLEAR = 10;

    private final PanelActions actions;
    private final PanelClicks clicks = new PanelClicks();
    private final PanelChrome.Frame frame;
    private final PanelChrome.TitleBar titleBar;
    private final Button backButton;
    private final TextField search;
    private final PanelList list;
    private final ScrollArea listScroll;
    private final PanelGrid grid;
    private final ScrollArea gridScroll;
    private final PanelFooter footer;
    private final Map<String, Integer> chromeSlots = new LinkedHashMap<>();
    private final RowHost rowHost = new RowHost();
    private final PanelEditors editors = new PanelEditors(new EditorHost(), rowHost);
    private PanelView view;
    private boolean silent;

    public NativePanelScreen(PanelView view, PanelActions actions) {
        this(view, actions, UiTheme.DEFAULT, UiMotion.system());
    }

    public NativePanelScreen(PanelView view, PanelActions actions, UiTheme theme, UiMotion motion) {
        super(view.title(), theme, motion);
        this.view = Objects.requireNonNull(view, "view");
        this.actions = Objects.requireNonNull(actions, "actions");
        frame = root().add(new PanelChrome.Frame());
        titleBar = frame.add(new PanelChrome.TitleBar(() -> this.view));
        backButton = frame.add(new Button("back", UiTome.Glyph.BACK, () -> sendChrome("back")));
        search = frame.add(new TextField("search", 64));
        search.setOnChange((String text) -> {
            syncBody();
            requestLayout();
        });
        list = new PanelList(rowHost);
        listScroll = frame.add(new ScrollArea("body.scroll", list));
        grid = new PanelGrid(new TileListener());
        gridScroll = frame.add(new ScrollArea("grid.scroll", grid));
        footer = new PanelFooter(frame, rowHost, this::sendChrome);
        sync();
    }

    public PanelView view() {
        return view;
    }

    public int sessionId() {
        return view.sessionId();
    }

    /**
     * Shows a newer revision of the same session in place: rows and tiles keep their nodes, so hover, focus, scroll,
     * search and an open editor survive. Views for another session or an older revision are ignored.
     */
    public void apply(PanelView next) {
        Objects.requireNonNull(next, "view");
        if (next.sessionId() != view.sessionId() || next.revision() < view.revision()) {
            return;
        }
        view = next;
        sync();
        requestLayout();
    }

    /** Closes without telling the server, for closes the server asked for or a replacement panel. */
    void closeSilently() {
        silent = true;
        close();
    }

    void markSilent() {
        silent = true;
    }

    /** The grid tile for {@code slot}, or null when the slot is empty, filler, navigation or the panel is not a grid. */
    public IconTile tile(int slot) {
        return grid.shown() ? grid.tile(slot) : null;
    }

    /** The list row for {@code slot}, or null when the slot has no row. */
    public UiNode row(int slot) {
        return listScroll.shown() ? list.row(slot) : null;
    }

    /** The control on the row for {@code slot}, or null. */
    public UiNode control(int slot) {
        PanelRow row = list.row(slot);
        return row == null ? null : row.control();
    }

    /** The title bar back button or a footer page button ("first", "previous", "next", "last"), or null when absent. */
    public Button navigationButton(String target) {
        if ("back".equals(target)) {
            return backButton.shown() ? backButton : null;
        }
        return footer.button(target);
    }

    public TextField searchField() {
        return search.shown() ? search : null;
    }

    public Popover editor() {
        return editors.editor();
    }

    public PanelLayout layout() {
        return view.layout();
    }

    @Override
    protected String probeKind() {
        return "panel";
    }

    @Override
    protected String probeTitle() {
        return view.title();
    }

    @Override
    protected void describe(Map<String, String> state) {
        state.put("sessionId", Integer.toString(view.sessionId()));
        state.put("revision", Integer.toString(view.revision()));
        state.put("rows", Integer.toString(view.rows()));
        state.put("layout", view.layout().name());
        PanelPaging paging = view.paging();
        if (paging != null) {
            state.put("page", Integer.toString(paging.page() + 1));
            state.put("pages", Integer.toString(paging.pages()));
        }
        if (!view.breadcrumb().isEmpty()) {
            state.put("breadcrumb", String.join(" / ", view.breadcrumb()));
        }
        if (search.shown() && !search.text().isEmpty()) {
            state.put("search", search.text());
        }
    }

    @Override
    protected void init() {
        super.init();
        sync();
    }

    @Override
    public void removed() {
        super.removed();
        if (!silent) {
            silent = true;
            actions.closed(view.sessionId());
        }
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        if (!editingText() && overlays().isEmpty()) {
            ClickKind kind = PanelClicks.key(event.key(), event.modifiers());
            int target = slotUnder(hovered());
            if (target < 0) {
                target = slotUnder(focused());
            }
            if (kind != null && target >= 0) {
                send(target, kind);
                return true;
            }
            boolean plain = event.modifiers().equals(UiModifiers.NONE);
            if (event.key() == UiKey.E && plain) {
                close();
                return true;
            }
            if (event.key() == UiKey.BACKSPACE && plain && chromeSlots.containsKey("back")) {
                sendChrome("back");
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private static int slotUnder(UiNode node) {
        for (UiNode current = node; current != null; current = current.parent()) {
            if (current instanceof PanelRow row) {
                return row.slot();
            }
            if (current instanceof PanelGrid.SlotTile tile) {
                return tile.slot();
            }
        }
        return -1;
    }

    private boolean listed() {
        return view.layout() != PanelLayout.GRID;
    }

    private void sync() {
        chromeSlots.clear();
        List<PanelTile> info = new ArrayList<>();
        boolean chips = listed() && PanelList.hasRows(view.tiles());
        for (PanelTile tile : view.tiles()) {
            String target = navigationTarget(tile);
            if (target != null) {
                chromeSlots.putIfAbsent(target, tile.slot());
            } else if (chips && PanelList.isChip(tile)) {
                info.add(tile);
            }
        }
        boolean listed = listed();
        listScroll.setVisible(listed);
        gridScroll.setVisible(!listed);
        syncBody();
        footer.syncChips(info, host());
        syncChrome();
        editors.sync();
        UiHost host = host();
        backButton.setTooltip(Tooltip.of(PanelChrome.tooltip(view, chromeSlots, "back", PanelText.BACK.text(host))));
        search.setPlaceholder(PanelText.SEARCH.text(host));
    }

    private void syncBody() {
        UiHost host = host();
        if (listed()) {
            String query = search.text();
            list.sync(view.tiles(), query, PanelText.NO_MATCH.text(host, LegacyText.strip(query)), PanelText.EMPTY.text(host));
            grid.sync(view.rows(), List.of(), host);
        } else {
            grid.sync(view.rows(), view.tiles(), host);
            list.sync(List.of(), "", "", "");
        }
    }

    private void syncChrome() {
        backButton.setVisible(chromeSlots.containsKey("back"));
        footer.syncPager(view, chromeSlots, host());
        search.setVisible(listed() && (list.rows().size() >= SEARCH_MIN_ROWS || !search.text().isEmpty()));
    }

    @Override
    protected void layoutScreen(UiRect screen, UiDraw metrics) {
        UiTheme theme = theme();
        UiBreakpoint breakpoint = breakpoint();
        UiRect area = screen.inset(MARGIN);
        int tallest = area.height() - (breakpoint == UiBreakpoint.COMPACT ? 0 : CLEAR * 2);
        int widest = Math.min(area.width() - (breakpoint == UiBreakpoint.COMPACT ? 0 : CLEAR * 2), UiLayout.widest(tallest));
        int control = theme.controlHeight();
        int barHeight = Math.max(control, UiIcons.SIZE);
        int pad = theme.padding();
        int rim = theme.frame() * 2;
        boolean chipsInBar = footer.chipsOnly();
        boolean footed = footer.visible() && !chipsInBar;
        int chromeHeight = rim + pad * 2 + barHeight + pad + (footed ? control + pad : 0);
        int width;
        int bodyHeight;
        if (listed()) {
            width = listWidth(switch (breakpoint) {
                case WIDE -> Math.min(WIDE_WIDTH, widest);
                case REGULAR -> Math.min(REGULAR_WIDTH, widest);
                case COMPACT -> widest;
            }, chromeHeight, rim + pad * 2, barHeight, metrics);
            int content = list.preferredHeight(width - rim - pad * 2, metrics);
            bodyHeight = content > area.height() - chromeHeight ? tallest - chromeHeight : Math.max(MIN_BODY, content);
        } else {
            int gap = theme.gap();
            int maxTile = switch (breakpoint) {
                case WIDE -> 30;
                case REGULAR -> 26;
                case COMPACT -> 22;
            };
            int available = widest - rim - pad * 2 - ScrollArea.BAR_WIDTH - 1;
            int room = area.height() - chromeHeight;
            int cell = (available - gap * (PanelView.COLUMNS - 1)) / PanelView.COLUMNS;
            int labelled = Math.min(PanelGrid.MAX_LABELLED_CELL, cell);
            int labelHeight = labelled >= PanelGrid.MIN_LABELLED_CELL
                    ? grid.labelLines(metrics, labelled) * metrics.lineHeight() + 3 : 0;
            if (UiLayout.span(view.rows(), MIN_TILE + labelHeight, gap) > room) {
                labelHeight = 0;
            }
            int tile = Math.min(UiLayout.fit(available, PanelView.COLUMNS, gap, MIN_TILE, maxTile),
                    UiLayout.fit(room - view.rows() * labelHeight, view.rows(), gap, MIN_TILE, maxTile));
            grid.setMetrics(tile, labelHeight > 0 ? labelled : tile, labelHeight);
            int gridWidth = grid.preferredWidth(gap);
            int gridHeight = grid.preferredHeight(gridWidth, metrics);
            bodyHeight = Math.max(Math.min(gridHeight, gridHeight > room ? tallest - chromeHeight : room), Math.min(gridHeight, tile));
            int scrollbar = gridHeight > bodyHeight ? ScrollArea.BAR_WIDTH + 1 : 0;
            int titleWidth = metrics.textWidth(LegacyText.strip(view.title())) + barHeight * 2 + pad * 2
                    + (chipsInBar ? footer.chipsWidth(theme.gap()) + theme.gap() * 2 : 0);
            width = Math.min(widest, Math.max(gridWidth + scrollbar + rim + pad * 2, titleWidth));
        }
        int height = Math.min(area.height(), Math.max(chromeHeight + bodyHeight, Math.min(tallest, UiLayout.shortest(width))));
        UiRect box = UiLayout.center(area, width, height);
        frame.place(box, metrics);
        UiRect inner = box.inset(theme.frame()).inset(pad);
        UiRect bar = inner.top(barHeight);
        int left = bar.x();
        if (backButton.visible()) {
            backButton.place(new UiRect(left, bar.y(), barHeight, barHeight), metrics);
            left += barHeight + theme.gap() * 2;
        }
        int right = bar.right();
        if (chipsInBar) {
            int chipsWidth = footer.chipsWidth(theme.gap());
            footer.layoutChips(new UiRect(right - chipsWidth, bar.y(), chipsWidth, barHeight), metrics, theme.gap());
            right -= chipsWidth + theme.gap() * 2;
        }
        if (search.visible()) {
            int searchWidth = Math.min(breakpoint == UiBreakpoint.COMPACT ? MIN_SEARCH : 110, (right - left) / 2);
            search.place(new UiRect(right - searchWidth, bar.y() + (barHeight - control) / 2, searchWidth, control), metrics);
            right -= searchWidth + theme.gap() * 2;
        }
        titleBar.place(UiRect.of(left, bar.y(), Math.max(left, right), bar.bottom()), metrics);
        int bodyTop = bar.bottom() + pad;
        int footerTop = inner.bottom() - control;
        frame.dividers(bar.bottom(), footed ? footerTop - pad + 1 : Integer.MIN_VALUE);
        UiRect body = UiRect.of(inner.x(), bodyTop, inner.right(), footed ? footerTop - pad : inner.bottom());
        if (listed()) {
            listScroll.place(body, metrics);
        } else {
            int gridWidth = grid.preferredWidth(theme.gap());
            int scrollWidth = Math.min(body.width(), gridWidth + (grid.preferredHeight(gridWidth, metrics) > body.height()
                    ? ScrollArea.BAR_WIDTH + 1 : 0));
            gridScroll.place(new UiRect(body.x() + (body.width() - scrollWidth) / 2, body.y(), scrollWidth, body.height()), metrics);
        }
        if (footed) {
            footer.layout(UiRect.of(inner.x(), footerTop, inner.right(), inner.bottom()), metrics, theme, view, chromeSlots, host());
        }
        editors.anchor();
    }

    /**
     * The width of a list panel: the narrowest from the one that shows every row and the title on one line up to
     * {@code most} whose 16:9 height holds the rows, so a short menu opens a small book instead of a page of blank
     * paper; {@code most} when no narrower one does or a row holds a control.
     */
    private int listWidth(int most, int chromeHeight, int chromeWidth, int barHeight, UiDraw metrics) {
        int rows = list.naturalWidth(metrics);
        if (rows == 0) {
            return most;
        }
        UiTheme theme = theme();
        int bar = titleBar.naturalWidth(metrics) + (backButton.visible() ? barHeight + theme.gap() * 2 : 0)
                + (search.visible() ? MIN_SEARCH + theme.gap() * 2 : 0)
                + (footer.chipsOnly() ? footer.chipsWidth(theme.gap()) + theme.gap() * 2 : 0);
        int least = UiLayout.snapUp(Math.max(MIN_LIST_WIDTH, Math.max(rows + ScrollArea.BAR_WIDTH + 1, bar) + chromeWidth));
        for (int width = least; width < most; width += LIST_STEP) {
            if (chromeHeight + list.preferredHeight(width - chromeWidth, metrics) <= UiLayout.shortest(width)) {
                return width;
            }
        }
        return most;
    }

    private void sendChrome(String target) {
        Integer slot = chromeSlots.get(target);
        if (slot != null) {
            send(slot, ClickKind.LEFT);
        }
    }

    private void send(int slot, ClickKind kind) {
        actions.click(view.sessionId(), view.revision(), slot, kind);
    }

    private void input(int slot, String text) {
        actions.input(view.sessionId(), view.revision(), slot, text);
    }

    private PanelTile tileData(int slot) {
        for (PanelTile tile : view.tiles()) {
            if (tile.slot() == slot) {
                return tile;
            }
        }
        return null;
    }

    /** The chrome target a navigation tile moves to, or null for tiles that stay in the body. */
    static String navigationTarget(PanelTile tile) {
        TileHint hint = tile.hint();
        if (hint == null || hint.kind() != HintKind.NAVIGATION) {
            return null;
        }
        String target = hint.value().toLowerCase(Locale.ROOT);
        return CHROME_TARGETS.contains(target) ? target : null;
    }

    static boolean toggleOn(String value) {
        return "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    private final class RowHost implements PanelRow.Host {
        @Override
        public ClickKind press(int slot, UiMouseEvent event) {
            return clicks.press(slot, event, motion().now());
        }

        @Override
        public ClickKind release(int slot, UiMouseEvent event) {
            return clicks.release(slot, event);
        }

        @Override
        public void openActions(PanelRow row) {
            UiRect anchor = row.moreButton() == null ? row.bounds() : row.moreButton().bounds();
            editors.openActions(row.slot(), anchor, row.data(), row.alternates());
        }

        @Override
        public void openOptions(PanelRow row) {
            editors.openOptions(row.slot(), row.control().bounds(), row.data());
        }

        @Override
        public UiHost host() {
            return NativePanelScreen.this.host();
        }

        @Override
        public void click(int slot, ClickKind kind) {
            send(slot, kind);
        }

        @Override
        public void input(int slot, String text) {
            NativePanelScreen.this.input(slot, text);
        }
    }

    private final class EditorHost implements PanelEditors.Host {
        @Override
        public UiHost host() {
            return NativePanelScreen.this.host();
        }

        @Override
        public void openOverlay(Popover popover) {
            NativePanelScreen.this.openOverlay(popover);
        }

        @Override
        public void closeOverlay(Popover popover) {
            NativePanelScreen.this.closeOverlay(popover);
        }

        @Override
        public PanelTile tile(int slot) {
            return tileData(slot);
        }

        @Override
        public UiRect anchor(int slot) {
            PanelGrid.SlotTile tile = grid.tile(slot);
            if (tile != null && grid.shown()) {
                return tile.bounds();
            }
            PanelRow row = list.row(slot);
            if (row == null) {
                return null;
            }
            return row.control() != null ? row.control().bounds() : row.bounds();
        }
    }

    private final class TileListener implements IconTile.Listener {
        @Override
        public void pressed(IconTile tile, UiMouseEvent event) {
            PanelGrid.SlotTile node = (PanelGrid.SlotTile) tile;
            ClickKind kind = clicks.press(node.slot(), event, motion().now());
            if (kind == null) {
                return;
            }
            if (kind == ClickKind.LEFT && node.edits()) {
                editors.openGrid(node);
                return;
            }
            send(node.slot(), kind);
        }

        @Override
        public void released(IconTile tile, UiMouseEvent event) {
            PanelGrid.SlotTile node = (PanelGrid.SlotTile) tile;
            ClickKind kind = clicks.release(node.slot(), event);
            if (kind != null && !node.edits()) {
                send(node.slot(), kind);
            }
        }

        @Override
        public void activated(IconTile tile) {
            PanelGrid.SlotTile node = (PanelGrid.SlotTile) tile;
            if (node.edits()) {
                editors.openGrid(node);
            } else {
                send(node.slot(), ClickKind.LEFT);
            }
        }
    }
}
