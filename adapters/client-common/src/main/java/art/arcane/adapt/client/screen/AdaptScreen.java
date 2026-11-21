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
 * The frame every Adapt page shares: an open book centred over the world and sized to its pages, with a one-line top
 * bar (back, the breadcrumbs on a ribbon, an optional tool, small stat chips) above a ruled divider, and one page
 * below it or two facing pages across the spine when the screen has room for both. The page scrolls only
 * when it cannot fit the screen at all. Messages show on a paper slip under the book, which always leaves room for it, so
 * they never cover the page. Escape and Backspace go back one level, E closes. The page follows the client state live
 * while open; requests the page sends show a waiting state until the server answers with {@code Feedback}, and nothing
 * on the page changes before the server's own update arrives.
 */
public abstract class AdaptScreen extends KitScreen {
    public static final long ANSWER_TIMEOUT_MILLIS = 8000L;
    public static final int PAGE_GAP = 3;
    static final int BAR = 16;
    /** Room for the ruled divider under the top bar, on top of the padding. */
    static final int RULE = 2;
    /** Width between facing pages: the spine and a little paper either side. */
    static final int SPREAD_GAP = UiTome.SPINE_WIDTH + 4;
    private static final int MIN_PAGE = 140;
    private static final int MARGIN = 2;
    private static final int MIN_WIDTH = 220;
    private static final int CRUMBS_NATURAL = 200;

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
     * The width the page would like, at most {@code available}; the panel grows to it and never past the screen.
     * {@code room} is the page height that shows without scrolling.
     */
    protected abstract int pageWidth(UiDraw metrics, int available, int room);

    /**
     * The width the facing page would like, or 0 when the screen has nothing for it. With room for both the pages open
     * side by side across the spine; otherwise the facing page follows the first one below a divider.
     */
    protected int facingWidth(UiDraw metrics) {
        return 0;
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
        UiTheme theme = theme();
        int pad = theme.padding();
        int gap = theme.gap() * 2;
        UiRect area = screen.inset(MARGIN);
        int chrome = theme.frame() * 2 + pad * 2;
        int maxInner = Math.max(0, area.width() - chrome);
        int chipsWidth = chipsWidth(metrics);
        int shownTool = tool == null || !tool.visible() ? 0 : toolWidth;
        int barWidth = BAR + gap + Math.max(crumbs.minimumWidth(metrics), Math.min(CRUMBS_NATURAL, crumbs.preferredWidth(metrics))) + (shownTool > 0 ? shownTool + gap : 0)
                + (chipsWidth > 0 ? chipsWidth + gap : 0);
        int messageHeight = snackbar.preferredHeight(maxInner, metrics);
        int messageRoom = messageHeight + 2 - MARGIN;
        int bodyRoom = Math.max(0, area.height() - chrome - BAR - pad - RULE - messageRoom);
        int minWidth = Math.max(MIN_WIDTH, barWidth);
        int facingNeed = facing.preferredHeight(maxInner, metrics) > 0 ? facingWidth(metrics) : 0;
        int innerWidth = 0;
        spread.open = false;
        if (facingNeed > 0 && maxInner - facingNeed - SPREAD_GAP >= MIN_PAGE) {
            int left = Math.max(MIN_PAGE, pageWidth(metrics, maxInner - facingNeed - SPREAD_GAP, bodyRoom));
            int right = Math.max(facingNeed, minWidth - left - SPREAD_GAP);
            if (left + SPREAD_GAP + right <= maxInner && page.preferredHeight(left, metrics) <= bodyRoom
                    && facing.preferredHeight(right, metrics) <= bodyRoom) {
                spread.open = true;
                spread.left = left;
                innerWidth = left + SPREAD_GAP + right;
            }
        }
        if (!spread.open) {
            int facingHeight = facingNeed > 0 ? facing.preferredHeight(maxInner, metrics) + Spread.STACK_GAP : 0;
            innerWidth = Math.min(maxInner, Math.max(Math.max(minWidth, facingNeed),
                    pageWidth(metrics, maxInner, Math.max(0, bodyRoom - facingHeight))));
        }
        int bodyHeight = Math.min(bodyRoom, spread.preferredHeight(innerWidth, metrics));
        UiRect centred = UiLayout.center(area, innerWidth + chrome, chrome + BAR + pad + RULE + bodyHeight);
        int lift = Math.max(0, centred.bottom() - (area.bottom() - messageRoom));
        UiRect box = new UiRect(centred.x(), Math.max(area.y(), centred.y() - lift), centred.width(), centred.height());
        frame.place(box, metrics);
        UiRect inner = frame.contentBounds();
        UiRect bar = inner.top(BAR);
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
        crumbs.place(UiRect.of(bar.x() + BAR + gap, bar.y(), Math.max(bar.x() + BAR + gap, right), bar.bottom()), metrics);
        int bodyTop = bar.bottom() + pad + RULE;
        frame.divider = bar.bottom() + (pad + RULE - UiTome.DIVIDER) / 2;
        frame.ribbon = crumbs.bounds().isEmpty() ? UiRect.EMPTY : UiRect.of(crumbs.bounds().x() - 4, bar.y(),
                Math.min(crumbs.bounds().right(), crumbs.bounds().x() + crumbs.preferredWidth(metrics)) + 8, bar.bottom());
        UiRect body = UiRect.of(inner.x(), bodyTop, inner.right(), inner.bottom());
        scroll.place(body, metrics);
        if (!scrollRestored) {
            scrollRestored = true;
            scroll.setScroll(router.scroll(route));
        }
        snackbar.place(new UiRect(box.x(), box.bottom() + 2, box.width(), messageHeight), metrics);
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

    /** The book: cover, page, the title ribbon behind the breadcrumbs and a ruled divider under the top bar. */
    private static final class Frame extends Panel {
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
            if (!ribbon.isEmpty()) {
                UiTome.ribbon(frame.draw(), ribbon, frame.theme());
            }
            if (divider != Integer.MIN_VALUE) {
                UiTome.divider(frame.draw(), box.x() + frame.theme().frame(), box.right() - frame.theme().frame(), divider);
            }
        }
    }

    /**
     * The two pages: side by side across the spine when {@link #open}, else the facing page under the first one below a
     * divider.
     */
    private final class Spread extends UiGroup {
        static final int STACK_GAP = PAGE_GAP * 2 + UiTome.DIVIDER;
        private boolean open;
        private int left;

        private Spread() {
            super("spread");
            add(page);
            add(facing);
            setLayout(new UiLayoutPolicy() {
                @Override
                public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                    if (open) {
                        page.place(new UiRect(area.x(), area.y(), left, area.height()), metrics);
                        facing.place(UiRect.of(area.x() + left + SPREAD_GAP, area.y(), area.right(), area.bottom()), metrics);
                        return;
                    }
                    int first = page.preferredHeight(area.width(), metrics);
                    page.place(new UiRect(area.x(), area.y(), area.width(), first), metrics);
                    int below = facing.preferredHeight(area.width(), metrics);
                    int top = first == 0 ? area.y() : area.y() + first + STACK_GAP;
                    facing.place(below == 0 ? UiRect.EMPTY : new UiRect(area.x(), top, area.width(), below), metrics);
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
            if (second.isEmpty()) {
                return;
            }
            if (open) {
                UiTome.spine(frame.draw(), box.x() + left + SPREAD_GAP / 2, box.y(), box.bottom());
            } else if (page.bounds().height() > 0) {
                UiTome.divider(frame.draw(), box.x(), box.right(), second.y() - PAGE_GAP - UiTome.DIVIDER);
            }
        }
    }
}
