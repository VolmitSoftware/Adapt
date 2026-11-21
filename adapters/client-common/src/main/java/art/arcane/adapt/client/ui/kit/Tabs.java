package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A tab strip. Tabs share the width equally; the selected tab is a raised paper tab underlined in the accent colour,
 * the others bare muted text on the rule. Click to select, or Left/Right while the strip is focused.
 */
public class Tabs extends UiNode {
    private final List<String> labels = new ArrayList<>();
    private int selected;
    private IntConsumer onSelect;

    public Tabs(String id, List<String> labels, int selected, IntConsumer onSelect) {
        super(id);
        this.labels.addAll(labels);
        this.selected = this.labels.isEmpty() ? -1 : Math.max(0, Math.min(this.labels.size() - 1, selected));
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
    }

    @Override
    public String kind() {
        return "tabs";
    }

    @Override
    public String label() {
        return selected < 0 ? "" : LegacyText.strip(labels.get(selected));
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("selected", Integer.toString(selected));
        state.put("tabs", String.join("|", labels.stream().map(LegacyText::strip).toList()));
    }

    public int selected() {
        return selected;
    }

    public List<String> labels() {
        return List.copyOf(labels);
    }

    public Tabs setLabels(List<String> newLabels) {
        labels.clear();
        labels.addAll(newLabels);
        selected = labels.isEmpty() ? -1 : Math.max(0, Math.min(labels.size() - 1, selected));
        return this;
    }

    /** Selects without notifying the listener. */
    public Tabs setSelected(int index) {
        if (index >= 0 && index < labels.size()) {
            selected = index;
        }
        return this;
    }

    public Tabs setOnSelect(IntConsumer listener) {
        onSelect = Objects.requireNonNull(listener, "onSelect");
        return this;
    }

    /** Selects tab {@code index} with the click sound and notifies the listener when it changed. */
    public void select(int index) {
        if (index < 0 || index >= labels.size() || index == selected || !enabled()) {
            return;
        }
        selected = index;
        services().playClick();
        onSelect.accept(index);
    }

    /** The tab under pointer x, or -1. */
    public int tabAt(double x) {
        if (labels.isEmpty()) {
            return -1;
        }
        List<UiRect> cells = cells();
        for (int index = 0; index < cells.size(); index++) {
            UiRect cell = cells.get(index);
            if (x >= cell.x() && x < cell.right()) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public boolean focusable() {
        return !labels.isEmpty();
    }

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return services().theme().controlHeight();
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        if (event.button() != UiMouseButton.LEFT) {
            return false;
        }
        select(tabAt(event.x()));
        return true;
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        switch (event.key()) {
            case LEFT -> select(Math.max(0, selected - 1));
            case RIGHT -> select(Math.min(labels.size() - 1, selected + 1));
            default -> {
                return false;
            }
        }
        return true;
    }

    private List<UiRect> cells() {
        int[] widths = new int[labels.size()];
        return UiLayout.row(bounds(), 0, widths);
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        boolean active = enabled();
        draw.fill(box.x(), box.bottom() - 1, box.right(), box.bottom(), theme.surfaceBorder());
        List<UiRect> cells = cells();
        int hoveredTab = active && frame.isHovered(this) ? tabAt(frame.mouseX()) : -1;
        int pressedTab = frame.isPressed(this) ? hoveredTab : -1;
        for (int index = 0; index < cells.size(); index++) {
            UiRect cell = cells.get(index);
            boolean chosen = index == selected;
            UiControl.State state = !active ? UiControl.State.DISABLED : index == pressedTab ? UiControl.State.PRESSED
                    : index == hoveredTab ? UiControl.State.HOVER : UiControl.State.REST;
            if (chosen && active) {
                UiTome.soft(draw, cell.x(), cell.y(), cell.right(), cell.bottom(), theme.surface());
                UiTome.softEdge(draw, cell.x(), cell.y(), cell.width(), cell.height(), theme.surfaceBorder());
                draw.fill(cell.x() + 1, cell.bottom() - 2, cell.right() - 1, cell.bottom(), theme.accent());
            } else if (chosen) {
                UiControl.stitchedEdge(draw, cell.x(), cell.y(), cell.width(), cell.height(), theme.textDisabled());
            } else if (state == UiControl.State.HOVER || state == UiControl.State.PRESSED) {
                UiTome.soft(draw, cell.x(), cell.y(), cell.right(), cell.bottom() - 1, UiTome.HOVER);
            }
            String line = LegacyText.truncate(draw, labels.get(index), cell.width() - 4);
            int color = !active ? theme.textDisabled() : chosen ? theme.text() : theme.textMuted();
            draw.centeredText(line, cell.centerX(), UiControl.textY(cell, state), color, false);
        }
        if (frame.showsFocus(this) && selected >= 0) {
            UiControl.focusRing(draw, cells.get(selected), theme);
        }
    }
}
