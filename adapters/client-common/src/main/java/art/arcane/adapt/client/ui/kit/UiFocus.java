package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiKey;

import java.util.ArrayList;
import java.util.List;

/** Keyboard focus traversal: tree order for Tab, nearest neighbour for the arrow keys. */
public final class UiFocus {
    private UiFocus() {
    }

    /** Focusable, visible, enabled nodes under {@code root} in tree order. */
    public static List<UiNode> order(UiNode root) {
        List<UiNode> order = new ArrayList<>();
        collect(root, order);
        return order;
    }

    /** The node after (or before) {@code current}, wrapping around; the first (or last) one when nothing is focused. */
    public static UiNode next(List<UiNode> order, UiNode current, boolean backward) {
        if (order.isEmpty()) {
            return null;
        }
        int index = order.indexOf(current);
        if (index < 0) {
            return backward ? order.get(order.size() - 1) : order.get(0);
        }
        int size = order.size();
        return order.get(((backward ? index - 1 : index + 1) % size + size) % size);
    }

    /**
     * The closest node in the arrow's direction, preferring nodes that line up with {@code current}; null when there
     * is none. With nothing focused, the first node.
     */
    public static UiNode toward(List<UiNode> order, UiNode current, UiKey direction) {
        if (order.isEmpty()) {
            return null;
        }
        if (current == null || !order.contains(current)) {
            return order.get(0);
        }
        UiRect from = current.bounds();
        UiNode best = null;
        long bestScore = Long.MAX_VALUE;
        for (UiNode candidate : order) {
            if (candidate == current) {
                continue;
            }
            UiRect to = candidate.bounds();
            int primary;
            int secondary;
            switch (direction) {
                case LEFT -> {
                    primary = from.centerX() - to.centerX();
                    secondary = gap(from.y(), from.bottom(), to.y(), to.bottom());
                }
                case RIGHT -> {
                    primary = to.centerX() - from.centerX();
                    secondary = gap(from.y(), from.bottom(), to.y(), to.bottom());
                }
                case UP -> {
                    primary = from.centerY() - to.centerY();
                    secondary = gap(from.x(), from.right(), to.x(), to.right());
                }
                case DOWN -> {
                    primary = to.centerY() - from.centerY();
                    secondary = gap(from.x(), from.right(), to.x(), to.right());
                }
                default -> {
                    return null;
                }
            }
            if (primary <= 0) {
                continue;
            }
            long score = (long) primary + (long) secondary * 4L;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static int gap(int startA, int endA, int startB, int endB) {
        if (endB <= startA) {
            return startA - endB + 1;
        }
        if (startB >= endA) {
            return startB - endA + 1;
        }
        return 0;
    }

    private static void collect(UiNode node, List<UiNode> order) {
        if (!node.visible() || !node.enabled()) {
            return;
        }
        if (node.focusable()) {
            order.add(node);
        }
        for (UiNode child : node.children()) {
            collect(child, order);
        }
    }
}
