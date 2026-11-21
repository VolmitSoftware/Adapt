package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileHint;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.qa.UiProbe;
import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.FakeHost;
import art.arcane.adapt.client.ui.kit.IconTile;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.KitScreen;
import art.arcane.adapt.client.ui.kit.ManualClock;
import art.arcane.adapt.client.ui.kit.NumberStepper;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativePanelScreenTest {
    private static final UiModifiers NONE = UiModifiers.NONE;
    private static final UiModifiers SHIFT = new UiModifiers(true, false, false, false);

    private final ManualClock clock = new ManualClock();
    private final RecordingActions actions = new RecordingActions();
    private FakeHost host = new FakeHost(427, 240);
    private final KitDraw draw = new KitDraw();

    private static PanelTile plain(int slot, String name) {
        return new PanelTile(slot, IconRef.item("minecraft:stone"), 1, name, List.of("§7Lore of " + name), false, null, TileRole.ENTRY,
                "", "", null, List.of());
    }

    private static PanelTile hinted(int slot, String name, HintKind kind, String value) {
        TileRole role = kind == HintKind.NAVIGATION ? TileRole.NAVIGATION : TileRole.SETTING;
        return new PanelTile(slot, IconRef.item("minecraft:lever"), 1, name, List.of(), false, TileHint.of(kind, value), role, "", "",
                null, List.of());
    }

    private static PanelTile filler(int slot) {
        return new PanelTile(slot, IconRef.item("minecraft:gray_stained_glass_pane"), 1, " ", List.of(), false, null,
                TileRole.DECORATION, "", "", null, List.of());
    }

    private static PanelView grid(int session, int revision, String title, int rows, List<PanelTile> tiles) {
        return new PanelView(session, revision, title, rows, PanelLayout.GRID, null, List.of(), tiles);
    }

    private static PanelView view(int revision, PanelTile... tiles) {
        return grid(7, revision, "§6Skills", 6, List.of(tiles));
    }

    private NativePanelScreen open(PanelView view) {
        NativePanelScreen screen = new NativePanelScreen(view, actions, UiTheme.DEFAULT, clock.motion());
        host.open(screen);
        clock.advance(KitScreen.OPEN_MILLIS);
        screen.render(draw, 0, 0, 0.0F);
        return screen;
    }

    private static UiMouseEvent at(UiNode node, UiMouseButton button, UiModifiers modifiers) {
        return new UiMouseEvent(node.bounds().centerX(), node.bounds().centerY(), button, modifiers);
    }

    private void click(NativePanelScreen screen, UiNode node, UiMouseButton button, UiModifiers modifiers) {
        UiMouseEvent event = at(node, button, modifiers);
        screen.mouseClicked(event);
        screen.mouseReleased(event);
    }

    private void hover(NativePanelScreen screen, UiNode node) {
        screen.render(draw, node.bounds().centerX(), node.bounds().centerY(), 0.0F);
    }

    @Test
    void tilesKeepTheirChestSlotPositionsOnTheNineColumnGrid() {
        NativePanelScreen screen = open(view(1, plain(0, "a"), plain(8, "b"), plain(9, "c"), plain(53, "d")));
        UiRect origin = screen.tile(0).bounds();
        int pitch = screen.tile(9).bounds().y() - origin.y();
        assertEquals(origin.width() + UiTheme.DEFAULT.gap(), pitch);
        assertEquals(origin.x() + 8 * pitch, screen.tile(8).bounds().x());
        assertEquals(origin.y(), screen.tile(8).bounds().y());
        assertEquals(origin.x(), screen.tile(9).bounds().x());
        assertEquals(origin.x() + 8 * pitch, screen.tile(53).bounds().x());
        assertEquals(origin.y() + 5 * pitch, screen.tile(53).bounds().y());
    }

    @Test
    void fillerPanesAreHiddenAndNavigationMovesIntoTheChrome() {
        NativePanelScreen screen = open(view(1, filler(0), filler(1), plain(4, "Book"), hinted(45, "Back", HintKind.NAVIGATION, "back"),
                hinted(53, "Next", HintKind.NAVIGATION, "next"), hinted(50, "Elsewhere", HintKind.NAVIGATION, "skills")));
        assertNull(screen.tile(0));
        assertNull(screen.tile(1));
        assertNotNull(screen.tile(4));
        assertNull(screen.tile(45));
        assertNull(screen.tile(53));
        assertNotNull(screen.tile(50));
        assertNotNull(screen.navigationButton("back"));
        click(screen, screen.navigationButton("next"), UiMouseButton.LEFT, NONE);
        click(screen, screen.navigationButton("back"), UiMouseButton.LEFT, NONE);
        assertEquals(List.of("click 7 1 53 LEFT", "click 7 1 45 LEFT"), actions.sent);
    }

    @Test
    void mouseButtonsReachTheServerAsChestClicksWithTheCurrentRevision() {
        NativePanelScreen screen = open(view(4, plain(10, "Item")));
        IconTile tile = screen.tile(10);
        click(screen, tile, UiMouseButton.RIGHT, NONE);
        click(screen, tile, UiMouseButton.LEFT, SHIFT);
        click(screen, tile, UiMouseButton.RIGHT, SHIFT);
        click(screen, tile, UiMouseButton.MIDDLE, NONE);
        click(screen, tile, UiMouseButton.OTHER, NONE);
        assertEquals(List.of("click 7 4 10 RIGHT", "click 7 4 10 SHIFT_LEFT", "click 7 4 10 SHIFT_RIGHT", "click 7 4 10 MIDDLE"),
                actions.sent);
    }

    @Test
    void doubleClickSendsTwoLeftsThenADoubleClickLikeVanilla() {
        NativePanelScreen screen = open(view(1, plain(10, "Item")));
        IconTile tile = screen.tile(10);
        click(screen, tile, UiMouseButton.LEFT, NONE);
        clock.advance(100);
        click(screen, tile, UiMouseButton.LEFT, NONE);
        clock.advance(1000);
        click(screen, tile, UiMouseButton.LEFT, NONE);
        assertEquals(List.of("click 7 1 10 LEFT", "click 7 1 10 LEFT", "click 7 1 10 DOUBLE_CLICK", "click 7 1 10 LEFT"), actions.sent);
    }

    @Test
    void keysOverTheHoveredTileSendHotbarDropAndOffhandClicks() {
        NativePanelScreen screen = open(view(1, plain(10, "Item"), plain(11, "Other")));
        hover(screen, screen.tile(11));
        screen.keyPressed(new UiKeyEvent(UiKey.DIGIT_3, NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.Q, NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.F, NONE));
        screen.render(draw, 0, 0, 0.0F);
        assertFalse(screen.keyPressed(new UiKeyEvent(UiKey.Q, NONE)));
        assertEquals(List.of("click 7 1 11 NUMBER_KEY", "click 7 1 11 DROP", "click 7 1 11 SWAP_OFFHAND"), actions.sent);
    }

    @Test
    void keyboardFocusedTileTakesKeysAndEnterWhenNothingIsHovered() {
        NativePanelScreen screen = open(view(1, plain(10, "Item")));
        screen.keyPressed(new UiKeyEvent(UiKey.TAB, NONE));
        assertSame(screen.tile(10), screen.focused());
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.DIGIT_1, NONE));
        assertEquals(List.of("click 7 1 10 LEFT", "click 7 1 10 NUMBER_KEY"), actions.sent);
    }

    @Test
    void inventoryKeyAndEscapeCloseAndReportOnce() {
        NativePanelScreen screen = open(view(1, plain(10, "Item")));
        assertTrue(screen.keyPressed(new UiKeyEvent(UiKey.E, NONE)));
        assertNull(host.current());
        screen.removed();
        assertEquals(List.of("closed 7"), actions.sent);
        NativePanelScreen again = open(view(1, plain(10, "Item")));
        assertFalse(again.keyPressed(new UiKeyEvent(UiKey.ESCAPE, NONE)));
        assertTrue(again.closesOnEscape());
    }

    @Test
    void toggleAndDestructiveTilesAreStyledAndClickThrough() {
        NativePanelScreen screen = open(view(1, hinted(11, "Sound", HintKind.TOGGLE, "ON"), hinted(14, "Reset", HintKind.DESTRUCTIVE,
                "Reset")));
        assertEquals(Boolean.TRUE, screen.tile(11).switchState());
        assertEquals(IconTile.Tone.DESTRUCTIVE, screen.tile(14).tone());
        click(screen, screen.tile(11), UiMouseButton.LEFT, NONE);
        click(screen, screen.tile(14), UiMouseButton.LEFT, NONE);
        assertEquals(List.of("click 7 1 11 LEFT", "click 7 1 14 LEFT"), actions.sent);
        screen.apply(view(2, hinted(11, "Sound", HintKind.TOGGLE, "OFF"), hinted(14, "Reset", HintKind.DESTRUCTIVE, "Reset")));
        assertEquals(Boolean.FALSE, screen.tile(11).switchState());
    }

    @Test
    void numberTileOpensAStepperThatStepsWithChestClicksAndSendsTypedValues() {
        NativePanelScreen screen = open(view(1, hinted(12, "Range", HintKind.NUMBER, "8")));
        assertEquals("8", screen.tile(12).cornerText());
        click(screen, screen.tile(12), UiMouseButton.LEFT, NONE);
        assertNotNull(screen.editor());
        screen.render(draw, 0, 0, 0.0F);
        NumberStepper stepper = assertInstanceOf(NumberStepper.class, screen.editor().content());
        click(screen, stepper.plus(), UiMouseButton.LEFT, NONE);
        click(screen, stepper.minus(), UiMouseButton.LEFT, NONE);
        host.setModifiers(SHIFT);
        click(screen, stepper.plus(), UiMouseButton.LEFT, SHIFT);
        host.setModifiers(NONE);
        screen.mouseClicked(at(stepper.field(), UiMouseButton.LEFT, NONE));
        stepper.field().selectAll();
        "12".codePoints().forEach((int c) -> screen.charTyped(new UiCharEvent(c, NONE)));
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, NONE));
        click(screen, screen.tile(12), UiMouseButton.RIGHT, NONE);
        assertTrue(screen.overlays().isEmpty());
        click(screen, screen.tile(12), UiMouseButton.RIGHT, NONE);
        assertEquals(List.of("click 7 1 12 LEFT", "click 7 1 12 RIGHT", "click 7 1 12 SHIFT_LEFT", "input 7 1 12 12",
                "click 7 1 12 RIGHT"), actions.sent);
    }

    @Test
    void textTileOpensAFieldThatCommitsInputAndCloses() {
        NativePanelScreen screen = open(view(1, hinted(13, "Nickname", HintKind.TEXT, "Steve")));
        screen.keyPressed(new UiKeyEvent(UiKey.TAB, NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, NONE));
        screen.render(draw, 0, 0, 0.0F);
        TextField field = assertInstanceOf(TextField.class, screen.editor().content());
        assertSame(field, screen.focused());
        assertEquals("Steve", field.selectedText());
        "Alex".codePoints().forEach((int c) -> screen.charTyped(new UiCharEvent(c, NONE)));
        screen.keyPressed(new UiKeyEvent(UiKey.Q, NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, NONE));
        assertEquals(List.of("input 7 1 13 Alex"), actions.sent);
        assertNull(screen.editor());
        assertTrue(screen.overlays().isEmpty());
        assertSame(screen.tile(13), screen.focused());
    }

    @Test
    void escapeClosesTheEditorBeforeThePanel() {
        NativePanelScreen screen = open(view(1, hinted(13, "Nickname", HintKind.TEXT, "Steve")));
        click(screen, screen.tile(13), UiMouseButton.LEFT, NONE);
        assertTrue(screen.keyPressed(new UiKeyEvent(UiKey.ESCAPE, NONE)));
        assertNull(screen.editor());
        assertSame(screen, host.current());
        assertTrue(actions.sent.isEmpty());
    }

    @Test
    void applyUpdatesInPlaceAndKeepsFocusEditorAndNodes() {
        NativePanelScreen screen = open(view(1, plain(10, "A"), hinted(12, "Range", HintKind.NUMBER, "8")));
        IconTile a = screen.tile(10);
        click(screen, screen.tile(12), UiMouseButton.LEFT, NONE);
        screen.render(draw, 0, 0, 0.0F);
        NumberStepper stepper = (NumberStepper) screen.editor().content();
        screen.apply(view(2, plain(10, "A2"), hinted(12, "Range", HintKind.NUMBER, "9"), plain(20, "New")));
        screen.render(draw, 0, 0, 0.0F);
        assertSame(a, screen.tile(10));
        assertEquals("A2", a.label());
        assertSame(stepper, screen.editor().content());
        assertEquals("9", stepper.value());
        assertNotNull(screen.tile(20));
        click(screen, stepper.plus(), UiMouseButton.LEFT, NONE);
        assertEquals("click 7 2 12 LEFT", actions.sent.get(actions.sent.size() - 1));
        screen.apply(view(3, plain(10, "A3")));
        screen.render(draw, 0, 0, 0.0F);
        assertNull(screen.editor());
        assertNull(screen.tile(12));
    }

    @Test
    void applyKeepsKeyboardFocusOnTheSameSlot() {
        NativePanelScreen screen = open(view(1, plain(10, "A"), plain(11, "B")));
        screen.keyPressed(new UiKeyEvent(UiKey.TAB, NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.TAB, NONE));
        assertSame(screen.tile(11), screen.focused());
        screen.apply(view(2, plain(11, "B2")));
        screen.render(draw, 0, 0, 0.0F);
        assertSame(screen.tile(11), screen.focused());
    }

    @Test
    void staleRevisionsAndOtherSessionsAreIgnored() {
        NativePanelScreen screen = open(view(5, plain(10, "A")));
        screen.apply(view(4, plain(10, "Old")));
        screen.apply(grid(8, 9, "Other", 1, List.of(plain(0, "X"))));
        assertEquals("A", screen.tile(10).label());
        assertEquals(5, screen.view().revision());
    }

    @Test
    void scrollPositionSurvivesUpdatesOnTinyScreens() {
        host = new FakeHost(427, 150);
        NativePanelScreen screen = open(view(1, plain(0, "A"), plain(53, "Z"), hinted(53 - 1, "Next", HintKind.NAVIGATION, "next")));
        ScrollArea scroll = (ScrollArea) screen.root().find("grid.scroll");
        assertTrue(scroll.maxScroll() > 0);
        scroll.setScroll(20);
        screen.apply(view(2, plain(0, "A"), plain(53, "Z2"), hinted(52, "Next", HintKind.NAVIGATION, "next")));
        screen.render(draw, 0, 0, 0.0F);
        assertEquals(20, scroll.scroll());
    }

    @Test
    void panelFitsEveryCommonScaledSize() {
        int[][] sizes = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {427, 240}, {320, 240}, {300, 160}};
        for (int[] size : sizes) {
            host = new FakeHost(size[0], size[1]);
            List<PanelTile> tiles = new ArrayList<>();
            for (int slot = 0; slot < 45; slot++) {
                tiles.add(plain(slot, "T" + slot));
            }
            tiles.add(hinted(45, "Back", HintKind.NAVIGATION, "back"));
            tiles.add(hinted(53, "Next", HintKind.NAVIGATION, "next"));
            NativePanelScreen screen = open(grid(1, 1, "§6A fairly long panel title", 6, tiles));
            UiNode panel = screen.root().find("panel");
            UiRect bounds = panel.bounds();
            String where = size[0] + "x" + size[1];
            assertTrue(bounds.x() >= 0 && bounds.y() >= 0 && bounds.right() <= size[0] && bounds.bottom() <= size[1], where + " " + bounds);
            int tile = screen.tile(0).bounds().width();
            assertTrue(tile >= NativePanelScreen.MIN_TILE, where);
            ScrollArea scroll = (ScrollArea) screen.root().find("grid.scroll");
            assertTrue(scroll.bounds().x() >= bounds.x() && scroll.bounds().right() <= bounds.right(), where);
            assertTrue(screen.tile(8).bounds().right() <= scroll.bounds().right(), where);
            assertTrue(screen.navigationButton("next").bounds().bottom() <= bounds.bottom(), where);
            if (size[0] >= 640) {
                assertTrue(tile > 22, where + " tile " + tile);
                assertTrue(bounds.width() * 9 <= bounds.height() * 16, where + " wider than 16:9 " + bounds);
            }
        }
    }

    @Test
    void aCutBreadcrumbKeepsItsSeparatorBeforeTheTitle() {
        host = new FakeHost(320, 240);
        PanelView view = new PanelView(1, 1, "§7Configure: §fGeneral Settings", 2, PanelLayout.FORM, null,
                List.of("Configure Adapt", "Configure: Core"), List.of(hinted(0, "Debug", HintKind.TOGGLE, "OFF")));
        open(view);
        draw.calls().clear();
        open(view);
        List<String> texts = draw.calls("text ");
        String trail = texts.stream().filter((String call) -> call.contains(" / ")).findFirst().orElse(String.join(" | ", texts));
        assertTrue(trail.endsWith("... / "), trail);
    }

    @Test
    void probeDescribesThePanelAndItsTiles() {
        NativePanelScreen screen = open(view(3, hinted(11, "Sound", HintKind.TOGGLE, "ON")));
        UiProbe.snapshot();
        clock.advance(200);
        screen.render(draw, 0, 0, 0.0F);
        String json = UiProbe.snapshot();
        assertTrue(json.startsWith("{\"screen\":\"panel\",\"title\":\"§6Skills\""), json);
        assertTrue(json.contains("\"sessionId\":\"7\",\"revision\":\"3\",\"rows\":\"6\""), json);
        assertTrue(json.contains("\"id\":\"slot-11\",\"kind\":\"tile\",\"label\":\"Sound\""), json);
        assertTrue(json.contains("\"switch\":\"ON\""), json);
        assertTrue(json.contains("\"hint\":\"TOGGLE\""), json);
        assertFalse(json.contains("\"id\":\"close\""), json);
        host.close();
    }

    @Test
    void controllerOpensUpdatesAndClosesWithoutEchoingServerCloses() {
        PanelScreens screens = new PanelScreens(host, actions, UiTheme.DEFAULT, clock.motion());
        screens.open(view(1, plain(10, "A")));
        NativePanelScreen first = screens.current();
        screens.open(view(2, plain(10, "B")));
        assertSame(first, screens.current());
        assertEquals(1, host.opened());
        assertEquals("B", first.tile(10).label());
        screens.update(grid(99, 1, "Other", 1, List.of()));
        screens.open(grid(8, 1, "Second", 1, List.of(plain(0, "X"))));
        assertEquals(8, screens.current().sessionId());
        screens.update(grid(8, 2, "Second", 1, List.of(plain(0, "Y"))));
        assertEquals("Y", screens.current().tile(0).label());
        screens.close(99);
        assertNotNull(screens.current());
        screens.close(8);
        assertNull(screens.current());
        screens.open(view(3, plain(10, "C")));
        screens.reset();
        assertNull(screens.current());
        assertTrue(actions.sent.isEmpty(), actions.sent.toString());
        screens.open(view(4, plain(10, "D")));
        host.close();
        assertEquals(List.of("closed 7"), actions.sent);
    }

    @Test
    void previewSampleCoversEveryHintAndReactsLikeAServer() {
        PanelPreview preview = new PanelPreview();
        PanelView sample = preview.view();
        Set<HintKind> kinds = EnumSet.noneOf(HintKind.class);
        for (PanelTile tile : sample.tiles()) {
            if (tile.hint() != null) {
                kinds.add(tile.hint().kind());
            }
        }
        assertEquals(EnumSet.allOf(HintKind.class), kinds);
        assertTrue(sample.tiles().stream().anyMatch(PanelTile::glint));
        assertTrue(sample.tiles().stream().anyMatch((PanelTile tile) -> tile.amount() > 1));
        assertTrue(sample.tiles().stream().anyMatch((PanelTile tile) -> tile.icon().customModelData() != 0));
        assertTrue(sample.tiles().stream().anyMatch((PanelTile tile) -> tile.role() == TileRole.DECORATION));

        preview.click(PanelPreview.SESSION, 0, PanelPreview.NUMBER_SLOT, art.arcane.adapt.platform.menu.ClickKind.SHIFT_LEFT);
        preview.click(PanelPreview.SESSION, 1, PanelPreview.TOGGLE_SLOT, art.arcane.adapt.platform.menu.ClickKind.LEFT);
        preview.input(PanelPreview.SESSION, 2, PanelPreview.TEXT_SLOT, "Alex");
        preview.click(PanelPreview.SESSION, 3, PanelPreview.NEXT_SLOT, art.arcane.adapt.platform.menu.ClickKind.LEFT);
        PanelView after = preview.view();
        assertEquals(4, after.revision());
        assertEquals("18", hint(after, PanelPreview.NUMBER_SLOT).value());
        assertEquals("OFF", hint(after, PanelPreview.TOGGLE_SLOT).value());
        assertEquals("Alex", hint(after, PanelPreview.TEXT_SLOT).value());
        assertTrue(after.tiles().stream().anyMatch((PanelTile tile) -> tile.slot() == PanelPreview.PREVIOUS_SLOT));

        NativePanelScreen screen = open(after);
        assertNotNull(screen.navigationButton("previous"));
        assertNotNull(screen.navigationButton("back"));
    }

    private static TileHint hint(PanelView view, int slot) {
        return view.tiles().stream().filter((PanelTile tile) -> tile.slot() == slot).findFirst().orElseThrow().hint();
    }
}
