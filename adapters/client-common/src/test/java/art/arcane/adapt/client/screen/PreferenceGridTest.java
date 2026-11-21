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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preference checklists are a real grid at every target size: equal columns and rows, no cell meets another, every cell
 * stays in the list and holds its name, wrapped when long, and its checkbox, and the main switch has its row to itself.
 */
class PreferenceGridTest {
    private static final int[][] SIZES = {{480, 270, 4}, {640, 360, 3}, {960, 540, 2}};
    private static final List<String> ADAPTATIONS = List.of(MaxCatalog.SWITCHES_ADAPTATION, MaxCatalog.CHOICES_ADAPTATION,
            MaxCatalog.GROUPED_ADAPTATION);

    private static List<UiNode> cells(UiNode list) {
        List<UiNode> cells = new ArrayList<>();
        for (UiNode child : list.children()) {
            if (child.visible()) {
                cells.add(child);
            }
        }
        return cells;
    }

    private static void assertGrid(ScreenFixture fixture, String where) {
        UiNode list = fixture.find("preferences.list");
        assertNotNull(list, where);
        List<UiNode> cells = cells(list);
        assertFalse(cells.isEmpty(), where);
        UiRect area = list.bounds();
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
            assertTrue(box.x() >= area.x() && box.right() <= area.right() && box.y() >= area.y() && box.bottom() <= area.bottom(),
                    what + " leaves " + area);
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
                fixture.open(new Route.Adaptation(adaptation));
                fixture.render();
                assertGrid(fixture, size[0] + "x" + size[1] + " " + adaptation);
            }
        }
    }

    @Test
    void theMainSwitchHasItsRowToItself() {
        for (int[] size : SIZES) {
            ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2]);
            fixture.open(new Route.Adaptation("rift-gate"));
            fixture.render();
            UiNode main = fixture.find("pref:enabled");
            if (main == null) {
                continue;
            }
            UiRect row = main.bounds();
            UiNode list = fixture.find("preferences.list");
            if (list != null) {
                assertTrue(list.bounds().y() > row.bottom(), size[0] + "x" + size[1] + ": the checklist starts under the main switch");
            }
        }
        ScreenFixture skill = new ScreenFixture(640, 360, 3, TypicalCatalog.catalog(), TypicalCatalog.player());
        skill.open(new Route.Skill(TypicalCatalog.SKILL));
        skill.render();
        UiNode main = skill.find("pref:enabled");
        assertNotNull(main);
        UiRect row = main.bounds();
        UiNode reset = skill.find("pref-reset");
        assertNotNull(reset);
        assertTrue(reset.bounds().y() >= row.bottom(), "reset sits under the main switch " + reset.bounds() + " " + row);
        assertEquals(skill.find("preferences").bounds().right(), reset.bounds().right(), "reset sits at the right edge");
    }

    @Test
    void resetDropsUnderTheMainSwitchOnANarrowPage() {
        ScreenFixture skill = new ScreenFixture(640, 360, 3);
        skill.open(new Route.Skill("agility"));
        skill.render();
        UiRect row = skill.find("pref:enabled").bounds();
        UiRect reset = skill.find("pref-reset").bounds();
        assertTrue(reset.y() >= row.bottom(), "reset sits under the main switch " + reset + " " + row);
        assertEquals(skill.find("preferences").bounds().right(), reset.right(), "reset sits at the right edge");
        assertEquals(skill.find("facing").bounds().bottom(), reset.bottom(), "reset sits at the foot of the page");
    }
}
