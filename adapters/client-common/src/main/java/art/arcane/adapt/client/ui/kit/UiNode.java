package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiScrollEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A widget in a screen's tree. Parents place children in {@link #layout(UiDraw)}; the screen routes pointer events to
 * the topmost hit node and bubbles them to its parents until one returns true, and routes keys to the focused node the
 * same way. Bounds are absolute scaled GUI coordinates.
 */
public abstract class UiNode {
    private final String id;
    private final List<UiNode> children = new ArrayList<>();
    private UiNode parent;
    private UiServices services;
    private UiRect bounds = UiRect.EMPTY;
    private boolean visible = true;
    private boolean enabled = true;
    private Tooltip tooltip;

    protected UiNode(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public final String id() {
        return id;
    }

    /** Short widget kind reported by the QA probe, such as "button". */
    public abstract String kind();

    /** Human-readable label reported by the QA probe. */
    public String label() {
        return "";
    }

    /** Adds this widget's state (value, selection, toggled, ...) for the QA probe. */
    public void describe(Map<String, String> state) {
    }

    public final UiRect bounds() {
        return bounds;
    }

    public final UiNode parent() {
        return parent;
    }

    public final List<UiNode> children() {
        return Collections.unmodifiableList(children);
    }

    public <T extends UiNode> T add(T child) {
        UiNode node = Objects.requireNonNull(child, "child");
        if (node.parent != null) {
            node.parent.remove(node);
        }
        children.add(node);
        node.parent = this;
        services().requestLayout();
        return child;
    }

    public void remove(UiNode child) {
        if (children.remove(child)) {
            child.parent = null;
            services().requestLayout();
        }
    }

    /** Replaces {@code old} with {@code replacement} at the same position. */
    public void replace(UiNode old, UiNode replacement) {
        int index = children.indexOf(old);
        if (index < 0) {
            add(replacement);
            return;
        }
        if (replacement.parent != null) {
            replacement.parent.remove(replacement);
            index = children.indexOf(old);
        }
        old.parent = null;
        children.set(index, replacement);
        replacement.parent = this;
        services().requestLayout();
    }

    public void clearChildren() {
        for (UiNode child : children) {
            child.parent = null;
        }
        children.clear();
        services().requestLayout();
    }

    public final boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            services().requestLayout();
        }
    }

    /** False when this node or any ancestor is disabled. */
    public final boolean enabled() {
        return enabled && (parent == null || parent.enabled());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Whether this node and all its ancestors are visible. */
    public final boolean shown() {
        return visible && (parent == null || parent.shown());
    }

    public Tooltip tooltip() {
        return tooltip;
    }

    public void setTooltip(Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    /** Whether keyboard focus can land here. */
    public boolean focusable() {
        return false;
    }

    /** Whether the pointer can hit this node itself (containers that only hold children return false). */
    public boolean hittable() {
        return false;
    }

    /** Whether children are clipped to this node's bounds, for drawing and hit testing. */
    public boolean clipsChildren() {
        return false;
    }

    /** Whether this node takes typed text while focused; the screen then gives it every key first. */
    public boolean capturesText() {
        return false;
    }

    public int preferredWidth(UiDraw metrics) {
        return 0;
    }

    public int preferredHeight(int width, UiDraw metrics) {
        return 0;
    }

    /** Sets the bounds and lays out the children. */
    public final void place(UiRect newBounds, UiDraw metrics) {
        bounds = Objects.requireNonNull(newBounds, "bounds");
        layout(metrics);
    }

    protected void layout(UiDraw metrics) {
    }

    /** Moves this node and its subtree without laying it out again. */
    public final void offset(int dx, int dy) {
        bounds = bounds.offset(dx, dy);
        for (UiNode child : children) {
            child.offset(dx, dy);
        }
    }

    public final void render(UiFrame frame) {
        if (!visible) {
            return;
        }
        paint(frame);
        paintChildren(frame);
        paintOver(frame);
    }

    protected void paint(UiFrame frame) {
    }

    protected void paintChildren(UiFrame frame) {
        if (children.isEmpty()) {
            return;
        }
        boolean clip = clipsChildren();
        if (clip) {
            frame.draw().pushScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        }
        for (UiNode child : children) {
            if (!clip || child.bounds.intersects(bounds)) {
                child.render(frame);
            }
        }
        if (clip) {
            frame.draw().popScissor();
        }
    }

    protected void paintOver(UiFrame frame) {
    }

    /** The topmost visible node at the point, or null. */
    public UiNode nodeAt(double x, double y) {
        if (!visible) {
            return null;
        }
        boolean inside = bounds.contains(x, y);
        if (clipsChildren() && !inside) {
            return null;
        }
        for (int index = children.size() - 1; index >= 0; index--) {
            UiNode hit = children.get(index).nodeAt(x, y);
            if (hit != null) {
                return hit;
            }
        }
        return inside && hittable() ? this : null;
    }

    public UiNode find(String nodeId) {
        if (id.equals(nodeId)) {
            return this;
        }
        for (UiNode child : children) {
            UiNode found = child.find(nodeId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public final boolean isAncestorOf(UiNode node) {
        for (UiNode current = node; current != null; current = current.parent) {
            if (current == this) {
                return true;
            }
        }
        return false;
    }

    public boolean mousePressed(UiMouseEvent event) {
        return false;
    }

    /** Sent to the node that took the press, wherever the pointer is now. */
    public boolean mouseReleased(UiMouseEvent event) {
        return false;
    }

    /** Sent to the node that took the press. */
    public boolean mouseDragged(UiMouseEvent event, double dragX, double dragY) {
        return false;
    }

    public boolean mouseScrolled(UiScrollEvent event) {
        return false;
    }

    public boolean keyPressed(UiKeyEvent event) {
        return false;
    }

    public boolean charTyped(UiCharEvent event) {
        return false;
    }

    /** Enter or Space on the focused node. */
    public boolean activate() {
        return false;
    }

    protected void focusChanged(boolean focused) {
    }

    public final boolean isFocused() {
        return services().focused() == this;
    }

    public final UiServices services() {
        UiNode top = this;
        while (top.parent != null) {
            top = top.parent;
        }
        return top.services == null ? UiServices.DETACHED : top.services;
    }

    final void bindServices(UiServices rootServices) {
        services = rootServices;
    }

    final void notifyFocus(boolean focused) {
        focusChanged(focused);
    }
}
