package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.RecipeView;
import art.arcane.adapt.client.view.ToolView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An adaptation page draws the recipes the adaptation unlocks under "Recipes", above its settings pinned to the page foot, at every level. */
class RecipesSectionTest {
    private static final String BREWS = "agility-wall-jump";
    private static final String SWAPS = "agility-super-jump";
    private static final String ONE = "rift-gate";
    private static final String NONE = "stealth-vision";
    private static final int[][] SCREENS = {{960, 540, 2}, {640, 360, 3}, {480, 270, 4}};

    private static RecipeView.Item item(String id) {
        return new RecipeView.Item(IconRef.item(id), "", "", 1, List.of());
    }

    private static RecipeView.Item potion(String name, String effect, int amplifier, int ticks) {
        return new RecipeView.Item(new IconRef("minecraft:potion", 0, null,
                "minecraft:textures/item/potion_overlay.png#ffff00|minecraft:textures/item/potion.png"), name, "", 1,
                List.of(new RecipeView.Effect(effect, amplifier, ticks, false)));
    }

    /** Apothecary's set: five brews and their stronger brews, an elixir, a mix of two and at level III a mix of three. */
    private static List<RecipeView> brews(String page) {
        List<RecipeView> recipes = new ArrayList<>();
        RecipeView.Item base = new RecipeView.Item(IconRef.item("minecraft:potion"), "", "item.minecraft.potion.effect.healing", 1, List.of());
        for (String effect : List.of("absorption", "health_boost", "resistance", "saturation", "haste")) {
            for (int strength = 0; strength < 2; strength++) {
                recipes.add(new RecipeView(page, RecipeView.Kind.BREWING, 1, "minecraft:" + effect, 0,
                        List.of(base, item("minecraft:quartz")), potion("Bottled " + effect, "minecraft:" + effect, strength, 600)));
            }
        }
        recipes.add(new RecipeView(page, RecipeView.Kind.BREWING, 1, "elixir", 0, List.of(base, item("minecraft:golden_carrot")),
                potion("Elixir", "minecraft:absorption", 0, 1200)));
        RecipeView.Item bottle = item("minecraft:potion");
        recipes.add(new RecipeView(page, RecipeView.Kind.MIXING, 1, "mix", 0, List.of(bottle, bottle, item("minecraft:glowstone_dust")),
                potion("Mixed Potion", "minecraft:speed", 0, 0)));
        recipes.add(new RecipeView(page, RecipeView.Kind.MIXING, 3, "mix", 0,
                List.of(bottle, bottle, bottle, item("minecraft:glowstone_dust")), potion("Mixed Potion", "minecraft:speed", 0, 0)));
        return recipes;
    }

    /** A log swapper's set: every log from eight of every other, ten logs, seventy recipes. */
    private static List<RecipeView> swaps(String page) {
        List<String> logs = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped");
        List<RecipeView> recipes = new ArrayList<>();
        for (String from : logs) {
            for (String to : logs.subList(0, 8)) {
                if (recipes.size() < 70 && !from.equals(to)) {
                    List<RecipeView.Item> inputs = new ArrayList<>();
                    for (int index = 0; index < 8; index++) {
                        inputs.add(item("minecraft:" + from + "_log"));
                    }
                    inputs.add(item("minecraft:iron_axe"));
                    recipes.add(new RecipeView(page, RecipeView.Kind.SHAPELESS, 1, "minecraft:" + from + "_log", 0, inputs,
                            new RecipeView.Item(IconRef.item("minecraft:" + to + "_log"), "", "", 8, List.of())));
                }
            }
        }
        return recipes;
    }

    private static RecipeView shaped(String page) {
        RecipeView.Item none = RecipeView.Item.NONE;
        RecipeView.Item wool = item("minecraft:white_wool");
        return new RecipeView(page, RecipeView.Kind.SHAPED, 1, "minecraft:white_wool", 3,
                List.of(wool, wool, wool, wool, item("minecraft:ender_pearl"), wool, wool, wool, none),
                new RecipeView.Item(IconRef.item("minecraft:note_block"), "Elevator Block", "", 1, List.of()));
    }

    private static ScreenFixture fixture(int[] screen) {
        ScreenFixture fixture = new ScreenFixture(screen[0], screen[1], screen[2]);
        List<RecipeView> all = new ArrayList<>(brews(BREWS));
        all.addAll(swaps(SWAPS));
        all.add(shaped(ONE));
        fixture.state.acceptRecipes(all);
        return fixture;
    }

    private static List<UiNode> cells(ScreenFixture fixture) {
        List<UiNode> cells = new ArrayList<>();
        for (UiNode child : fixture.find("recipes").children()) {
            if (child.kind().equals("recipe-cell")) {
                cells.add(child);
            }
        }
        return cells;
    }

    @Test
    void theLargestBrewSetShowsACellPerRecipeAboveTheSettingsOnTheRightPage() {
        for (int[] screen : SCREENS) {
            ScreenFixture fixture = fixture(screen);
            AdaptScreen page = fixture.open(new Route.Adaptation(BREWS));
            String where = screen[0] + "x" + screen[1] + "@" + screen[2];
            assertTrue(fixture.shows("heading:recipes"), where);
            assertEquals("Recipes", fixture.find("heading:recipes").label());
            List<UiNode> cells = cells(fixture);
            assertEquals(13, cells.size(), where + ": a cell per recipe");
            assertEquals(1, page.leaves(), where + ": the recipes and settings fit one spread");
            UiRect frame = fixture.find("recipe-frame:detail").bounds();
            UiRect settings = fixture.find("heading:settings").bounds();
            assertTrue(frame.bottom() <= settings.y(), where + ": the frame " + frame + " runs into the settings " + settings);
            assertTrue(fixture.find("heading:recipes").bounds().x() > fixture.find("summary").bounds().right(), where + ": on the right page");
            ScreenLayoutTest.check(page, where);
        }
    }

    @Test
    void cellsFormAStrictGridOfOneSizeWithEvenGaps() {
        for (int[] screen : SCREENS) {
            ScreenFixture fixture = fixture(screen);
            fixture.open(new Route.Adaptation(BREWS));
            String where = screen[0] + "x" + screen[1] + "@" + screen[2];
            List<UiNode> cells = cells(fixture);
            UiRect shelf = fixture.find("recipes").bounds();
            int columns = RecipesSection.columns(shelf.width());
            int left = shelf.x() + UiLayout.snap((shelf.width() - RecipesSection.gridWidth(columns)) / 2);
            int top = cells.get(0).bounds().y();
            int rowGap = Integer.parseInt(state(fixture.find("recipes"), "rowGap"));
            assertTrue(rowGap >= RecipesSection.MIN_ROW_GAP && rowGap <= RecipesSection.MAX_ROW_GAP, where + ": row gap " + rowGap);
            for (UiNode cell : cells) {
                UiRect box = cell.bounds();
                assertEquals(RecipesSection.CELL, box.width(), where + ": " + cell.id());
                assertEquals(RecipesSection.CELL, box.height(), where + ": " + cell.id());
                assertEquals(0, (box.x() - left) % (RecipesSection.CELL + RecipesSection.COLUMN_GAP), where + ": " + cell.id() + " off the columns");
                assertEquals(0, (box.y() - top) % (RecipesSection.CELL + rowGap), where + ": " + cell.id() + " off the rows");
                assertTrue(box.x() >= left && box.right() <= left + RecipesSection.gridWidth(columns), where + ": " + cell.id());
            }
            int rightMargin = shelf.right() - left - RecipesSection.gridWidth(columns);
            assertTrue(Math.abs(rightMargin - (left - shelf.x())) <= UiLayout.UNIT, where + ": the grid is centred");
            UiRect frame = fixture.find("recipe-frame:detail").bounds();
            int lastRow = cells.get(cells.size() - 1).bounds().bottom();
            assertEquals(RecipesSection.FRAME_GAP, frame.y() - lastRow, where + ": the frame sits a gutter under the grid");
        }
    }

    @Test
    void aGroupNeverSplitsAcrossRowsWhenItFitsOne() {
        List<int[]> spots = RecipesSection.Shelf.spots(7, List.of(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 6, 6));
        int[][] expected = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {5, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1}};
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index][0], spots.get(index)[0], "column of " + index);
            assertEquals(expected[index][1], spots.get(index)[1], "row of " + index);
        }
        List<int[]> wrapping = RecipesSection.Shelf.spots(3, List.of(0, 0, 0, 0, 0));
        assertEquals(1, wrapping.get(4)[1], "a group longer than a row wraps");
    }

    @Test
    void theFrameKeepsItsPlaceAndSizeWhicheverCellIsShown() {
        for (int[] screen : SCREENS) {
            ScreenFixture fixture = fixture(screen);
            AdaptScreen page = fixture.open(new Route.Adaptation(BREWS));
            String where = screen[0] + "x" + screen[1] + "@" + screen[2];
            UiRect frame = fixture.find("recipe-frame:detail").bounds();
            UiRect settings = fixture.find("heading:settings").bounds();
            for (UiNode cell : cells(fixture)) {
                page.render(fixture.draw, cell.bounds().centerX(), cell.bounds().centerY(), 0.0F);
                assertEquals("true", state(cell, "selected"), where + ": the pointed cell shows");
                assertEquals(frame, fixture.find("recipe-frame:detail").bounds(), where + ": " + cell.id() + " moved the frame");
                UiRect diagram = fixture.find("recipe:detail").bounds();
                assertTrue(frame.contains(diagram.x(), diagram.y()) && diagram.right() <= frame.right() && diagram.bottom() <= frame.bottom(),
                        where + ": " + cell.id() + " diagram " + diagram + " leaves its frame " + frame);
                assertEquals(settings, fixture.find("heading:settings").bounds(), where + ": " + cell.id() + " moved the settings");
            }
        }
    }

    @Test
    void settingsKeepToThePageFootUnderEveryRecipeSetWithSettings() {
        for (String adaptation : List.of(BREWS, ONE)) {
            for (int[] screen : SCREENS) {
                ScreenFixture fixture = fixture(screen);
                fixture.open(new Route.Adaptation(adaptation));
                String where = adaptation + " " + screen[0] + "x" + screen[1] + "@" + screen[2];
                UiNode sheet = sheet(fixture.find("heading:settings"));
                int lowest = Integer.MIN_VALUE;
                for (UiNode block : sheet.children()) {
                    if (block.visible()) {
                        lowest = Math.max(lowest, block.bounds().bottom());
                    }
                }
                assertEquals(sheet.bounds().bottom(), lowest, where + ": the settings end on the page foot");
                UiRect shelf = fixture.find("recipes").bounds();
                assertTrue(shelf.bottom() <= fixture.find("heading:settings").bounds().y(), where + ": the recipes run into the settings");
            }
        }
    }

    private static UiNode sheet(UiNode node) {
        UiNode current = node;
        while (current != null && !current.id().startsWith("sheet:")) {
            current = current.parent();
        }
        return current;
    }

    @Test
    void aLockedCellCarriesItsLevelInABadgeInsideIt() {
        ScreenFixture fixture = fixture(SCREENS[1]);
        AdaptationScreen page = fixture.open(new Route.Adaptation(BREWS));
        page.pick(1);
        fixture.render();
        RecipesSection.Shelf shelf = (RecipesSection.Shelf) fixture.find("recipes");
        int locked = 0;
        for (RecipesSection.Shelf.Cell cell : shelf.cells()) {
            if (cell.locked()) {
                locked++;
                UiRect badge = cell.badge(fixture.draw);
                UiRect box = cell.bounds();
                assertTrue(badge.x() > box.x() && badge.y() > box.y() && badge.right() < box.right() && badge.bottom() < box.bottom(),
                        cell.id() + " badge " + badge + " is not inside " + box);
            }
        }
        assertEquals(1, locked, "the level III mix");
    }

    @Test
    void mixBottlesTakeAnyPotion() {
        ScreenFixture fixture = fixture(SCREENS[0]);
        fixture.open(new Route.Adaptation(BREWS));
        fixture.click(cells(fixture).get(11));
        assertEquals("mixing", state(fixture.find("recipe:detail"), "recipe"));
        UiNode bottle = fixture.find("recipe:detail:in:0");
        assertEquals("true", state(bottle, "any"));
        assertEquals("Any potion", bottle.tooltip().title());
        assertEquals("false", state(fixture.find("recipe:detail:in:2"), "any"), "the ingredient is itself");
    }

    @Test
    void lockedRecipesFollowTheShownLevel() {
        ScreenFixture fixture = fixture(SCREENS[0]);
        AdaptationScreen page = fixture.open(new Route.Adaptation(BREWS));
        assertEquals(3, page.shownLevel(), "level II held, so the next level shows");
        List<UiNode> cells = cells(fixture);
        UiNode three = cells.get(cells.size() - 1);
        assertEquals("false", state(three, "locked"));
        assertEquals("true", state(three, "selected"), "the detail shows what the shown level unlocks");
        assertEquals("mixing", state(fixture.find("recipe:detail"), "recipe"));
        page.pick(1);
        fixture.render();
        assertEquals(1, page.shownLevel());
        assertEquals("true", state(three, "locked"));
        assertEquals(List.of("§9Speed", "§6Level III"), three.tooltip().lines());
        assertEquals("false", state(cells.get(0), "locked"));
        assertEquals("true", state(cells.get(0), "selected"));
    }

    @Test
    void theDetailDrawsTheCellUnderThePointerAndKeepsAClickedOne() {
        ScreenFixture fixture = fixture(SCREENS[0]);
        fixture.open(new Route.Adaptation(BREWS));
        List<UiNode> cells = cells(fixture);
        UiNode elixir = cells.get(10);
        fixture.click(elixir);
        assertEquals("true", state(elixir, "selected"));
        UiNode detail = fixture.find("recipe:detail");
        assertEquals("brewing", state(detail, "recipe"));
        assertEquals("Elixir", detail.label());
        assertEquals(3, detail.children().size(), "a base bottle, an ingredient and the result");
        UiNode result = fixture.find("recipe:detail:out");
        assertEquals("Elixir", result.tooltip().title());
        assertEquals(List.of("§9Absorption (1:00)"), result.tooltip().lines());
    }

    @Test
    void seventyRecipesFoldIntoACellPerGroupThatStillFitsTheSpread() {
        for (int[] screen : SCREENS) {
            ScreenFixture fixture = fixture(screen);
            AdaptScreen page = fixture.open(new Route.Adaptation(SWAPS));
            String where = screen[0] + "x" + screen[1] + "@" + screen[2];
            assertEquals("true", state(fixture.find("recipes"), "folded"), where);
            assertEquals(10, cells(fixture).size(), where + ": a cell per input log");
            assertEquals(1, page.leaves(), where);
            Map<Integer, Integer> rows = new HashMap<>();
            for (UiNode cell : cells(fixture)) {
                assertEquals(RecipesSection.CELL, cell.bounds().width(), where);
                rows.merge(cell.bounds().y(), 1, Integer::sum);
            }
            assertEquals(1, new HashSet<>(rows.values()).size(), where + ": rows of " + rows.values() + " cells are not even");
            assertEquals(3, RecipeDiagram.grid(((RecipesSection.Shelf) fixture.find("recipes")).detail().recipe()));
            ScreenLayoutTest.check(page, where);
        }
    }

    @Test
    void oneRecipeShowsWholeAsACraftingGrid() {
        ScreenFixture fixture = fixture(SCREENS[1]);
        AdaptScreen page = fixture.open(new Route.Adaptation(ONE));
        assertTrue(cells(fixture).isEmpty());
        UiNode diagram = fixture.find("recipe:0");
        assertNotNull(diagram);
        assertEquals("shaped", state(diagram, "recipe"));
        assertEquals(10, diagram.children().size(), "nine cells and the result");
        assertEquals(RecipeDiagram.SLOT * 3 + RecipeDiagram.SLOT_GAP * 2, diagram.bounds().height());
        assertEquals(RecipeDiagram.SLOT, fixture.find("recipe:0:in:0").bounds().width());
        assertEquals("Elevator Block", fixture.find("recipe:0:out").tooltip().title());
        assertFalse(fixture.find("recipe:0:in:8").hittable(), "an empty cell has nothing to name");
        UiRect frame = fixture.find("recipe-frame:0").bounds();
        UiRect shelf = fixture.find("recipes").bounds();
        assertTrue(Math.abs(frame.centerX() - shelf.centerX()) <= 1, "the frame is centred on the page");
        assertTrue(Math.abs(diagram.bounds().centerX() - frame.centerX()) <= 1, "the diagram is centred in its frame");
        assertEquals("Elevator Block", fixture.find("recipe-frame:0").label());
        assertTrue(frame.bottom() <= fixture.find("heading:settings").bounds().y());
        ScreenLayoutTest.check(page, "one recipe");
    }

    @Test
    void anAdaptationWithoutRecipesHasNoSection() {
        ScreenFixture fixture = fixture(SCREENS[0]);
        fixture.open(new Route.Adaptation(NONE));
        UiNode heading = fixture.find("heading:recipes");
        assertTrue(heading == null || heading.parent() == null);
    }

    @Test
    void recipesTakeOverFromTheToolsList() {
        ScreenFixture fixture = fixture(SCREENS[0]);
        fixture.state.acceptTools(List.of(new ToolView(ONE, "tool-rift-anchor", "Rift Anchor", IconRef.item("minecraft:lodestone"), 1, List.of()),
                new ToolView(NONE, "tool-stealth-pellet", "Smoke Pellet", IconRef.item("minecraft:firework_star"), 1, List.of())));
        fixture.open(new Route.Adaptation(ONE));
        assertTrue(fixture.find("tool:0") == null || fixture.find("tool:0").parent() == null);
        fixture.open(new Route.Adaptation(NONE));
        assertTrue(fixture.shows("tool:0"), "a page without recipes keeps its tools");
    }

    @Test
    void effectsReadAsVanillaWritesThem() {
        assertEquals("Haste II (0:30)", RecipeDiagram.effect(new RecipeView.Effect("minecraft:haste", 1, 600, false)));
        assertEquals("Instant health", RecipeDiagram.effect(new RecipeView.Effect("minecraft:instant_health", 0, 0, false)));
        assertEquals("Potion", RecipeDiagram.title(new RecipeView.Item(IconRef.item("minecraft:potion"), "", "", 1, List.of())));
    }

    private static String state(UiNode node, String key) {
        Map<String, String> state = new HashMap<>();
        node.describe(state);
        return state.get(key);
    }

    @Test
    void aClickedCellOpensNothingElse() {
        ScreenFixture fixture = fixture(SCREENS[0]);
        fixture.open(new Route.Adaptation(BREWS));
        fixture.click(cells(fixture).get(0));
        Route.Adaptation route = assertInstanceOf(Route.Adaptation.class, fixture.current().route());
        assertEquals(BREWS, route.adaptationId());
    }
}
