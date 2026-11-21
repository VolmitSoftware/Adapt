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
 * are as wide as their text. The skills list, a typical skill and a typical adaptation never scroll; the catalog's
 * extremes ({@link MaxCatalog}: the largest skill with 25 character names, adaptations with 114 character stat lines and
 * paragraph descriptions) wrap instead of being cut and fit from the sizes in {@link #FITS} up.
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
    /** The smallest scaled width each route fits without scrolling, in {@link #ROUTES} order. */
    private static final int[] FITS = {480, 480, 480, 640, 960, 960, 960};

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
                if (size[0] >= FITS[index] && scrolls(screen.root())) {
                    failures.append(where).append(" scrolls\n");
                }
            }
        }
        System.out.print(report);
        assertEquals("", failures.toString(), report.toString());
    }

    @Test
    void everySkillAndAdaptationIsOnScreen() {
        ScreenFixture fixture = new ScreenFixture(480, 270, 4, MaxCatalog.catalog(), MaxCatalog.player());
        AdaptScreen skills = fixture.open(new Route.Skills());
        UiRect view = skills.scroll().bounds();
        for (int index = 0; index < MaxCatalog.SKILLS; index++) {
            UiRect tile = fixture.find(String.format("skill:skill%02d", index)).bounds();
            assertTrue(contains(view, tile), tile + " outside " + view);
        }
        AdaptScreen skill = fixture.open(new Route.Skill(TYPICAL_SKILL));
        view = skill.scroll().bounds();
        for (int index = 0; index < 15; index++) {
            UiRect tile = fixture.find(String.format("adaptation:%s-a%02d", TYPICAL_SKILL, index)).bounds();
            assertTrue(contains(view, tile), tile + " outside " + view);
        }
        fixture = new ScreenFixture(960, 540, 2, MaxCatalog.catalog(), MaxCatalog.player());
        AdaptScreen largest = fixture.open(new Route.Skill(MaxCatalog.LARGEST_SKILL));
        view = largest.scroll().bounds();
        for (int index = 0; index < MaxCatalog.ADAPTATIONS; index++) {
            UiRect tile = fixture.find(String.format("adaptation:%s-a%02d", MaxCatalog.LARGEST_SKILL, index)).bounds();
            assertTrue(contains(view, tile), tile + " outside " + view);
        }
        AdaptScreen adaptation = fixture.open(new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION));
        view = adaptation.scroll().bounds();
        for (String id : List.of("levels", "learn", "stat:cost", "pref:grouped-19", "pref:toggle-2", "pref:style:C12", "pref-reset")) {
            assertTrue(contains(view, fixture.find(id).bounds()), id + " " + fixture.find(id).bounds() + " outside " + view);
        }
        AdaptScreen switches = fixture.open(new Route.Adaptation(MaxCatalog.SWITCHES_ADAPTATION));
        view = switches.scroll().bounds();
        for (String id : List.of("levels", "learn", "pref:toggle-0", "pref:toggle-18", "pref-reset")) {
            assertTrue(contains(view, fixture.find(id).bounds()), id + " " + fixture.find(id).bounds() + " outside " + view);
        }
        AdaptScreen choices = fixture.open(new Route.Adaptation(MaxCatalog.CHOICES_ADAPTATION));
        view = choices.scroll().bounds();
        for (String id : List.of("levels", "learn", "pref:toggle-3", "pref:style:C12", "pref:style-3:C12", "pref-reset")) {
            assertTrue(contains(view, fixture.find(id).bounds()), id + " " + fixture.find(id).bounds() + " outside " + view);
        }
    }

    @Test
    void framesAreCentredAndHugTheirPage() {
        for (int[] size : SIZES) {
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
            for (int index = 0; index < ROUTES.size(); index++) {
                Route route = ROUTES.get(index);
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                UiRect frame = fixture.find("frame").bounds();
                assertEquals(screen.width() / 2, frame.centerX(), 1.0, where + " " + frame);
                if (size[0] >= FITS[index]) {
                    assertEquals(screen.scroll().bounds().height(), screen.scroll().contentHeight(), where);
                }
            }
        }
    }

    @Test
    void wideScreensUseMoreColumnsOfFullWidthTiles() {
        int[] columns = new int[SIZES.length];
        for (int index = 0; index < SIZES.length; index++) {
            int[] size = SIZES[index];
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
            fixture.open(new Route.Skills());
            UiRect first = fixture.find("skill:skill00").bounds();
            int row = 0;
            for (int skill = 0; skill < MaxCatalog.SKILLS; skill++) {
                if (fixture.find(String.format("skill:skill%02d", skill)).bounds().y() == first.y()) {
                    row++;
                }
            }
            columns[index] = row;
            assertTrue(index == 0 || row >= columns[index - 1], size[0] + "x" + size[1] + ": " + row + " columns");
        }
        assertTrue(columns[0] >= 3, columns[0] + " columns at 480x270");
        assertEquals(SkillsScreen.MAX_COLUMNS, columns[SIZES.length - 1]);
        ScreenFixture wide = new ScreenFixture(1920, 1080, 1, MaxCatalog.catalog(), MaxCatalog.player());
        wide.open(new Route.Skills());
        UiRect tile = wide.find("skill:skill00").bounds();
        assertEquals(Math.max(TileGrid.WIDTH, ((RowTile) wide.find("skill:skill00")).naturalWidth(DRAW)), tile.width());
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

    private static void checkTiles(UiNode node, String where) {
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
