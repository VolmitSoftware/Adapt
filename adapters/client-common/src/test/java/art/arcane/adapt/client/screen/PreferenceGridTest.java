package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preference checklists are a real grid at every target size and on every leaf: equal columns and rows, no cell meets
 * another, every cell holds its name, wrapped when long, and its checkbox, and the main switch has its row to itself.
 */
class PreferenceGridTest {
    private static final int[][] SIZES = {{480, 270, 4}, {640, 352, 4}, {960, 540, 2}, {427, 240, 2}};
    private static final List<String> ADAPTATIONS = List.of(MaxCatalog.SWITCHES_ADAPTATION, MaxCatalog.CHOICES_ADAPTATION,
            MaxCatalog.GROUPED_ADAPTATION);

    /** The checklist cells showing on the open leaf. */
    private static List<UiNode> cells(UiNode node, List<UiNode> found) {
        if (!node.visible()) {
            return found;
        }
        if (node.id().startsWith("preferences.list:")) {
            found.addAll(node.children());
            return found;
        }
        for (UiNode child : node.children()) {
            cells(child, found);
        }
        return found;
    }

    private static void assertGrid(ScreenFixture fixture, String where) {
        List<UiNode> cells = cells(fixture.current().root(), new ArrayList<>());
        if (cells.isEmpty()) {
            return;
        }
        UiRect first = cells.get(0).bounds();
        TreeSet<Integer> columns = new TreeSet<>();
        for (UiNode cell : cells) {
            columns.add(cell.bounds().x());
        }
        List<Integer> lefts = new ArrayList<>(columns);
        for (int index = 1; index < lefts.size(); index++) {
            assertEquals(lefts.get(1) - lefts.get(0), lefts.get(index) - lefts.get(index - 1), where + " columns " + lefts);
            assertTrue(lefts.get(index) - lefts.get(index - 1) >= first.width() + PreferencePanel.COLUMN_GAP, where + " columns " + lefts);
        }
        for (int index = 0; index < cells.size(); index++) {
            UiNode cell = cells.get(index);
            UiRect box = cell.bounds();
            String what = where + " " + cell.id() + " " + box;
            assertEquals(first.width(), box.width(), what + " width");
            assertEquals(first.height(), box.height(), what + " height");
            assertTrue(cell.preferredHeight(box.width(), fixture.draw) <= box.height(), what + " is too short for its name and control");
            for (int other = index + 1; other < cells.size(); other++) {
                assertFalse(box.intersects(cells.get(other).bounds()), what + " meets " + cells.get(other).id());
            }
        }
    }

    @Test
    void checklistsAreAlignedGridsAtEveryTargetSize() {
        for (int[] size : SIZES) {
            for (String adaptation : ADAPTATIONS) {
                ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], MaxCatalog.catalog(), MaxCatalog.player());
                AdaptScreen screen = fixture.open(new Route.Adaptation(adaptation));
                for (int leaf = 0; leaf < screen.leaves(); leaf++) {
                    screen.turnTo(leaf);
                    fixture.render();
                    assertGrid(fixture, size[0] + "x" + size[1] + " " + adaptation + " leaf " + leaf);
                }
            }
        }
    }

    @Test
    void theMainSwitchHasItsRowToItselfAndItsCheckboxLinesUp() {
        ScreenFixture fixture = new ScreenFixture(640, 352, 4, TypicalCatalog.catalog(), TypicalCatalog.player());
        fixture.open(new Route.Adaptation(TypicalCatalog.CHECKLIST_ADAPTATION));
        UiRect main = fixture.find("pref:enabled").bounds();
        UiRect first = fixture.find("pref:effect-0").bounds();
        assertTrue(first.y() > main.bottom(), "the checklist starts under the main switch");
        assertEquals(first.x(), main.x(), "the main switch's checkbox leaves the checklist's column");
    }

    @Test
    void resetEndsTheSettingsHeading() {
        for (int[] size : SIZES) {
            for (Route route : List.of(new Route.Adaptation(TypicalCatalog.CHECKLIST_ADAPTATION))) {
                ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], TypicalCatalog.catalog(), TypicalCatalog.player());
                fixture.open(route);
                UiNode reset = fixture.find("pref-reset");
                UiNode heading = fixture.find("heading:settings");
                String where = size[0] + "x" + size[1] + " " + AdaptScreen.routeId(route);
                assertEquals(heading, reset.parent(), where + ": reset leaves the settings heading");
                assertEquals(heading.bounds().right(), reset.bounds().right(), where + ": reset leaves the right edge");
                assertTrue(fixture.find("pref:enabled").bounds().y() > heading.bounds().bottom(), where);
            }
        }
    }
}
