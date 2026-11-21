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
 * <p>One model sizes every page. From the scaled screen, its {@link UiBreakpoint} and what the pages say they need
 * ({@link #pageSpan}, {@link #facingSpan}, {@link #arrange}) the book tries its possible shapes and keeps the best
 * one: a shape that does not scroll before one that does, book proportions before a tall strip, facing pages before a
 * single one where the size class allows them and the second page has enough to show, a facing page wide enough for
 * the columns it wants beside a narrower first page before two even ones, pages their content fills
 * sideways, and then the widest shape whose spare height the rows and margins can take up ({@link #slack}). So the
 * height follows the content and the frame is never wider than 16:9. It tries the widths the pages want first and
 * wider ones only when those scroll. Spare height goes to a small margin above and below the pages, then to the row
 * pitch, then to the margins again; pages of text with actions at their foot ({@link #footed}) keep those margins
 * small and leave the rest between the text and the actions. Small screens get one page with tight rows, medium and
 * large ones facing pages with rows that breathe and bounded margins. The page scrolls only when no shape fits the
 * screen. A resize solves the book again and keeps the first widget showing in a scrolled page where it was.
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
    static final int MIN_PAGE = 140;
    private static final int MARGIN = 2;
    private static final int MIN_WIDTH = 220;
    private static final int CRUMBS_NATURAL = 200;
    /** How far the title ribbon reaches past the breadcrumbs on each side. */
    private static final int RIBBON_LEAD = 4;
    private static final int RIBBON_TAIL = 8;
    /** The widest a page grows before it would read as a banner; a facing page wider by nature keeps its width. */
    static final int MAX_PAGE = 280;
    /** Widths the book tries are this far apart. */
    private static final int STEP = UiLayout.UNIT * 2;
    /** Blank paper a shape may keep beyond what its rows and margins take up before a narrower one is preferred. */
    private static final int TOLERANCE = 8;
    /** How much narrower than its page a page's content may be before a shape that fits it closer is preferred. */
    private static final int LOOSE = 32;
    /** A facing page opens only when its content fills at least one part in this many of its height. */
    private static final int SPARSE = 4;

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
    private int floor;

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

    /** The narrowest a page may get before it is cramped, and the widest it wants to be unless that would scroll. */
    record Span(int least, int most) {
        static final Span NONE = new Span(0, 0);
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

    /** The widths the first page can take. */
    protected abstract Span pageSpan(UiDraw metrics);

    /**
     * The widths the facing page can take, or {@link Span#NONE} when the screen has nothing for it. With room for both
     * the pages open side by side across the spine; otherwise the facing page follows the first one below a divider.
     */
    protected Span facingSpan(UiDraw metrics) {
        return Span.NONE;
    }

    /**
     * Arranges the first page for {@code width} with {@code room} of height before it scrolls, and returns the width
     * it uses. Called for every shape the book tries; {@code split} lets a page that divides itself over two facing
     * pages do so.
     */
    protected int arrange(UiDraw metrics, int width, int room, boolean split) {
        return width;
    }

    /** The narrowest the inside of the book gets, which the top bar sets; a page given less still ends up this wide. */
    protected final int floor() {
        return floor;
    }

    /** Whether the page as last arranged divided itself over two facing pages. */
    protected boolean split() {
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

    /** The right page; see {@link #facingWidth}. */
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

    /** The book's shape for the current screen: see the class comment. */
    private Book solve(UiDraw metrics) {
        UiTheme theme = theme();
        UiBreakpoint size = breakpoint();
        Density density = Density.of(size);
        int gap = theme.gap() * 2;
        UiRect area = new UiRect(0, 0, width(), height()).inset(MARGIN);
        int chrome = (theme.frame() + theme.padding() + density.edge()) * 2;
        int head = BAR + theme.padding() + RULE;
        int messageHeight = snackbar.preferredHeight(area.width(), metrics);
        int messageRoom = messageHeight + 2 - MARGIN;
        int tallest = Math.max(0, area.height() - messageRoom);
        int widest = Math.min(area.width(), UiLayout.widest(size == UiBreakpoint.WIDE ? tallest : area.height()));
        int maxInner = UiLayout.snap(Math.max(0, widest - chrome));
        int chipsWidth = chipsWidth(metrics);
        int shownTool = tool == null || !tool.visible() ? 0 : toolWidth;
        int barWidth = BAR + gap + Math.max(crumbs.minimumWidth(metrics), Math.min(CRUMBS_NATURAL, crumbs.preferredWidth(metrics)))
                + RIBBON_TAIL + (shownTool > 0 ? shownTool + gap : 0) + (chipsWidth > 0 ? chipsWidth + gap : 0);
        floor = Math.min(maxInner, UiLayout.snapUp(Math.max(MIN_WIDTH, barWidth)));
        Span first = pageSpan(metrics);
        Span second = facing.preferredHeight(Math.max(1, maxInner), metrics) > 0 ? facingSpan(metrics) : Span.NONE;
        Sheet sheet = new Sheet(density, chrome, head, Math.max(0, tallest - chrome - head), floor, messageHeight, messageRoom,
                UiLayout.snapUp(second.most()));
        boolean hasFacing = second.most() > 0;
        int natural = UiLayout.snap(Math.max(first.most(), Math.min(MAX_PAGE, second.most())));
        boolean facingPages = density.spreads() && hasFacing;
        int half = UiLayout.snap((maxInner - SPREAD_GAP) / 2);
        int leastSide = UiLayout.snapUp(Math.max(Math.max(MIN_PAGE, first.least()), Math.max(second.least(), (floor - SPREAD_GAP + 1) / 2)));
        int wantedSide = Math.max(leastSide, natural);
        int least = Math.min(maxInner, UiLayout.snapUp(Math.max(floor, Math.max(first.least(), second.least()))));
        int wanted = Math.min(maxInner, Math.max(least, natural));
        Trial best = null;
        if (facingPages) {
            for (int side : widths(Math.min(half, wantedSide), leastSide, STEP)) {
                best = better(best, trial(sheet, new Shape(true, side, side, false, false), wantedSide * 2 + SPREAD_GAP, metrics));
            }
            int narrow = UiLayout.snapUp(Math.max(MIN_PAGE, first.least()));
            int wide = Math.min(sheet.facingMost(), maxInner - SPREAD_GAP - narrow);
            if (wide > MAX_PAGE) {
                for (int left : widths(Math.min(UiLayout.snap(first.most()), maxInner - SPREAD_GAP - wide), narrow, STEP)) {
                    best = better(best, trial(sheet, new Shape(true, left, wide, false, true), left + SPREAD_GAP + wide, metrics));
                }
            }
        }
        for (int width : widths(wanted, least, STEP)) {
            best = better(best, single(sheet, width, density.spreads() && !hasFacing, wanted, metrics));
        }
        if (best.overflow() > 0 || facingPages && !best.two()) {
            if (facingPages) {
                for (int side : widths(half, wantedSide + STEP, STEP * 2)) {
                    best = better(best, trial(sheet, new Shape(true, side, side, false, false), wantedSide * 2 + SPREAD_GAP, metrics));
                }
                int narrowFirst = UiLayout.snapUp(Math.max(MIN_PAGE, first.least()));
                for (int wide : widths(Math.min(UiLayout.snap(second.most()), maxInner - SPREAD_GAP - narrowFirst), half + STEP, STEP * 2)) {
                    best = better(best, trial(sheet, new Shape(true, narrowFirst, wide, false, true), maxInner, metrics));
                }
                int narrowSecond = UiLayout.snapUp(Math.max(MIN_PAGE, second.least()));
                for (int wide : widths(Math.min(UiLayout.snap(first.most()), maxInner - SPREAD_GAP - narrowSecond), half + STEP, STEP * 2)) {
                    best = better(best, trial(sheet, new Shape(true, wide, narrowSecond, false, true), maxInner, metrics));
                }
            }
            for (int width : widths(maxInner, wanted + STEP, STEP * 2)) {
                best = better(best, single(sheet, width, density.spreads() && !hasFacing, wanted, metrics));
            }
        }
        Trial chosen = trial(sheet, best.shape(), maxInner, metrics);
        int content = chosen.content();
        boolean rows = slack(density.maxPitch()) > 0;
        int spare = chosen.body() - content;
        if (spare > 0 && rows) {
            stretch(spare - Math.min(density.air(), spare), density.maxPitch());
            content = spread.preferredHeight(chosen.inner(), metrics);
        }
        int rest = Math.max(0, chosen.body() - content);
        int top = UiLayout.snap(footed() && !rows ? Math.min(rest / 2, density.air() / 2) : (rest + 1) / 2);
        int bottom = footed() && !rows ? top : rest - top;
        return new Book(chosen.inner(), chosen.body(), top, bottom, sheet);
    }

    /** The better of one page {@code width} wide as it is and, when {@code splits}, dividing itself in two. */
    private Trial single(Sheet sheet, int width, boolean splits, int wanted, UiDraw metrics) {
        Trial whole = trial(sheet, new Shape(false, width, 0, false, false), wanted, metrics);
        return splits ? better(whole, trial(sheet, new Shape(false, width, 0, true, false), wanted, metrics)) : whole;
    }

    /** Widths from {@code most} down to {@code least}, {@code step} apart and always ending on {@code least}. */
    private static List<Integer> widths(int most, int least, int step) {
        List<Integer> widths = new ArrayList<>();
        for (int width = most; width > least; width -= step) {
            widths.add(width);
        }
        if (most >= least) {
            widths.add(least);
        }
        return widths;
    }

    /** What the screen leaves a book: its density, cover and top bar sizes, page height and narrowest inside width. */
    private record Sheet(Density density, int chrome, int head, int room, int floor, int messageHeight, int messageRoom, int facingMost) {
    }

    /**
     * A shape the book could take: facing pages {@code left} and {@code right} wide when {@code open}, else one page
     * {@code left} wide that may {@code split} itself in two. {@code uneven} marks facing pages of different widths.
     */
    private record Shape(boolean open, int left, int right, boolean split, boolean uneven) {
    }

    /**
     * A shape tried on the pages: the width inside the cover, the height the pages need and the page height the book
     * gets, the blank paper nothing takes up, and how the shape ranks ({@code two} pages, book proportions, a facing
     * page too {@code sparse} to open, content {@code loose} in its page, how far {@code over} the width the pages
     * want, how much it would scroll). {@code shy} is how far a facing page that wants more than {@link #MAX_PAGE},
     * such as a checklist in two columns, falls short of it.
     */
    private record Trial(Shape shape, int inner, int content, int body, int dead, boolean two, boolean shapely, boolean sparse,
                         int shy, boolean loose, int over, int overflow) {
    }

    /** The shape the book settled on: the width inside the cover, the page height, and the margins above and below the pages. */
    private record Book(int inner, int body, int top, int bottom, Sheet sheet) {
    }

    /** Arranges the pages in {@code shape} and measures the result; {@code wanted} is the inside width the pages would like. */
    private Trial trial(Sheet sheet, Shape shape, int wanted, UiDraw metrics) {
        spread.open = shape.open();
        spread.left = shape.left();
        spread.right = shape.right();
        spread.top = 0;
        spread.bottom = 0;
        int inner;
        int page;
        int used;
        if (shape.open()) {
            used = arrange(metrics, shape.left(), sheet.room(), false);
            page = shape.left();
            inner = shape.left() + SPREAD_GAP + shape.right();
        } else {
            int below = facing.preferredHeight(shape.left(), metrics);
            used = arrange(metrics, shape.left(), Math.max(0, sheet.room() - (below > 0 ? below + Spread.STACK_GAP : 0)), shape.split());
            inner = below > 0 ? shape.left() : Math.min(shape.left(), Math.max(sheet.floor(), UiLayout.snapUp(used)));
            page = inner;
        }
        int content = spread.preferredHeight(inner, metrics);
        boolean two = shape.open() || split();
        int frameWidth = inner + sheet.chrome();
        int sixteen = Math.max(0, UiLayout.shortest(frameWidth) - sheet.chrome() - sheet.head());
        int body = Math.min(sheet.room(), Math.max(content, sixteen));
        int frameHeight = body + sheet.chrome() + sheet.head();
        int dead = Math.max(0, body - content - slack(sheet.density().maxPitch()) - sheet.density().air() * 2);
        boolean shapely = two ? frameWidth * 4 >= frameHeight * 5 : frameWidth * 4 >= frameHeight * 3;
        boolean sparse = shape.open() && facing.preferredHeight(shape.right(), metrics) * SPARSE < body;
        boolean loose = used + LOOSE < page && (shape.open() || page > sheet.floor());
        int shy = shape.open() ? Math.max(0, sheet.facingMost() - Math.max(MAX_PAGE, shape.right())) : 0;
        return new Trial(shape, inner, content, body, dead, two, shapely, sparse, shy, loose, Math.max(0, inner - wanted),
                Math.max(0, content - sheet.room()));
    }

    /** The better of two shapes, in the order the class comment gives. */
    private static Trial better(Trial held, Trial next) {
        if (held == null) {
            return next;
        }
        if (held.overflow() != next.overflow()) {
            return next.overflow() < held.overflow() ? next : held;
        }
        if (held.overflow() > 0) {
            return next.inner() > held.inner() ? next : held;
        }
        if (held.shapely() != next.shapely()) {
            return next.shapely() ? next : held;
        }
        if (held.sparse() != next.sparse()) {
            return next.sparse() ? held : next;
        }
        if (held.two() != next.two()) {
            return next.two() ? next : held;
        }
        if (held.shy() != next.shy()) {
            return next.shy() < held.shy() ? next : held;
        }
        if (held.shape().uneven() != next.shape().uneven()) {
            return next.shape().uneven() ? held : next;
        }
        if (held.loose() != next.loose()) {
            return next.loose() ? held : next;
        }
        boolean heldFills = held.dead() <= TOLERANCE;
        boolean nextFills = next.dead() <= TOLERANCE;
        if (heldFills != nextFills) {
            return nextFills ? next : held;
        }
        if (!heldFills && held.dead() != next.dead()) {
            return next.dead() < held.dead() ? next : held;
        }
        if (held.over() != next.over()) {
            return next.over() < held.over() ? next : held;
        }
        return next.inner() > held.inner() ? next : held;
    }

    private void place(Book shape, UiRect screen, UiDraw metrics) {
        UiTheme theme = theme();
        Sheet sheet = shape.sheet();
        int pad = theme.padding();
        int gap = theme.gap() * 2;
        int edge = sheet.density().edge();
        UiRect area = screen.inset(MARGIN);
        spread.top = shape.top();
        spread.bottom = shape.bottom();
        UiRect centred = UiLayout.center(area, shape.inner() + sheet.chrome(), sheet.chrome() + sheet.head() + shape.body());
        int lift = Math.max(0, centred.bottom() - (area.bottom() - sheet.messageRoom()));
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
        snackbar.place(new UiRect(box.x(), box.bottom() + 2, box.width(), sheet.messageHeight()), metrics);
    }

    /** Where a page that splits itself into two (see {@link TileGrid#spine}) has its spine, or {@link Integer#MIN_VALUE}. */
    protected int gutter() {
        return Integer.MIN_VALUE;
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
        int gutter = gutter();
        if (gutter != Integer.MIN_VALUE) {
            return gutter;
        }
        return spread.open && !facing.bounds().isEmpty() ? spread.bounds().x() + spread.left + SPREAD_GAP / 2 : Integer.MIN_VALUE;
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
     * The two pages: side by side across the fold when {@link #open}, else the facing page under the first one below a
     * divider. {@link #top} and {@link #bottom} are the margins the book keeps above and below them; height left over
     * goes to the pages, the first one when they are stacked, so its actions sit just above the divider.
     */
    private final class Spread extends UiGroup {
        static final int STACK_GAP = PAGE_GAP * 2 + UiTome.DIVIDER;
        private boolean open;
        private int left;
        private int right;
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
