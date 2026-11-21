package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.List;

/** Places a container's visible children inside its content area. */
@FunctionalInterface
public interface UiLayoutPolicy {
    void layout(List<UiNode> children, UiRect area, UiDraw metrics);

    /** Height the children need at {@code width}; 0 when the policy simply fills whatever it is given. */
    default int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
        return 0;
    }

    /** Every child fills the whole area. */
    static UiLayoutPolicy fill() {
        return (List<UiNode> children, UiRect area, UiDraw metrics) -> {
            for (UiNode child : children) {
                child.place(area, metrics);
            }
        };
    }

    /** Children top to bottom at full width and their preferred height; children preferring 0 share the rest. */
    static UiLayoutPolicy column(int gap) {
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                int[] heights = new int[children.size()];
                for (int index = 0; index < heights.length; index++) {
                    heights[index] = children.get(index).preferredHeight(area.width(), metrics);
                }
                List<UiRect> cells = UiLayout.column(area, gap, heights);
                for (int index = 0; index < heights.length; index++) {
                    children.get(index).place(cells.get(index), metrics);
                }
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                int total = 0;
                for (UiNode child : children) {
                    total += child.preferredHeight(width, metrics);
                }
                return total + Math.max(0, children.size() - 1) * gap;
            }
        };
    }

    /**
     * {@link #column(int)} with the children in {@code foot} held against the bottom of the area when it is taller
     * than the column needs, so a page's actions stay in one place whatever is above them.
     */
    static UiLayoutPolicy footed(int gap, UiNode... foot) {
        List<UiNode> held = List.of(foot);
        UiLayoutPolicy column = column(gap);
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                int needed = column.preferredHeight(children, area.width(), metrics);
                int spare = area.height() - needed;
                if (spare <= 0) {
                    column.layout(children, area, metrics);
                    return;
                }
                column.layout(children, area.top(needed), metrics);
                for (UiNode child : children) {
                    if (held.contains(child)) {
                        child.place(child.bounds().offset(0, spare), metrics);
                    }
                }
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                return column.preferredHeight(children, width, metrics);
            }
        };
    }

    /** Children left to right at full height and their preferred width; children preferring 0 share the rest. */
    static UiLayoutPolicy row(int gap) {
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                int[] widths = new int[children.size()];
                for (int index = 0; index < widths.length; index++) {
                    widths[index] = children.get(index).preferredWidth(metrics);
                }
                List<UiRect> cells = UiLayout.row(area, gap, widths);
                for (int index = 0; index < widths.length; index++) {
                    children.get(index).place(cells.get(index), metrics);
                }
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                int tallest = 0;
                for (UiNode child : children) {
                    tallest = Math.max(tallest, child.preferredHeight(Math.max(1, child.preferredWidth(metrics)), metrics));
                }
                return tallest;
            }
        };
    }

    /**
     * Children left to right at their preferred size, wrapping to a new line when the next one would overflow; each
     * line is as tall as its tallest child. A child wider than the area gets the full width.
     */
    static UiLayoutPolicy flow(int gap) {
        return flow(gap, gap);
    }

    /** {@link #flow(int)} with {@code gap} between children on a line and {@code lineGap} between lines. */
    static UiLayoutPolicy flow(int gap, int lineGap) {
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                flowLines(children, area, gap, lineGap, metrics, true);
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                return flowLines(children, new UiRect(0, 0, width, 0), gap, lineGap, metrics, false);
            }
        };
    }

    private static int flowLines(List<UiNode> children, UiRect area, int gap, int lineGap, UiDraw metrics, boolean place) {
        int x = area.x();
        int y = area.y();
        int lineHeight = 0;
        int used = 0;
        for (UiNode child : children) {
            int width = Math.min(area.width(), child.preferredWidth(metrics));
            int height = child.preferredHeight(width, metrics);
            if (x > area.x() && x + width > area.right()) {
                y += lineHeight + lineGap;
                x = area.x();
                lineHeight = 0;
            }
            if (place) {
                child.place(new UiRect(x, y, width, height), metrics);
            }
            x += width + gap;
            lineHeight = Math.max(lineHeight, height);
            used = y + lineHeight - area.y();
        }
        return used;
    }
}
