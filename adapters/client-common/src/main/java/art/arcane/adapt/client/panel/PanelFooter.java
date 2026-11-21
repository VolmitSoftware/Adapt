package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.PanelPaging;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The panel footer: summary tiles as small chips on the left (in the title bar when there is nothing else to show
 * here), page buttons and the page label on the right. Page
 * buttons appear for every page target the menu has and for all four on paged menus, disabled where the menu lacks
 * the target; the outer two drop out first when the footer runs short of room.
 */
final class PanelFooter {
    private static final List<String> PAGER = List.of("first", "previous", "next", "last");
    private static final int CHIP = 16;

    private final PanelChrome.Frame frame;
    private final PanelControls.Sink sink;
    private final Map<String, Button> pager = new LinkedHashMap<>();
    private final Set<String> pagerShown = new HashSet<>();
    private final Map<Integer, PanelChrome.InfoChip> chips = new TreeMap<>();
    private final PanelChrome.PageLabel pageLabel;

    PanelFooter(PanelChrome.Frame frame, PanelControls.Sink sink, Consumer<String> chrome) {
        this.frame = frame;
        this.sink = sink;
        for (String target : PAGER) {
            pager.put(target, frame.add(new Button("page." + target, glyph(target), () -> chrome.accept(target))));
        }
        pageLabel = frame.add(new PanelChrome.PageLabel());
    }

    /** The page button for {@code target}, or null when it is not a page target or hidden. */
    Button button(String target) {
        Button button = pager.get(target);
        return button != null && button.shown() ? button : null;
    }

    void syncChips(List<PanelTile> info, UiHost host) {
        Set<Integer> slots = new HashSet<>();
        for (PanelTile tile : info) {
            slots.add(tile.slot());
        }
        chips.entrySet().removeIf((Map.Entry<Integer, PanelChrome.InfoChip> entry) -> {
            if (slots.contains(entry.getKey())) {
                return false;
            }
            frame.remove(entry.getValue());
            return true;
        });
        for (PanelTile tile : info) {
            PanelChrome.InfoChip chip = chips.get(tile.slot());
            if (chip == null) {
                chip = frame.add(new PanelChrome.InfoChip(tile.slot(), sink));
                chips.put(tile.slot(), chip);
            }
            chip.update(tile, host);
        }
    }

    void syncPager(PanelView view, Map<String, Integer> chromeSlots, UiHost host) {
        boolean paged = view.paging() != null && view.paging().pages() > 1;
        pagerShown.clear();
        for (Map.Entry<String, Button> entry : pager.entrySet()) {
            boolean present = chromeSlots.containsKey(entry.getKey());
            if (present || paged) {
                pagerShown.add(entry.getKey());
            }
            entry.getValue().setVisible(present || paged);
            entry.getValue().setEnabled(present);
            entry.getValue().setTooltip(Tooltip.of(PanelChrome.tooltip(view, chromeSlots, entry.getKey(), tooltip(entry.getKey(), host))));
        }
        pageLabel.setVisible(paged || chromeSlots.containsKey("page-info"));
    }

    boolean visible() {
        if (!chips.isEmpty() || pageLabel.visible()) {
            return true;
        }
        for (Button button : pager.values()) {
            if (button.visible()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the footer holds nothing but summary chips, which then sit in the title bar instead. */
    boolean chipsOnly() {
        if (chips.isEmpty() || pageLabel.visible()) {
            return false;
        }
        for (Button button : pager.values()) {
            if (button.visible()) {
                return false;
            }
        }
        return true;
    }

    int chipsWidth(int gap) {
        return chips.isEmpty() ? 0 : chips.size() * (CHIP + gap) - gap;
    }

    /** Places the summary chips left to right in {@code area}, centred vertically. */
    void layoutChips(UiRect area, UiDraw metrics, int gap) {
        int size = Math.min(CHIP, area.height());
        int x = area.x();
        for (PanelChrome.InfoChip chip : chips.values()) {
            chip.place(new UiRect(x, area.y() + (area.height() - size) / 2, size, size), metrics);
            x += size + gap;
        }
    }

    void layout(UiRect bar, UiDraw metrics, UiTheme theme, PanelView view, Map<String, Integer> chromeSlots, UiHost host) {
        int gap = theme.gap();
        layoutChips(new UiRect(bar.x(), bar.y(), chipsWidth(gap), bar.height()), metrics, gap);
        int x = bar.x() + chipsWidth(gap) + (chips.isEmpty() ? 0 : gap);
        int right = bar.right();
        Button last = pager.get("last");
        Button next = pager.get("next");
        Button previous = pager.get("previous");
        Button first = pager.get("first");
        int button = theme.controlHeight();
        boolean roomy = bar.width() - (x - bar.x()) >= button * 4 + gap * 6 + metrics.textWidth(pageText(view, chromeSlots, host, false));
        boolean ends = roomy || view.paging() == null;
        last.setVisible(ends && pagerShown.contains("last"));
        first.setVisible(ends && pagerShown.contains("first"));
        for (Button nav : List.of(last, next)) {
            if (nav.visible()) {
                nav.place(new UiRect(right - button, bar.y(), button, button), metrics);
                right -= button + gap;
            }
        }
        if (pageLabel.visible()) {
            String text = pageText(view, chromeSlots, host, !roomy);
            int width = metrics.textWidth(text) + 8;
            pageLabel.setText(text);
            pageLabel.place(new UiRect(right - width, bar.y(), width, button), metrics);
            right -= width + gap;
        }
        for (Button nav : List.of(previous, first)) {
            if (nav.visible()) {
                nav.place(new UiRect(right - button, bar.y(), button, button), metrics);
                right -= button + gap;
            }
        }
    }

    private static String pageText(PanelView view, Map<String, Integer> chromeSlots, UiHost host, boolean shortForm) {
        PanelPaging paging = view.paging();
        if (paging == null) {
            Integer slot = chromeSlots.get("page-info");
            for (PanelTile tile : view.tiles()) {
                if (slot != null && tile.slot() == slot) {
                    return tile.name();
                }
            }
            return "";
        }
        PanelText text = shortForm ? PanelText.PAGE_SHORT : PanelText.PAGE;
        return text.text(host, paging.page() + 1, paging.pages());
    }

    private static String tooltip(String target, UiHost host) {
        return switch (target) {
            case "first" -> PanelText.FIRST.text(host);
            case "previous" -> PanelText.PREVIOUS.text(host);
            case "next" -> PanelText.NEXT.text(host);
            default -> PanelText.LAST.text(host);
        };
    }

    private static UiTome.Glyph glyph(String target) {
        return switch (target) {
            case "first" -> UiTome.Glyph.FIRST;
            case "previous" -> UiTome.Glyph.BACK;
            case "next" -> UiTome.Glyph.FORWARD;
            default -> UiTome.Glyph.LAST;
        };
    }
}
