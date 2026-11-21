package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.UiBreakpoint;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiRect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The book fits its content and the screen as one system, on the everyday catalog ({@link TypicalCatalog}): columns
 * balance across the spine, no page ends in a band of blank paper, the frame stays within 16:9 and never scrolls at the
 * scaled sizes players run, the size classes hold steady around their thresholds, and a resize or GUI scale change
 * under an open page gives the layout a fresh page would have while keeping scroll, selection, focus and hover.
 */
class BookFitTest {
    /** 1080p at GUI 4, 3 and 2, 1440p at GUI 2, and a 2445x1376 window at GUI 3. */
    private static final int[][] SIZES = {{480, 270, 4}, {640, 360, 3}, {960, 540, 2}, {1280, 720, 2}, {815, 458, 3}};
    private static final List<Route> ROUTES = List.of(new Route.Skills(), new Route.Skill(TypicalCatalog.SKILL),
            new Route.Adaptation(TypicalCatalog.ADAPTATION), new Route.Adaptation(TypicalCatalog.PLAIN_ADAPTATION),
            new Route.Adaptation(TypicalCatalog.SWITCH_ADAPTATION));
    /** The most blank paper allowed above or below a page of tiles: one row at its tightest. */
    private static final int BLANK = TileGrid.PITCH;

    private static ScreenFixture typical(int[] size) {
        return typical(size, TypicalCatalog.SKILLS);
    }

    private static ScreenFixture typical(int[] size, int skills) {
        return new ScreenFixture(size[0], size[1], size[2], TypicalCatalog.catalog(skills), TypicalCatalog.player(skills));
    }

    private static List<UiRect> tiles(UiNode node, List<UiRect> found) {
        if (node instanceof RowTile && node.visible()) {
            found.add(node.bounds());
        }
        for (UiNode child : node.children()) {
            if (child.visible()) {
                tiles(child, found);
            }
        }
        return found;
    }

    private static TreeSet<Integer> rows(List<UiRect> tiles) {
        TreeSet<Integer> rows = new TreeSet<>();
        for (UiRect tile : tiles) {
            rows.add(tile.y());
        }
        return rows;
    }

    @Test
    void skillsBalanceAcrossTheSpine() {
        int[][] expected = {{24, 12, 12}, {23, 12, 11}, {22, 12, 10}, {21, 12, 9}, {17, 10, 7}, {13, 7, 6}};
        for (int[] size : SIZES) {
            for (int[] counts : expected) {
                ScreenFixture fixture = typical(size, counts[0]);
                SkillsScreen screen = fixture.open(new Route.Skills());
                String where = size[0] + "x" + size[1] + " " + counts[0] + " skills";
                int spine = screen.gutter();
                assertNotEquals(Integer.MIN_VALUE, spine, where + " has no spine");
                List<UiRect> left = new ArrayList<>();
                List<UiRect> right = new ArrayList<>();
                for (UiRect tile : tiles(fixture.find("skills"), new ArrayList<>())) {
                    (tile.x() < spine ? left : right).add(tile);
                }
                assertEquals(counts[1], left.size(), where + " left page");
                assertEquals(counts[2], right.size(), where + " right page");
                TreeSet<Integer> leftRows = rows(left);
                TreeSet<Integer> rightRows = rows(right);
                assertTrue(leftRows.size() - rightRows.size() <= 1, where + " rows " + leftRows.size() + " and " + rightRows.size());
                assertTrue(leftRows.containsAll(rightRows), where + ": rows do not line up across the spine " + leftRows + " " + rightRows);
                TreeSet<Integer> columns = new TreeSet<>();
                for (UiRect tile : left) {
                    columns.add(tile.x());
                }
                assertEquals(left.size(), leftRows.size() * columns.size(), where + ": the left page has a short row");
                int shortRows = 0;
                for (int row : rightRows) {
                    int inRow = 0;
                    for (UiRect tile : right) {
                        inRow += tile.y() == row ? 1 : 0;
                    }
                    if (inRow < columns.size()) {
                        shortRows++;
                        assertEquals(rightRows.last().intValue(), row, where + ": a short row that is not the last");
                    }
                }
                assertTrue(shortRows <= 1, where);
                assertTrue(columns.size() * 2 <= SkillsScreen.MAX_COLUMNS, where + " columns " + columns);
                UiRect frame = fixture.find("frame").bounds();
                assertEquals(frame.centerX(), spine, 1.0, where + ": the spine is off centre");
            }
        }
    }

    @Test
    void aSmallCatalogsTilesReachThePageMargins() {
        for (int[] size : new int[][] {{480, 270, 4}, {640, 360, 3}}) {
            ScreenFixture fixture = typical(size, 13);
            SkillsScreen screen = fixture.open(new Route.Skills());
            String where = size[0] + "x" + size[1];
            UiRect page = screen.scroll().bounds();
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            for (UiRect tile : tiles(fixture.find("skills"), new ArrayList<>())) {
                left = Math.min(left, tile.x());
                right = Math.max(right, tile.right());
                assertTrue(tile.width() <= TileGrid.MAX_WIDTH, where + " " + tile);
            }
            assertTrue(left - page.x() <= UiLayout.UNIT && page.right() - right <= UiLayout.UNIT,
                    where + ": tiles float " + (left - page.x()) + " and " + (page.right() - right) + " inside a page the top bar widened");
        }
    }

    @Test
    void tilePagesHaveNoBandOfBlankPaper() {
        for (int[] size : SIZES) {
            for (Route route : List.of(new Route.Skills(), new Route.Skill(TypicalCatalog.SKILL))) {
                ScreenFixture fixture = typical(size);
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                UiRect page = screen.scroll().bounds();
                List<UiRect> tiles = tiles(screen.root(), new ArrayList<>());
                int top = Integer.MAX_VALUE;
                int bottom = Integer.MIN_VALUE;
                for (UiRect tile : tiles) {
                    top = Math.min(top, tile.y());
                    bottom = Math.max(bottom, tile.bottom());
                }
                int above = top - page.y();
                int below = page.bottom() - bottom;
                assertTrue(above <= BLANK, where + ": " + above + " blank above the tiles");
                assertTrue(below <= BLANK, where + ": " + below + " blank below the tiles");
                assertTrue(Math.abs(above - below) <= UiLayout.UNIT, where + ": margins " + above + " above and " + below + " below");
                TreeSet<Integer> rows = rows(tiles);
                int pitch = rows.higher(rows.first()) - rows.first();
                assertTrue(pitch >= TileGrid.PITCH && pitch <= TileGrid.MAX_PITCH, where + ": rows " + pitch + " apart");
            }
        }
    }

    @Test
    void theBookIsNoBiggerThanItsContentNeeds() {
        for (int[] size : SIZES) {
            ScreenFixture fixture = typical(size);
            AdaptScreen screen = fixture.open(new Route.Adaptation(TypicalCatalog.ADAPTATION));
            UiRect frame = fixture.find("frame").bounds();
            String where = size[0] + "x" + size[1] + " " + frame;
            assertTrue(screen.spreadOpen(), where);
            assertTrue(frame.width() <= 400 && frame.height() <= 225, where + ": a short adaptation opens a large book");
            assertEquals(fixture.find("page").bounds().width(), fixture.find("facing").bounds().width(), where);
        }
    }

    @Test
    void aLoneSwitchDoesNotOpenAPageOfItsOwn() {
        for (int[] size : SIZES) {
            ScreenFixture fixture = typical(size);
            AdaptScreen screen = fixture.open(new Route.Adaptation(TypicalCatalog.SWITCH_ADAPTATION));
            String where = size[0] + "x" + size[1];
            assertFalse(screen.spreadOpen(), where + ": one switch has a page to itself");
            UiRect action = fixture.find("detail.action").bounds();
            UiRect toggle = fixture.find("pref:enabled").bounds();
            UiRect reset = fixture.find("pref-reset").bounds();
            assertTrue(toggle.y() >= action.bottom(), where + ": the switch sits under the action " + toggle + " " + action);
            assertEquals(action.x(), toggle.x(), where);
            assertTrue(reset.x() >= toggle.right() && reset.intersects(new UiRect(reset.x(), toggle.y(), 1, toggle.height())), where + " " + reset);
            assertEquals(0, screen.scroll().maxScroll(), where);
        }
    }

    @Test
    void aLongChecklistOpensInColumnsBesideANarrowerPage() {
        for (int[] size : new int[][] {{640, 360, 3}, {960, 540, 2}, {1280, 720, 2}, {815, 458, 3}}) {
            ScreenFixture fixture = typical(size);
            AdaptScreen screen = fixture.open(new Route.Adaptation(TypicalCatalog.CHECKLIST_ADAPTATION));
            String where = size[0] + "x" + size[1];
            UiRect frame = fixture.find("frame").bounds();
            UiRect page = fixture.find("page").bounds();
            UiRect facing = fixture.find("facing").bounds();
            assertTrue(screen.spreadOpen(), where);
            assertEquals(0, screen.scroll().maxScroll(), where + " scrolls");
            assertTrue(facing.width() > page.width(), where + ": the checklist's page is no wider than the text page");
            assertTrue(frame.height() * 10 <= size[1] * 9, where + ": the book runs edge to edge " + frame);
            assertTrue(frame.width() * 9 <= frame.height() * 16 + 16, where + " is wider than 16:9 " + frame);
            UiRect first = fixture.find("pref:effect-0").bounds();
            UiRect second = fixture.find("pref:effect-1").bounds();
            UiRect third = fixture.find("pref:effect-2").bounds();
            assertEquals(first.y(), second.y(), where + ": the checklist is one column");
            assertTrue(second.x() > first.right(), where);
            assertEquals(first.x(), third.x(), where);
            int pitch = third.y() - first.y();
            assertTrue(pitch > PreferencePanel.CELL_HEIGHT && pitch <= PreferencePanel.CELL_HEIGHT + PreferencePanel.ROW_SPACING + 5,
                    where + ": rows " + pitch + " apart");
            assertEquals(first.right(), fixture.find("pref:enabled").bounds().right(), where + ": the main switch's box leaves the column");
            assertEquals(fixture.find("detail.action").bounds().bottom(), fixture.find("pref-reset").bounds().bottom() + 1, 1.0, where);
        }
    }

    @Test
    void resetJoinsTheMainSwitchWhenThePagesStackAndStatNotesHang() {
        ScreenFixture wide = typical(new int[] {640, 360, 3});
        wide.open(new Route.Adaptation(TypicalCatalog.CHECKLIST_ADAPTATION));
        UiRect foot = wide.find("pref-reset").bounds();
        assertTrue(foot.y() > wide.find("pref:effect-17").bounds().bottom(), "reset leaves the foot of its own page");
        ScreenFixture small = typical(new int[] {427, 240, 2});
        AdaptScreen stacked = small.open(new Route.Adaptation(TypicalCatalog.CHECKLIST_ADAPTATION));
        assertFalse(stacked.spreadOpen());
        UiRect reset = small.find("pref-reset").bounds();
        UiRect main = small.find("pref:enabled").bounds();
        assertTrue(reset.intersects(new UiRect(reset.x(), main.y(), 1, main.height())) && reset.x() > main.right(),
                "reset takes a row of its own under a stacked checklist: " + reset + " " + main);
        assertEquals(small.find("preferences").bounds().right(), reset.right());

        ScreenFixture notes = typical(new int[] {640, 360, 3});
        notes.open(new Route.Adaptation(TypicalCatalog.ADAPTATION));
        UiNode line = notes.find("stat-line:0");
        assertEquals("+ 4 Max Jumps", line.label());
        notes.draw.calls().clear();
        notes.render();
        assertTrue(notes.draw.calls("text ").contains("text " + line.bounds().x() + "," + line.bounds().y() + " +"), "the mark leaves the margin");
        assertTrue(notes.draw.calls("wrapped ").stream().anyMatch((String call) -> call.startsWith("wrapped " + (line.bounds().x() + 9) + ","
                + line.bounds().y() + " ") && call.endsWith(" 4 Max Jumps")), "a marked stat line wraps beside its mark");
    }

    @Test
    void theGutterRunsTheWholePageWithTheDividersDiamondOnIt() {
        for (Route route : List.of(new Route.Skills(), new Route.Skill(TypicalCatalog.SKILL), new Route.Adaptation(TypicalCatalog.CHECKLIST_ADAPTATION))) {
            ScreenFixture fixture = typical(new int[] {640, 360, 3});
            AdaptScreen screen = fixture.open(route);
            String where = AdaptScreen.routeId(route);
            UiRect frame = fixture.find("frame").bounds();
            fixture.draw.calls().clear();
            fixture.render();
            int top = Integer.MAX_VALUE;
            int bottom = Integer.MIN_VALUE;
            int centre = Integer.MIN_VALUE;
            String crease = " " + Integer.toHexString(UiRamp.PAGE[6]);
            for (String call : fixture.draw.calls("fill ")) {
                String[] box = call.substring(5, call.indexOf(' ', 5)).split(",");
                if (call.endsWith(crease) && Integer.parseInt(box[2]) - Integer.parseInt(box[0]) == 1) {
                    top = Integer.parseInt(box[1]);
                    bottom = Integer.parseInt(box[3]);
                    centre = Integer.parseInt(box[0]);
                }
            }
            assertEquals(frame.y() + screen.theme().frame(), top, where + ": the gutter starts below the page's top edge");
            assertEquals(frame.bottom() - screen.theme().frame(), bottom, where + ": the gutter stops short of the page's bottom edge");
            UiRect second = route instanceof Route.Skills ? null : fixture.find("facing").bounds();
            if (second != null) {
                assertEquals(second.x() - AdaptScreen.SPREAD_GAP / 2, centre, where + ": the gutter is off the fold");
            } else {
                assertEquals(((SkillsScreen) screen).gutter(), centre, where);
            }
        }
    }

    @Test
    void everydayPagesNeverScrollAndStayWithinSixteenByNine() {
        for (int[] size : SIZES) {
            for (Route route : ROUTES) {
                ScreenFixture fixture = typical(size);
                AdaptScreen screen = fixture.open(route);
                UiRect frame = fixture.find("frame").bounds();
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route) + " " + frame;
                ScreenLayoutTest.check(screen, where);
                ScreenLayoutTest.checkText(fixture, where);
                assertEquals(0, screen.scroll().maxScroll(), where + " scrolls");
                if (screen.breakpoint() == UiBreakpoint.WIDE) {
                    assertTrue(frame.width() * 9 <= frame.height() * 16, where + " is wider than 16:9");
                }
                assertTrue(frame.width() < screen.width() && frame.height() < screen.height(), where + " fills the screen");
                assertEquals(screen.width() / 2, frame.centerX(), 1.0, where);
            }
        }
    }

    @Test
    void everythingSitsOnTheUnitGrid() {
        for (int[] size : SIZES) {
            for (Route route : ROUTES) {
                ScreenFixture fixture = typical(size);
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                UiRect page = screen.scroll().bounds();
                for (UiRect tile : tiles(screen.root(), new ArrayList<>())) {
                    assertEquals(0, (tile.x() - page.x()) % UiLayout.UNIT, where + " " + tile);
                    assertEquals(0, (tile.y() - page.y()) % UiLayout.UNIT, where + " " + tile);
                    assertEquals(0, tile.width() % UiLayout.UNIT, where + " " + tile);
                }
                assertEquals(0, page.width() % UiLayout.UNIT, where + " page " + page);
                if (screen.spreadOpen()) {
                    assertEquals(0, fixture.find("page").bounds().width() % UiLayout.UNIT, where);
                    assertEquals(fixture.find("page").bounds().y(), fixture.find("facing").bounds().y(), where);
                }
            }
        }
    }

    @Test
    void theTopBarKeepsToThePageMargins() {
        for (int[] size : SIZES) {
            for (Route route : ROUTES) {
                ScreenFixture fixture = typical(size);
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                UiRect page = screen.scroll().bounds();
                UiRect frame = fixture.find("frame").bounds();
                assertEquals(page.x(), fixture.find("back").bounds().x(), where + " back");
                assertEquals(page.right(), fixture.find("stat:power").bounds().right(), where + " chips");
                assertEquals(page.x() - frame.x(), frame.right() - page.right(), where + " side margins");
                assertEquals(page.x() - frame.x(), fixture.find("back").bounds().y() - frame.y(), where + " top margin");
            }
        }
    }

    @Test
    void firstLinesShareABaselineAcrossTheSpine() {
        for (int[] size : new int[][] {{640, 360, 3}, {815, 458, 3}, {960, 540, 2}}) {
            ScreenFixture fixture = typical(size);
            SkillScreen skill = fixture.open(new Route.Skill(TypicalCatalog.SKILL));
            assertTrue(skill.spreadOpen());
            int top = skill.scroll().bounds().y();
            assertEquals(textY(fixture, "Air Dash", top), textY(fixture, "Agility", top), size[0] + "x" + size[1] + " skill");
            AdaptationScreen adaptation = fixture.open(new Route.Adaptation(TypicalCatalog.ADAPTATION));
            assertTrue(adaptation.spreadOpen());
            top = adaptation.scroll().bounds().y();
            assertEquals(textY(fixture, "Wall Jump", top), textY(fixture, "Adaptation enabled", top), size[0] + "x" + size[1] + " adaptation");
            assertEquals(fixture.find("unlearn").bounds().centerY(), fixture.find("pref-reset").bounds().centerY(),
                    size[0] + "x" + size[1] + ": unlearn and reset share the foot of the pages");
        }
    }

    /** The y of the first drawn line of exactly {@code text} at or below {@code from}. */
    private static int textY(ScreenFixture fixture, String text, int from) {
        fixture.draw.calls().clear();
        fixture.render();
        for (String call : fixture.draw.calls("text ")) {
            int space = call.indexOf(' ', 5);
            int y = Integer.parseInt(call.substring(5, space).split(",")[1]);
            if (call.substring(space + 1).equals(text) && y >= from) {
                return y;
            }
        }
        throw new AssertionError("no text \"" + text + "\" below " + from);
    }

    @Test
    void theDetailPageKeepsTheSkillAboveTheHoveredAdaptation() {
        ScreenFixture fixture = typical(new int[] {960, 540, 2});
        SkillScreen screen = fixture.open(new Route.Skill(TypicalCatalog.SKILL));
        AdaptationTile tile = screen.tile(TypicalCatalog.adaptationId(0, 4));
        UiRect strip = fixture.find("strip").bounds();
        UiRect frame = fixture.find("frame").bounds();
        fixture.draw.calls().clear();
        screen.render(fixture.draw, tile.bounds().centerX(), tile.bounds().centerY(), 0.0F);
        int skill = -1;
        int adaptation = -1;
        boolean described = false;
        for (String call : fixture.draw.calls()) {
            String[] parts = call.split(" ", 3);
            if (call.startsWith("text ") && Integer.parseInt(parts[1].split(",")[0]) == strip.x()) {
                skill = parts[2].equals("Agility") ? Integer.parseInt(parts[1].split(",")[1]) : skill;
                adaptation = parts[2].equals("Kip-Up") ? Integer.parseInt(parts[1].split(",")[1]) : adaptation;
            }
            described |= call.startsWith("wrapped ") && call.endsWith("release shift to jump.");
        }
        assertTrue(skill >= strip.y() && adaptation > skill, "skill at " + skill + ", adaptation at " + adaptation);
        assertTrue(described, "the detail page leaves out what the adaptation does");
        assertNull(tile.tooltip(), "the tooltip repeats the detail page");
        assertEquals(frame, fixture.find("frame").bounds(), "hovering resizes the book");
        assertEquals(fixture.find("facing").bounds().bottom(), fixture.find("preferences").bounds().bottom(),
                "the skill's preferences leave the foot of the page");

        ScreenFixture small = typical(new int[] {427, 240, 2});
        SkillScreen stacked = small.open(new Route.Skill(TypicalCatalog.SKILL));
        assertFalse(stacked.spreadOpen());
        assertNotNull(stacked.tile(TypicalCatalog.adaptationId(0, 4)).tooltip(), "a single page keeps the description in the tooltip");
    }

    @Test
    void theActionStaysAtTheFootOfThePageForEveryLevel() {
        for (int[] size : SIZES) {
            ScreenFixture fixture = typical(size);
            AdaptationScreen screen = fixture.open(new Route.Adaptation(TypicalCatalog.ADAPTATION));
            String where = size[0] + "x" + size[1];
            UiRect frame = fixture.find("frame").bounds();
            UiRect page = fixture.find("page").bounds();
            Integer row = null;
            for (int level = 1; level <= 5; level++) {
                screen.pick(level);
                fixture.render();
                UiRect action = fixture.find("detail.action").bounds();
                assertEquals(page.bottom(), action.bottom(), where + " level " + level + ": the action row leaves the foot of the page");
                assertEquals(page.x(), action.x(), where + " level " + level + ": the action row leaves the page's left edge");
                if (row == null) {
                    row = action.y();
                }
                assertEquals(row.intValue(), action.y(), where + " level " + level + " moves the action");
                assertEquals(frame, fixture.find("frame").bounds(), where + " level " + level + " resizes the book");
            }
        }
    }

    @Test
    void searchingKeepsTheBookItsSize() {
        ScreenFixture fixture = typical(new int[] {815, 458, 3});
        SkillsScreen screen = fixture.open(new Route.Skills());
        UiRect frame = fixture.find("frame").bounds();
        UiRect first = screen.tile(TypicalCatalog.SKILL).bounds();
        for (String query : List.of("a", "ag", "agi", "zzz", "")) {
            screen.search().setText(query);
            fixture.render();
            assertEquals(frame, fixture.find("frame").bounds(), "query \"" + query + "\"");
        }
        assertEquals(first, screen.tile(TypicalCatalog.SKILL).bounds());
    }

    @Test
    void sizeClassesHoldSteadyAroundAThreshold() {
        ScreenFixture fixture = typical(new int[] {700, 400, 2});
        AdaptScreen screen = fixture.open(new Route.Skill(TypicalCatalog.SKILL));
        assertEquals(UiBreakpoint.WIDE, screen.breakpoint());
        int changes = 0;
        UiBreakpoint held = screen.breakpoint();
        for (int width = 700; width >= 560; width--) {
            fixture.resize(width, 400, 2);
            fixture.render();
            changes += screen.breakpoint() == held ? 0 : 1;
            held = screen.breakpoint();
        }
        assertEquals(UiBreakpoint.REGULAR, held);
        assertEquals(1, changes, "shrinking crosses the threshold once");
        for (int step = 0; step < 40; step++) {
            fixture.resize(UiBreakpoint.WIDE_WIDTH - 2 + step % 5, 400, 2);
            fixture.render();
            assertEquals(UiBreakpoint.REGULAR, screen.breakpoint(), "a drag back and forth over the threshold flips the class");
        }
        fixture.resize(UiBreakpoint.WIDE_WIDTH + UiBreakpoint.HYSTERESIS, 400, 2);
        fixture.render();
        assertEquals(UiBreakpoint.WIDE, screen.breakpoint());
        for (int step = 0; step < 40; step++) {
            fixture.resize(UiBreakpoint.WIDE_WIDTH - 2 + step % 5, 400, 2);
            fixture.render();
            assertEquals(UiBreakpoint.WIDE, screen.breakpoint(), "a drag back and forth over the threshold flips the class");
        }
    }

    @Test
    void aWiderWindowNeverTakesColumnsAway() {
        ScreenFixture fixture = typical(new int[] {300, 400, 2});
        SkillsScreen screen = fixture.open(new Route.Skills());
        int columns = 0;
        int spines = 0;
        boolean spine = false;
        StringBuilder seen = new StringBuilder();
        for (int width = 300; width <= 1000; width++) {
            fixture.resize(width, 400, 2);
            fixture.render();
            TreeSet<Integer> lefts = new TreeSet<>();
            for (UiRect tile : tiles(fixture.find("skills"), new ArrayList<>())) {
                lefts.add(tile.x());
            }
            boolean open = screen.gutter() != Integer.MIN_VALUE;
            if (lefts.size() != columns || open != spine) {
                seen.append(width).append(':').append(lefts.size()).append(open ? "s " : " ");
            }
            assertTrue(lefts.size() >= columns, "columns drop as the window grows: " + seen);
            assertTrue(lefts.size() <= SkillsScreen.MAX_COLUMNS, seen.toString());
            spines += open && !spine ? 1 : 0;
            assertTrue(open || !spine, "the spine goes away as the window grows: " + seen);
            columns = lefts.size();
            spine = open;
        }
        assertEquals(SkillsScreen.MAX_COLUMNS, columns, seen.toString());
        assertEquals(1, spines, seen.toString());
    }

    @Test
    void commonSizesGetOneLayoutHoweverTheWindowGotThere() {
        int[][] sequence = {{640, 360, 3}, {960, 540, 2}, {480, 270, 4}, {815, 458, 3}, {427, 240, 2}, {1280, 720, 2}, {320, 240, 2},
                {640, 360, 3}, {480, 270, 4}, {1920, 1080, 1}, {815, 458, 3}};
        for (Route route : ROUTES) {
            ScreenFixture fixture = typical(sequence[0]);
            AdaptScreen screen = fixture.open(route);
            for (int[] size : sequence) {
                fixture.resize(size[0], size[1], size[2]);
                fixture.render();
                String where = AdaptScreen.routeId(route) + " resized to " + size[0] + "x" + size[1];
                ScreenLayoutTest.check(screen, where);
                ScreenFixture fresh = typical(size);
                AdaptScreen opened = fresh.open(route);
                assertEquals(opened.breakpoint(), screen.breakpoint(), where);
                assertEquals(snapshot(opened.root()), snapshot(screen.root()), where + " differs from a page opened at that size");
            }
        }
    }

    private static Map<String, UiRect> snapshot(UiNode node) {
        Map<String, UiRect> bounds = new LinkedHashMap<>();
        collect(node, bounds);
        return bounds;
    }

    private static void collect(UiNode node, Map<String, UiRect> bounds) {
        if (!node.visible()) {
            return;
        }
        bounds.put(node.id(), node.bounds());
        for (UiNode child : node.children()) {
            collect(child, bounds);
        }
    }

    @Test
    void aResizeIsLaidOutBeforeItsFirstFrame() {
        ScreenFixture fixture = typical(new int[] {960, 540, 2});
        AdaptScreen screen = fixture.open(new Route.Skills());
        for (int[] size : new int[][] {{480, 270, 4}, {815, 458, 3}, {320, 240, 2}, {960, 540, 2}}) {
            fixture.resize(size[0], size[1], size[2]);
            fixture.draw.calls().clear();
            fixture.render();
            UiRect frame = fixture.find("frame").bounds();
            String where = size[0] + "x" + size[1] + " " + frame;
            assertTrue(frame.x() >= 0 && frame.y() >= 0 && frame.right() <= size[0] && frame.bottom() <= size[1], where);
            for (String call : fixture.draw.calls("blit ")) {
                String[] parts = call.split(" ");
                int x = Integer.parseInt(parts[2].split(",")[0]);
                int width = Integer.parseInt(parts[3].split("x")[0]);
                assertTrue(x + width <= size[0] + 4, where + ": drew past the new screen: " + call);
            }
            assertSame(screen, fixture.current());
        }
    }

    @Test
    void aResizeKeepsFocusSelectionAndHover() {
        ScreenFixture fixture = typical(new int[] {960, 540, 2});
        SkillsScreen skills = fixture.open(new Route.Skills());
        UiNode tile = skills.tile("crafting");
        skills.focus(tile);
        skills.search().setText("a");
        fixture.resize(480, 270, 4);
        fixture.render();
        assertSame(tile, skills.focused());
        assertEquals("a", skills.search().text());
        skills.render(fixture.draw, tile.bounds().centerX(), tile.bounds().centerY(), 0.0F);
        assertSame(tile, skills.hovered());
        fixture.resize(815, 458, 3);
        skills.render(fixture.draw, tile.bounds().centerX(), tile.bounds().centerY(), 0.0F);
        skills.render(fixture.draw, tile.bounds().centerX(), tile.bounds().centerY(), 0.0F);
        assertSame(tile, skills.hovered());
        assertSame(tile, skills.focused());

        AdaptationScreen adaptation = fixture.open(new Route.Adaptation(TypicalCatalog.ADAPTATION));
        adaptation.pick(5);
        fixture.render();
        for (int[] size : new int[][] {{480, 270, 4}, {320, 240, 2}, {1280, 720, 2}}) {
            fixture.resize(size[0], size[1], size[2]);
            fixture.render();
            assertEquals(5, adaptation.shownLevel(), size[0] + "x" + size[1]);
        }

        SkillScreen skill = fixture.open(new Route.Skill(TypicalCatalog.SKILL));
        UiNode hovered = skill.tile(TypicalCatalog.adaptationId(0, 4));
        fixture.resize(815, 458, 3);
        skill.render(fixture.draw, 0, 0, 0.0F);
        skill.render(fixture.draw, hovered.bounds().centerX(), hovered.bounds().centerY(), 0.0F);
        assertEquals("Kip-Up", fixture.find("strip").label());
        fixture.resize(640, 360, 3);
        skill.render(fixture.draw, 0, 0, 0.0F);
        assertEquals("Kip-Up", fixture.find("strip").label(), "the detail page forgets its adaptation on a resize");
    }

    @Test
    void aResizeKeepsTheScrolledPagesPlace() {
        ScreenFixture fixture = new ScreenFixture(320, 180, 4, MaxCatalog.catalog(), MaxCatalog.player());
        SkillScreen screen = fixture.open(new Route.Skill(MaxCatalog.LARGEST_SKILL));
        assertTrue(screen.scroll().maxScroll() > 0);
        UiNode tile = fixture.find(String.format("adaptation:%s-a%02d", MaxCatalog.LARGEST_SKILL, 12));
        screen.scroll().setScroll(screen.scroll().scroll() + tile.bounds().y() - screen.scroll().bounds().y());
        assertTrue(screen.scroll().scroll() > 0);
        fixture.render();
        UiNode anchor = firstShowing(screen);
        int offset = anchor.bounds().y() - screen.scroll().bounds().y();
        for (int[] size : new int[][] {{330, 186, 4}, {300, 170, 4}, {284, 160, 3}, {320, 180, 4}}) {
            fixture.resize(size[0], size[1], size[2]);
            fixture.render();
            String where = size[0] + "x" + size[1];
            assertTrue(screen.scroll().maxScroll() > 0, where);
            assertEquals(offset, anchor.bounds().y() - screen.scroll().bounds().y(), where + ": the page jumped");
        }
    }

    private static UiNode firstShowing(AdaptScreen screen) {
        int top = screen.scroll().bounds().y();
        UiNode best = null;
        List<UiNode> leaves = new ArrayList<>();
        leaves(screen.scroll(), leaves);
        for (UiNode leaf : leaves) {
            if (leaf.bounds().bottom() > top && (best == null || leaf.bounds().y() < best.bounds().y())) {
                best = leaf;
            }
        }
        return best;
    }

    private static void leaves(UiNode node, List<UiNode> found) {
        boolean leaf = true;
        for (UiNode child : node.children()) {
            if (child.visible()) {
                leaf = false;
                leaves(child, found);
            }
        }
        if (leaf && !node.bounds().isEmpty()) {
            found.add(node);
        }
    }
}
