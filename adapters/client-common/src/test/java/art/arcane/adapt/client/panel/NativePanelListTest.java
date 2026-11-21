package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelPaging;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileAction;
import art.arcane.adapt.client.protocol.TileHint;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.qa.UiProbe;
import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.FakeHost;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.KitScreen;
import art.arcane.adapt.client.ui.kit.ManualClock;
import art.arcane.adapt.client.ui.kit.NumberStepper;
import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativePanelListTest {
    private static final UiModifiers NONE = UiModifiers.NONE;
    private static final UiModifiers SHIFT = new UiModifiers(true, false, false, false);

    private final ManualClock clock = new ManualClock();
    private final RecordingActions actions = new RecordingActions();
    private final KitDraw draw = new KitDraw();
    private FakeHost host = new FakeHost(640, 360);

    private static PanelTile entry(int slot, String name, String section, TileAction... clicks) {
        return new PanelTile(slot, IconRef.item("minecraft:book"), 1, name, List.of("§7About " + name, "§8path." + slot), false, null,
                TileRole.ENTRY, section, "", List.of("§7About " + name), List.of(clicks));
    }

    private static PanelTile setting(int slot, String name, TileHint hint, TileAction... clicks) {
        return new PanelTile(slot, IconRef.item("minecraft:clock"), 1, name, List.of("§7Lore"), false, hint, TileRole.SETTING, "Settings",
                "", List.of("§7Changes " + name), List.of(clicks));
    }

    private static PanelTile tile(int slot, String name, TileRole role, String value, TileHint hint) {
        return new PanelTile(slot, IconRef.item("minecraft:paper"), 1, name, List.of("§7" + name), false, hint, role, "", value, null,
                List.of());
    }

    private static PanelTile pane(int slot) {
        return new PanelTile(slot, IconRef.item("minecraft:black_stained_glass_pane"), 1, " ", List.of(), false, null,
                TileRole.DECORATION, "", "", null, List.of());
    }

    private static PanelTile nav(int slot, String target) {
        return new PanelTile(slot, IconRef.item("minecraft:arrow"), 1, "§7" + target, List.of(), false, TileHint.of(HintKind.NAVIGATION, target),
                TileRole.NAVIGATION, "", "", null, List.of(new TileAction(ClickKind.LEFT, "")));
    }

    private static PanelView view(PanelLayout layout, PanelPaging paging, int revision, PanelTile... tiles) {
        return new PanelView(9, revision, "§7Configure: core", 6, layout, paging, List.of("Configure Adapt", "Core"), List.of(tiles));
    }

    private NativePanelScreen open(PanelView view) {
        NativePanelScreen screen = new NativePanelScreen(view, actions, UiTheme.DEFAULT, clock.motion());
        host.open(screen);
        clock.advance(KitScreen.OPEN_MILLIS);
        screen.render(draw, 0, 0, 0.0F);
        return screen;
    }

    private void click(NativePanelScreen screen, UiNode node, UiMouseButton button, UiModifiers modifiers) {
        click(screen, node.bounds().centerX(), node.bounds().centerY(), button, modifiers);
    }

    private void click(NativePanelScreen screen, int x, int y, UiMouseButton button, UiModifiers modifiers) {
        UiMouseEvent event = new UiMouseEvent(x, y, button, modifiers);
        screen.mouseClicked(event);
        screen.mouseReleased(event);
        screen.render(draw, x, y, 0.0F);
    }

    private static void type(NativePanelScreen screen, String text) {
        text.codePoints().forEach((int c) -> screen.charTyped(new UiCharEvent(c, NONE)));
    }

    private static PanelRow row(NativePanelScreen screen, int slot) {
        return (PanelRow) screen.row(slot);
    }

    @Test
    void rowsFollowReadingOrderUnderTheirSectionsAndChromeLeavesTheBody() {
        NativePanelScreen screen = open(view(PanelLayout.LIST, null, 1, pane(0), entry(12, "B", "Second"), entry(10, "A", ""),
                entry(11, "C", "First"), entry(13, "D", "First"), nav(45, "back"), nav(53, "next")));
        List<String> order = new ArrayList<>();
        for (UiNode node : screen.root().find("list").children()) {
            order.add(node.id());
        }
        assertEquals(List.of("row:10", "section:First", "row:11", "row:13", "section:Second", "row:12"), order);
        assertNull(screen.row(0));
        assertNull(screen.row(45));
        assertNull(screen.tile(10));
        assertNotNull(screen.navigationButton("back"));
        assertNotNull(screen.navigationButton("next"));
        click(screen, screen.navigationButton("back"), UiMouseButton.LEFT, NONE);
        click(screen, screen.navigationButton("next"), UiMouseButton.LEFT, NONE);
        assertEquals(List.of("click 9 1 45 LEFT", "click 9 1 53 LEFT"), actions.sent);
    }

    @Test
    void mouseButtonsOnARowSendTheChestClicks() {
        NativePanelScreen screen = open(view(PanelLayout.LIST, null, 3, entry(10, "A", "")));
        UiNode row = screen.row(10);
        click(screen, row, UiMouseButton.LEFT, NONE);
        clock.advance(1000);
        click(screen, row, UiMouseButton.RIGHT, NONE);
        click(screen, row, UiMouseButton.LEFT, SHIFT);
        click(screen, row, UiMouseButton.RIGHT, SHIFT);
        click(screen, row, UiMouseButton.MIDDLE, NONE);
        screen.keyPressed(new UiKeyEvent(UiKey.DIGIT_2, NONE));
        assertEquals(List.of("click 9 3 10 LEFT", "click 9 3 10 RIGHT", "click 9 3 10 SHIFT_LEFT", "click 9 3 10 SHIFT_RIGHT",
                "click 9 3 10 MIDDLE", "click 9 3 10 NUMBER_KEY"), actions.sent);
    }

    @Test
    void switchShowsTheServerValueAndSendsALeftClick() {
        NativePanelScreen screen = open(view(PanelLayout.FORM, null, 1, setting(10, "Sounds", TileHint.of(HintKind.TOGGLE, "ON"))));
        PanelControls.Switch control = assertInstanceOf(PanelControls.Switch.class, screen.control(10));
        assertEquals("setting:10", control.id());
        assertTrue(control.on());
        click(screen, control, UiMouseButton.LEFT, NONE);
        assertTrue(control.on());
        screen.apply(view(PanelLayout.FORM, null, 2, setting(10, "Sounds", TileHint.of(HintKind.TOGGLE, "OFF"))));
        assertFalse(control.on());
        assertEquals(List.of("click 9 1 10 LEFT"), actions.sent);
    }

    @Test
    void typedNumbersStepWithinBoundsAndValidateTypedText() {
        TileHint hint = new TileHint(HintKind.NUMBER, "9", true, List.of(), 0D, 10D, 1D);
        NativePanelScreen screen = open(view(PanelLayout.FORM, null, 1, setting(10, "Range", hint)));
        NumberStepper stepper = assertInstanceOf(NumberStepper.class, screen.control(10));
        click(screen, stepper.plus(), UiMouseButton.LEFT, NONE);
        click(screen, stepper.minus(), UiMouseButton.LEFT, NONE);
        host.setModifiers(SHIFT);
        click(screen, stepper.minus(), UiMouseButton.LEFT, SHIFT);
        host.setModifiers(NONE);
        click(screen, screen.row(10), UiMouseButton.LEFT, NONE);
        assertTrue(stepper.field().isFocused());
        stepper.field().selectAll();
        type(screen, "abc");
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, NONE));
        stepper.field().selectAll();
        type(screen, "42");
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, NONE));
        stepper.field().selectAll();
        type(screen, "4.5");
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, NONE));
        stepper.field().selectAll();
        type(screen, "3");
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, NONE));
        assertEquals(List.of("input 9 1 10 10", "input 9 1 10 8", "input 9 1 10 0", "input 9 1 10 10", "input 9 1 10 3"), actions.sent);
    }

    @Test
    void decimalStepsKeepTheValuesPrecision() {
        TileHint hint = new TileHint(HintKind.NUMBER, "0.25", true, List.of(), null, null, 0.1D);
        NativePanelScreen screen = open(view(PanelLayout.FORM, null, 1, setting(10, "Chance", hint)));
        NumberStepper stepper = (NumberStepper) screen.control(10);
        click(screen, stepper.plus(), UiMouseButton.LEFT, NONE);
        assertEquals(List.of("input 9 1 10 0.35"), actions.sent);
    }

    @Test
    void untypedNumbersStepWithChestClicks() {
        NativePanelScreen screen = open(view(PanelLayout.FORM, null, 1, setting(10, "Priority", TileHint.of(HintKind.NUMBER, "3"))));
        NumberStepper stepper = (NumberStepper) screen.control(10);
        click(screen, stepper.plus(), UiMouseButton.LEFT, NONE);
        click(screen, stepper.minus(), UiMouseButton.LEFT, NONE);
        assertEquals(List.of("click 9 1 10 LEFT", "click 9 1 10 RIGHT"), actions.sent);
    }

    @Test
    void textFieldsSendTheTypedValue() {
        TileHint hint = new TileHint(HintKind.TEXT, "Steve", true, List.of(), null, null, null);
        NativePanelScreen screen = open(view(PanelLayout.FORM, null, 1, setting(10, "Name", hint)));
        TextField field = assertInstanceOf(TextField.class, screen.control(10));
        assertEquals("Steve", field.text());
        click(screen, screen.row(10), UiMouseButton.LEFT, NONE);
        assertTrue(field.isFocused());
        field.selectAll();
        type(screen, "Alex");
        screen.keyPressed(new UiKeyEvent(UiKey.Q, NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, NONE));
        assertEquals(List.of("input 9 1 10 Alex"), actions.sent);
    }

    @Test
    void typedChoicesStepAndPickFromTheList() {
        TileHint hint = new TileHint(HintKind.CHOICE, "FAST", true, List.of("SLOW", "FAST", "TURBO"), null, null, null);
        NativePanelScreen screen = open(view(PanelLayout.FORM, null, 1, setting(10, "Mode", hint)));
        PanelControls.Choice choice = assertInstanceOf(PanelControls.Choice.class, screen.control(10));
        UiRect box = choice.bounds();
        click(screen, box.right() - 2, box.centerY(), UiMouseButton.LEFT, NONE);
        click(screen, box.x() + 2, box.centerY(), UiMouseButton.LEFT, NONE);
        click(screen, box.centerX(), box.centerY(), UiMouseButton.LEFT, NONE);
        assertNotNull(screen.editor());
        UiNode option = screen.editor().find("option:10:2");
        assertNotNull(option);
        click(screen, option, UiMouseButton.LEFT, NONE);
        assertNull(screen.editor());
        assertEquals(List.of("input 9 1 10 TURBO", "input 9 1 10 SLOW", "input 9 1 10 TURBO"), actions.sent);
    }

    @Test
    void untypedChoicesCycleWithLeftAndRightClicks() {
        TileHint hint = TileHint.of(HintKind.CHOICE, "WHITELIST");
        NativePanelScreen screen = open(view(PanelLayout.FORM, null, 1,
                setting(10, "Filter", hint, new TileAction(ClickKind.LEFT, ""), new TileAction(ClickKind.RIGHT, "")),
                setting(11, "Mode", hint, new TileAction(ClickKind.LEFT, ""))));
        UiRect first = screen.control(10).bounds();
        UiRect second = screen.control(11).bounds();
        click(screen, first.right() - 2, first.centerY(), UiMouseButton.LEFT, NONE);
        click(screen, first.x() + 2, first.centerY(), UiMouseButton.LEFT, NONE);
        click(screen, second.x() + 2, second.centerY(), UiMouseButton.LEFT, NONE);
        assertEquals(List.of("click 9 1 10 LEFT", "click 9 1 10 RIGHT", "click 9 1 11 LEFT"), actions.sent);
    }

    @Test
    void alternateClicksAreListedInTheTooltipAndReachableFromTheRowMenu() {
        NativePanelScreen screen = open(view(PanelLayout.LIST, null, 1, entry(10, "Golem", "",
                new TileAction(ClickKind.LEFT, "Locate"), new TileAction(ClickKind.RIGHT, "Recall"), new TileAction(ClickKind.SHIFT_RIGHT, ""))));
        PanelRow row = row(screen, 10);
        assertTrue(row.find("label:10").tooltip().lines().stream().anyMatch((String line) -> line.contains("Recall")));
        assertTrue(row.find("label:10").tooltip().lines().stream().anyMatch((String line) -> line.contains("Shift + right click")));
        Button more = row.moreButton();
        assertEquals("more:10", more.id());
        click(screen, more, UiMouseButton.LEFT, NONE);
        assertNotNull(screen.editor());
        assertNull(screen.editor().find("action:10:LEFT"));
        click(screen, screen.editor().find("action:10:SHIFT_RIGHT"), UiMouseButton.LEFT, NONE);
        assertEquals(List.of("click 9 1 10 SHIFT_RIGHT"), actions.sent);
        NativePanelScreen unnamed = open(view(PanelLayout.LIST, null, 1, entry(11, "Plain", "", new TileAction(ClickKind.RIGHT, ""))));
        assertNull(row(unnamed, 11).moreButton());
        assertTrue(row(unnamed, 11).find("label:11").tooltip().lines().stream().anyMatch((String line) -> line.contains("Right click")));
    }

    @Test
    void longListsGetASearchThatFiltersRows() {
        List<PanelTile> tiles = new ArrayList<>();
        for (int slot = 0; slot < 12; slot++) {
            tiles.add(entry(slot, "Entry " + (slot == 5 ? "Speed" : Integer.toString(slot)), ""));
        }
        NativePanelScreen screen = open(view(PanelLayout.LIST, null, 1, tiles.toArray(new PanelTile[0])));
        TextField search = screen.searchField();
        assertNotNull(search);
        assertEquals("search", search.id());
        click(screen, search, UiMouseButton.LEFT, NONE);
        type(screen, "speed");
        screen.render(draw, 0, 0, 0.0F);
        PanelList list = (PanelList) screen.root().find("list");
        assertEquals(1, list.visibleRows());
        assertNotNull(screen.row(5));
        assertTrue(screen.row(5).shown());
        search.selectAll();
        type(screen, "zzz");
        screen.render(draw, 0, 0, 0.0F);
        assertEquals(0, list.visibleRows());
        assertTrue(screen.probe().contains("Nothing matches"), screen.probe());
        assertTrue(actions.sent.isEmpty());
        NativePanelScreen shortList = open(view(PanelLayout.LIST, null, 1, entry(1, "A", "")));
        assertNull(shortList.searchField());
    }

    @Test
    void pagingShowsPageControlsAndTheCurrentPage() {
        NativePanelScreen screen = open(view(PanelLayout.LIST, new PanelPaging(1, 3, 120), 1, entry(10, "A", ""), nav(45, "previous"),
                nav(53, "next"), nav(48, "first"), nav(50, "last"), nav(49, "page-info")));
        assertTrue(screen.navigationButton("first").enabled());
        click(screen, screen.navigationButton("next"), UiMouseButton.LEFT, NONE);
        click(screen, screen.navigationButton("previous"), UiMouseButton.LEFT, NONE);
        assertEquals("Page 2 of 3", screen.root().find("page.label").label());
        NativePanelScreen first = open(view(PanelLayout.LIST, new PanelPaging(0, 3, 120), 1, entry(10, "A", ""), nav(53, "next"),
                nav(50, "last")));
        assertFalse(first.navigationButton("previous").enabled());
        click(first, first.navigationButton("previous"), UiMouseButton.LEFT, NONE);
        assertEquals(List.of("click 9 1 53 LEFT", "click 9 1 45 LEFT", "closed 9"), actions.sent);
    }

    @Test
    void plainInfoTilesBecomeFooterChipsAndFactsStayRows() {
        NativePanelScreen screen = open(view(PanelLayout.LIST, null, 1, entry(10, "A", ""), tile(48, "Overview", TileRole.INFO, "", null),
                tile(20, "Health", TileRole.INFO, "20/20", null), tile(0, "No entries", TileRole.HEADER, "", null)));
        UiRect chip = screen.root().find("info:48").bounds();
        assertTrue(chip.bottom() <= screen.root().find("body.scroll").bounds().y(), "a lone chip sits in the title bar: " + chip);
        assertTrue(chip.right() <= screen.root().find("body.scroll").bounds().right(), chip.toString());
        assertNull(screen.root().find("close"), "Escape closes the panel, so it has no close button");
        NativePanelScreen paged = open(view(PanelLayout.LIST, new PanelPaging(0, 2, 40), 1, entry(10, "A", ""),
                tile(48, "Overview", TileRole.INFO, "", null), nav(53, "next")));
        assertTrue(paged.root().find("info:48").bounds().y() > paged.root().find("body.scroll").bounds().bottom(),
                "chips stay in the footer beside the pager");
        assertNull(screen.row(48));
        assertNotNull(screen.row(20));
        assertNotNull(screen.root().find("heading:0"));
        NativePanelScreen empty = open(view(PanelLayout.LIST, null, 1, tile(4, "You have no golems", TileRole.INFO, "", null)));
        assertNotNull(empty.row(4));
        assertNull(empty.root().find("info:4"));
    }

    @Test
    void backspaceGoesBackAndTheTitleCarriesTheBreadcrumb() {
        NativePanelScreen screen = open(view(PanelLayout.LIST, null, 1, entry(10, "A", ""), nav(45, "back")));
        screen.keyPressed(new UiKeyEvent(UiKey.BACKSPACE, NONE));
        assertEquals(List.of("click 9 1 45 LEFT"), actions.sent);
        UiProbe.snapshot();
        clock.advance(200);
        screen.render(draw, 0, 0, 0.0F);
        String json = UiProbe.snapshot();
        assertTrue(json.contains("\"layout\":\"LIST\""), json);
        assertTrue(json.contains("\"breadcrumb\":\"Configure Adapt / Core\""), json);
        assertTrue(json.contains("\"id\":\"row:10\""), json);
        host.close();
    }

    @Test
    void formFitsEveryGuiScale() {
        int[][] sizes = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {427, 240}, {320, 240}, {300, 160}};
        for (int[] size : sizes) {
            host = new FakeHost(size[0], size[1]);
            List<PanelTile> tiles = new ArrayList<>();
            tiles.add(setting(1, "A toggle with a fairly long name that has to be cut", TileHint.of(HintKind.TOGGLE, "ON")));
            tiles.add(setting(2, "Number", new TileHint(HintKind.NUMBER, "12345", true, List.of(), null, null, 1D)));
            tiles.add(setting(3, "Text", new TileHint(HintKind.TEXT, "some text", true, List.of(), null, null, null)));
            tiles.add(setting(4, "Choice", new TileHint(HintKind.CHOICE, "MEDIUM", true, List.of("LOW", "MEDIUM", "HIGH"), null, null, null)));
            for (int slot = 9; slot < 45; slot++) {
                tiles.add(entry(slot, "Entry " + slot, slot % 2 == 0 ? "Even" : "Odd", new TileAction(ClickKind.RIGHT, "Inspect")));
            }
            tiles.add(nav(45, "back"));
            tiles.add(nav(53, "next"));
            NativePanelScreen screen = open(view(PanelLayout.FORM, new PanelPaging(0, 2, 80), 1, tiles.toArray(new PanelTile[0])));
            String where = size[0] + "x" + size[1];
            UiRect panel = screen.root().find("panel").bounds();
            assertTrue(panel.x() >= 0 && panel.y() >= 0 && panel.right() <= size[0] && panel.bottom() <= size[1], where + " " + panel);
            UiRect body = screen.root().find("body.scroll").bounds();
            assertTrue(body.right() <= panel.right() && body.height() > 0, where);
            for (int slot : new int[] {1, 2, 3, 4, 9}) {
                PanelRow row = row(screen, slot);
                assertTrue(row.bounds().right() <= body.right(), where + " row " + slot);
                UiNode control = row.control() == null ? row.moreButton() : row.control();
                assertTrue(control.bounds().x() >= row.bounds().x() && control.bounds().right() <= row.bounds().right(),
                        where + " control " + slot + " " + control.bounds() + " in " + row.bounds());
            }
            assertTrue(screen.navigationButton("next").bounds().bottom() <= panel.bottom(), where);
            if (size[0] >= 640) {
                assertTrue(panel.width() <= 460, where);
                assertTrue(panel.width() * 9 <= panel.height() * 16, where + " wider than 16:9 " + panel);
            }
        }
    }

    @Test
    void aTwelveRowSettingsPageFitsWithoutScrollingAtTargetSizes() {
        for (int[] size : new int[][] {{640, 360}, {480, 270}}) {
            host = new FakeHost(size[0], size[1]);
            List<PanelTile> tiles = new ArrayList<>();
            for (int slot = 0; slot < 12; slot++) {
                TileHint hint = switch (slot % 4) {
                    case 0 -> TileHint.of(HintKind.TOGGLE, "ON");
                    case 1 -> new TileHint(HintKind.NUMBER, "12.5", true, List.of(), 0D, 100D, 0.5D);
                    case 2 -> new TileHint(HintKind.CHOICE, "MEDIUM", true, List.of("LOW", "MEDIUM", "HIGH"), null, null, null);
                    default -> new TileHint(HintKind.TEXT, "value", true, List.of(), null, null, null);
                };
                tiles.add(setting(slot, "Setting number " + slot, hint));
            }
            tiles.add(nav(45, "back"));
            NativePanelScreen screen = open(view(PanelLayout.FORM, null, 1, tiles.toArray(new PanelTile[0])));
            String where = size[0] + "x" + size[1];
            ScrollArea body = (ScrollArea) screen.root().find("body.scroll");
            assertEquals(0, body.maxScroll(), where + " content " + body.contentHeight() + " in " + body.bounds());
            UiRect panel = screen.root().find("panel").bounds();
            assertTrue(panel.height() <= size[1] - 4, where + " " + panel);
            for (int slot = 0; slot < 12; slot++) {
                UiRect row = row(screen, slot).bounds();
                assertTrue(row.y() >= body.bounds().y() && row.bottom() <= body.bounds().bottom(), where + " row " + slot + " " + row);
                assertEquals(PanelRow.MIN_HEIGHT, row.height(), where);
            }
        }
    }

    @Test
    void sectionHeadingsFoldTheirRowsAndSearchStillFindsThem() {
        List<PanelTile> tiles = new ArrayList<>();
        for (int slot = 10; slot < 18; slot++) {
            tiles.add(entry(slot, "Entry " + slot, "First"));
        }
        tiles.add(entry(18, "Last", "Second"));
        NativePanelScreen screen = open(view(PanelLayout.LIST, null, 1, tiles.toArray(new PanelTile[0])));
        click(screen, screen.root().find("section:First"), UiMouseButton.LEFT, NONE);
        assertNull(screen.root().find("row:10"));
        assertNotNull(screen.root().find("row:18"));
        assertTrue(actions.sent.isEmpty());
        screen.focus(screen.searchField());
        type(screen, "Entry 12");
        screen.render(draw, 0, 0, 0.0F);
        assertNotNull(screen.root().find("row:12"));
        for (int index = 0; index < "Entry 12".length(); index++) {
            screen.keyPressed(new UiKeyEvent(UiKey.BACKSPACE, NONE));
        }
        screen.focus(null);
        click(screen, screen.root().find("section:First"), UiMouseButton.LEFT, NONE);
        assertNotNull(screen.root().find("row:10"));
    }
}
