package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.UiBreakpoint;
import art.arcane.adapt.client.ui.kit.UiNode;
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
 * REGULAR and WIDE breakpoints): nothing leaves the screen or the page frame sideways, no two widgets overlap, no text
 * is drawn past the frame or cut short with an ellipsis, and every control is big enough to hit.
 */
class ScreenLayoutTest {
    private static final int[][] SIZES = {
            {284, 160, 3}, {320, 240, 4}, {427, 240, 3}, {480, 270, 4}, {640, 360, 3}, {683, 384, 2}, {960, 540, 2}, {1280, 720, 2}, {1920, 1080, 1}, {2560, 1440, 1}};
    /** 1080p at GUI 3 and 2, 1440p at GUI 2, and a 2445x1376 window at GUI 3. */
    private static final int[][] BOOK_SIZES = {{640, 360, 3}, {960, 540, 2}, {1280, 720, 2}, {815, 458, 3}};
    private static final int MIN_TARGET = 9;
    private static final List<Route> ROUTES = List.of(new Route.Skills(), new Route.Skill("agility"), new Route.Skill("rift"),
            new Route.Adaptation("rift-gate"), new Route.Adaptation("agility-wall-jump"), new Route.Adaptation("rift-resistance"));

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
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                check(screen, where);
                checkText(fixture, where);
            }
        }
        assertEquals(List.of(UiBreakpoint.COMPACT, UiBreakpoint.REGULAR, UiBreakpoint.WIDE), seen);
    }

    @Test
    void theSkillsBookKeepsToFourColumnsCentredAndNeverScrolls() {
        for (int[] size : BOOK_SIZES) {
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
            AdaptScreen screen = fixture.open(new Route.Skills());
            String where = size[0] + "x" + size[1];
            List<Integer> columns = new ArrayList<>();
            int top = fixture.find("skill:skill00").bounds().y();
            for (int index = 0; index < MaxCatalog.SKILLS; index++) {
                UiRect tile = fixture.find(String.format("skill:skill%02d", index)).bounds();
                assertEquals(TileGrid.HEIGHT, tile.height(), where);
                if (tile.y() == top && !columns.contains(tile.x())) {
                    columns.add(tile.x());
                }
            }
            assertEquals(SkillsScreen.MAX_COLUMNS, columns.size(), where + " columns " + columns);
            assertEquals(0, screen.scroll().maxScroll(), where + " scrolls");
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
    void facingPagesShareOneWidth() {
        ScreenFixture fixture = new ScreenFixture(960, 540, 2);
        AdaptScreen screen = fixture.open(new Route.Skill("agility"));
        assertTrue(screen.spreadOpen());
        assertEquals(fixture.find("page").bounds().width(), fixture.find("facing").bounds().width());
    }

    @Test
    void statChipsSitInTheTopBar() {
        ScreenFixture fixture = new ScreenFixture(960, 540, 2);
        AdaptScreen screen = fixture.open(new Route.Skills());
        UiRect power = fixture.find("stat:power").bounds();
        UiRect back = fixture.find("back").bounds();
        assertEquals(back.y(), power.y());
        assertTrue(power.right() <= fixture.find("frame").bounds().right(), power.toString());
        assertNull(fixture.find("close"), "Escape closes the page, so it has no close button");
        assertTrue(power.bottom() <= screen.scroll().bounds().y(), power.toString());
    }

    @Test
    void tilesShareRowsAtEverySize() {
        for (int[] size : new int[][] {{284, 160, 3}, {960, 540, 2}}) {
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2]);
            fixture.open(new Route.Skills());
            List<UiNode> tiles = fixture.find("skills").children();
            UiRect first = tiles.get(0).bounds();
            UiRect second = tiles.get(1).bounds();
            assertEquals(first.y(), second.y());
            assertEquals(first.right() + TileGrid.GAP, second.x());
        }
    }

    static void check(AdaptScreen screen, String where) {
        UiRect screenBounds = new UiRect(0, 0, screen.width(), screen.height());
        UiNode frame = screen.root().find("frame");
        UiRect frameBounds = frame.bounds();
        assertTrue(contains(screenBounds, frameBounds), where + ": frame " + frameBounds + " leaves the screen");
        ScrollArea scroll = screen.scroll();
        List<UiNode> leaves = new ArrayList<>();
        collect(screen.root(), leaves);
        assertFalse(leaves.isEmpty(), where);
        List<UiRect> shown = new ArrayList<>();
        for (UiNode leaf : leaves) {
            UiRect bounds = leaf.bounds();
            if ((leaf.focusable() || leaf.hittable()) && (bounds.width() < MIN_TARGET || bounds.height() < MIN_TARGET)) {
                fail(where + ": " + leaf.id() + " " + bounds + " is too small to hit");
            }
            if (scroll.isAncestorOf(leaf)) {
                if (bounds.x() < scroll.bounds().x() || bounds.right() > scroll.bounds().right()) {
                    fail(where + ": " + leaf.id() + " " + bounds + " leaves the page " + scroll.bounds() + " sideways");
                }
                shown.add(bounds.intersection(scroll.bounds()));
            } else {
                if (!contains(frameBounds, bounds)) {
                    fail(where + ": " + leaf.id() + " " + bounds + " leaves the frame " + frameBounds);
                }
                shown.add(bounds);
            }
        }
        for (int first = 0; first < leaves.size(); first++) {
            for (int second = first + 1; second < leaves.size(); second++) {
                if (shown.get(first).intersects(shown.get(second))) {
                    fail(where + ": " + leaves.get(first).id() + " " + leaves.get(first).bounds() + " overlaps "
                            + leaves.get(second).id() + " " + leaves.get(second).bounds());
                }
            }
        }
    }

    static void checkText(ScreenFixture fixture, String where) {
        UiRect frame = fixture.find("frame").bounds();
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
            } else if (call.startsWith("text ")) {
                int space = call.indexOf(' ', 5);
                float x = pose[0] + Integer.parseInt(call.substring(5, space).split(",")[0]) * pose[1];
                float width = fixture.draw.textWidth(call.substring(space + 1)) * pose[1];
                if (x < frame.x() || x + width > frame.right()) {
                    fail(where + ": text \"" + call.substring(space + 1) + "\" at x=" + x + " width " + width + " leaves the frame " + frame);
                }
                if (call.substring(space + 1).endsWith(LegacyText.ELLIPSIS) && !inField(fields, x, Integer.parseInt(call.substring(5, space).split(",")[1]))) {
                    fail(where + ": text \"" + call.substring(space + 1) + "\" is cut short");
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
