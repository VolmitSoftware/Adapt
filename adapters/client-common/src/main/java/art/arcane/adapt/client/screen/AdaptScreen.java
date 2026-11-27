package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.state.ClientState;
import art.arcane.adapt.client.state.ClientSubscription;
import art.arcane.adapt.client.theme.BookGeometry;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.ThemedBook;
import art.arcane.adapt.client.theme.TileFrame;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiScrollEvent;
import art.arcane.adapt.client.ui.kit.Breadcrumbs;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.KitScreen;
import art.arcane.adapt.client.ui.kit.Panel;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.StatChip;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiBreakpoint;
import art.arcane.adapt.client.ui.kit.UiFocus;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The frame every Adapt page shares: an open book centred over the world. Each page of the book has a header line of
 * its own above a ruled divider: the left page carries the back button and the breadcrumbs on a ribbon, the right page
 * an optional title naming what it holds, the optional tool and the stat chips, so nothing but the spine sits on the
 * fold. A small screen shows one page with
 * both header halves on its one line, or on two lines when one would cut the page's name.
 *
 * <p>The book is {@link #BOOK_WIDTH} by {@link #BOOK_HEIGHT} scaled units on every page, so it keeps its place while
 * the player moves between pages or the content changes, and grows and shrinks with the GUI scale. A screen too small
 * for it gets the largest book it has room for, never wider than 16:9. Nothing in the book scrolls and nothing turns:
 * a page {@link #compose composes} its content as a flow of whole blocks (a row of tiles, a preference with its
 * choices, a heading) that fills the left page and then the right one, a block never split, and it can compose itself
 * more densely in steps ({@link #densities}); the book takes the roomiest step that fits the two open pages. A
 * heading stays with the block after it, a page break starts the right page and blocks at a page's foot keep to its
 * bottom edge. A flow may pin its first page to the left. Running text is the one thing that gives up height: its box
 * shrinks to its least and scrolls the rest by itself ({@link art.arcane.adapt.client.ui.kit.TextBox}); a skill's
 * adaptations too many for its page are the one other block that may scroll ({@link GridScroll}). Rows of tiles
 * spread a little apart when the book has height to spare.
 *
 * <p>A single page shows the spread's two pages one at a time: its number sits at its foot and arrows at the outer
 * corners turn to the other, as do the wheel, Page Up and Page Down, and the left and right arrows when nothing is
 * focused or focus is already at the edge of the page. Content past two pages, which the catalog's budget never
 * reaches, turns the same way on an open spread by the wheel and the keys. The router remembers the page turned to,
 * and a new layout, such as after a resize or the pointer moving onto another card, turns to the page holding the
 * first block that was showing. A page may stand one node in the outer margin of its last page ({@link #setGutter}).
 *
 * <p>Messages show on a paper slip under the book, which always leaves room for it, so they never cover the page.
 * Escape and Backspace go back one level, E closes. The page follows the client state live while open; requests the
 * page sends show a waiting state until the server answers with {@code Feedback}, and nothing on the page changes
 * before the server's own update arrives.
 */
public abstract class AdaptScreen extends KitScreen {
    public static final long ANSWER_TIMEOUT_MILLIS = 8000L;
    public static final int PAGE_GAP = 3;
    /** The space above a heading that follows other blocks on its page. */
    static final int SECTION_GAP = 8;
    static final int BAR = 16;
    /** Room for the ruled divider under the header line, on top of the padding. */
    static final int RULE = 2;
    /** The strip under each page holding its number and the arrow that turns the leaf. */
    static final int FOOT = 12;
    /** Width between facing pages: the spine and a little paper either side. */
    static final int SPREAD_GAP = UiTome.SPINE_WIDTH + 4;
    /** The book's size in scaled units on a screen with room for it: 16:9, between a pocket book and a ledger. */
    static final int BOOK_WIDTH = 460;
    static final int BOOK_HEIGHT = 260;
    private static final int MARGIN = 2;
    private static final int FOOT_GAP = 2;
    private static final int TURN_WIDTH = 16;
    /** Space between the page's title and the control after it. */
    private static final int TITLE_MARK_GAP = 4;
    /** The narrowest the tool gets before it gives up its place to the chips. */
    private static final int TOOL_LEAST = 48;
    /** How far the title ribbon reaches past the breadcrumbs on each side. */
    private static final int RIBBON_LEAD = 4;
    private static final int RIBBON_TAIL = 8;
    /** Height a themed book's foot band takes from the bottom of its pages. */
    static final int THEME_FOOT = 9;

    private final ScreenRouter router;
    private final Route route;
    private final Frame frame;
    private final Button back;
    private final Breadcrumbs crumbs;
    private final UiGroup chips;
    private final Button previous;
    private final Button next;
    private final Snackbar snackbar;
    private final List<Sheet> sheets = new ArrayList<>();
    private final List<ClientSubscription> subscriptions = new ArrayList<>();
    private UiNode tool;
    private UiNode gutter;
    private UiNode titleMark;
    private SectionHeading pageTitle;
    private int toolWidth;
    private List<Route> path = List.of();
    private String pendingAction;
    private long pendingSince;
    private boolean leafRestored;
    private boolean restart;
    private int leaf;
    private boolean pinned;
    private int density;
    private Book book;
    private int solved = -1;
    private PageTheme pageTheme;
    private ThemedBook themedBook;
    private int themedLeaf = -1;
    private final long themeEpoch;
    private final TileTheming tileTheming = new TileTheming() {
        @Override
        public PageTheme theme(UiSprites sprites, String skillId) {
            return router.themes().theme(sprites, skillId);
        }

        @Override
        public int fold() {
            if (book == null || book.open()) {
                return frame.fold;
            }
            return themeLeaf() == 1 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        }

        @Override
        public long millis() {
            return motion().now() - themeEpoch;
        }

        @Override
        public boolean still() {
            return router.themes().still(motion());
        }

        @Override
        public void defer(RowTile tile) {
            deferred.add(tile);
        }
    };
    private final List<RowTile> deferred = new ArrayList<>();

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
        previous = frame.add(new Button("leaf:previous", UiTome.Glyph.BACK, () -> turnTo(leaf - 1)));
        previous.setTooltip(Tooltip.of(ScreenText.PREVIOUS_PAGE.text()));
        next = frame.add(new Button("leaf:next", UiTome.Glyph.FORWARD, () -> turnTo(leaf + 1)));
        next.setTooltip(Tooltip.of(ScreenText.NEXT_PAGE.text()));
        snackbar = frame.add(new Snackbar("snackbar"));
        ClientState state = router.state();
        subscriptions.add(state.onCatalog(ignored -> refreshNow()));
        subscriptions.add(state.onPlayer(ignored -> refreshNow()));
        subscriptions.add(state.onMutateOffers(ignored -> refreshNow()));
        subscriptions.add(state.onKept(this::refreshNow));
        subscriptions.add(state.onTools(this::refreshNow));
        subscriptions.add(state.onRecipes(this::refreshNow));
        subscriptions.add(state.onXpGuides(this::refreshNow));
        subscriptions.add(state.onSpending(this::refreshNow));
        subscriptions.add(state.onGroupLabels(this::refreshNow));
        subscriptions.add(state.onShortNames(this::refreshNow));
        themeEpoch = router.motion().now();
    }

    /** The skill whose theme dresses this page, or null for the plain book. */
    protected String themeSkill() {
        return null;
    }

    /** How tiles on this page find and animate their skills' themes. */
    protected final TileTheming tileTheming() {
        return tileTheming;
    }

    /** The page's theme as laid out, or null for the plain book. */
    final PageTheme pageTheme() {
        return pageTheme;
    }

    /** The theme's leaf: 0 for an open spread, 1 for a single page on the lead's side, 2 on the partner's. */
    private int themeLeaf() {
        if (book == null || book.open()) {
            return 0;
        }
        return leaf == 0 ? 1 : 2;
    }

    /**
     * What a size class gives a book. Small: one page and rows at their tightest. Medium: facing pages and rows up to
     * {@link TileGrid#MAX_PITCH} apart. Large: the same with 2 more units inside the cover all round.
     */
    record Density(boolean spreads, int edge, int maxPitch) {
        static Density of(UiBreakpoint size) {
            return switch (size) {
                case COMPACT -> new Density(false, 0, TileGrid.PITCH);
                case REGULAR -> new Density(true, 0, TileGrid.MAX_PITCH);
                case WIDE -> new Density(true, 2, TileGrid.MAX_PITCH);
            };
        }
    }

    /**
     * Adds the page's content to {@code flow} as whole blocks for pages {@code width} wide, as densely as
     * {@link Flow#density} says.
     */
    protected abstract void compose(Flow flow, UiDraw metrics, int width);

    /** How many ways, from the roomiest to the densest, the page can compose itself; the book takes the first that fits. */
    protected int densities() {
        return 1;
    }

    /** Reads the client state again and updates the page in place. */
    protected abstract void refresh();

    public final Route route() {
        return route;
    }

    protected final ScreenRouter router() {
        return router;
    }

    public final Snackbar snackbar() {
        return snackbar;
    }

    /** The leaf showing, from 0. */
    public final int leaf() {
        return leaf;
    }

    /** How many leaves the content takes. */
    public final int leaves() {
        if (pinned) {
            return Math.max(1, sheets.size() - 1);
        }
        return Math.max(1, (sheets.size() + pagesPerLeaf() - 1) / pagesPerLeaf());
    }

    /** Whether the book shows two pages side by side. */
    protected final boolean spread() {
        return book != null && book.open();
    }

    /** Turns to leaf {@code target}, kept within the book. */
    public final void turnTo(int target) {
        int clamped = Math.max(0, Math.min(leaves() - 1, target));
        if (clamped != leaf) {
            leaf = clamped;
            showLeaf();
            if (focused() != null && !focused().shown()) {
                focus(null);
            }
        }
    }

    /** Turns to the leaf that holds {@code node}, if any page holds it, and scrolls a list that holds it to it. */
    public final void reveal(UiNode node) {
        int found = leafOf(node);
        if (found >= 0) {
            turnTo(found);
        }
        for (UiNode ancestor = node.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (ancestor instanceof ScrollArea area) {
                area.reveal(node.bounds());
            }
        }
    }

    /** Starts the page from its first leaf at the next layout, as after a new search. */
    protected final void restart() {
        restart = true;
        requestLayout();
    }

    /** The header chip with {@code chipId}, created on first use; chips sit at the right end of the header in call order. */
    protected final StatChip chip(String chipId) {
        StatChip chip = (StatChip) chips.find(chipId);
        return chip != null ? chip : chips.add(new StatChip(chipId, null, "", ""));
    }

    /** Puts {@code node} in the header after the chips. */
    protected final <T extends UiNode> T addToHeader(T node) {
        return chips.add(node);
    }

    /** Puts {@code node} in the header before the chips, at most {@code width} wide. */
    /**
     * Names what the right page holds in its header, before the chips, so the page itself can start at the top; on a
     * single page the screen heads that content itself.
     */
    protected final void setPageTitle(String text) {
        pageTitle = frame.add(new SectionHeading("page-title", text).unruled());
    }

    /** Puts {@code node}, a control of the page's own as small as a checkbox, right after the page's title in the header. */
    protected final void setTitleMark(UiNode node) {
        titleMark = frame.add(node);
    }

    /** Stands {@code node} in the outer margin of the page the content turns on, from the page's top to its foot. */
    protected final void setGutter(UiNode node) {
        gutter = frame.add(node);
    }

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
        state.put("leaf", Integer.toString(leaf));
        state.put("leaves", Integer.toString(leaves()));
        state.put("density", Integer.toString(density));
        state.put("pending", pendingAction == null ? "" : pendingAction);
        state.put("message", snackbar.live() ? snackbar.label() : "");
        state.put("messageTone", snackbar.tone().name());
    }

    static String routeId(Route route) {
        return switch (route) {
            case Route.Skills ignored -> "skills";
            case Route.Skill skill -> "skill:" + skill.skillId();
            case Route.Adaptation adaptation -> "adaptation:" + adaptation.adaptationId();
            case Route.Mutate mutate -> "mutate:" + mutate.skillId();
        };
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        boolean plain = event.modifiers().equals(UiModifiers.NONE) && overlays().isEmpty() && !editingText();
        UiKey key = event.key();
        if (plain && (key == UiKey.PAGE_UP || key == UiKey.PAGE_DOWN)) {
            turnTo(leaf + (key == UiKey.PAGE_DOWN ? 1 : -1));
            return true;
        }
        if (plain && (key == UiKey.LEFT || key == UiKey.RIGHT) && leaves() > 1
                && (focused() == null || UiFocus.toward(UiFocus.order(root()), focused(), key) == null)) {
            turnTo(leaf + (key == UiKey.RIGHT ? 1 : -1));
            return true;
        }
        if (super.keyPressed(event)) {
            return true;
        }
        boolean bare = event.modifiers().equals(UiModifiers.NONE);
        if (key == UiKey.ESCAPE || key == UiKey.BACKSPACE && !editingText()) {
            router.back();
            return true;
        }
        if (key == UiKey.E && bare && !editingText()) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(UiScrollEvent event) {
        if (super.mouseScrolled(event)) {
            return true;
        }
        if (!overlays().isEmpty() || event.vertical() == 0.0D) {
            return false;
        }
        turnTo(leaf + (event.vertical() < 0.0D ? 1 : -1));
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        for (ClientSubscription subscription : subscriptions) {
            subscription.cancel();
        }
        subscriptions.clear();
        router.remember(route, leaf);
    }

    @Override
    protected void layoutScreen(UiRect screen, UiDraw metrics) {
        boolean fresh = book == null || solved != layoutVersion();
        if (fresh) {
            UiNode anchor = restart ? null : anchor();
            book = solve(metrics);
            solved = layoutVersion();
            if (!leafRestored) {
                leafRestored = true;
                leaf = router.leaf(route);
            } else if (restart) {
                leaf = 0;
            } else if (anchor != null && leafOf(anchor) >= 0) {
                leaf = leafOf(anchor);
            }
            restart = false;
            leaf = Math.max(0, Math.min(leaves() - 1, leaf));
        }
        place(book, screen, metrics);
        showLeaf();
    }

    /** The first widget showing on the open leaf, which a new layout keeps in sight. */
    private UiNode anchor() {
        int first = pinned ? leaf + 1 : leaf * pagesPerLeaf();
        int last = pinned ? first + 1 : first + pagesPerLeaf();
        for (int index = first; index < Math.min(sheets.size(), last); index++) {
            UiNode found = firstLeafNode(sheets.get(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static UiNode firstLeafNode(UiNode node) {
        for (UiNode child : node.children()) {
            if (child.visible()) {
                UiNode found = firstLeafNode(child);
                return found != null ? found : child;
            }
        }
        return null;
    }

    private int leafOf(UiNode node) {
        for (int index = 0; index < sheets.size(); index++) {
            if (sheets.get(index).isAncestorOf(node)) {
                return pinned ? (index == 0 ? leaf : index - 1) : index / pagesPerLeaf();
            }
        }
        return -1;
    }

    private int pagesPerLeaf() {
        return book != null && book.open() ? 2 : 1;
    }

    private void showLeaf() {
        for (int index = 0; index < sheets.size(); index++) {
            sheets.get(index).setVisible(pinned ? index == 0 || index - 1 == leaf : index / pagesPerLeaf() == leaf);
        }
        boolean single = book != null && !book.open();
        previous.setVisible(single && leaf > 0);
        next.setVisible(single && leaf < leaves() - 1);
    }

    /** The book for the current screen and its content poured into pages: see the class comment. */
    private Book solve(UiDraw metrics) {
        UiTheme theme = theme();
        Density room = Density.of(breakpoint());
        UiRect area = new UiRect(0, 0, width(), height()).inset(MARGIN);
        int chrome = (theme.frame() + theme.padding() + room.edge()) * 2;
        int headerGap = theme.gap() * 2;
        int messageHeight = snackbar.preferredHeight(area.width(), metrics);
        int messageRoom = messageHeight + 2 - MARGIN;
        pageTheme = tileTheming.theme(metrics.sprites(), themeSkill());
        int reserve = pageTheme != null ? THEME_FOOT : 0;
        int height = Math.min(BOOK_HEIGHT, Math.max(0, area.height() - messageRoom));
        int widest = Math.max(0, Math.min(Math.min(BOOK_WIDTH, area.width()), UiLayout.widest(height)) - chrome);
        boolean open = room.spreads();
        int side = open ? UiLayout.snap(Math.max(0, widest - SPREAD_GAP) / 2) : UiLayout.snap(widest);
        int inner = open ? side * 2 + SPREAD_GAP : side;
        int header = open || headerFits(metrics, side, headerGap) ? BAR : BAR * 2 + headerGap;
        int body = Math.max(0, height - chrome - header - theme.padding() - RULE - (open ? 0 : FOOT + FOOT_GAP) - reserve);
        Pagination.Poured poured = null;
        for (int level = 0; level < densities(); level++) {
            Flow flow = new Flow(open, body, level);
            compose(flow, metrics, side);
            poured = Pagination.pour(flow, side, body, metrics, flow.pitchHeld() ? 0 : room.maxPitch() - TileGrid.PITCH, open ? 2 : 1);
            density = level;
            if (poured.pages().size() <= 2) {
                break;
            }
        }
        pinned = poured.pinned();
        fill(poured.pages());
        return new Book(inner + chrome, height, room.edge(), side, open, header, body, messageHeight, messageRoom, reserve);
    }

    /** Puts each page's blocks on its sheet, adding sheets as the flow needs them and dropping the rest. */
    private void fill(List<List<Placed>> pages) {
        for (Sheet sheet : sheets) {
            sheet.clear();
        }
        while (sheets.size() < pages.size()) {
            sheets.add(frame.add(new Sheet("sheet:" + sheets.size())));
        }
        while (sheets.size() > Math.max(1, pages.size())) {
            frame.remove(sheets.remove(sheets.size() - 1));
        }
        for (int index = 0; index < pages.size(); index++) {
            sheets.get(index).hold(pages.get(index));
        }
    }

    /**
     * The book as laid out: its size, the margin inside the cover, a page's width, whether two pages show, the header's
     * height, the pages' height and the message slip.
     */
    private record Book(int width, int height, int edge, int side, boolean open, int header, int body, int messageHeight, int messageRoom,
                        int reserve) {
    }

    private void place(Book shape, UiRect screen, UiDraw metrics) {
        UiTheme theme = theme();
        int pad = theme.padding();
        int gap = theme.gap() * 2;
        UiRect area = screen.inset(MARGIN);
        UiRect centred = UiLayout.center(area, shape.width(), shape.height());
        int lift = Math.max(0, centred.bottom() - (area.bottom() - shape.messageRoom()));
        UiRect box = new UiRect(centred.x(), Math.max(area.y(), centred.y() - lift), centred.width(), centred.height());
        frame.place(box, metrics);
        UiRect inner = frame.contentBounds().inset(shape.edge());
        UiRect left = new UiRect(inner.x(), inner.y(), shape.side(), inner.height());
        UiRect right = shape.open() ? new UiRect(inner.right() - shape.side(), inner.y(), shape.side(), inner.height()) : left;
        frame.pages = shape.open() ? List.of(left, right) : List.of(left);
        frame.fold = shape.open() ? left.right() + SPREAD_GAP / 2 : Integer.MIN_VALUE;
        UiRect second = shape.header() > BAR ? new UiRect(left.x(), left.y() + shape.header() - BAR, left.width(), BAR) : right.top(BAR);
        placeHeader(left.top(BAR), second, metrics, gap);
        int bodyTop = inner.y() + shape.header() + pad + RULE;
        frame.divider = inner.y() + shape.header() + (pad + RULE - UiTome.DIVIDER) / 2;
        for (int index = 0; index < sheets.size(); index++) {
            UiRect page = shape.open() && (pinned ? index > 0 : index % 2 == 1) ? right : left;
            sheets.get(index).reach = pageTheme != null;
            sheets.get(index).place(new UiRect(page.x(), bodyTop, page.width(), shape.body()), metrics);
        }
        int footY = inner.bottom() - FOOT - shape.reserve();
        frame.footY = footY;
        previous.place(new UiRect(left.x(), footY, TURN_WIDTH, FOOT), metrics);
        next.place(new UiRect(right.right() - TURN_WIDTH, footY, TURN_WIDTH, FOOT), metrics);
        if (gutter != null) {
            gutter.place(new UiRect(right.right(), bodyTop, theme.padding() + shape.edge(), shape.body()), metrics);
        }
        snackbar.place(new UiRect(box.x(), box.bottom() + 2, box.width(), shape.messageHeight()), metrics);
        frame.inner = inner;
        frame.bodyTop = bodyTop;
        frame.bodyBottom = inner.bottom() - shape.reserve();
        frame.header = shape.header();
        themedBook = null;
    }

    /** The themed book for the leaf showing, laid out once per layout and leaf. */
    private ThemedBook themedBook() {
        if (pageTheme == null || book == null || frame.pages.isEmpty()) {
            return null;
        }
        int themeLeaf = themeLeaf();
        if (themedBook == null || themedLeaf != themeLeaf) {
            themedLeaf = themeLeaf;
            themedBook = ThemedBook.of(geometry(themeLeaf), pageTheme);
        }
        return themedBook;
    }

    /** Where this book's parts are for its theme, and what its content keeps clear. */
    private BookGeometry geometry(int themeLeaf) {
        UiRect box = frame.bounds();
        UiRect inner = frame.inner;
        UiRect first = frame.pages.get(0);
        BookGeometry.Page left = themeLeaf == 2 ? null : new BookGeometry.Page(first.x(), first.right());
        BookGeometry.Page right = themeLeaf == 1 ? null
                : new BookGeometry.Page(frame.pages.get(frame.pages.size() - 1).x(), frame.pages.get(frame.pages.size() - 1).right());
        int top = inner.y();
        List<BookGeometry.Box> keep = new ArrayList<>();
        for (UiRect page : frame.pages) {
            keep.add(new BookGeometry.Box(page.x() - 1, frame.bodyTop - 1, page.right() + 1, frame.bodyBottom));
            keep.add(new BookGeometry.Box(page.x(), frame.divider - 2, page.right(), frame.divider + 5));
        }
        int ribbonEnd = frame.ribbon.isEmpty() ? first.x() + BAR : frame.ribbon.right();
        int rightStart = Integer.MAX_VALUE;
        for (UiNode node : headerRight()) {
            if (node != null && node.visible() && !node.bounds().isEmpty() && node.bounds().y() < top + BAR) {
                rightStart = Math.min(rightStart, node.bounds().x());
            }
        }
        int hangTo;
        if (book.open()) {
            hangTo = first.right() - 2;
            UiRect second = frame.pages.get(frame.pages.size() - 1);
            int from = rightStart == Integer.MAX_VALUE ? second.x() + 8 : Math.min(second.x() + 8, rightStart - 1);
            keep.add(new BookGeometry.Box(from, top - 1, second.right() + 1, top + BAR + 1));
        } else {
            hangTo = Math.min(first.right(), rightStart) - 2;
            if (rightStart != Integer.MAX_VALUE) {
                keep.add(new BookGeometry.Box(rightStart - 1, top - 1, first.right() + 1, top + BAR + 1));
            }
            if (frame.header > BAR) {
                keep.add(new BookGeometry.Box(first.x() - 1, top + frame.header - BAR - 1, first.right() + 1, top + frame.header + 1));
            }
        }
        keep.add(new BookGeometry.Box(first.x() - 1, top - 1, ribbonEnd + 1, top + BAR + 1));
        BookGeometry.Box bookBox = new BookGeometry.Box(box.x(), box.y(), box.right(), box.bottom());
        return new BookGeometry(themeLeaf, bookBox, top, frame.bodyTop, frame.divider, frame.fold, left, right, theme().frame(),
                ribbonEnd + 3, hangTo, keep, width(), height());
    }

    private List<UiNode> headerRight() {
        List<UiNode> nodes = new ArrayList<>();
        nodes.add(chips);
        if (titleMark != null) {
            nodes.add(titleMark);
        }
        if (tool != null) {
            nodes.add(tool);
        }
        if (pageTitle != null) {
            nodes.add(pageTitle);
        }
        return nodes;
    }

    /**
     * Whether one page's header line holds back, the current page's crumb, the chips and the tool at its least; when
     * it does not, the tool and the chips take a second line.
     */
    private boolean headerFits(UiDraw metrics, int width, int gap) {
        int tool = this.tool != null && this.tool.visible() ? TOOL_LEAST + gap : 0;
        int chipsWidth = chipsWidth(metrics);
        return BAR + gap + crumbs.minimumWidth(metrics) + RIBBON_TAIL + gap + tool + chipsWidth <= width;
    }

    /**
     * Back and the breadcrumbs from the left page's left edge, the chips and then the tool against the right page's
     * right edge. When both halves share one line, the tool takes what the current page's crumb leaves it, and the
     * breadcrumbs take the rest.
     */
    private void placeHeader(UiRect leftBar, UiRect rightBar, UiDraw metrics, int gap) {
        boolean shared = leftBar.equals(rightBar);
        int chipsWidth = chipsWidth(metrics);
        int right = rightBar.right();
        chips.place(chipsWidth == 0 ? UiRect.EMPTY : new UiRect(right - chipsWidth, rightBar.y(), chipsWidth, BAR), metrics);
        right -= chipsWidth == 0 ? 0 : chipsWidth + gap;
        int crumbsFrom = leftBar.x() + BAR + gap;
        if (tool != null) {
            int start = shared ? crumbsFrom + crumbs.minimumWidth(metrics) + RIBBON_TAIL + gap : rightBar.x();
            int room = right - start;
            int cell = tool.visible() && room >= TOOL_LEAST ? Math.min(toolWidth, room) : 0;
            tool.place(cell == 0 ? UiRect.EMPTY : centred(new UiRect(right - cell, rightBar.y(), cell, BAR), theme().controlHeight()), metrics);
            right -= cell == 0 ? 0 : cell + gap;
        }
        if (pageTitle != null) {
            boolean shown = !shared && book != null && book.open();
            pageTitle.setVisible(shown);
            int titleRight = titleMark == null ? right : Math.min(right, rightBar.x() + metrics.textWidth(pageTitle.label()));
            pageTitle.place(shown ? UiRect.of(rightBar.x(), rightBar.y(), Math.max(rightBar.x(), titleRight), rightBar.bottom()) : UiRect.EMPTY,
                    metrics);
            if (titleMark != null) {
                int markX = rightBar.x() + metrics.textWidth(pageTitle.label()) + TITLE_MARK_GAP;
                int markWidth = titleMark.preferredWidth(metrics);
                int markHeight = Math.min(BAR, titleMark.preferredHeight(markWidth, metrics));
                boolean room = shown && markX + markWidth <= right;
                titleMark.place(room ? new UiRect(markX, rightBar.y() + (BAR - markHeight) / 2, markWidth, markHeight) : UiRect.EMPTY, metrics);
            }
        }
        back.place(new UiRect(leftBar.x(), leftBar.y(), BAR, BAR), metrics);
        int crumbsTo = (shared ? right : leftBar.right()) - RIBBON_TAIL;
        crumbs.place(UiRect.of(crumbsFrom, leftBar.y(), Math.max(crumbsFrom, crumbsTo), leftBar.bottom()), metrics);
        int lettered = crumbs.bounds().x();
        for (UiNode crumb : crumbs.children()) {
            lettered = crumb.bounds().isEmpty() ? lettered : Math.max(lettered, crumb.bounds().right());
        }
        frame.ribbon = crumbs.bounds().isEmpty() ? UiRect.EMPTY : UiRect.of(crumbs.bounds().x() - RIBBON_LEAD, leftBar.y(),
                lettered + RIBBON_TAIL, leftBar.bottom());
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

    /**
     * The blocks a page pours into the book, in reading order. {@link #heading} and {@link #rule} keep with the next block,
     * {@link #text} may give up height down to its least, {@link #row} is a row of tiles whose gap may grow when the
     * book has height to spare, {@link #foot} keeps to the bottom of its page and {@link #nextPage} starts a new page.
     * {@link #balance} shares the last leaf's blocks evenly between its two pages.
     */
    static final class Flow {
        private final boolean spread;
        private final int height;
        private final int density;
        private final List<Block> blocks = new ArrayList<>();
        private boolean balanced;
        private boolean pinned;
        private boolean pitchHeld;

        Flow(boolean spread, int height, int density) {
            this.spread = spread;
            this.height = height;
            this.density = density;
        }

        /** How densely to compose, from 0, the roomiest, up to the page's {@link AdaptScreen#densities} less one. */
        int density() {
            return density;
        }

        /** Whether the book shows two pages side by side. */
        boolean spread() {
            return spread;
        }

        /** The height of a page. */
        int height() {
            return height;
        }

        /** Keeps the blocks before the first page break on the left page of every leaf while the rest turns on the right. */
        void pinFirstPage() {
            pinned = true;
        }

        boolean pinned() {
            return pinned;
        }

        /** Keeps rows at their own gaps: the book never spreads them apart to fill a page. */
        void holdPitch() {
            pitchHeld = true;
        }

        boolean pitchHeld() {
            return pitchHeld;
        }

        void add(UiNode node) {
            add(node, PAGE_GAP);
        }

        void add(UiNode node, int gap) {
            blocks.add(new Block(node, Kind.ITEM, gap, -1));
        }

        void heading(UiNode node) {
            heading(node, SECTION_GAP);
        }

        void heading(UiNode node, int gap) {
            blocks.add(new Block(node, Kind.HEADING, gap, -1));
        }

        /** A rule over what follows it: it never ends a page and is left out where it would open one. */
        void rule(UiNode node, int gap) {
            blocks.add(new Block(node, Kind.RULE, gap, -1));
        }

        void text(UiNode node, int least) {
            blocks.add(new Block(node, Kind.ITEM, PAGE_GAP, least));
        }

        void row(UiNode node, int gap) {
            blocks.add(new Block(node, Kind.ROW, gap, -1));
        }

        void foot(UiNode node) {
            foot(node, PAGE_GAP);
        }

        void foot(UiNode node, int gap) {
            blocks.add(new Block(node, Kind.FOOT, gap, -1));
        }

        void nextPage() {
            blocks.add(new Block(null, Kind.BREAK, 0, -1));
        }

        void balance() {
            balanced = true;
        }

        List<Block> blocks() {
            return blocks;
        }

        boolean balanced() {
            return balanced;
        }
    }

    enum Kind {
        ITEM,
        HEADING,
        RULE,
        ROW,
        FOOT,
        BREAK
    }

    /** One block of a flow: {@code least} is the height running text may shrink to, or -1 for a block of fixed height. */
    record Block(UiNode node, Kind kind, int gap, int least) {
    }

    /** A block placed on a page, {@code y} down from the page's top. */
    record Placed(UiNode node, int y, int height) {
    }

    /** One page of the book: holds the blocks poured into it, clipped to the page. */
    private static final class Sheet extends UiGroup {
        private final Map<UiNode, Placed> spots = new HashMap<>();
        /** Whether hover frames may reach past the page, as they do on a themed book. */
        private boolean reach;

        private Sheet(String id) {
            super(id);
        }

        @Override
        public String kind() {
            return "page";
        }

        @Override
        public boolean clipsChildren() {
            return true;
        }

        void clear() {
            spots.clear();
            clearChildren();
        }

        void hold(List<Placed> blocks) {
            for (Placed block : blocks) {
                spots.put(block.node(), block);
                add(block.node());
            }
        }

        @Override
        protected void paintChildren(UiFrame frame) {
            if (!reach) {
                super.paintChildren(frame);
                return;
            }
            UiRect box = bounds();
            frame.draw().pushScissor(box.x() - TileFrame.REACH_X, box.y() - TileFrame.REACH_Y, box.right() + TileFrame.REACH_X,
                    box.bottom() + TileFrame.REACH_Y);
            for (UiNode child : children()) {
                if (child.bounds().intersects(box)) {
                    child.render(frame);
                }
            }
            frame.draw().popScissor();
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            for (UiNode child : children()) {
                Placed spot = spots.get(child);
                if (spot != null) {
                    child.place(new UiRect(box.x(), box.y() + spot.y(), box.width(), spot.height()), metrics);
                }
            }
        }
    }

    /**
     * The book: cover and paper, the gutter down the whole height with the cover's hinge above and below it when two
     * pages show, a bookmark over the top edge beside the fold, the title ribbon behind the breadcrumbs, a ruled
     * divider under each page's header and, when the book has more than one leaf, the number of each page that turns at its foot.
     */
    private final class Frame extends Panel {
        private int divider = Integer.MIN_VALUE;
        private int fold = Integer.MIN_VALUE;
        private int footY;
        private List<UiRect> pages = List.of();
        private UiRect ribbon = UiRect.EMPTY;
        private UiRect inner = UiRect.EMPTY;
        private int bodyTop;
        private int bodyBottom;
        private int header;

        private Frame() {
            super("frame", null);
            setLayout((List<UiNode> children, UiRect area, UiDraw metrics) -> {
            });
        }

        @Override
        protected void paint(UiFrame frame) {
            ThemedBook themed = themedBook();
            if (themed != null) {
                paintThemed(frame, themed);
                return;
            }
            super.paint(frame);
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            int cover = theme.frame();
            if (fold != Integer.MIN_VALUE) {
                UiTome.spine(draw, fold, box.y() + cover, box.bottom() - cover);
                UiTome.hinge(draw, fold, box.y() - OVERHANG + 1, cover + OVERHANG - 1);
                UiTome.hinge(draw, fold, box.bottom() - cover, cover + OVERHANG_FAR - 1);
            }
            int middle = fold != Integer.MIN_VALUE ? fold : box.centerX();
            UiTome.bookmark(draw, middle + UiTome.SPINE_WIDTH / 2 + 4, box.y() - OVERHANG, cover + OVERHANG);
            if (!ribbon.isEmpty()) {
                UiTome.ribbon(draw, ribbon, theme);
            }
            for (int index = 0; index < pages.size(); index++) {
                UiRect page = pages.get(index);
                if (divider != Integer.MIN_VALUE) {
                    UiTome.divider(draw, page.x(), page.right(), divider);
                }
                paintNumber(draw, theme, page, index);
            }
        }

        /** The book dressed in its skill's theme: the themed cover, edges and dividers, and the title on its paper slip. */
        private void paintThemed(UiFrame frame, ThemedBook themed) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            long millis = tileTheming.still() ? 0L : tileTheming.millis();
            themed.paintBack(draw, millis);
            if (!ribbon.isEmpty()) {
                themed.ribbon(draw, ribbon.x(), ribbon.y(), ribbon.width());
            }
            if (pageTitle != null && pageTitle.visible() && !pageTitle.bounds().isEmpty()) {
                themed.slip(draw, pageTitle.bounds().x(), pageTitle.bounds().y(), draw.textWidth(pageTitle.label()));
            }
            for (int index = 0; index < pages.size(); index++) {
                UiRect page = pages.get(index);
                if (divider != Integer.MIN_VALUE) {
                    themed.divider(draw, page.x(), page.right(), divider);
                }
                paintNumber(draw, theme, page, index);
            }
        }

        @Override
        protected void paintOver(UiFrame frame) {
            UiDraw draw = frame.draw();
            for (int index = 0; index < deferred.size(); index++) {
                RowTile tile = deferred.get(index);
                UiRect page = sheetOf(tile);
                if (page.isEmpty()) {
                    continue;
                }
                draw.pushScissor(page.x() - TileFrame.REACH_X, page.y() - TileFrame.REACH_Y, page.right() + TileFrame.REACH_X,
                        page.bottom() + TileFrame.REACH_Y);
                tile.paintFrame(draw);
                draw.popScissor();
            }
            deferred.clear();
            ThemedBook themed = themedBook();
            if (themed != null) {
                themed.paintFront(frame.draw(), tileTheming.still() ? 0L : tileTheming.millis());
            }
        }

        /** The number of a single page that turns, at its foot. */
        /** The page {@code node} lies on, or an empty box when it is on none. */
        private UiRect sheetOf(UiNode node) {
            for (UiNode current = node; current != null; current = current.parent()) {
                if (current instanceof Sheet sheet) {
                    return sheet.bounds();
                }
            }
            return UiRect.EMPTY;
        }

        private void paintNumber(UiDraw draw, UiTheme theme, UiRect page, int index) {
            if (leaves() < 2 || pinned && index == 0 || pages.size() > 1) {
                return;
            }
            int sheet = pinned ? leaf + 1 : leaf * pages.size() + index;
            if (sheet >= sheets.size() || sheets.get(sheet).children().isEmpty()) {
                return;
            }
            String text = Integer.toString(pinned ? leaf + 1 : sheet + 1);
            draw.text(text, page.centerX() - draw.textWidth(text) / 2, footY + (FOOT - draw.lineHeight()) / 2 + 1,
                    theme.textMuted(), false);
        }
    }
}
