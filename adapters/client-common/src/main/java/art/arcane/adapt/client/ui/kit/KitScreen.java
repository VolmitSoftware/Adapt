package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.qa.UiProbe;
import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiInput;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiScreen;
import art.arcane.adapt.client.ui.UiScrollEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A screen built from a {@link UiNode} tree. Subclasses add widgets to {@link #root()} and place them in
 * {@link #layoutScreen}; the screen handles layout on resize, hover, pointer capture, focus and keyboard navigation
 * (Tab, arrows, Enter/Space, Escape), overlays, tooltips, the open transition and the QA probe.
 */
public abstract class KitScreen extends UiScreen {
    public static final long OPEN_MILLIS = 160L;
    public static final int OPEN_DISTANCE = 6;
    private static final long PROBE_INTERVAL_MILLIS = 100L;

    private final UiGroup root;
    private final List<UiOverlay> overlays = new ArrayList<>();
    private final Map<UiOverlay, UiNode> returnFocus = new HashMap<>();
    private final Services services = new Services();
    private UiTheme theme;
    private final UiMotion motion;
    private UiNode focused;
    private String focusedId;
    private UiNode hovered;
    private UiNode pressed;
    private boolean focusVisible;
    private boolean layoutDirty = true;
    private boolean opened;
    private long openedAt;
    private int laidOffset = Integer.MIN_VALUE;
    private int laidWidth = -1;
    private int laidHeight = -1;
    private long probedAt = Long.MIN_VALUE;
    private double guiScale = 1.0;

    protected KitScreen(String title, UiTheme theme, UiMotion motion) {
        super(title);
        this.theme = Objects.requireNonNull(theme, "theme");
        this.motion = Objects.requireNonNull(motion, "motion");
        root = new UiGroup("root", (List<UiNode> children, UiRect area, UiDraw metrics) -> layoutScreen(area, metrics));
        root.bindServices(services);
    }

    /** Places the root's children inside {@code screen}, the whole screen (shifted during the open transition). */
    protected abstract void layoutScreen(UiRect screen, UiDraw metrics);

    /** Screen kind reported by the QA probe. */
    protected String probeKind() {
        return "kit";
    }

    /** Title reported by the QA probe. */
    protected String probeTitle() {
        return title();
    }

    /** Screen-level state reported by the QA probe. */
    protected void describe(Map<String, String> state) {
    }

    public final UiNode root() {
        return root;
    }

    public final UiTheme theme() {
        return theme;
    }

    public void setTheme(UiTheme newTheme) {
        theme = Objects.requireNonNull(newTheme, "theme");
        layoutDirty = true;
    }

    public final UiMotion motion() {
        return motion;
    }

    public final UiServices services() {
        return services;
    }

    public final UiBreakpoint breakpoint() {
        return UiBreakpoint.of(width(), height());
    }

    public final UiNode focused() {
        return focused;
    }

    /** The topmost node under the pointer at the last frame. */
    public final UiNode hovered() {
        return hovered;
    }

    public final List<UiOverlay> overlays() {
        return Collections.unmodifiableList(overlays);
    }

    public final void requestLayout() {
        layoutDirty = true;
    }

    /** Moves keyboard focus; null clears it. Nodes that cannot take focus are ignored. */
    public final void focus(UiNode node) {
        if (node != null && (!node.focusable() || !node.enabled() || !node.shown() || !attached(node))) {
            return;
        }
        if (node == focused) {
            return;
        }
        UiNode previous = focused;
        focused = node;
        focusedId = node == null ? null : node.id();
        if (previous != null) {
            previous.notifyFocus(false);
        }
        if (node != null) {
            node.notifyFocus(true);
            reveal(node);
        }
    }

    public final void openOverlay(UiOverlay overlay) {
        Objects.requireNonNull(overlay, "overlay");
        if (overlays.contains(overlay)) {
            return;
        }
        overlay.bindServices(services);
        overlays.add(overlay);
        returnFocus.put(overlay, focused);
        layoutDirty = true;
        UiNode initial = overlay.initialFocus();
        if (initial != null) {
            focus(initial);
        }
    }

    public final void closeOverlay(UiOverlay overlay) {
        if (!overlays.remove(overlay)) {
            return;
        }
        UiNode restore = returnFocus.remove(overlay);
        if (focused != null && overlay.isAncestorOf(focused)) {
            focus(restore != null && attached(restore) ? restore : null);
        }
        if (pressed != null && overlay.isAncestorOf(pressed)) {
            pressed = null;
        }
        overlay.bindServices(null);
        layoutDirty = true;
    }

    /** Lays the tree out now with {@code metrics}; normally done by {@link #render} when something changed. */
    public final void layoutNow(UiDraw metrics) {
        int offset = openOffset();
        root.place(new UiRect(0, offset, width(), height()), metrics);
        UiRect screen = new UiRect(0, 0, width(), height());
        for (UiOverlay overlay : overlays) {
            overlay.place(screen, metrics);
        }
        laidOffset = offset;
        laidWidth = width();
        laidHeight = height();
        layoutDirty = false;
        restoreFocus();
    }

    @Override
    protected void init() {
        layoutDirty = true;
        if (!opened) {
            opened = true;
            openedAt = motion.now();
        }
    }

    @Override
    public void render(UiDraw draw, int mouseX, int mouseY, float delta) {
        draw.legacyPalette(theme.legacyColours());
        try {
            renderInk(draw, mouseX, mouseY);
        } finally {
            draw.legacyPalette(null);
        }
    }

    private void renderInk(UiDraw draw, int mouseX, int mouseY) {
        guiScale = draw.guiScale();
        boolean relaid = layoutDirty || laidWidth != width() || laidHeight != height() || laidOffset != openOffset();
        if (relaid) {
            layoutNow(draw);
        }
        hovered = hit(mouseX, mouseY);
        UiFrame frame = new UiFrame(draw, theme, motion, mouseX, mouseY, hovered, focused, pressed, focusVisible);
        draw.pushPose();
        root.render(frame);
        for (UiOverlay overlay : overlays) {
            draw.nextLayer();
            if (overlay.modal()) {
                draw.fill(0, 0, width(), height(), theme.scrim());
            }
            overlay.render(frame);
        }
        renderTooltip(draw, mouseX, mouseY);
        draw.popPose();
        long now = motion.now();
        if (relaid || UiProbe.watched() && now - probedAt >= PROBE_INTERVAL_MILLIS) {
            probedAt = now;
            UiProbe.publish(this, probe());
        }
    }

    /** The QA probe JSON for the current layout. */
    public final String probe() {
        Map<String, String> state = new LinkedHashMap<>();
        describe(state);
        return UiProbe.json(new UiProbe.Scene(probeKind(), probeTitle(), state, width(), height(), guiScale, root, overlays, focused,
                hovered));
    }

    @Override
    public void removed() {
        UiProbe.withdraw(this);
    }

    @Override
    public boolean mouseClicked(UiMouseEvent event) {
        focusVisible = false;
        UiOverlay top = topOverlay();
        UiNode target = hit(event.x(), event.y());
        if (top != null && !top.modal() && (target == null || !top.isAncestorOf(target))) {
            dismiss(top);
            return true;
        }
        if (target == null) {
            focus(null);
            return top != null;
        }
        UiNode focusTarget = target;
        while (focusTarget != null && !focusTarget.focusable()) {
            focusTarget = focusTarget.parent();
        }
        focus(focusTarget);
        for (UiNode node = target; node != null; node = node.parent()) {
            if (node.enabled() && node.mousePressed(event)) {
                pressed = node;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(UiMouseEvent event) {
        UiNode node = pressed;
        pressed = null;
        return node != null && node.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(UiMouseEvent event, double dragX, double dragY) {
        return pressed != null && pressed.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        focusVisible = false;
    }

    @Override
    public boolean mouseScrolled(UiScrollEvent event) {
        for (UiNode node = hit(event.x(), event.y()); node != null; node = node.parent()) {
            if (node.enabled() && node.mouseScrolled(event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        for (UiNode node = focused; node != null; node = node.parent()) {
            if (node.enabled() && node.keyPressed(event)) {
                return true;
            }
        }
        UiKey key = event.key();
        switch (key) {
            case TAB -> {
                focusVisible = true;
                focus(UiFocus.next(focusOrder(), focused, event.modifiers().shift()));
                return true;
            }
            case LEFT, RIGHT, UP, DOWN -> {
                List<UiNode> order = focusOrder();
                if (order.isEmpty()) {
                    return false;
                }
                focusVisible = true;
                UiNode target = UiFocus.toward(order, focused, key);
                if (target != null) {
                    focus(target);
                }
                return true;
            }
            case ENTER, SPACE -> {
                if (focused != null && focused.enabled() && focused.activate()) {
                    focusVisible = true;
                    return true;
                }
                return false;
            }
            case ESCAPE -> {
                UiOverlay top = topOverlay();
                if (top != null) {
                    dismiss(top);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(UiCharEvent event) {
        for (UiNode node = focused; node != null; node = node.parent()) {
            if (node.enabled() && node.charTyped(event)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the focused node is taking typed text. */
    public final boolean editingText() {
        return focused != null && focused.capturesText() && focused.enabled();
    }

    /** The topmost node at the point: overlays first, a modal overlay blocks everything below it. */
    public final UiNode hit(double x, double y) {
        for (int index = overlays.size() - 1; index >= 0; index--) {
            UiOverlay overlay = overlays.get(index);
            UiNode node = overlay.nodeAt(x, y);
            if (node != null) {
                return node;
            }
            if (overlay.modal()) {
                return overlay;
            }
        }
        return root.nodeAt(x, y);
    }

    private UiOverlay topOverlay() {
        return overlays.isEmpty() ? null : overlays.get(overlays.size() - 1);
    }

    private void dismiss(UiOverlay overlay) {
        closeOverlay(overlay);
        overlay.dismissed();
    }

    private List<UiNode> focusOrder() {
        UiOverlay top = topOverlay();
        return UiFocus.order(top == null ? root : top);
    }

    private int openOffset() {
        if (!opened) {
            return 0;
        }
        float progress = motion.progress(openedAt, OPEN_MILLIS);
        return Math.round((1.0F - UiEase.outCubic(progress)) * OPEN_DISTANCE);
    }

    private boolean attached(UiNode node) {
        if (root.isAncestorOf(node)) {
            return true;
        }
        for (UiOverlay overlay : overlays) {
            if (overlay.isAncestorOf(node)) {
                return true;
            }
        }
        return false;
    }

    private void restoreFocus() {
        if (focused != null && attached(focused) && focused.shown() && focused.enabled()) {
            return;
        }
        String id = focusedId;
        UiNode replacement = null;
        if (id != null) {
            UiOverlay top = topOverlay();
            replacement = (top == null ? root : top).find(id);
        }
        UiNode previous = focused;
        focused = null;
        if (previous != null) {
            previous.notifyFocus(false);
        }
        if (replacement != null) {
            focus(replacement);
        }
        focusedId = focused == null ? id : focused.id();
        if (pressed != null && !attached(pressed)) {
            pressed = null;
        }
    }

    private void reveal(UiNode node) {
        for (UiNode ancestor = node.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (ancestor instanceof ScrollArea area) {
                area.reveal(node.bounds());
            }
        }
    }

    private void renderTooltip(UiDraw draw, int mouseX, int mouseY) {
        if (pressed != null) {
            return;
        }
        UiNode source = focusVisible && focused != null ? focused : hovered;
        Tooltip tooltip = null;
        for (UiNode node = source; node != null && tooltip == null; node = node.parent()) {
            tooltip = node.tooltip();
            if (node instanceof UiOverlay) {
                break;
            }
        }
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }
        draw.nextLayer();
        if (source == focused && focusVisible) {
            UiRect bounds = focused.bounds();
            tooltip.render(draw, theme, bounds.right() - 4, bounds.y() + 12, width(), height());
        } else {
            tooltip.render(draw, theme, mouseX, mouseY, width(), height());
        }
    }

    private final class Services implements UiServices {
        @Override
        public UiTheme theme() {
            return theme;
        }

        @Override
        public UiMotion motion() {
            return motion;
        }

        @Override
        public UiModifiers modifiers() {
            UiInput input = input();
            return input == null ? UiModifiers.NONE : input.modifiers();
        }

        @Override
        public void playClick() {
            UiHost host = host();
            if (host != null) {
                host.playClick();
            }
        }

        @Override
        public String clipboard() {
            UiHost host = host();
            String text = host == null ? null : host.clipboard();
            return text == null ? "" : text;
        }

        @Override
        public void setClipboard(String text) {
            UiHost host = host();
            if (host != null) {
                host.setClipboard(text);
            }
        }

        @Override
        public UiNode focused() {
            return focused;
        }

        @Override
        public void focus(UiNode node) {
            KitScreen.this.focus(node);
        }

        @Override
        public void openOverlay(UiOverlay overlay) {
            KitScreen.this.openOverlay(overlay);
        }

        @Override
        public void closeOverlay(UiOverlay overlay) {
            KitScreen.this.closeOverlay(overlay);
        }

        @Override
        public void requestLayout() {
            layoutDirty = true;
        }
    }
}
