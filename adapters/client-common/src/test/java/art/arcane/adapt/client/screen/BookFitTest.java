package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiScrollEvent;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.TextBox;
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
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One book for every page: at each screen size and GUI scale the skills list, a skill and an adaptation get the same
 * frame, {@link AdaptScreen#BOOK_WIDTH} by {@link AdaptScreen#BOOK_HEIGHT} where the screen has room and the largest
 * 16:9 book it allows where it does not, whatever the pages hold and however it changes. Medium and large screens open
 * two equal facing pages with the spine in the middle, and every page of the budget's catalog fits the one open spread
 * with nothing to turn; a single page reaches the spread's second page by turning. A resize or GUI scale change under an
 * open page gives the layout a fresh page would have while keeping the page, selection, focus and hover.
 */
class BookFitTest {
    /** 1080p at GUI 4, 3 and 2, 1440p at GUI 2, and a 2445x1376 window at GUI 3. */
    private static final int[][] SIZES = {{480, 270, 4}, {640, 360, 3}, {960, 540, 2}, {1280, 720, 2}, {815, 458, 3}};
    /**
     * Scaled screens with their GUI scale: a 2560x1410 window at GUI 4, 1080p at GUI 1 to 4, an 854x480 window at
     * GUI 2 and a 427x240 one at GUI 1.
     */
    private static final int[][] SCREENS = {{640, 352, 4}, {1920, 1080, 1}, {960, 540, 2}, {640, 360, 3}, {480, 270, 4}, {427, 240, 2},
            {427, 240, 1}};
    private static final List<Route> ROUTES = List.of(new Route.Skills(), new Route.Skill(TypicalCatalog.SKILL),
            new Route.Adaptation(TypicalCatalog.ADAPTATION), new Route.Adaptation(TypicalCatalog.PLAIN_ADAPTATION),
            new Route.Adaptation(TypicalCatalog.SWITCH_ADAPTATION), new Route.Adaptation(TypicalCatalog.CHECKLIST_ADAPTATION));

    private static ScreenFixture typical(int[] size) {
        return typical(size, TypicalCatalog.SKILLS);
    }

    private static ScreenFixture typical(int[] size, int skills) {
        return new ScreenFixture(size[0], size[1], size[2], TypicalCatalog.catalog(skills), TypicalCatalog.player(skills));
    }

    private static List<UiRect> tiles(UiNode node, List<UiRect> found) {
        if (node instanceof RowTile && node.shown()) {
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

    private static boolean spread(AdaptScreen screen) {
        return screen.breakpoint() != UiBreakpoint.COMPACT;
    }

    @Test
    void skillsTakeTheirSeatsInWholeRowsOfThreeCards() {
        for (int[] size : SIZES) {
            for (int skills : new int[] {24, 23, 22, 21, 17, 13, 5}) {
                ScreenFixture fixture = typical(size, skills);
                SkillsScreen screen = fixture.open(new Route.Skills());
                String where = size[0] + "x" + size[1] + " " + skills + " skills";
                assertTrue(spread(screen), where + " has no spine");
                assertEquals(1, screen.leaves(), where + " needs a second leaf");
                UiRect leftPage = fixture.find("sheet:0").bounds();
                List<UiRect> left = tiles(fixture.find("sheet:0"), new ArrayList<>());
                List<UiRect> right = fixture.find("sheet:1") == null ? List.of() : tiles(fixture.find("sheet:1"), new ArrayList<>());
                assertEquals(skills, left.size() + right.size(), where);
                TreeSet<Integer> leftRows = rows(left);
                assertEquals(Math.min(skills, SkillsScreen.LAYOUT.columns() * SkillsScreen.LAYOUT.rows()), left.size(),
                        where + ": the left page fills first");
                assertEquals(leftPage.y(), leftRows.first().intValue(), where + ": the first row leaves the top of the page");
                int row = 0;
                for (int y : leftRows) {
                    assertEquals(leftPage.y() + row++ * SkillsScreen.LAYOUT.pitch(), y, where + ": rows keep their pitch");
                }
                TreeSet<Integer> columns = new TreeSet<>();
                for (UiRect tile : left) {
                    columns.add(tile.x());
                    assertEquals(SkillsScreen.LAYOUT.tileHeight(), tile.height(), where);
                }
                assertEquals(Math.min(skills, SkillsScreen.LAYOUT.columns()), columns.size(), where + " columns " + columns);
            }
        }
    }

    @Test
    void cardsSitCentredOnTheirPage() {
        for (int[] size : new int[][] {{480, 270, 4}, {640, 352, 4}, {960, 540, 2}}) {
            for (int skills : new int[] {24, 9}) {
                ScreenFixture fixture = typical(size, skills);
                fixture.open(new Route.Skills());
                for (String id : List.of("sheet:0", "sheet:1")) {
                    if (fixture.find(id) == null) {
                        continue;
                    }
                    UiRect page = fixture.find(id).bounds();
                    int left = Integer.MAX_VALUE;
                    int right = Integer.MIN_VALUE;
                    for (UiRect tile : tiles(fixture.find(id), new ArrayList<>())) {
                        left = Math.min(left, tile.x());
                        right = Math.max(right, tile.right());
                    }
                    String where = size[0] + "x" + size[1] + " " + skills + " skills " + id;
                    assertTrue(Math.abs((left - page.x()) - (page.right() - right)) <= UiLayout.UNIT,
                            where + ": cards from " + left + " to " + right + " on " + page);
                }
            }
        }
    }

    @Test
    void everyPageGetsTheSameFrame() {
        UiRect owner = null;
        for (int[] size : SCREENS) {
            for (String catalog : List.of("typical", "preview", "max")) {
                List<Route> routes = switch (catalog) {
                    case "typical" -> ROUTES;
                    case "preview" -> List.of(new Route.Skills(), new Route.Skill("agility"), new Route.Skill("rift"),
                            new Route.Adaptation("agility-wall-jump"), new Route.Adaptation("rift-gate"));
                    default -> List.of(new Route.Skills(), new Route.Skill(MaxCatalog.LARGEST_SKILL),
                            new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION), new Route.Adaptation(MaxCatalog.CHOICES_ADAPTATION));
                };
                ScreenFixture fixture = switch (catalog) {
                    case "typical" -> typical(size);
                    case "preview" -> new ScreenFixture(size[0], size[1], size[2]);
                    default -> new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
                };
                UiRect first = null;
                for (Route route : routes) {
                    AdaptScreen screen = fixture.open(route);
                    UiRect frame = fixture.find("frame").bounds();
                    String where = size[0] + "x" + size[1] + "/" + size[2] + " " + catalog + " " + AdaptScreen.routeId(route) + " " + frame;
                    if (first == null) {
                        first = frame;
                    }
                    assertEquals(first, frame, where + ": the pages differ in size");
                    assertTrue(frame.x() >= 0 && frame.y() >= 0 && frame.right() <= size[0] && frame.bottom() <= size[1], where + " leaves the screen");
                    assertTrue(frame.width() * 9 <= frame.height() * 16, where + " is wider than 16:9");
                    assertTrue(frame.width() <= AdaptScreen.BOOK_WIDTH && frame.height() <= AdaptScreen.BOOK_HEIGHT, where);
                    assertEquals(screen.width() / 2, frame.centerX(), 1.0, where);
                    UiNode right = fixture.find("sheet:1");
                    if (spread(screen) && right != null) {
                        UiRect left = fixture.find("sheet:0").bounds();
                        assertEquals(left.width(), right.bounds().width(), where + ": the facing pages differ");
                        assertEquals(frame.centerX(), left.right() + AdaptScreen.SPREAD_GAP / 2, 1.0, where + ": the spine is off centre");
                    }
                    ScreenLayoutTest.checkEveryLeaf(fixture, where);
                    CompactFitTest.checkTiles(screen.root(), where);
                }
                if (size[0] == 640 && size[1] == 352) {
                    owner = first;
                }
            }
        }
        assertEquals(new UiRect(90, 46, AdaptScreen.BOOK_WIDTH, AdaptScreen.BOOK_HEIGHT), owner);
    }

    @Test
    void theBookKeepsItsSizeInScaledUnitsAndShrinksOnlyToFitTheScreen() {
        for (int scale = 1; scale <= 3; scale++) {
            ScreenFixture fixture = typical(new int[] {1920 / scale, 1080 / scale, scale});
            fixture.open(new Route.Skills());
            UiRect frame = fixture.find("frame").bounds();
            assertEquals(AdaptScreen.BOOK_WIDTH, frame.width(), "1080p at GUI " + scale);
            assertEquals(AdaptScreen.BOOK_HEIGHT, frame.height(), "1080p at GUI " + scale);
        }
        ScreenFixture quarter = typical(new int[] {480, 270, 4});
        quarter.open(new Route.Skills());
        assertEquals(new UiRect(16, 2, 448, 252), quarter.find("frame").bounds(), "1080p at GUI 4 leaves room for the message slip");
        ScreenFixture small = typical(new int[] {427, 240, 2});
        AdaptScreen screen = small.open(new Route.Skill(TypicalCatalog.SKILL));
        UiRect frame = small.find("frame").bounds();
        assertEquals(UiBreakpoint.COMPACT, screen.breakpoint());
        assertEquals(new UiRect(16, 2, 394, 222), frame);
        ScreenFixture squat = typical(new int[] {1720, 480, 2});
        squat.open(new Route.Skills());
        frame = squat.find("frame").bounds();
        assertTrue(frame.width() * 9 <= frame.height() * 16, frame.toString());
    }

    /**
     * Every block of every page of the budget's catalog shows on the one open spread, with no leaf to turn, but for the
     * largest skill's adaptations, which turn on further right pages.
     */
    @Test
    void everythingFitsTheOpenSpread() {
        List<Route> routes = new ArrayList<>(List.of(new Route.Skills(), new Route.Skill("skill01"),
                new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION), new Route.Adaptation(MaxCatalog.SWITCHES_ADAPTATION)));
        for (int index = 0; index < 15; index++) {
            routes.add(new Route.Adaptation(String.format("skill01-a%02d", index)));
        }
        for (int[] size : new int[][] {{640, 352, 4}, {1280, 705, 2}, {2560, 1410, 1}, {960, 540, 2}, {853, 470, 3}}) {
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player(), true);
            for (Route route : routes) {
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                if (route instanceof Route.Skill) {
                    ScreenLayoutTest.checkEveryLeaf(fixture, where);
                    continue;
                }
                assertEquals(1, screen.leaves(), where + " needs a second leaf");
                assertFalse(fixture.shows("leaf:next"), where);
                ScreenLayoutTest.check(screen, where);
                ScreenLayoutTest.checkText(fixture, where);
            }
            for (Route route : ROUTES) {
                ScreenFixture typical = typical(size);
                AdaptScreen screen = typical.open(route);
                assertEquals(1, screen.leaves(), size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route) + " needs a second leaf");
            }
        }
    }

    /**
     * The adaptations take the right page alone, in one strict grid: up to three cards a row, every card the same width
     * in the same three columns, the grid centred on the page.
     */
    @Test
    void theAdaptationsAreOneStrictGridOnTheRightPage() {
        for (int[] size : new int[][] {{640, 352, 4}, {480, 270, 4}, {960, 540, 2}}) {
            for (boolean max : new boolean[] {false, true}) {
                ScreenFixture fixture = max ? new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player(), true)
                        : typical(size);
                String skill = max ? "skill01" : TypicalCatalog.SKILL;
                AdaptScreen screen = fixture.open(new Route.Skill(skill));
                String where = size[0] + "x" + size[1] + " " + skill;
                UiRect right = fixture.find("sheet:1").bounds();
                List<UiRect> cells = new ArrayList<>();
                for (int leaf = 0; leaf < screen.leaves(); leaf++) {
                    screen.turnTo(leaf);
                    fixture.render();
                    assertTrue(tiles(fixture.find("sheet:0"), new ArrayList<>()).isEmpty(), where + ": an adaptation on the left page");
                    cells.addAll(tiles(fixture.find("sheet:" + (leaf + 1)), new ArrayList<>()));
                }
                Set<Integer> columns = new TreeSet<>();
                for (UiRect cell : cells) {
                    assertEquals(cells.get(0).width(), cell.width(), where + " " + cell);
                    assertTrue(cell.x() >= right.x() && cell.right() <= right.right(), where + " " + cell);
                    columns.add(cell.x());
                }
                assertEquals(SkillScreen.COLUMNS, columns.size(), where + " columns " + columns);
                int leftRoom = cells.get(0).x() - right.x();
                int rightRoom = right.right() - cells.stream().mapToInt(UiRect::right).max().orElse(0);
                assertTrue(Math.abs(leftRoom - rightRoom) <= 3, where + " the grid is not centred");
                assertEquals(max ? 15 : TypicalCatalog.ADAPTATIONS, cells.size(), where);
                assertTrue(fixture.shows("detail"), where + ": no adaptation beside the skill");
            }
        }
    }

    /** The words that open the two pages, the skill's name and the adaptations' heading, share one line. */
    @Test
    void thePagesOpenOnOneLine() {
        for (String[] pair : new String[][] {{"adaptation", "Wall Jump", "Settings"}}) {
            ScreenFixture fixture = typical(new int[] {640, 352, 4});
            fixture.open(pair[0].equals("skill") ? new Route.Skill(TypicalCatalog.SKILL) : new Route.Adaptation(TypicalCatalog.ADAPTATION));
            fixture.draw.calls().clear();
            fixture.render();
            int top = fixture.find("sheet:0").bounds().y();
            int left = Integer.MIN_VALUE;
            int right = Integer.MAX_VALUE - 1;
            for (String call : fixture.draw.calls("text ")) {
                String[] parts = call.split(" ", 3);
                int y = Integer.parseInt(parts[1].split(",")[1]);
                if (y < top) {
                    continue;
                }
                left = parts[2].equals(pair[1]) && left == Integer.MIN_VALUE ? y : left;
                right = parts[2].equals(pair[2]) && right == Integer.MAX_VALUE - 1 ? y : right;
            }
            assertEquals(left, right, pair[0] + ": the pages open on different lines");
        }
    }

    @Test
    void aLongNameWrapsInsideItsCellAtWordsBeforeHyphens() {
        KitDraw draw = KitDraw.vanilla(640, 352, 4);
        int room = AdaptationTile.nameRoom(AdaptationGrid.cardWidth(212, 3));
        assertEquals(List.of("Infernal", "Attunement"), CardName.fit(draw, "Infernal Attunement", room, 3));
        assertEquals(List.of("Wall Jump"), CardName.fit(draw, "Wall Jump", room, 3));
        assertEquals(List.of("Items", "Drop-To-", "Inventory"), CardName.fit(draw, "Items Drop-To-Inventory", room, 3));
        List<String> broken = CardName.fit(draw, "Chains of Mephistopheles", room, 3);
        assertEquals("Chains of", broken.get(0), "a word wider than the cell breaks inside at the font's size: " + broken);
        AdaptationGridTest.assertWholeWordsOrFourLetters("Chains of Mephistopheles", broken);
    }

    @Test
    void anArmorUpChecklistFitsTheRightPageWhole() {
        for (int[] size : new int[][] {{640, 352, 4}, {640, 360, 3}, {960, 540, 2}, {815, 458, 3}, {480, 270, 4}}) {
            ScreenFixture fixture = typical(size);
            AdaptScreen screen = fixture.open(new Route.Adaptation(TypicalCatalog.CHECKLIST_ADAPTATION));
            String where = size[0] + "x" + size[1];
            assertEquals(1, screen.leaves(), where);
            UiRect right = fixture.find("sheet:1").bounds();
            for (int index = 0; index < 18; index++) {
                UiRect entry = fixture.find("pref:effect-" + index).bounds();
                assertTrue(entry.x() >= right.x() && entry.right() <= right.right() && entry.y() >= right.y()
                        && entry.bottom() <= right.bottom(), where + " effect-" + index + " " + entry + " outside " + right);
            }
            ScreenLayoutTest.check(screen, where);
        }
    }

    @Test
    void statMarksHangInTheMargin() {
        ScreenFixture notes = typical(new int[] {640, 360, 3});
        notes.open(new Route.Adaptation(TypicalCatalog.ADAPTATION));
        UiRect box = notes.find("detail.stats").bounds();
        notes.draw.calls().clear();
        notes.render();
        assertTrue(notes.draw.calls("text ").contains("text " + box.x() + "," + box.y() + " +"), "the mark leaves the margin");
        assertTrue(notes.draw.calls("wrapped ").stream().anyMatch((String call) -> call.startsWith("wrapped " + (box.x() + 9) + ","
                + box.y() + " ") && call.endsWith(" 4 Max Jumps")), "a marked stat line wraps beside its mark");
    }

    @Test
    void theGutterRunsTheWholePageAndHoldsNothingButTheSpine() {
        for (Route route : ROUTES) {
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
            assertEquals(frame.centerX(), centre, where + ": the gutter is off the fold");
            assertEquals(fixture.find("sheet:0").bounds().right() + AdaptScreen.SPREAD_GAP / 2, centre, where + ": the gutter is off the fold");
            ScreenLayoutTest.checkEveryLeaf(fixture, where);
        }
    }

    @Test
    void everythingSitsOnTheUnitGrid() {
        for (int[] size : SIZES) {
            for (Route route : ROUTES) {
                ScreenFixture fixture = typical(size);
                fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                UiRect page = fixture.find("sheet:0").bounds();
                for (UiRect tile : tiles(fixture.current().root(), new ArrayList<>())) {
                    UiRect own = tile.x() >= page.right() ? fixture.find("sheet:1").bounds() : page;
                    assertEquals(0, (tile.x() - own.x()) % UiLayout.UNIT, where + " " + tile);
                }
                assertEquals(0, page.width() % UiLayout.UNIT, where + " page " + page);
            }
        }
    }

    @Test
    void theHeadersKeepToThePageMargins() {
        for (int[] size : SIZES) {
            for (Route route : ROUTES) {
                ScreenFixture fixture = typical(size);
                fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                UiRect left = fixture.find("sheet:0").bounds();
                UiNode second = fixture.find("sheet:1");
                UiRect right = second == null ? left : second.bounds();
                UiRect frame = fixture.find("frame").bounds();
                assertEquals(left.x(), fixture.find("back").bounds().x(), where + " back");
                assertEquals(right.right(), fixture.find("stat:power").bounds().right(), where + " chips");
                assertEquals(left.x() - frame.x(), fixture.find("back").bounds().y() - frame.y(), where + " top margin");
            }
        }
    }

    @Test
    void contentChangesNeverMoveTheBook() {
        ScreenFixture fixture = typical(new int[] {640, 352, 4});
        SkillsScreen skills = fixture.open(new Route.Skills());
        UiRect frame = fixture.find("frame").bounds();
        for (String query : List.of("a", "ag", "zzz", "")) {
            skills.search().setText(query);
            fixture.render();
            assertEquals(frame, fixture.find("frame").bounds(), "query \"" + query + "\"");
        }
        AdaptationScreen adaptation = fixture.open(new Route.Adaptation(TypicalCatalog.ADAPTATION));
        for (int level = 1; level <= 5; level++) {
            adaptation.pick(level);
            fixture.render();
            assertEquals(frame, fixture.find("frame").bounds(), "level " + level);
        }
        fixture.state.acceptPlayer(TypicalCatalog.player(9));
        fixture.render();
        assertEquals(frame, fixture.find("frame").bounds(), "a player update");
        fixture.state.acceptCatalog(TypicalCatalog.catalog(5));
        fixture.state.acceptPlayer(TypicalCatalog.player(5));
        fixture.open(new Route.Skills());
        assertEquals(frame, fixture.find("frame").bounds(), "a smaller catalog");
        fixture.open(new Route.Skill(TypicalCatalog.SKILL));
        assertEquals(frame, fixture.find("frame").bounds(), "the skill page of a smaller catalog");
    }

    @Test
    void theActionStaysAtTheFootOfTheLeftPageForEveryLevel() {
        for (int[] size : SIZES) {
            ScreenFixture fixture = typical(size);
            AdaptationScreen screen = fixture.open(new Route.Adaptation(TypicalCatalog.ADAPTATION));
            String where = size[0] + "x" + size[1];
            UiRect frame = fixture.find("frame").bounds();
            UiRect page = fixture.find("sheet:0").bounds();
            Integer row = null;
            Map<String, UiRect> before = null;
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
                Map<String, UiRect> blocks = new LinkedHashMap<>();
                for (UiNode block : fixture.find("sheet:0").children()) {
                    blocks.put(block.id(), block.bounds());
                }
                if (before != null) {
                    assertEquals(before, blocks, where + " level " + level + " moves the left page");
                }
                before = blocks;
            }
        }
    }

    @Test
    void searchingKeepsTheBookItsSizeAndStartsFromTheFirstPage() {
        ScreenFixture fixture = new ScreenFixture(427, 240, 2, MaxCatalog.catalog(), MaxCatalog.player());
        SkillsScreen screen = fixture.open(new Route.Skills());
        UiRect frame = fixture.find("frame").bounds();
        assertTrue(screen.leaves() > 1);
        screen.focus(screen.search());
        for (char typed : "skill".toCharArray()) {
            screen.turnTo(1);
            fixture.render();
            screen.charTyped(new UiCharEvent(typed, UiModifiers.NONE));
            fixture.render();
            assertEquals(frame, fixture.find("frame").bounds(), "query \"" + screen.search().text() + "\"");
            assertEquals(0, screen.leaf(), "query \"" + screen.search().text() + "\"");
        }
        assertEquals("skill", screen.search().text());
    }

    /** A single page shows the spread's second page by turning, with arrows, its number, the wheel and the keys. */
    @Test
    void aSinglePageTurnsToTheSecondPage() {
        ScreenFixture fixture = typical(new int[] {427, 240, 2});
        AdaptScreen screen = fixture.open(new Route.Skills());
        assertEquals(2, screen.leaves());
        assertFalse(fixture.shows("leaf:previous"), "a first page offers to turn back");
        fixture.click(fixture.find("leaf:next"));
        assertEquals(1, screen.leaf());
        fixture.click(fixture.find("leaf:previous"));
        assertEquals(0, screen.leaf());
        screen.focus(null);
        assertTrue(fixture.key(UiKey.PAGE_DOWN));
        assertEquals(1, screen.leaf());
        assertTrue(fixture.key(UiKey.PAGE_UP));
        assertEquals(0, screen.leaf());
        assertTrue(fixture.key(UiKey.RIGHT));
        assertEquals(1, screen.leaf(), "the right arrow with nothing focused turns");
        assertTrue(fixture.key(UiKey.LEFT));
        assertEquals(0, screen.leaf());
        UiRect page = fixture.find("sheet:0").bounds();
        assertTrue(screen.mouseScrolled(new UiScrollEvent(page.centerX(), page.centerY(), 0.0, -1.0)));
        assertEquals(1, screen.leaf(), "the wheel turns");
        fixture.key(UiKey.PAGE_DOWN);
        assertEquals(1, screen.leaf(), "turning stops at the last page");
        assertFalse(fixture.shows("leaf:next"));
        fixture.draw.calls().clear();
        fixture.render();
        assertTrue(fixture.draw.calls("text ").stream().anyMatch((String call) -> call.endsWith(" 2")), "the page number is missing");
    }

    @Test
    void anOpenSpreadHasNoArrowsNoNumbersAndNothingToTurn() {
        ScreenFixture fixture = new ScreenFixture(640, 352, 4, MaxCatalog.catalog(), MaxCatalog.player());
        AdaptScreen screen = fixture.open(new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION));
        assertEquals(1, screen.leaves());
        assertFalse(fixture.shows("leaf:previous"));
        assertFalse(fixture.shows("leaf:next"));
        screen.focus(null);
        fixture.key(UiKey.PAGE_DOWN);
        fixture.key(UiKey.RIGHT);
        assertEquals(0, screen.leaf());
        UiRect frame = fixture.find("frame").bounds();
        fixture.draw.calls().clear();
        fixture.render();
        for (String call : fixture.draw.calls("text ")) {
            String[] parts = call.split(" ", 3);
            int y = Integer.parseInt(parts[1].split(",")[1]);
            assertFalse(y > frame.bottom() - 16 && parts[2].matches("\\d+"), "a page number on an open spread: " + call);
        }
    }

    @Test
    void longTextScrollsItselfInsideItsBox() {
        ScreenFixture fixture = new ScreenFixture(640, 352, 4, MaxCatalog.catalog(), MaxCatalog.player());
        fixture.open(new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION));
        TextBox description = (TextBox) fixture.find("description");
        assertTrue(description.overflow() > 0, "the long description fits its box");
        assertEquals(0, description.offset());
        fixture.clock.advance(TextBox.PAUSE_MILLIS - 100L);
        fixture.render();
        fixture.render();
        assertEquals(0, description.offset(), "the text moves before its pause is over");
        fixture.clock.advance(200L);
        fixture.render();
        fixture.clock.advance(1000L);
        fixture.render();
        assertEquals(Math.round(TextBox.SPEED), description.offset(), 1, "the text drifts at its slow speed");
        UiRect box = description.bounds();
        fixture.current().render(fixture.draw, box.centerX(), box.centerY(), 0.0F);
        int held = description.offset();
        fixture.clock.advance(3000L);
        fixture.current().render(fixture.draw, box.centerX(), box.centerY(), 0.0F);
        assertEquals(held, description.offset(), "the text moves under the pointer");
        assertTrue(fixture.current().mouseScrolled(new UiScrollEvent(box.centerX(), box.centerY(), 0.0, -2.0)));
        assertTrue(description.offset() > held, "the wheel does not move the text");
        assertEquals(0, fixture.current().leaf(), "the wheel over the text turned the leaf");
        long full = (long) (description.overflow() / TextBox.SPEED * 1000.0F) + TextBox.PAUSE_MILLIS * 3;
        boolean bottom = false;
        boolean over = false;
        for (long step = 0; step < full; step += 250L) {
            fixture.clock.advance(250L);
            fixture.render();
            bottom |= description.offset() == description.overflow();
            over |= bottom && description.offset() == 0;
        }
        assertTrue(bottom, "the text never reaches its end");
        assertTrue(over, "the text never starts over");
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
    void commonSizesGetOneLayoutHoweverTheWindowGotThere() {
        int[][] sequence = {{640, 360, 3}, {960, 540, 2}, {480, 270, 4}, {815, 458, 3}, {427, 240, 2}, {1280, 720, 2}, {320, 240, 2},
                {640, 360, 3}, {480, 270, 4}, {1920, 1080, 1}, {815, 458, 3}};
        for (Route route : ROUTES) {
            ScreenFixture fixture = typical(sequence[0]);
            AdaptScreen screen = fixture.open(route);
            for (int[] size : sequence) {
                fixture.resize(size[0], size[1], size[2]);
                fixture.render();
                screen.turnTo(0);
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
    }

    @Test
    void aResizeKeepsThePageHoldingTheFirstBlockShowing() {
        ScreenFixture fixture = typical(new int[] {427, 240, 2});
        SkillsScreen screen = fixture.open(new Route.Skills());
        assertEquals(2, screen.leaves());
        screen.turnTo(1);
        fixture.render();
        for (int[] size : new int[][] {{427, 240, 3}, {420, 236, 2}, {427, 240, 2}}) {
            UiNode first = null;
            for (int index = 0; index < TypicalCatalog.SKILLS && first == null; index++) {
                UiNode tile = screen.tile(TypicalCatalog.skillId(index));
                first = tile.shown() ? tile : null;
            }
            assertNotNull(first);
            fixture.resize(size[0], size[1], size[2]);
            fixture.render();
            assertTrue(first.shown(), size[0] + "x" + size[1] + ": the page lost " + first.id());
        }
        fixture.resize(640, 352, 4);
        fixture.render();
        assertEquals(0, screen.leaf(), "an open spread shows everything from its first leaf");
    }
}
