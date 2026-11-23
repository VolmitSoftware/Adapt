package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.UiBreakpoint;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * At the scaled sizes a typical modpack client runs (1080p at GUI 4 = 480x270, 1080p at GUI 3 or 720p at GUI 2 =
 * 640x360, 1080p at GUI 2 = 960x540, 1080p at GUI 1) and below them, on every leaf, nothing overlaps, leaves the frame
 * or its page or is cut short, and row tiles are as wide as their text. The catalog's extremes ({@link MaxCatalog}: 24
 * skills with long names, the largest skill with 25 character names, adaptations with 114 character stat lines and
 * paragraph descriptions) wrap instead of being cut and turn over to further leaves of the same book every page gets,
 * with everything on some leaf; a typical adaptation fits one leaf.
 */
class CompactFitTest {
    private static final KitDraw DRAW = new KitDraw();
    private static final int[][] SIZES = {{480, 270, 4}, {640, 360, 3}, {960, 540, 2}, {1920, 1080, 1}, {427, 240, 2}, {320, 180, 4}};
    private static final String TYPICAL_SKILL = "skill01";
    private static final String TYPICAL_ADAPTATION = "skill01-a04";
    private static final List<Route> ROUTES = List.of(new Route.Skills(), new Route.Skill(TYPICAL_SKILL),
            new Route.Adaptation(TYPICAL_ADAPTATION), new Route.Skill(MaxCatalog.LARGEST_SKILL),
            new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION), new Route.Adaptation(MaxCatalog.SWITCHES_ADAPTATION),
            new Route.Adaptation(MaxCatalog.CHOICES_ADAPTATION));

    @Test
    void everyLeafFitsWithoutScrollingOrCuttingText() {
        for (int[] size : SIZES) {
            for (Route route : ROUTES) {
                ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                ScreenLayoutTest.checkEveryLeaf(fixture, where);
                for (int leaf = 0; leaf < screen.leaves(); leaf++) {
                    screen.turnTo(leaf);
                    fixture.render();
                    checkTiles(screen.root(), where + " leaf " + leaf);
                }
                if (route.equals(ROUTES.get(2)) && size[0] >= 427) {
                    assertEquals(1, screen.leaves(), where + " needs a second leaf");
                }
            }
        }
    }

    @Test
    void everySkillAndAdaptationIsOnALeaf() {
        ScreenFixture fixture = new ScreenFixture(480, 270, 4, MaxCatalog.catalog(), MaxCatalog.player());
        fixture.open(new Route.Skills());
        for (int index = 0; index < MaxCatalog.SKILLS; index++) {
            assertOnALeaf(fixture, String.format("skill:skill%02d", index));
        }
        fixture.open(new Route.Skill(TYPICAL_SKILL));
        for (int index = 0; index < 15; index++) {
            assertOnALeaf(fixture, String.format("adaptation:%s-a%02d", TYPICAL_SKILL, index));
        }
        fixture = new ScreenFixture(320, 180, 4, MaxCatalog.catalog(), MaxCatalog.player());
        fixture.open(new Route.Skill(MaxCatalog.LARGEST_SKILL));
        for (int index = 0; index < MaxCatalog.ADAPTATIONS; index++) {
            assertOnALeaf(fixture, String.format("adaptation:%s-a%02d", MaxCatalog.LARGEST_SKILL, index));
        }
        fixture.open(new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION));
        for (String id : List.of("levels", "learn", "stat:cost", "pref:grouped-19", "pref:toggle-2", "pref:style:C12", "pref-reset")) {
            assertOnALeaf(fixture, id);
        }
    }

    /** Turns to the leaf holding {@code id} and checks it shows there whole, inside its page. */
    private static void assertOnALeaf(ScreenFixture fixture, String id) {
        AdaptScreen screen = fixture.current();
        UiNode node = fixture.find(id);
        screen.reveal(node);
        fixture.render();
        assertTrue(node.shown(), id + " is on no leaf");
        UiNode page = node;
        while (page != null && !page.id().startsWith("sheet:")) {
            page = page.parent();
        }
        assertTrue(page == null || contains(page.bounds(), node.bounds()), id + " " + node.bounds() + " outside its page");
    }

    @Test
    void framesAreCentredAndBookShaped() {
        for (int[] size : SIZES) {
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
            for (Route route : ROUTES) {
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                UiRect frame = fixture.find("frame").bounds();
                assertEquals(screen.width() / 2, frame.centerX(), 1.0, where + " " + frame);
                if (size[0] >= 640) {
                    assertTrue(frame.width() * 9 <= frame.height() * 16, where + " " + frame);
                }
            }
        }
    }

    @Test
    void everyBookSizedScreenGivesTheSkillsColumnsOfFullWidthTiles() {
        for (boolean extreme : new boolean[] {false, true}) {
            for (int[] size : SIZES) {
                ScreenFixture fixture = extreme ? new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player())
                        : new ScreenFixture(size[0], size[1], size[2], TypicalCatalog.catalog(), TypicalCatalog.player());
                AdaptScreen screen = fixture.open(new Route.Skills());
                List<UiNode> row = fixture.find("skills:0").children();
                for (UiNode tile : row) {
                    assertTrue(tile.bounds().width() >= ((RowTile) tile).naturalWidth(DRAW), size[0] + "x" + size[1] + " " + tile.id());
                }
                int most = screen.breakpoint() == UiBreakpoint.COMPACT ? SkillsScreen.SINGLE_COLUMNS : SkillsScreen.COLUMNS;
                assertTrue(row.size() <= most, size[0] + "x" + size[1] + (extreme ? " long names" : ""));
            }
        }
    }

    static void checkTiles(UiNode node, String where) {
        if (node instanceof SkillTile tile) {
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

    private static boolean contains(UiRect outer, UiRect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y() && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }
}
