package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * At the scaled sizes a typical modpack client runs (1080p at GUI 4 = 480x270, 1080p at GUI 3 or 720p at GUI 2 =
 * 640x360, 1080p at GUI 2 = 960x540, 1080p at GUI 1) nothing overlaps, leaves the frame or is cut short, and row tiles
 * are as wide as their text. The catalog's extremes ({@link MaxCatalog}: 24 skills with long names, the largest skill
 * with 25 character names, adaptations with 114 character stat lines and paragraph descriptions) wrap instead of being
 * cut and scroll inside the same book every page gets, with everything in reach; a typical adaptation never scrolls.
 */
class CompactFitTest {
    private static final KitDraw DRAW = new KitDraw();
    private static final int[][] SIZES = {{480, 270, 4}, {640, 360, 3}, {960, 540, 2}, {1920, 1080, 1}};
    private static final String TYPICAL_SKILL = "skill01";
    private static final String TYPICAL_ADAPTATION = "skill01-a04";
    private static final List<Route> ROUTES = List.of(new Route.Skills(), new Route.Skill(TYPICAL_SKILL),
            new Route.Adaptation(TYPICAL_ADAPTATION), new Route.Skill(MaxCatalog.LARGEST_SKILL),
            new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION), new Route.Adaptation(MaxCatalog.SWITCHES_ADAPTATION),
            new Route.Adaptation(MaxCatalog.CHOICES_ADAPTATION));
    /** Whether each route fits the book without scrolling, in {@link #ROUTES} order. */
    private static final boolean[] FITS = {false, false, true, false, false, false, false};

    @Test
    void pagesFitWithoutScrollingOrCuttingText() {
        StringBuilder report = new StringBuilder();
        StringBuilder failures = new StringBuilder();
        for (int[] size : SIZES) {
            for (int index = 0; index < ROUTES.size(); index++) {
                Route route = ROUTES.get(index);
                ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                ScreenLayoutTest.check(screen, where);
                ScreenLayoutTest.checkText(fixture, where);
                checkTiles(screen.root(), where);
                UiRect frame = fixture.find("frame").bounds();
                report.append(where).append(": frame ").append(frame.width()).append('x').append(frame.height()).append(", content ")
                        .append(screen.scroll().contentHeight()).append(" of ").append(screen.scroll().bounds().height()).append('\n');
                if (FITS[index] && scrolls(screen.root())) {
                    failures.append(where).append(" scrolls\n");
                }
            }
        }
        System.out.print(report);
        assertEquals("", failures.toString(), report.toString());
    }

    @Test
    void everySkillAndAdaptationIsInReach() {
        ScreenFixture fixture = new ScreenFixture(480, 270, 4, MaxCatalog.catalog(), MaxCatalog.player());
        AdaptScreen skills = fixture.open(new Route.Skills());
        for (int index = 0; index < MaxCatalog.SKILLS; index++) {
            assertInReach(fixture, skills, String.format("skill:skill%02d", index));
        }
        AdaptScreen skill = fixture.open(new Route.Skill(TYPICAL_SKILL));
        for (int index = 0; index < 15; index++) {
            assertInReach(fixture, skill, String.format("adaptation:%s-a%02d", TYPICAL_SKILL, index));
        }
        fixture = new ScreenFixture(960, 540, 2, MaxCatalog.catalog(), MaxCatalog.player());
        AdaptScreen largest = fixture.open(new Route.Skill(MaxCatalog.LARGEST_SKILL));
        for (int index = 0; index < MaxCatalog.ADAPTATIONS; index++) {
            assertInReach(fixture, largest, String.format("adaptation:%s-a%02d", MaxCatalog.LARGEST_SKILL, index));
        }
        AdaptScreen adaptation = fixture.open(new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION));
        for (String id : List.of("levels", "learn", "stat:cost", "pref:grouped-19", "pref:toggle-2", "pref:style:C12", "pref-reset")) {
            assertInReach(fixture, adaptation, id);
        }
        AdaptScreen switches = fixture.open(new Route.Adaptation(MaxCatalog.SWITCHES_ADAPTATION));
        for (String id : List.of("levels", "learn", "pref:toggle-0", "pref:toggle-18", "pref-reset")) {
            assertInReach(fixture, switches, id);
        }
        AdaptScreen choices = fixture.open(new Route.Adaptation(MaxCatalog.CHOICES_ADAPTATION));
        for (String id : List.of("levels", "learn", "pref:toggle-3", "pref:style:C12", "pref:style-3:C12", "pref-reset")) {
            assertInReach(fixture, choices, id);
        }
    }

    private static void assertInReach(ScreenFixture fixture, AdaptScreen screen, String id) {
        UiNode node = fixture.find(id);
        screen.scroll().reveal(node.bounds());
        fixture.render();
        assertTrue(contains(screen.scroll().bounds(), node.bounds()), id + " " + node.bounds() + " outside " + screen.scroll().bounds());
    }

    @Test
    void framesAreCentredAndBookShaped() {
        for (int[] size : SIZES) {
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
            for (int index = 0; index < ROUTES.size(); index++) {
                Route route = ROUTES.get(index);
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                UiRect frame = fixture.find("frame").bounds();
                assertEquals(screen.width() / 2, frame.centerX(), 1.0, where + " " + frame);
                if (size[0] >= 640) {
                    assertTrue(frame.width() * 9 <= frame.height() * 16, where + " " + frame);
                }
                if (FITS[index]) {
                    assertEquals(0, screen.scroll().maxScroll(), where);
                }
            }
        }
    }

    @Test
    void everyBookSizedScreenGivesTheSkillsTheSameColumnsOfFullWidthTiles() {
        for (boolean extreme : new boolean[] {false, true}) {
            int expected = extreme ? 2 : SkillsScreen.MAX_COLUMNS;
            for (int[] size : SIZES) {
                ScreenFixture fixture = extreme ? new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player())
                        : new ScreenFixture(size[0], size[1], size[2], TypicalCatalog.catalog(), TypicalCatalog.player());
                fixture.open(new Route.Skills());
                List<UiNode> tiles = fixture.find("skills").children();
                int row = 0;
                for (UiNode tile : tiles) {
                    row += tile.bounds().y() == tiles.get(0).bounds().y() ? 1 : 0;
                    assertTrue(tile.bounds().width() >= ((RowTile) tile).naturalWidth(DRAW), size[0] + "x" + size[1] + " " + tile.id());
                }
                assertEquals(expected, row, size[0] + "x" + size[1] + (extreme ? " long names" : ""));
            }
        }
    }

    @Test
    void aSkillTooBigForTheScreenScrollsWithEveryTileInReach() {
        ScreenFixture fixture = new ScreenFixture(480, 270, 4, MaxCatalog.catalog(), MaxCatalog.player());
        AdaptScreen screen = fixture.open(new Route.Skill(MaxCatalog.LARGEST_SKILL));
        assertTrue(screen.scroll().maxScroll() > 0);
        screen.scroll().setScroll(screen.scroll().maxScroll());
        fixture.render();
        ScreenLayoutTest.check(screen, "480x270 largest skill scrolled");
        UiRect last = fixture.find(String.format("adaptation:%s-a%02d", MaxCatalog.LARGEST_SKILL, MaxCatalog.ADAPTATIONS - 1)).bounds();
        assertTrue(contains(screen.scroll().bounds(), last), last + " outside " + screen.scroll().bounds());
    }

    @Test
    void belowTheTargetsThePageScrollsInsteadOfOverflowing() {
        for (Route route : List.of(new Route.Skills(), new Route.Skill(MaxCatalog.LARGEST_SKILL), new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION),
                new Route.Adaptation(MaxCatalog.SWITCHES_ADAPTATION), new Route.Adaptation(MaxCatalog.CHOICES_ADAPTATION))) {
            ScreenFixture fixture = new ScreenFixture(320, 180, 4, MaxCatalog.catalog(), MaxCatalog.player());
            AdaptScreen screen = fixture.open(route);
            String where = "320x180 " + AdaptScreen.routeId(route);
            assertTrue(screen.scroll().maxScroll() > 0, where);
            ScreenLayoutTest.check(screen, where);
            screen.scroll().setScroll(screen.scroll().maxScroll());
            fixture.render();
            ScreenLayoutTest.check(screen, where + " scrolled");
        }
    }

    static void checkTiles(UiNode node, String where) {
        if (node instanceof RowTile tile) {
            int natural = tile.naturalWidth(DRAW);
            assertTrue(tile.bounds().width() >= Math.max(TileGrid.MIN_WIDTH, natural), where + ": " + tile.id() + " " + tile.bounds()
                    + " for text " + natural);
            assertEquals(TileGrid.HEIGHT, tile.bounds().height(), where + ": " + tile.id());
        }
        for (UiNode child : node.children()) {
            if (child.visible()) {
                checkTiles(child, where);
            }
        }
    }

    private static boolean scrolls(UiNode node) {
        if (node instanceof ScrollArea area && area.maxScroll() > 0) {
            return true;
        }
        for (UiNode child : node.children()) {
            if (child.visible() && scrolls(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(UiRect outer, UiRect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y() && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }
}
