package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.ArrayList;
import java.util.List;

/**
 * Pours an {@link AdaptScreen.Flow} into pages of one size. A flow that pins its first page on a spread keeps that
 * page on the left and runs the rest through the right page; pinned blocks that do not fit one page stay on it all the
 * same, their running text shortened and the rest clipped, so the pages never trade places. A block goes on the
 * current page when it fits beside what is already there and the page's foot, and starts the next page when it does
 * not; a block taller than a whole page still gets one to itself, clipped there. A heading or a rule needs room for
 * the block after it too, and a rule that would open a page is left out. Running text takes its least height while the
 * pages are filled and then shares out the height its page has left, a line at a time. Rows of tiles then spread apart
 * by the same amount everywhere, at most {@code stretch} each, so rows line up across the spine.
 */
final class Pagination {
    private Pagination() {
    }

    private static final class Entry {
        private final AdaptScreen.Block block;
        private final int natural;
        private int height;
        private int gap;

        private Entry(AdaptScreen.Block block, int natural, int height) {
            this.block = block;
            this.natural = natural;
            this.height = height;
        }
    }

    private static final class Page {
        private final List<Entry> items = new ArrayList<>();
        private final List<Entry> foot = new ArrayList<>();

        private int used() {
            int used = 0;
            for (Entry entry : items) {
                used += entry.gap + entry.height;
            }
            return used;
        }

        private int footUsed() {
            int used = 0;
            for (Entry entry : foot) {
                used += entry.gap + entry.height;
            }
            return used;
        }

        private boolean empty() {
            return items.isEmpty() && foot.isEmpty();
        }

        private int spare(int height) {
            int footUsed = footUsed();
            return height - used() - (footUsed > 0 ? footUsed + (items.isEmpty() ? 0 : AdaptScreen.PAGE_GAP) : 0);
        }
    }

    /** The pages a flow fills; with {@code pinned} the first one stays on the left while the others turn. */
    record Poured(List<List<AdaptScreen.Placed>> pages, boolean pinned) {
    }

    /** The blocks of {@code flow} on pages {@code width} by {@code height}, {@code perLeaf} pages to a leaf. */
    static Poured pour(AdaptScreen.Flow flow, int width, int height, UiDraw metrics, int stretch, int perLeaf) {
        List<AdaptScreen.Block> blocks = new ArrayList<>();
        for (AdaptScreen.Block block : flow.blocks()) {
            if (block.kind() == AdaptScreen.Kind.BREAK || block.node().visible()) {
                blocks.add(block);
            }
        }
        int split = blocks.size();
        for (int index = 0; index < blocks.size(); index++) {
            if (blocks.get(index).kind() == AdaptScreen.Kind.BREAK) {
                split = index;
                break;
            }
        }
        if (flow.pinned() && perLeaf == 2) {
            List<List<AdaptScreen.Placed>> pages = new ArrayList<>();
            List<List<AdaptScreen.Placed>> pinned = pour(blocks.subList(0, split), false, width, height, metrics, stretch, 1);
            pages.add(pinned.size() == 1 ? pinned.get(0) : onePage(blocks.subList(0, split), width, height, metrics));
            List<List<AdaptScreen.Placed>> rest = pour(blocks.subList(split, blocks.size()), false, width, height, metrics, stretch, 1);
            if (!rest.get(0).isEmpty()) {
                pages.addAll(rest);
            }
            return new Poured(pages, true);
        }
        return new Poured(pour(blocks, flow.balanced(), width, height, metrics, stretch, perLeaf), false);
    }

    private static List<List<AdaptScreen.Placed>> pour(List<AdaptScreen.Block> blocks, boolean balanced, int width, int height, UiDraw metrics,
                                                       int stretch, int perLeaf) {
        List<Page> pages = new ArrayList<>();
        Page page = new Page();
        pages.add(page);
        for (int index = 0; index < blocks.size(); index++) {
            AdaptScreen.Block block = blocks.get(index);
            if (block.kind() == AdaptScreen.Kind.BREAK) {
                if (!page.empty()) {
                    page = new Page();
                    pages.add(page);
                }
                continue;
            }
            Entry entry = entry(block, width, metrics);
            if (!fits(page, entry, after(blocks, index, width, metrics), height) && !page.empty()) {
                page = new Page();
                pages.add(page);
            }
            if (block.kind() == AdaptScreen.Kind.RULE && page.items.isEmpty()) {
                continue;
            }
            entry.gap = block.kind() == AdaptScreen.Kind.FOOT ? (page.foot.isEmpty() ? 0 : block.gap()) : page.items.isEmpty() ? 0 : block.gap();
            (block.kind() == AdaptScreen.Kind.FOOT ? page.foot : page.items).add(entry);
        }
        if (balanced && perLeaf == 2) {
            balance(pages);
        }
        for (Page each : pages) {
            grow(each, height, metrics.lineHeight());
        }
        int extra = spread(pages, height, stretch);
        List<List<AdaptScreen.Placed>> placed = new ArrayList<>();
        for (Page each : pages) {
            placed.add(place(each, height, extra));
        }
        while (placed.size() > 1 && placed.get(placed.size() - 1).isEmpty()) {
            placed.remove(placed.size() - 1);
        }
        return placed;
    }

    /**
     * Blocks too tall for one page kept on it anyway: running text gives up height down to a line, and what still does
     * not fit is clipped at the page's foot.
     */
    private static List<AdaptScreen.Placed> onePage(List<AdaptScreen.Block> blocks, int width, int height, UiDraw metrics) {
        Page page = new Page();
        for (AdaptScreen.Block block : blocks) {
            if (block.kind() == AdaptScreen.Kind.BREAK || block.kind() == AdaptScreen.Kind.RULE && page.items.isEmpty()) {
                continue;
            }
            Entry entry = entry(block, width, metrics);
            entry.gap = block.kind() == AdaptScreen.Kind.FOOT ? (page.foot.isEmpty() ? 0 : block.gap()) : page.items.isEmpty() ? 0 : block.gap();
            (block.kind() == AdaptScreen.Kind.FOOT ? page.foot : page.items).add(entry);
        }
        for (Entry entry : page.items) {
            if (page.spare(height) >= 0) {
                break;
            }
            if (entry.block.least() >= 0) {
                entry.height = Math.max(Math.min(entry.height, metrics.lineHeight()), entry.height + page.spare(height));
            }
        }
        grow(page, height, metrics.lineHeight());
        return place(page, height, 0);
    }

    private static Entry entry(AdaptScreen.Block block, int width, UiDraw metrics) {
        int natural = block.node().preferredHeight(width, metrics);
        return new Entry(block, natural, block.least() >= 0 ? Math.min(natural, block.least()) : natural);
    }

    /** The height a heading at {@code index} must keep free below itself for the block it introduces. */
    private static int after(List<AdaptScreen.Block> blocks, int index, int width, UiDraw metrics) {
        AdaptScreen.Kind kind = blocks.get(index).kind();
        if (kind != AdaptScreen.Kind.HEADING && kind != AdaptScreen.Kind.RULE || index + 1 >= blocks.size()) {
            return 0;
        }
        AdaptScreen.Block next = blocks.get(index + 1);
        if (next.kind() == AdaptScreen.Kind.BREAK || next.kind() == AdaptScreen.Kind.FOOT) {
            return 0;
        }
        return next.gap() + entry(next, width, metrics).height;
    }

    private static boolean fits(Page page, Entry entry, int keep, int height) {
        int footUsed = page.footUsed();
        if (entry.block.kind() == AdaptScreen.Kind.FOOT) {
            int foot = footUsed + (footUsed > 0 ? entry.block.gap() : 0) + entry.height;
            int used = page.used();
            return used + (used > 0 ? AdaptScreen.PAGE_GAP : 0) + foot <= height;
        }
        int gap = page.items.isEmpty() ? 0 : entry.block.gap();
        int footRoom = footUsed > 0 ? footUsed + AdaptScreen.PAGE_GAP : 0;
        return page.used() + gap + entry.height + keep + footRoom <= height;
    }

    /** Shares the last leaf's blocks between its two pages, the left page taking the odd one. */
    private static void balance(List<Page> pages) {
        int last = (pages.size() - 1) / 2 * 2;
        Page left = pages.get(last);
        Page right = last + 1 < pages.size() ? pages.get(last + 1) : new Page();
        if (!left.foot.isEmpty() || !right.foot.isEmpty()) {
            return;
        }
        List<Entry> all = new ArrayList<>(left.items);
        all.addAll(right.items);
        if (all.size() < 2) {
            return;
        }
        int keep = (all.size() + 1) / 2;
        left.items.clear();
        right.items.clear();
        left.items.addAll(all.subList(0, keep));
        right.items.addAll(all.subList(keep, all.size()));
        for (Page page : List.of(left, right)) {
            for (int index = 0; index < page.items.size(); index++) {
                Entry entry = page.items.get(index);
                entry.gap = index == 0 ? 0 : entry.block.gap();
            }
        }
        if (last + 1 >= pages.size()) {
            pages.add(right);
        }
    }

    /** Shares the height {@code page} has left between its running text a line at a time, until each has all it needs. */
    private static void grow(Page page, int height, int step) {
        int spare = page.spare(height);
        boolean grew = true;
        while (spare > 0 && grew) {
            grew = false;
            for (Entry entry : page.items) {
                int more = Math.min(spare, Math.min(step, entry.natural - entry.height));
                if (more > 0) {
                    entry.height += more;
                    spare -= more;
                    grew = true;
                }
            }
        }
    }

    /** How far every row gap may widen: as far as the fullest page with rows allows, at most {@code stretch}. */
    private static int spread(List<Page> pages, int height, int stretch) {
        int extra = stretch;
        boolean rows = false;
        for (Page page : pages) {
            int gaps = 0;
            for (int index = 1; index < page.items.size(); index++) {
                if (page.items.get(index).block.kind() == AdaptScreen.Kind.ROW) {
                    gaps++;
                }
            }
            if (gaps > 0) {
                rows = true;
                extra = Math.min(extra, page.spare(height) / gaps);
            }
        }
        return rows ? Math.max(0, extra) : 0;
    }

    private static List<AdaptScreen.Placed> place(Page page, int height, int extra) {
        List<AdaptScreen.Placed> placed = new ArrayList<>();
        int y = 0;
        for (int index = 0; index < page.items.size(); index++) {
            Entry entry = page.items.get(index);
            y += entry.gap + (index > 0 && entry.block.kind() == AdaptScreen.Kind.ROW ? extra : 0);
            placed.add(new AdaptScreen.Placed(entry.block.node(), y, entry.height));
            y += entry.height;
        }
        int foot = height - page.footUsed();
        for (Entry entry : page.foot) {
            foot += entry.gap;
            placed.add(new AdaptScreen.Placed(entry.block.node(), foot, entry.height));
            foot += entry.height;
        }
        return placed;
    }
}
