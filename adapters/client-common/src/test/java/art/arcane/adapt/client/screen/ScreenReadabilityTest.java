package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tile text stays on its two lines between the icon and the tile's edge, level pips and tracks keep WCAG contrast
 * whatever the skill colour, and messages never cover the page.
 */
class ScreenReadabilityTest {
    private static final int[][] SIZES = {{480, 270, 4}, {640, 360, 3}, {960, 540, 2}, {1920, 1080, 1}, {427, 240, 3}};
    private static final int[] SKILL_COLOURS = {0xFFFF55, 0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0x00AAAA, 0xFFAA00, 0xFF5555,
            0x000000, 0x7F3FB2, 0x2E8B57};

    @Test
    void tileTextStaysInItsLinesClearOfTheIcon() {
        for (int[] size : SIZES) {
            for (Route route : List.of(new Route.Skills(), new Route.Skill("skill01"))) {
                ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
                AdaptScreen screen = fixture.open(route);
                String where = size[0] + "x" + size[1] + " gui " + size[2] + " " + AdaptScreen.routeId(route);
                List<RowTile> tiles = new ArrayList<>();
                for (int leaf = 0; leaf < screen.leaves() && tiles.isEmpty(); leaf++) {
                    screen.turnTo(leaf);
                    fixture.render();
                    collectTiles(screen.root(), tiles);
                }
                assertFalse(tiles.isEmpty(), where);
                fixture.draw.calls().clear();
                fixture.render();
                int checked = 0;
                for (String call : fixture.draw.calls()) {
                    if (!call.startsWith("text ")) {
                        continue;
                    }
                    int space = call.indexOf(' ', 5);
                    String[] at = call.substring(5, space).split(",");
                    int x = Integer.parseInt(at[0]);
                    int y = Integer.parseInt(at[1]);
                    String text = call.substring(space + 1);
                    for (RowTile tile : tiles) {
                        UiRect box = tile.bounds();
                        if (!box.contains(x, y)) {
                            continue;
                        }
                        checked++;
                        if (tile instanceof SkillTile || tile instanceof AdaptationTile) {
                            assertTrue(x >= box.x() && x + fixture.draw.textWidth(text) <= box.right(),
                                    where + " " + tile.id() + " \"" + text + "\" at " + x + "," + y + " leaves its card " + box);
                            continue;
                        }
                        UiRect lines = tile.textBounds();
                        String what = where + " " + tile.id() + " \"" + text + "\" at " + x + "," + y;
                        assertTrue(x >= lines.x() && x + fixture.draw.textWidth(text) <= lines.right(), what + " leaves " + lines);
                        int names = 1;
                        int line = (y - box.y() - tile.nameTop()) / fixture.draw.lineHeight();
                        boolean onName = y >= box.y() + tile.nameTop() && line < names && (y - box.y() - tile.nameTop()) % fixture.draw.lineHeight() == 0;
                        assertTrue(onName || y == box.y() + RowTile.SECOND_LINE, what + " is off its line");
                        assertTrue(y + fixture.draw.lineHeight() <= box.bottom(), what + " runs past the tile " + box);
                        assertFalse(tile.iconBounds().intersects(new UiRect(x, y, fixture.draw.textWidth(text), fixture.draw.lineHeight())),
                                what + " covers the icon");
                    }
                }
                assertTrue(checked >= tiles.size(), where + ": " + checked + " lines in " + tiles.size() + " tiles");
            }
        }
    }

    private static void collectTiles(UiNode node, List<RowTile> tiles) {
        collectTiles(node, tiles, null);
    }

    /** The tiles under {@code node} wholly inside {@code window}, the window of the scrolling grid that holds them. */
    private static void collectTiles(UiNode node, List<RowTile> tiles, UiRect window) {
        if (!node.visible()) {
            return;
        }
        UiRect inside = node instanceof GridScroll ? node.bounds() : window;
        if (node instanceof RowTile tile && (inside == null || tile.bounds().y() >= inside.y() && tile.bounds().bottom() <= inside.bottom())) {
            tiles.add(tile);
        }
        for (UiNode child : node.children()) {
            collectTiles(child, tiles, inside);
        }
    }

    @Test
    void levelPipNumeralsKeepTextContrastForEverySkillColour() {
        UiTheme base = UiTheme.DEFAULT;
        int panel = base.backdrop();
        List<LevelTrack.LevelState> states = List.of(
                new LevelTrack.LevelState(LevelTrack.Pip.LEARNED, false),
                new LevelTrack.LevelState(LevelTrack.Pip.LEARNED, true),
                new LevelTrack.LevelState(LevelTrack.Pip.EMPTY, false),
                new LevelTrack.LevelState(LevelTrack.Pip.EMPTY, true));
        for (int colour : SKILL_COLOURS) {
            UiTheme theme = base.withAccent(Palette.accent(colour));
            for (LevelTrack.LevelState state : states) {
                LevelTrack.Look look = LevelTrack.look(state, theme);
                int fill = UiContrast.over(look.fill(), panel);
                double ratio = UiContrast.ratio(look.text(), fill);
                assertTrue(ratio >= UiContrast.TEXT, Integer.toHexString(colour) + " " + state + ": " + ratio);
            }
        }
    }

    @Test
    void unfilledTracksAndPipsStandOutFromTheTileAndPanel() {
        UiTheme theme = UiTheme.DEFAULT;
        int tile = UiContrast.over(theme.tile(), theme.backdrop());
        int rail = ProgressBar.rail(theme);
        assertTrue(UiContrast.ratio(UiContrast.over(rail, tile), tile) >= UiContrast.GRAPHIC, "rail on a tile");
        assertTrue(UiContrast.ratio(UiContrast.over(rail, theme.backdrop()), theme.backdrop()) >= UiContrast.GRAPHIC, "rail on the panel");
    }

    @Test
    void messagesNeverCoverTheControls() {
        List<Route> routes = List.of(new Route.Skills(), new Route.Skill(MaxCatalog.LARGEST_SKILL),
                new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION), new Route.Adaptation(MaxCatalog.SWITCHES_ADAPTATION),
                new Route.Adaptation(MaxCatalog.CHOICES_ADAPTATION));
        for (int[] size : new int[][] {{640, 360, 3}, {480, 270, 4}, {427, 240, 3}, {320, 180, 4}}) {
            for (Route route : routes) {
                ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
                AdaptScreen screen = fixture.open(route);
                screen.snackbar().show(Snackbar.Tone.SUCCESS, "Learned Growth Aura II with a long name");
                fixture.render();
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                UiRect message = screen.snackbar().bounds();
                assertTrue(message.bottom() <= screen.height() && message.y() >= 0, where + ": message " + message + " leaves the screen");
                List<Visible> controls = new ArrayList<>();
                collectControls(screen.root(), controls, null);
                for (Visible control : controls) {
                    UiRect shown = control.rect();
                    assertFalse(!shown.isEmpty() && shown.intersects(message), where + ": message " + message + " covers " + control.node().id()
                            + " " + shown);
                }
            }
        }
    }

    /** A control and the part of it the player can see. */
    private record Visible(UiNode node, UiRect rect) {
    }

    /** The controls the player can see, each cut to the view of the scrolling list that holds it. */
    private static void collectControls(UiNode node, List<Visible> controls, UiRect view) {
        if (!node.visible() || node instanceof Snackbar) {
            return;
        }
        UiRect bounds = node.bounds();
        if ((node.focusable() || node.hittable()) && !bounds.isEmpty() && (view == null || bounds.intersects(view))) {
            controls.add(new Visible(node, view == null ? bounds : UiRect.of(Math.max(bounds.x(), view.x()), Math.max(bounds.y(), view.y()),
                    Math.min(bounds.right(), view.right()), Math.min(bounds.bottom(), view.bottom()))));
        }
        UiRect inner = node instanceof ScrollArea ? bounds : view;
        for (UiNode child : node.children()) {
            collectControls(child, controls, inner);
        }
    }
}
