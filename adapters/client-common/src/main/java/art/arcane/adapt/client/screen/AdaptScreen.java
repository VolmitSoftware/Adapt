package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.state.ClientState;
import art.arcane.adapt.client.state.ClientSubscription;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.kit.Breadcrumbs;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.KitScreen;
import art.arcane.adapt.client.ui.kit.Panel;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.StatChip;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiBreakpoint;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The frame every Adapt page shares: an open book centred over the world with a one-line top bar (back, the
 * breadcrumbs on a ribbon, an optional tool, small stat chips) above a ruled divider, and one page below it or two
 * facing pages across the spine.
 *
 * <p>The book is {@link #BOOK_WIDTH} by {@link #BOOK_HEIGHT} scaled units on every page, so it keeps its place while
 * the player moves between pages or the content changes, and grows and shrinks with the GUI scale. A screen too small
 * for it gets the largest book it has room for, never wider than 16:9. Medium and large screens show two equal facing
 * pages with the spine in the middle; small ones one page with the facing page's content below it. Each page arranges
 * its content in the size it is given ({@link #arrange}); spare height goes to a small margin above and below the
 * pages, then to the row pitch, then to the margins again, and pages of text with actions at their foot
 * ({@link #footed}) keep those margins small and leave the rest between the text and the actions. Small screens get
 * rows at their tightest, medium and large ones rows that breathe and bounded margins. A page scrolls only when its
 * content does not fit; a resize lays the book out again and keeps the first widget showing in a scrolled page where
 * it was.
 *
 * <p>Messages show on a paper slip under the book, which always leaves room for it, so they never cover the page.
 * Escape and Backspace go back one level, E closes. The page follows the client state live while open; requests the
 * page sends show a waiting state until the server answers with {@code Feedback}, and nothing on the page changes
 * before the server's own update arrives.
 */
public abstract class AdaptScreen extends KitScreen {
    public static final long ANSWER_TIMEOUT_MILLIS = 8000L;
    public static final int PAGE_GAP = 3;
    static final int BAR = 16;
    /** Room for the ruled divider under the top bar, on top of the padding. */
    static final int RULE = 2;
    /** Width between facing pages: the spine and a little paper either side. */
    static final int SPREAD_GAP = UiTome.SPINE_WIDTH + 4;
    /** The book's size in scaled units on a screen with room for it: 16:9, between a pocket book and a ledger. */
    static final int BOOK_WIDTH = 460;
    static final int BOOK_HEIGHT = 260;
    private static final int MARGIN = 2;
    /** How far the title ribbon reaches past the breadcrumbs on each side. */
    private static final int RIBBON_LEAD = 4;
    private static final int RIBBON_TAIL = 8;

    private final ScreenRouter router;
    private final Route route;
    private final Frame frame;
    private final Button back;
    private final Breadcrumbs crumbs;
    private final UiGroup chips;
    private final UiGroup page;
    private final UiGroup facing;
    private final Spread spread;
    private final ScrollArea scroll;
    private final Snackbar snackbar;
    private final List<ClientSubscription> subscriptions = new ArrayList<>();
    private UiNode tool;
    private int toolWidth;
    private List<Route> path = List.of();
    private String pendingAction;
    private long pendingSince;
    private boolean scrollRestored;
    private Book book;
    private int solved = -1;

    protected AdaptScreen(String title, Route route, ScreenRouter router) {
        super(title, router.theme(), router.motion());
        this.router = Objects.requireNonNull(router, "router");
        this.route = Objects.requireNonNull(route, "route");
        frame = root().add(new Frame());
        back = frame.add(new Button("back", UiTome.Glyph.BACK, router::back));
        back.setTooltip(Tooltip.of(ScreenText.BACK.text()));
        crumbs = frame.add(new Breadcrumbs("crumb", (int index) -> {
            if (index < path.size()) {
                router.open(path.get(index));
            }
        }));
        chips = frame.add(new UiGroup("chips", UiLayoutPolicy.row(2)));
        page = new UiGroup("page", UiLayoutPolicy.column(PAGE_GAP));
        facing = new UiGroup("facing", UiLayoutPolicy.column(PAGE_GAP));
        spread = new Spread();
        scroll = frame.add(new ScrollArea("scroll", spread));
        snackbar = frame.add(new Snackbar("snackbar"));
        ClientState state = router.state();
        subscriptions.add(state.onCatalog(ignored -> refreshNow()));
        subscriptions.add(state.onPlayer(ignored -> refreshNow()));
    }

    /**
     * What a size class gives a book. Small: one page, rows at their tightest, no extra margin. Medium: facing pages,
     * rows up to {@link TileGrid#MAX_PITCH} apart and up to 8 units of margin above and below them. Large: the same
     * with 2 more units inside the cover all round and up to 12 above and below the pages.
     */
    record Density(boolean spreads, int edge, int air, int maxPitch) {
        static Density of(UiBreakpoint size) {
            return switch (size) {
                case COMPACT -> new Density(false, 0, 0, TileGrid.PITCH);
                case REGULAR -> new Density(true, 0, 8, TileGrid.MAX_PITCH);
                case WIDE -> new Density(true, 2, 12, TileGrid.MAX_PITCH);
            };
        }
    }

    /**
     * Arranges the first page for {@code width} with {@code room} of height before it scrolls. {@code open} says the
     * book shows facing pages, which a page that {@link #spans} both divides itself over.
     */
    protected void arrange(UiDraw metrics, int width, int room, boolean open) {
    }

    /** Whether the first page runs over both facing pages, dividing itself at the spine, and the facing page stays empty. */
    protected boolean spans() {
        return false;
    }

    /** How much taller the page as last arranged gets with its rows {@code maxPitch} apart. */
    protected int slack(int maxPitch) {
        return 0;
    }

    /** Spreads the rows of the page as last arranged to take up {@code extra} more height. */
    protected void stretch(int extra, int maxPitch) {
    }

    /** Whether the pages hold their actions against the bottom edge, where spare height can go between content and actions. */
    protected boolean footed() {
        return false;
    }

    /** Reads the client state again and updates the page in place. */
    protected abstract void refresh();

    public final Route route() {
        return route;
    }

    protected final ScreenRouter router() {
        return router;
    }

    /** The left page, or the only one. */
    protected final UiGroup page() {
        return page;
    }

    /** The right page, or the content under the first page when the book shows one page. */
    protected final UiGroup facing() {
        return facing;
    }

    /** Whether the two pages show side by side. */
    final boolean spreadOpen() {
        return spread.open;
    }

    public final ScrollArea scroll() {
        return scroll;
    }

    public final Snackbar snackbar() {
        return snackbar;
    }

    /** The top bar chip with {@code chipId}, created on first use; chips sit at the right end of the bar in call order. */
    protected final StatChip chip(String chipId) {
        StatChip chip = (StatChip) chips.find(chipId);
        return chip != null ? chip : chips.add(new StatChip(chipId, null, "", ""));
    }

    /** Puts {@code node} in the top bar between the breadcrumbs and the chips. */
    protected final void setTool(UiNode node, int width) {
        tool = frame.add(node);
        toolWidth = width;
    }

    /** Updates the breadcrumb path from the route's place in the router. */
    protected final void updatePath() {
        path = router.path(route);
        List<String> labels = new ArrayList<>();
        for (Route step : path) {
            labels.add(router.label(step));
        }
        crumbs.setCrumbs(labels);
    }

    /** Whether a request is waiting for the server's answer. */
    public final boolean pending() {
        return pendingAction != null;
    }

    /** The request waiting for an answer, as named by {@link #send}, or null. */
    protected final String pendingAction() {
        return pendingAction;
    }

    /**
     * Sends a request and waits for the server's answer: {@code request} returns false when nothing could be sent,
     * which shows a failure at once. Ignored while another request is waiting.
     */
    protected final boolean send(String action, String waitingText, BooleanSupplier request) {
        if (pendingAction != null) {
            return false;
        }
        pendingAction = Objects.requireNonNull(action, "action");
        pendingSince = motion().now();
        snackbar.waiting(waitingText);
        refreshNow();
        if (!request.getAsBoolean()) {
            pendingAction = null;
            snackbar.show(Snackbar.Tone.FAILURE, ScreenText.NOT_CONNECTED.text());
            refreshNow();
            return false;
        }
        return true;
    }

    /** The server's answer to the last request, or a message it sent on its own. */
    public final void feedback(FeedbackKind kind, String text) {
        Snackbar.Tone tone = switch (kind) {
            case SUCCESS -> Snackbar.Tone.SUCCESS;
            case FAILURE -> Snackbar.Tone.FAILURE;
            case INFO -> Snackbar.Tone.INFO;
        };
        snackbar.show(tone, text);
        String answered = pendingAction;
        pendingAction = null;
        if (answered != null) {
            answered(answered, kind);
        }
        refreshNow();
    }

    /** Called when the request named {@code action} got its answer, before the page refreshes. */
    protected void answered(String action, FeedbackKind kind) {
    }

    @Override
    public void tick() {
        if (pendingAction != null && motion().now() - pendingSince >= ANSWER_TIMEOUT_MILLIS) {
            pendingAction = null;
            snackbar.show(Snackbar.Tone.FAILURE, ScreenText.NO_ANSWER.text());
            refreshNow();
        }
    }

    @Override
    public boolean pausesGame() {
        return false;
    }

    @Override
    protected void describe(Map<String, String> state) {
        state.put("route", routeId(route));
        state.put("pending", pendingAction == null ? "" : pendingAction);
        state.put("message", snackbar.live() ? snackbar.label() : "");
        state.put("messageTone", snackbar.tone().name());
    }

    static String routeId(Route route) {
        return switch (route) {
            case Route.Skills ignored -> "skills";
            case Route.Skill skill -> "skill:" + skill.skillId();
            case Route.Adaptation adaptation -> "adaptation:" + adaptation.adaptationId();
        };
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        boolean plain = event.modifiers().equals(UiModifiers.NONE);
        if (event.key() == UiKey.ESCAPE || event.key() == UiKey.BACKSPACE && !editingText()) {
            router.back();
            return true;
        }
        if (event.key() == UiKey.E && plain && !editingText()) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        for (ClientSubscription subscription : subscriptions) {
            subscription.cancel();
        }
        subscriptions.clear();
        router.remember(route, scroll.scroll());
    }

    @Override
    protected void layoutScreen(UiRect screen, UiDraw metrics) {
        boolean fresh = book == null || solved != layoutVersion();
        UiNode anchor = fresh ? anchor() : null;
        int anchorTop = anchor == null ? 0 : anchor.bounds().y() - scroll.bounds().y();
        if (fresh) {
            book = solve(metrics);
            solved = layoutVersion();
        }
        place(book, screen, metrics);
        if (!scrollRestored) {
            scrollRestored = true;
            scroll.setScroll(router.scroll(route));
        } else if (anchor != null && scroll.isAncestorOf(anchor)) {
            scroll.setScroll(scroll.scroll() + anchor.bounds().y() - scroll.bounds().y() - anchorTop);
        }
    }

    /** The first widget showing in a scrolled page, which a relayout keeps where it is. */
    private UiNode anchor() {
        if (scroll.scroll() == 0) {
            return null;
        }
        return firstShowing(spread, scroll.bounds().y());
    }

    private static UiNode firstShowing(UiNode node, int top) {
        boolean leaf = true;
        for (UiNode child : node.children()) {
            if (!child.visible()) {
                continue;
            }
            leaf = false;
            UiNode found = firstShowing(child, top);
            if (found != null) {
                return found;
            }
        }
        return leaf && !node.bounds().isEmpty() && node.bounds().bottom() > top ? node : null;
    }

    /** The book for the current screen: see the class comment. */
    private Book solve(UiDraw metrics) {
        UiTheme theme = theme();
        Density density = Density.of(breakpoint());
        UiRect area = new UiRect(0, 0, width(), height()).inset(MARGIN);
        int chrome = (theme.frame() + theme.padding() + density.edge()) * 2;
        int head = BAR + theme.padding() + RULE;
        int messageHeight = snackbar.preferredHeight(area.width(), metrics);
        int messageRoom = messageHeight + 2 - MARGIN;
        int height = Math.min(BOOK_HEIGHT, Math.max(0, area.height() - messageRoom));
        int widest = Math.max(0, Math.min(Math.min(BOOK_WIDTH, area.width()), UiLayout.widest(height)) - chrome);
        boolean open = density.spreads();
        int side = open ? UiLayout.snap(Math.max(0, widest - SPREAD_GAP) / 2) : UiLayout.snap(widest);
        int inner = open ? side * 2 + SPREAD_GAP : side;
        int body = Math.max(0, height - chrome - head);
        spread.open = open;
        spread.spans = open && spans();
        spread.left = side;
        spread.top = 0;
        spread.bottom = 0;
        if (open) {
            arrange(metrics, spread.spans ? inner : side, body, true);
        } else {
            int below = facing.preferredHeight(inner, metrics);
            arrange(metrics, inner, Math.max(0, body - (below > 0 ? below + Spread.STACK_GAP : 0)), false);
        }
        int content = spread.preferredHeight(inner, metrics);
        boolean rows = slack(density.maxPitch()) > 0;
        int spare = body - content;
        if (spare > 0 && rows) {
            stretch(spare - Math.min(density.air(), spare), density.maxPitch());
            content = spread.preferredHeight(inner, metrics);
        }
        int rest = Math.max(0, body - content);
        int top = UiLayout.snap(footed() && !rows ? Math.min(rest / 2, density.air() / 2) : (rest + 1) / 2);
        int bottom = footed() && !rows ? top : rest - top;
        return new Book(inner + chrome, height, density.edge(), top, bottom, messageHeight, messageRoom);
    }

    /** The book as laid out: the frame's size, the margin inside the cover, the margins above and below the pages and the message slip. */
    private record Book(int width, int height, int edge, int top, int bottom, int messageHeight, int messageRoom) {
    }

    private void place(Book shape, UiRect screen, UiDraw metrics) {
        UiTheme theme = theme();
        int pad = theme.padding();
        int gap = theme.gap() * 2;
        int edge = shape.edge();
        UiRect area = screen.inset(MARGIN);
        spread.top = shape.top();
        spread.bottom = shape.bottom();
        UiRect centred = UiLayout.center(area, shape.width(), shape.height());
        int lift = Math.max(0, centred.bottom() - (area.bottom() - shape.messageRoom()));
        UiRect box = new UiRect(centred.x(), Math.max(area.y(), centred.y() - lift), centred.width(), centred.height());
        frame.place(box, metrics);
        UiRect inner = frame.contentBounds().inset(edge);
        UiRect bar = inner.top(BAR);
        int chipsWidth = chipsWidth(metrics);
        int shownTool = tool == null || !tool.visible() ? 0 : toolWidth;
        int toolCell = shownTool == 0 ? 0 : Math.min(shownTool, Math.max(0, (bar.width() - BAR - chipsWidth) / 2));
        int right = bar.right();
        chips.place(chipsWidth == 0 ? UiRect.EMPTY : new UiRect(right - chipsWidth, bar.y(), chipsWidth, BAR), metrics);
        right -= chipsWidth == 0 ? 0 : chipsWidth + gap;
        if (tool != null) {
            tool.place(toolCell == 0 ? UiRect.EMPTY : centred(new UiRect(right - toolCell, bar.y(), toolCell, BAR), theme.controlHeight()),
                    metrics);
            right -= toolCell == 0 ? 0 : toolCell + gap;
        }
        back.place(new UiRect(bar.x(), bar.y(), BAR, BAR), metrics);
        crumbs.place(UiRect.of(bar.x() + BAR + gap, bar.y(), Math.max(bar.x() + BAR + gap, right - RIBBON_TAIL), bar.bottom()), metrics);
        int bodyTop = bar.bottom() + pad + RULE;
        frame.divider = bar.bottom() + (pad + RULE - UiTome.DIVIDER) / 2;
        frame.ribbon = crumbs.bounds().isEmpty() ? UiRect.EMPTY : UiRect.of(crumbs.bounds().x() - RIBBON_LEAD, bar.y(),
                Math.min(crumbs.bounds().right(), crumbs.bounds().x() + crumbs.preferredWidth(metrics)) + RIBBON_TAIL, bar.bottom());
        scroll.place(UiRect.of(inner.x(), bodyTop, inner.right(), inner.bottom()), metrics);
        snackbar.place(new UiRect(box.x(), box.bottom() + 2, box.width(), shape.messageHeight()), metrics);
    }

    private static UiRect centred(UiRect cell, int height) {
        int shown = Math.min(cell.height(), height);
        return new UiRect(cell.x(), cell.y() + (cell.height() - shown) / 2, cell.width(), shown);
    }

    private int chipsWidth(UiDraw metrics) {
        int width = 0;
        for (UiNode chip : chips.children()) {
            if (chip.visible()) {
                width += (width == 0 ? 0 : 2) + chip.preferredWidth(metrics);
            }
        }
        return width;
    }

    private void refreshNow() {
        refresh();
        requestLayout();
    }

    /** Where the fold between two pages falls, or {@link Integer#MIN_VALUE} on a single page. */
    private int fold() {
        return spread.open ? spread.bounds().x() + spread.left + SPREAD_GAP / 2 : Integer.MIN_VALUE;
    }

    /**
     * The book: cover, page, the gutter down the whole page with the cover's hinge above and below it when two pages
     * show, a bookmark over the top edge beside the fold, the title ribbon behind the breadcrumbs and a ruled divider
     * under the top bar with its diamond on the fold.
     */
    private final class Frame extends Panel {
        private int divider = Integer.MIN_VALUE;
        private UiRect ribbon = UiRect.EMPTY;

        private Frame() {
            super("frame", null);
            setLayout((List<UiNode> children, UiRect area, UiDraw metrics) -> {
            });
        }

        @Override
        protected void paint(UiFrame frame) {
            super.paint(frame);
            UiRect box = bounds();
            int cover = frame.theme().frame();
            int fold = fold();
            if (fold != Integer.MIN_VALUE) {
                UiTome.spine(frame.draw(), fold, box.y() + cover, box.bottom() - cover);
                UiTome.hinge(frame.draw(), fold, box.y() - OVERHANG + 1, cover + OVERHANG - 2);
                UiTome.hinge(frame.draw(), fold, box.bottom() - cover + 1, cover + OVERHANG_FAR - 4);
            }
            int middle = fold != Integer.MIN_VALUE ? fold : box.centerX();
            UiTome.bookmark(frame.draw(), middle + UiTome.SPINE_WIDTH / 2 + 4, box.y() - OVERHANG, cover + OVERHANG);
            if (!ribbon.isEmpty()) {
                UiTome.ribbon(frame.draw(), ribbon, frame.theme());
            }
            if (divider != Integer.MIN_VALUE) {
                UiTome.divider(frame.draw(), box.x() + cover, box.right() - cover, divider, middle);
            }
        }
    }

    /**
     * The two pages: side by side across the fold when {@link #open}, the first one over both when it {@link #spans}
     * them, else the facing page under the first one below a divider. {@link #top} and {@link #bottom} are the margins the book keeps above and below them; height left over
     * goes to the pages, the first one when they are stacked, so its actions sit just above the divider.
     */
    private final class Spread extends UiGroup {
        static final int STACK_GAP = PAGE_GAP * 2 + UiTome.DIVIDER;
        private boolean open;
        private boolean spans;
        private int left;
        private int top;
        private int bottom;

        private Spread() {
            super("spread");
            add(page);
            add(facing);
            setLayout(new UiLayoutPolicy() {
                @Override
                public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                    UiRect pages = area.inset(0, top, 0, bottom);
                    if (spans) {
                        page.place(pages, metrics);
                        facing.place(UiRect.EMPTY, metrics);
                        return;
                    }
                    if (open) {
                        page.place(new UiRect(pages.x(), pages.y(), left, pages.height()), metrics);
                        facing.place(UiRect.of(pages.x() + left + SPREAD_GAP, pages.y(), pages.right(), pages.bottom()), metrics);
                        return;
                    }
                    int first = page.preferredHeight(pages.width(), metrics);
                    int below = facing.preferredHeight(pages.width(), metrics);
                    int used = below == 0 ? first : first == 0 ? below : first + STACK_GAP + below;
                    int extra = Math.max(0, pages.height() - used);
                    int taken = first == 0 ? 0 : first + extra;
                    page.place(new UiRect(pages.x(), pages.y(), pages.width(), taken), metrics);
                    int from = first == 0 ? pages.y() : pages.y() + taken + STACK_GAP;
                    facing.place(below == 0 ? UiRect.EMPTY : new UiRect(pages.x(), from, pages.width(), below + (first == 0 ? extra : 0)), metrics);
                }

                @Override
                public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                    if (spans) {
                        return page.preferredHeight(width, metrics);
                    }
                    if (open) {
                        return Math.max(page.preferredHeight(left, metrics), facing.preferredHeight(width - left - SPREAD_GAP, metrics));
                    }
                    int first = page.preferredHeight(width, metrics);
                    int below = facing.preferredHeight(width, metrics);
                    return below == 0 ? first : first == 0 ? below : first + STACK_GAP + below;
                }
            });
        }

        @Override
        protected void paint(UiFrame frame) {
            UiRect box = bounds();
            UiRect second = facing.bounds();
            if (!open && !second.isEmpty() && page.bounds().height() > 0) {
                UiTome.divider(frame.draw(), box.x(), box.right(), second.y() - PAGE_GAP - UiTome.DIVIDER);
            }
        }
    }
}
