package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A path such as "Skills / Agility / Rift Blink". Every crumb but the last is a link that reports its index. When the
 * path does not fit, whole crumbs drop out from the start; the current page is cut only when it alone is wider than the
 * bar. Drawn in the title ribbon's light lettering, the current page in gold. Crumb ids are {@code <id>:<index>}.
 */
public class Breadcrumbs extends UiNode {
    public static final String SEPARATOR = " / ";

    private final List<Crumb> crumbs = new ArrayList<>();
    private IntConsumer onSelect;

    public Breadcrumbs(String id, IntConsumer onSelect) {
        super(id);
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
    }

    @Override
    public String kind() {
        return "breadcrumbs";
    }

    @Override
    public String label() {
        List<String> labels = new ArrayList<>();
        for (Crumb crumb : crumbs) {
            labels.add(crumb.label());
        }
        return String.join(SEPARATOR, labels);
    }

    public Breadcrumbs setOnSelect(IntConsumer listener) {
        onSelect = Objects.requireNonNull(listener, "onSelect");
        return this;
    }

    /** Replaces the path; unchanged labels keep their nodes, so focus and hover survive. */
    public Breadcrumbs setCrumbs(List<String> labels) {
        boolean same = labels.size() == crumbs.size();
        for (int index = 0; same && index < labels.size(); index++) {
            same = crumbs.get(index).text.equals(labels.get(index));
        }
        if (same) {
            return this;
        }
        clearChildren();
        crumbs.clear();
        for (int index = 0; index < labels.size(); index++) {
            crumbs.add(add(new Crumb(id() + ":" + index, index, Objects.requireNonNull(labels.get(index), "label"))));
        }
        return this;
    }

    public List<String> labels() {
        List<String> labels = new ArrayList<>();
        for (Crumb crumb : crumbs) {
            labels.add(crumb.text);
        }
        return labels;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        int width = 0;
        for (Crumb crumb : crumbs) {
            width += metrics.textWidth(crumb.text);
        }
        return width + Math.max(0, crumbs.size() - 1) * metrics.textWidth(SEPARATOR);
    }

    /** The width of the current page's crumb, the least the bar should give the path. */
    public int minimumWidth(UiDraw metrics) {
        return crumbs.isEmpty() ? 0 : metrics.textWidth(crumbs.get(crumbs.size() - 1).text);
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return services().theme().controlHeight();
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiRect box = bounds();
        int count = crumbs.size();
        if (count == 0) {
            return;
        }
        int separator = metrics.textWidth(SEPARATOR);
        int[] widths = new int[count];
        for (int index = 0; index < count; index++) {
            widths[index] = metrics.textWidth(crumbs.get(index).text);
        }
        int total = total(widths, separator);
        for (int index = 0; index < count - 1 && total > box.width(); index++) {
            widths[index] = 0;
            total = total(widths, separator);
        }
        if (total > box.width()) {
            widths[count - 1] = Math.max(0, widths[count - 1] - (total - box.width()));
        }
        int x = box.x();
        for (int index = 0; index < count; index++) {
            crumbs.get(index).place(new UiRect(x, box.y(), widths[index], box.height()), metrics);
            if (widths[index] > 0) {
                x += widths[index] + separator;
            }
        }
    }

    private static int total(int[] widths, int separator) {
        int total = 0;
        int shown = 0;
        for (int width : widths) {
            if (width > 0) {
                total += width;
                shown++;
            }
        }
        return total + Math.max(0, shown - 1) * separator;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        int textY = bounds().y() + (bounds().height() - draw.lineHeight()) / 2 + 1;
        for (int index = 0; index + 1 < crumbs.size(); index++) {
            UiRect crumb = crumbs.get(index).bounds();
            if (!crumb.isEmpty()) {
                draw.text(SEPARATOR, crumb.right(), textY, UiEase.mix(frame.theme().textInverse(), frame.theme().ribbon(), 0.4F), true);
            }
        }
    }

    private final class Crumb extends UiNode {
        private final int index;
        private final String text;

        private Crumb(String id, int index, String text) {
            super(id);
            this.index = index;
            this.text = text;
        }

        private boolean link() {
            return index < crumbs.size() - 1;
        }

        @Override
        public String kind() {
            return link() ? "link" : "label";
        }

        @Override
        public String label() {
            return LegacyText.strip(text);
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("index", Integer.toString(index));
        }

        @Override
        public boolean focusable() {
            return link();
        }

        @Override
        public boolean hittable() {
            return link();
        }

        @Override
        public boolean mousePressed(UiMouseEvent event) {
            if (event.button() != UiMouseButton.LEFT || !link()) {
                return false;
            }
            return activate();
        }

        @Override
        public boolean activate() {
            if (!link()) {
                return false;
            }
            services().playClick();
            onSelect.accept(index);
            return true;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            boolean lit = link() && (frame.isHovered(this) || frame.showsFocus(this));
            String shown = link() ? LegacyText.strip(text) : text;
            String line = LegacyText.truncate(draw, shown, box.width());
            int textY = box.y() + (box.height() - draw.lineHeight()) / 2 + 1;
            int color = !link() ? UiTome.GILT : lit ? theme.textInverse() : UiEase.mix(theme.textInverse(), theme.ribbon(), 0.25F);
            draw.text(line, box.x(), textY, color, true);
            if (lit) {
                draw.fill(box.x(), textY + draw.lineHeight() - 1, box.x() + draw.textWidth(line), textY + draw.lineHeight(), theme.textInverse());
            }
        }
    }
}
