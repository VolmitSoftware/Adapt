package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.UiBreakpoint;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiRect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every page at GUI scales 1 to 4 on common windows (scaled sizes from 284x160 up to 2560x1440, covering the COMPACT,
 * REGULAR and WIDE breakpoints), on every leaf: nothing scrolls, nothing leaves the screen, the page frame or its own
 * page, nothing but the spine sits on the fold between facing pages, no two widgets overlap, no text is drawn past the
 * frame, across the fold or cut short with an ellipsis, and every control is big enough to hit.
 */
class ScreenLayoutTest {
    private static final int[][] SIZES = {
            {284, 160, 3}, {320, 240, 4}, {427, 240, 3}, {480, 270, 4}, {640, 360, 3}, {640, 352, 4}, {683, 384, 2}, {960, 540, 2}, {1280, 720, 2},
            {1920, 1080, 1}, {2560, 1440, 1}};
    /** 1080p at GUI 3 and 2, 1440p at GUI 2, and a 2445x1376 window at GUI 3. */
    private static final int[][] BOOK_SIZES = {{640, 360, 3}, {960, 540, 2}, {1280, 720, 2}, {815, 458, 3}};
    private static final int MIN_TARGET = 9;
    private static final List<Route> ROUTES = List.of(new Route.Skills(), new Route.Skill("agility"), new Route.Skill("rift"),
            new Route.Adaptation("rift-gate"), new Route.Adaptation("agility-wall-jump"), new Route.Adaptation("rift-resistance"),
            new Route.Adaptation("rift-blink"));

    @Test
    void pagesFitEverySizeWithoutOverlap() {
        List<UiBreakpoint> seen = new ArrayList<>();
        for (int[] size : SIZES) {
            for (Route route : ROUTES) {
                ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2]);
                AdaptScreen screen = fixture.open(route);
                if (!seen.contains(screen.breakpoint())) {
                    seen.add(screen.breakpoint());
                }
                checkEveryLeaf(fixture, size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route));
            }
        }
        assertEquals(List.of(UiBreakpoint.COMPACT, UiBreakpoint.REGULAR, UiBreakpoint.WIDE), seen);
    }

    @Test
    void theSkillsBookFillsEachPageWithWholeRowsAndNeverScrolls() {
        for (int[] size : BOOK_SIZES) {
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], TypicalCatalog.catalog(), TypicalCatalog.player());
            AdaptScreen screen = fixture.open(new Route.Skills());
            String where = size[0] + "x" + size[1];
            UiRect page = fixture.find("sheet:0").bounds();
            List<Integer> columns = new ArrayList<>();
            for (UiNode node : fixture.find("skills:0").children()) {
                UiRect tile = node.bounds();
                assertEquals(SkillsScreen.LAYOUT.tileHeight(), tile.height(), where);
                columns.add(tile.x());
            }
            assertEquals(SkillsScreen.LAYOUT.columns(), columns.size(), where + " columns " + columns);
            assertTrue(columns.get(0) - page.x() <= UiLayout.UNIT, where + ": the first column leaves the page's left edge");
            assertEquals(1, screen.leaves(), where);
            UiRect frame = fixture.find("frame").bounds();
            assertTrue(Math.abs(frame.centerX() - screen.width() / 2) <= 1, where + " " + frame);
            if (size[0] >= 960) {
                assertTrue(frame.width() < screen.width() * 3 / 4, where + " " + frame);
            }
        }
    }

    @Test
    void largeWindowsStayWithinSixteenByNine() {
        for (int[] size : BOOK_SIZES) {
            for (Route route : ROUTES) {
                ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2]);
                AdaptScreen screen = fixture.open(route);
                UiRect frame = fixture.find("frame").bounds();
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route) + " " + frame;
                assertTrue(frame.width() * 9 <= frame.height() * 16, where);
                assertTrue(frame.width() < screen.width(), where);
                assertTrue(Math.abs(frame.centerX() - screen.width() / 2) <= 1, where);
            }
        }
        ScreenFixture ultrawide = new ScreenFixture(1720, 480, 2);
        ultrawide.open(new Route.Skills());
        UiRect frame = ultrawide.find("frame").bounds();
        assertTrue(frame.width() * 9 <= frame.height() * 16, frame.toString());
    }

    @Test
    void facingPagesShareOneWidthEitherSideOfTheFold() {
        ScreenFixture fixture = new ScreenFixture(960, 540, 2);
        fixture.open(new Route.Skill("agility"));
        UiRect left = fixture.find("sheet:0").bounds();
        UiRect right = fixture.find("sheet:1").bounds();
        UiRect frame = fixture.find("frame").bounds();
        assertEquals(left.width(), right.width());
        assertEquals(AdaptScreen.SPREAD_GAP, right.x() - left.right());
        assertEquals(frame.centerX(), left.right() + AdaptScreen.SPREAD_GAP / 2, 1.0);
    }

    @Test
    void eachPageCarriesItsOwnHeader() {
        ScreenFixture fixture = new ScreenFixture(960, 540, 2, TypicalCatalog.catalog(), TypicalCatalog.player());
        fixture.open(new Route.Skills());
        UiRect left = fixture.find("sheet:0").bounds();
        UiRect right = fixture.find("sheet:1").bounds();
        UiRect back = fixture.find("back").bounds();
        UiRect crumbs = fixture.find("crumb").bounds();
        UiRect search = fixture.find("search").bounds();
        UiRect power = fixture.find("stat:power").bounds();
        assertEquals(left.x(), back.x(), "back opens the left page");
        assertTrue(crumbs.right() <= left.right(), "the breadcrumbs stay on the left page " + crumbs);
        assertEquals(right.right(), power.right(), "the chips close the right page");
        assertTrue(search.x() >= right.x(), "the search stays on the right page " + search);
        assertEquals(back.y(), power.y());
        assertTrue(power.bottom() <= left.y(), power.toString());
        assertNull(fixture.find("close"), "Escape closes the page, so it has no close button");
    }

    @Test
    void tilesShareRowsAtEverySize() {
        for (int[] size : new int[][] {{284, 160, 3}, {960, 540, 2}}) {
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], TypicalCatalog.catalog(), TypicalCatalog.player());
            fixture.open(new Route.Skills());
            List<UiNode> tiles = fixture.find("skills:0").children();
            UiRect first = tiles.get(0).bounds();
            UiRect second = tiles.get(1).bounds();
            assertEquals(first.y(), second.y());
            assertEquals(first.right() + SkillsScreen.LAYOUT.gap(), second.x());
        }
    }

    /** {@link #check} and {@link #checkText} on every leaf of the open page, which is left on the leaf it showed. */
    static void checkEveryLeaf(ScreenFixture fixture, String where) {
        AdaptScreen screen = fixture.current();
        int was = screen.leaf();
        for (int leaf = 0; leaf < screen.leaves(); leaf++) {
            screen.turnTo(leaf);
            fixture.render();
            String at = where + " leaf " + (leaf + 1) + "/" + screen.leaves();
            check(screen, at);
            checkText(fixture, at);
        }
        screen.turnTo(was);
        fixture.render();
    }

    /** The fold between facing pages, which nothing but the spine may touch; empty on a single page. */
    static UiRect gutter(AdaptScreen screen) {
        UiNode left = screen.root().find("sheet:0");
        UiNode right = screen.root().find("sheet:1");
        if (screen.breakpoint() == UiBreakpoint.COMPACT || left == null) {
            return UiRect.EMPTY;
        }
        int from = left.bounds().right();
        int to = right != null ? right.bounds().x() : from + AdaptScreen.SPREAD_GAP;
        UiRect frame = screen.root().find("frame").bounds();
        return UiRect.of(from, frame.y(), to, frame.bottom());
    }

    static void check(AdaptScreen screen, String where) {
        UiRect screenBounds = new UiRect(0, 0, screen.width(), screen.height());
        UiNode frame = screen.root().find("frame");
        UiRect frameBounds = frame.bounds();
        assertTrue(contains(screenBounds, frameBounds), where + ": frame " + frameBounds + " leaves the screen");
        assertFalse(scrolls(screen.root()), where + " has a scrolling area");
        UiRect fold = gutter(screen);
        List<UiNode> leaves = new ArrayList<>();
        collect(screen.root(), leaves);
        assertFalse(leaves.isEmpty(), where);
        leaves.removeIf((UiNode leaf) -> shown(leaf).isEmpty());
        for (UiNode leaf : leaves) {
            UiRect bounds = shown(leaf);
            if ((leaf.focusable() || leaf.hittable()) && (bounds.width() < MIN_TARGET || bounds.height() < MIN_TARGET)
                    && bounds.equals(leaf.bounds())) {
                fail(where + ": " + leaf.id() + " " + bounds + " is too small to hit");
            }
            if (!contains(frameBounds, bounds)) {
                fail(where + ": " + leaf.id() + " " + bounds + " leaves the frame " + frameBounds);
            }
            UiNode page = sheetOf(leaf);
            if (page != null && !contains(page.bounds(), bounds)) {
                fail(where + ": " + leaf.id() + " " + bounds + " leaves its page " + page.bounds());
            }
            if (!fold.isEmpty() && bounds.intersects(fold)) {
                fail(where + ": " + leaf.id() + " " + bounds + " sits on the fold " + fold);
            }
        }
        for (int first = 0; first < leaves.size(); first++) {
            for (int second = first + 1; second < leaves.size(); second++) {
                if (shown(leaves.get(first)).intersects(shown(leaves.get(second)))) {
                    fail(where + ": " + leaves.get(first).id() + " " + leaves.get(first).bounds() + " overlaps "
                            + leaves.get(second).id() + " " + leaves.get(second).bounds());
                }
            }
        }
    }

    /** The part of {@code node} that shows: inside a scrolling list, what the list's window lets through. */
    private static UiRect shown(UiNode node) {
        for (UiNode current = node.parent(); current != null; current = current.parent()) {
            if (current instanceof ScrollArea area) {
                return node.bounds().intersection(area.bounds());
            }
        }
        return node.bounds();
    }

    private static UiNode sheetOf(UiNode node) {
        for (UiNode current = node; current != null; current = current.parent()) {
            if (current.id().startsWith("sheet:")) {
                return current;
            }
        }
        return null;
    }

    private static boolean scrolls(UiNode node) {
        if (node instanceof ScrollArea && !(node instanceof GridScroll)) {
            return true;
        }
        for (UiNode child : node.children()) {
            if (scrolls(child)) {
                return true;
            }
        }
        return false;
    }

    static void checkText(ScreenFixture fixture, String where) {
        UiRect frame = fixture.find("frame").bounds();
        UiRect fold = gutter(fixture.current());
        List<UiRect> fields = new ArrayList<>();
        collectFields(fixture.current().root(), fields);
        fixture.draw.calls().clear();
        fixture.render();
        List<float[]> poses = new ArrayList<>();
        float[] pose = {0.0F, 1.0F};
        for (String call : fixture.draw.calls()) {
            if (call.equals("pushPose")) {
                poses.add(pose.clone());
            } else if (call.equals("popPose")) {
                pose = poses.remove(poses.size() - 1);
            } else if (call.startsWith("translate ")) {
                pose[0] += Float.parseFloat(call.substring(10, call.indexOf(','))) * pose[1];
            } else if (call.startsWith("scale ")) {
                pose[1] *= Float.parseFloat(call.substring(6, call.indexOf(',')));
            } else if (call.startsWith("wrapped ")) {
                String[] parts = call.split(" ", 4);
                float x = pose[0] + Integer.parseInt(parts[1].split(",")[0]) * pose[1];
                float width = Integer.parseInt(parts[2]) * pose[1];
                if (x < frame.x() || x + width > frame.right()) {
                    fail(where + ": wrapped text \"" + parts[3] + "\" at x=" + x + " width " + width + " leaves the frame " + frame);
                }
                if (!fold.isEmpty() && x < fold.right() && x + width > fold.x()) {
                    fail(where + ": wrapped text \"" + parts[3] + "\" at x=" + x + " width " + width + " crosses the fold " + fold);
                }
            } else if (call.startsWith("text ")) {
                int space = call.indexOf(' ', 5);
                float x = pose[0] + Integer.parseInt(call.substring(5, space).split(",")[0]) * pose[1];
                String text = call.substring(space + 1);
                float width = fixture.draw.textWidth(text) * pose[1];
                if (!text.isEmpty() && (x < frame.x() || x + width > frame.right())) {
                    fail(where + ": text \"" + text + "\" at x=" + x + " width " + width + " leaves the frame " + frame);
                }
                if (!fold.isEmpty() && !text.isEmpty() && x < fold.right() && x + width > fold.x()) {
                    fail(where + ": text \"" + text + "\" at x=" + x + " width " + width + " crosses the fold " + fold);
                }
                if (text.isEmpty()) {
                    continue;
                }
                if (text.endsWith(LegacyText.ELLIPSIS) && !inField(fields, x, Integer.parseInt(call.substring(5, space).split(",")[1]))) {
                    fail(where + ": text \"" + text + "\" is cut short");
                }
            }
        }
    }

    /** Text fields hold typed or unbounded values, the one place a cut with an ellipsis is allowed. */
    private static void collectFields(UiNode node, List<UiRect> fields) {
        if (node instanceof TextField && node.visible()) {
            fields.add(node.bounds());
        }
        for (UiNode child : node.children()) {
            collectFields(child, fields);
        }
    }

    private static boolean inField(List<UiRect> fields, float x, int y) {
        for (UiRect field : fields) {
            if (field.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private static void collect(UiNode node, List<UiNode> leaves) {
        if (!node.visible() || node instanceof Snackbar) {
            return;
        }
        boolean shownChild = false;
        for (UiNode child : node.children()) {
            if (child.visible()) {
                shownChild = true;
                collect(child, leaves);
            }
        }
        if (!shownChild && !node.bounds().isEmpty()) {
            leaves.add(node);
        }
    }

    private static boolean contains(UiRect outer, UiRect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y() && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }
}
