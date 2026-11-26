package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiScrollEvent;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The window a skill page's adaptations scroll in when they do not fit the page. It rests only with a row or a heading
 * at its top, a group's first row resting under its heading, so no card is cut there. The wheel glides one row a notch,
 * Page Up and Page Down a window, keyboard focus brings its card's row in, and the bar beside the page
 * ({@link ScrollRail}) drags it and settles on the nearest row when let go. Its full width stays the grid's, the bar
 * standing in the page's margin.
 */
final class GridScroll extends ScrollArea {
    static final long GLIDE_MILLIS = 140L;
    private static final int FADE = 6;
    private static final float FADE_DEPTH = 200.0F;

    private final int view;
    private final Set<UiNode> headings;
    private final State state;
    private final List<Integer> stops = new ArrayList<>();
    private boolean held;

    /** Where a page's grid rests and glides, kept by the page across the layouts that rebuild the grid. */
    static final class State {
        private int rest;
        private int from;
        private long start;
        private boolean gliding;
        private double notches;

        State(int rest) {
            this.rest = rest;
        }

        /** Where the grid rests, or is gliding to, from the top of its list. */
        int rest() {
            return rest;
        }
    }

    GridScroll(UiNode list, int view, Set<UiNode> headings, State state) {
        super("adaptations.scroll", list);
        this.view = view;
        this.headings = headings;
        this.state = state;
    }

    @Override
    protected boolean ownsBar() {
        return false;
    }

    /** Where the window may rest, from the top of the list, the last the furthest it scrolls. */
    List<Integer> stops() {
        return List.copyOf(stops);
    }

    /** Whether the window rests at, or glides to, its end. */
    boolean atEnd() {
        return state.rest >= maxScroll();
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return view;
    }

    @Override
    protected void layout(UiDraw metrics) {
        super.layout(metrics);
        stops.clear();
        int top = content().bounds().y();
        int max = maxScroll();
        boolean afterHeading = false;
        for (UiNode block : content().children()) {
            int offset = block.bounds().y() - top;
            if (!afterHeading && offset < max) {
                stops.add(offset);
            }
            afterHeading = headings.contains(block);
        }
        stops.add(max);
        if (held) {
            return;
        }
        state.rest = nearest(state.rest);
        if (state.gliding) {
            glideStep();
        } else {
            super.setScroll(state.rest);
        }
    }

    @Override
    public void setScroll(int value) {
        state.gliding = false;
        super.setScroll(value);
        state.rest = scroll();
    }

    @Override
    public void reveal(UiRect target) {
        UiRect window = bounds();
        int at = scroll();
        if (target.y() < window.y()) {
            setScroll(atOrBefore(at - (window.y() - target.y())));
        } else if (target.bottom() > window.bottom()) {
            setScroll(atOrAfter(at + Math.min(target.bottom() - window.bottom(), target.y() - window.y())));
        }
    }

    @Override
    public boolean mouseScrolled(UiScrollEvent event) {
        if (maxScroll() == 0) {
            return false;
        }
        state.notches += event.vertical();
        int target = state.rest;
        while (Math.abs(state.notches) >= 1.0D) {
            int step = state.notches > 0 ? -1 : 1;
            state.notches += step;
            target = step(target, step);
        }
        glide(target);
        return true;
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        if (maxScroll() == 0) {
            return false;
        }
        switch (event.key()) {
            case PAGE_UP -> page(-1);
            case PAGE_DOWN -> page(1);
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    /** Glides {@code rows} rows down, up for a negative count. */
    void rows(int rows) {
        glide(step(state.rest, rows));
    }

    /** Glides a window down, up for a negative {@code direction}. */
    void page(int direction) {
        glide(direction > 0 ? atOrBefore(state.rest + bounds().height()) : atOrAfter(state.rest - bounds().height()));
    }

    /** Holds the window at {@code fraction} of the way down while the bar is dragged. */
    void dragTo(double fraction) {
        held = true;
        state.gliding = false;
        super.setScroll((int) Math.round(Math.max(0.0D, Math.min(1.0D, fraction)) * maxScroll()));
    }

    /** Lets go of a drag: glides to the nearest resting place. */
    void settle() {
        held = false;
        glide(nearest(scroll()));
    }

    @Override
    protected void paint(UiFrame frame) {
        if (state.gliding && !held) {
            glideStep();
        }
    }

    @Override
    protected void paintOver(UiFrame frame) {
        if (scroll() >= maxScroll()) {
            return;
        }
        UiDraw draw = frame.draw();
        UiRect box = bounds();
        int page = frame.theme().backdrop();
        draw.pushPose();
        draw.depth(FADE_DEPTH);
        draw.gradient(box.x(), box.bottom() - FADE, box.right(), box.bottom(), page & 0x00FFFFFF, page);
        draw.popPose();
    }

    private void glide(int target) {
        state.from = scroll();
        state.rest = target;
        state.start = services().motion().now();
        state.gliding = target != scroll();
    }

    private void glideStep() {
        float progress = services().motion().progress(state.start, GLIDE_MILLIS);
        super.setScroll(Math.round(UiEase.lerp(state.from, state.rest, UiEase.outCubic(progress))));
        state.gliding = progress < 1.0F;
    }

    /** The stop {@code count} stops on from {@code from}, down for positive counts. */
    private int step(int from, int count) {
        int index = stops.indexOf(nearest(from));
        return stops.get(Math.max(0, Math.min(stops.size() - 1, index + count)));
    }

    private int nearest(int position) {
        int best = 0;
        for (int stop : stops) {
            if (Math.abs(stop - position) < Math.abs(best - position)) {
                best = stop;
            }
        }
        return best;
    }

    private int atOrBefore(int position) {
        int best = 0;
        for (int stop : stops) {
            if (stop <= position) {
                best = stop;
            }
        }
        return best;
    }

    private int atOrAfter(int position) {
        for (int stop : stops) {
            if (stop >= position) {
                return stop;
            }
        }
        return stops.get(stops.size() - 1);
    }
}
