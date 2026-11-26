package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiScrollEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudEditorScreenTest {
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final UiModifiers ALT = new UiModifiers(false, false, true, false);
    private static final UiModifiers SHIFT = new UiModifiers(true, false, false, false);
    private static final UiModifiers SHORTCUT = System.getProperty("os.name", "").toLowerCase().contains("mac")
            ? new UiModifiers(false, false, false, true) : new UiModifiers(false, true, false, false);

    @TempDir
    Path directory;
    private final HudFakes.Clock clock = new HudFakes.Clock();
    private HudFakes.Host host;
    private HudLayoutStore store;
    private HudController controller;
    private HudEditorScreen editor;
    private HudFakes.Draw draw;

    @BeforeEach
    void open() {
        host = new HudFakes.Host();
        store = new HudLayoutStore(directory.resolve("adapt-hud.json"));
        controller = new HudController(host, store, clock, false);
        controller.openEditor();
        editor = (HudEditorScreen) host.current;
        frame();
    }

    private void frame() {
        draw = new HudFakes.Draw(WIDTH, HEIGHT, 2.0, true);
        editor.render(draw, 0, 0, 0.0F);
    }

    private HudRect rect(String id) {
        return editor.renderer().placed(id).box().rect();
    }

    private HudPlacement placement(String id) {
        return editor.working().activeProfile().widget(id).placement();
    }

    private static UiMouseEvent mouse(double x, double y, UiMouseButton button, UiModifiers modifiers) {
        return new UiMouseEvent(x, y, button, modifiers);
    }

    private void drag(double fromX, double fromY, double toX, double toY, UiModifiers modifiers) {
        editor.mouseClicked(mouse(fromX, fromY, UiMouseButton.LEFT, modifiers));
        editor.mouseDragged(mouse((fromX + toX) / 2, (fromY + toY) / 2, UiMouseButton.LEFT, modifiers), 0, 0);
        editor.mouseDragged(mouse(toX, toY, UiMouseButton.LEFT, modifiers), 0, 0);
        editor.mouseReleased(mouse(toX, toY, UiMouseButton.LEFT, modifiers));
        frame();
    }

    private void click(String label) {
        String call = draw.calls.stream().filter((String entry) -> entry.startsWith("text ") && entry.endsWith(" " + label))
                .findFirst().orElseThrow(() -> new AssertionError("no button " + label));
        String[] position = call.split(" ")[1].split(",");
        editor.mouseClicked(mouse(Integer.parseInt(position[0]) + 1, Integer.parseInt(position[1]) + 1, UiMouseButton.LEFT, UiModifiers.NONE));
        frame();
    }

    private void key(UiKey key, UiModifiers modifiers) {
        editor.keyPressed(new UiKeyEvent(key, modifiers));
        frame();
    }

    @Test
    void showsEveryWidgetWithSampleContent() {
        for (HudWidgetKind kind : HudWidgetKind.values()) {
            HudRenderer.Placed placed = editor.renderer().placed(kind.id());
            assertTrue(placed.drawn(), kind.id());
            assertFalse(placed.placeholder(), kind.id());
        }
        assertTrue(draw.texts().contains("+12"));
        assertTrue(draw.texts().contains("Done"));
    }

    @Test
    void draggingMovesTheWidgetAndReanchorsItToTheNearestPoint() {
        HudRect start = rect("skills");
        double grabX = start.x() + 10;
        double grabY = start.centerY();
        drag(grabX, grabY, grabX + 497, grabY + 241, ALT);
        HudRect moved = rect("skills");
        assertEquals(start.x() + 497, moved.x(), 0.51);
        assertEquals(start.y() + 241, moved.y(), 0.51);
        assertEquals(HudAnchor.BOTTOM_RIGHT, placement("skills").anchor());
        assertEquals(placement("skills"), store.load().activeProfile().widget("skills").placement());
    }

    @Test
    void draggingSnapsToGuidesUnlessAltIsHeld() {
        HudRect start = rect("skills");
        double grabX = start.x() + 10;
        double grabY = start.centerY();
        double targetLeft = WIDTH / 2.0 + 2.5;
        editor.mouseClicked(mouse(grabX, grabY, UiMouseButton.LEFT, UiModifiers.NONE));
        editor.mouseDragged(mouse(grabX + targetLeft - start.x(), grabY + 100.5, UiMouseButton.LEFT, UiModifiers.NONE), 0, 0);
        assertTrue(editor.guides().contains(new HudSnap.Guide(true, WIDTH / 2.0)));
        editor.mouseReleased(mouse(0, 0, UiMouseButton.LEFT, UiModifiers.NONE));
        frame();
        assertTrue(editor.guides().isEmpty());
        assertEquals(WIDTH / 2.0, rect("skills").x(), 0.51);

        HudRect snapped = rect("skills");
        drag(snapped.x() + 10, snapped.bottom() - 4, snapped.x() + 10 + 2.5, snapped.bottom() - 4, ALT);
        assertEquals(WIDTH / 2.0 + 2.5, rect("skills").x(), 0.51);
    }

    @Test
    void draggingACornerScalesProportionallyAroundTheOppositeCorner() {
        HudRect start = rect("resources");
        editor.mouseClicked(mouse(start.centerX(), start.centerY(), UiMouseButton.LEFT, UiModifiers.NONE));
        editor.mouseReleased(mouse(start.centerX(), start.centerY(), UiMouseButton.LEFT, UiModifiers.NONE));
        frame();
        drag(start.x(), start.y(), start.x() - start.width() / 2, start.y() - start.height() / 2, UiModifiers.NONE);
        HudRect scaled = rect("resources");
        assertEquals(1.5, placement("resources").scale(), 1e-9);
        assertEquals(start.right(), scaled.right(), 0.51);
        assertEquals(start.bottom(), scaled.bottom(), 0.51);
        assertEquals(start.width() * 1.5, scaled.width(), 0.51);
        assertEquals(HudAnchor.BOTTOM_RIGHT, placement("resources").anchor());
    }

    @Test
    void scrollingOverAWidgetScalesIt() {
        HudRect start = rect("abilities");
        editor.mouseScrolled(new UiScrollEvent(start.centerX(), start.centerY(), 0, 1));
        frame();
        assertEquals(1.1, placement("abilities").scale(), 1e-9);
        assertEquals("abilities", editor.selected());
        editor.mouseScrolled(new UiScrollEvent(start.centerX(), start.centerY(), 0, -1));
        frame();
        assertEquals(1.0, placement("abilities").scale(), 1e-9);
        assertEquals(start.centerX(), rect("abilities").centerX(), 0.51);
    }

    @Test
    void arrowsNudgeAndOneUndoRevertsTheWholeRun() {
        HudRect start = rect("toasts");
        HudPlacement original = placement("toasts");
        editor.mouseClicked(mouse(start.centerX(), start.centerY(), UiMouseButton.LEFT, UiModifiers.NONE));
        editor.mouseReleased(mouse(start.centerX(), start.centerY(), UiMouseButton.LEFT, UiModifiers.NONE));
        frame();
        key(UiKey.LEFT, UiModifiers.NONE);
        assertEquals(start.x() - 1, rect("toasts").x(), 0.51);
        key(UiKey.DOWN, SHIFT);
        key(UiKey.DOWN, SHIFT);
        assertEquals(start.y() + 20, rect("toasts").y(), 0.51);
        key(UiKey.Z, SHORTCUT);
        assertEquals(original, placement("toasts"));
        key(UiKey.Y, SHORTCUT);
        assertEquals(start.y() + 20, rect("toasts").y(), 0.51);
    }

    @Test
    void undoAndRedoStepThroughDrags() {
        HudPlacement original = placement("skills");
        HudRect start = rect("skills");
        drag(start.centerX(), start.centerY(), start.centerX() + 150, start.centerY() + 50, ALT);
        HudPlacement moved = placement("skills");
        assertNotEquals(original, moved);
        editor.undo();
        assertEquals(original, placement("skills"));
        editor.redo();
        assertEquals(moved, placement("skills"));
    }

    @Test
    void exportAndImportGoThroughTheClipboard() {
        click("Export");
        assertTrue(host.clipboard.startsWith(HudLayoutCodec.EXPORT_PREFIX));
        assertEquals("Layout copied to the clipboard", editor.message());
        click("Import");
        assertEquals(2, editor.working().profiles().size());
        assertEquals("Default (2)", editor.working().activeProfile().name());
        host.clipboard = "nonsense";
        click("Import");
        assertEquals(2, editor.working().profiles().size());
        assertEquals("The clipboard has no HUD layout", editor.message());
    }

    @Test
    void profilesCanBeCreatedRenamedDuplicatedSwitchedAndDeleted() {
        click("New");
        assertEquals("Profile", editor.working().activeProfile().name());
        click("Profile");
        for (int index = 0; index < "Profile".length(); index++) {
            key(UiKey.BACKSPACE, UiModifiers.NONE);
        }
        for (char character : "Raid".toCharArray()) {
            editor.charTyped(new UiCharEvent(character, UiModifiers.NONE));
        }
        key(UiKey.ENTER, UiModifiers.NONE);
        assertEquals("Raid", editor.working().activeProfile().name());
        click("Duplicate");
        assertEquals("Raid copy", editor.working().activeProfile().name());
        assertEquals(3, editor.working().profiles().size());
        click("<");
        assertEquals("Raid", editor.working().activeProfile().name());
        click("Delete");
        assertEquals(2, editor.working().profiles().size());
        assertNull(editor.working().profile("Raid"));
        assertEquals(editor.working(), store.load());
    }

    @Test
    void globalScaleButtonsChangeTheProfileScale() {
        double before = rect("resources").width();
        click("+");
        assertEquals(1.1, editor.working().activeProfile().scale(), 1e-9);
        assertEquals(before * 1.1, rect("resources").width(), 0.51);
        click("Scale 110%");
        assertEquals(1.0, editor.working().activeProfile().scale(), 1e-9);
    }

    @Test
    void rightClickOpensOptionsAndRowsChangeTheWidget() {
        HudRect skills = rect("skills");
        editor.mouseClicked(mouse(skills.centerX(), skills.centerY(), UiMouseButton.RIGHT, UiModifiers.NONE));
        frame();
        assertTrue(editor.popoverOpen());
        click("Visible");
        assertFalse(placement("skills").visible());
        assertTrue(editor.renderer().placed("skills").drawn());
        click("Names");
        assertEquals(new HudOptions.Skills(List.of(), 4, 3000L, true, 10), editor.working().activeProfile().widget("skills").options());
        key(UiKey.ESCAPE, UiModifiers.NONE);
        assertFalse(editor.popoverOpen());
        assertFalse(editor.closesOnEscape() && editor.popoverOpen());
    }

    @Test
    void resourcesSplitIntoOneWidgetPerValue() {
        HudRect resources = rect("resources");
        editor.mouseClicked(mouse(resources.centerX(), resources.centerY(), UiMouseButton.RIGHT, UiModifiers.NONE));
        frame();
        click("Split into widgets");
        HudProfile profile = editor.working().activeProfile();
        assertEquals(new HudOptions.Resources(true, false, false, false), profile.widget("resources").options());
        assertEquals(new HudOptions.Resources(false, true, false, false), profile.widget("resources_2").options());
        assertEquals(new HudOptions.Resources(false, false, false, true), profile.widget("resources_4").options());
        assertTrue(editor.renderer().placed("resources_3").drawn());
        editor.undo();
        assertNull(editor.working().activeProfile().widget("resources_2"));
    }

    @Test
    void resetAllRestoresTheDefaultsAndClosingSaves() {
        HudRect start = rect("skills");
        drag(start.centerX(), start.centerY(), start.centerX() + 200, start.centerY() + 100, ALT);
        click("Reset all");
        assertEquals(HudDefaults.profile("Default"), editor.working().activeProfile());
        editor.removed();
        assertEquals(editor.working(), store.load());
        assertEquals(editor.working(), controller.layout());
    }

    @Test
    void theInGameHudIsNotDrawnUnderTheEditor() {
        controller.model().publish(new art.arcane.adapt.client.protocol.ServerFrame.HudPublish("adapt:notice", 1, 5000L,
                java.util.List.of(), "live"));
        clock.advance(500L);
        HudFakes.Draw game = new HudFakes.Draw(WIDTH, HEIGHT, 2.0, true);
        controller.render(game, true);
        assertTrue(game.calls.isEmpty());
        host.current = null;
        controller.render(game, true);
        assertTrue(game.texts().contains("live"));
    }

    private static final int[][] WINDOWS = {{1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 4}, {1024, 768, 2}, {1024, 768, 3},
            {854, 480, 2}, {854, 480, 3}, {1280, 720, 4}, {640, 480, 2}};

    private HudFakes.Draw window(int[] size) {
        HudFakes.Draw fake = HudFakes.Draw.window(size[0], size[1], size[2]);
        HudFakes.Draw recorded = new HudFakes.Draw(fake.width, fake.height, fake.guiScale, true);
        editor.render(recorded, 0, 0, 0.0F);
        return recorded;
    }

    private static boolean overlap(HudRect a, HudRect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    @Test
    void theBarNeverCoversTheHintAndStaysOnScreen() {
        for (int[] size : WINDOWS) {
            HudFakes.Draw frame = window(size);
            HudRect hint = editor.hintBox();
            assertTrue(hint != null, size[0] + "x" + size[1] + " gui" + size[2]);
            for (HudEditorScreen.Button button : editor.buttons()) {
                HudRect rect = new HudRect(button.x(), button.y(), button.width(), button.height());
                String where = size[0] + "x" + size[1] + " gui" + size[2] + " " + button.label();
                HudRect strip = new HudRect(rect.x(), rect.y() - 2, rect.width(), rect.height() + 4);
                assertFalse(overlap(strip, hint.inflate(2.0)), where + " " + strip + " runs into the hint " + hint);
                assertTrue(rect.x() >= 0 && rect.right() <= frame.width && rect.y() >= 0 && rect.bottom() <= frame.height, where);
            }
            assertTrue(hint.y() >= 0 && hint.bottom() <= frame.height, size[0] + "x" + size[1] + " gui" + size[2] + " hint " + hint);
        }
    }

    @Test
    void onALargeScreenTheBarLeavesEveryWidgetUncovered() {
        for (int[] size : new int[][]{{1920, 1080, 2}, {1920, 1080, 3}, {2560, 1440, 3}}) {
            window(size);
            HudRenderer renderer = editor.renderer();
            for (HudEditorScreen.Button button : editor.buttons()) {
                HudRect rect = new HudRect(button.x(), button.y() - 2, button.width(), button.height() + 4);
                for (int index = 0; index < renderer.placedCount(); index++) {
                    HudRenderer.Placed placed = renderer.placed(index);
                    if (placed.drawn()) {
                        assertFalse(overlap(rect, placed.box().rect()), size[0] + "x" + size[1] + " gui" + size[2] + " "
                                + button.label() + " covers " + placed.config().id());
                    }
                }
                assertFalse(overlap(rect, editor.hintBox()), button.label());
            }
        }
    }

    @Test
    void theBarAndPopoverStartNewLayersSoWidgetTextNeverShowsThrough() {
        HudRect resources = rect("resources");
        editor.mouseClicked(mouse(resources.centerX(), resources.centerY(), UiMouseButton.RIGHT, UiModifiers.NONE));
        frame();
        int bar = draw.calls.indexOf(draw.calls.stream().filter((String call) -> call.endsWith(" Done")).findFirst().orElseThrow());
        int popover = draw.calls.indexOf(draw.calls.stream().filter((String call) -> call.startsWith("fill " + editor.popoverX() + ","
                + editor.popoverY() + ",")).findFirst().orElseThrow());
        assertTrue(draw.calls.subList(0, bar).contains("nextLayer"));
        int layer = draw.calls.subList(bar, popover).lastIndexOf("nextLayer");
        assertTrue(layer >= 0, "a new layer before the popover");
    }

    @Test
    void sampleToastsFitTheToastBoxSoTheyStayOffTheNotices() {
        HudOptions.Toasts options = (HudOptions.Toasts) editor.working().activeProfile().widget("toasts").options();
        int shown = editor.renderer().model().toasts().shown().size();
        assertTrue(shown >= 1 && shown <= options.maxVisible(), shown + " sample cards for a box of " + options.maxVisible());
    }

    @Test
    void sampleWidgetsDoNotOverlapEachOther() {
        for (int[] size : WINDOWS) {
            window(size);
            HudRenderer renderer = editor.renderer();
            for (int first = 0; first < renderer.placedCount(); first++) {
                for (int second = first + 1; second < renderer.placedCount(); second++) {
                    HudRenderer.Placed a = renderer.placed(first);
                    HudRenderer.Placed b = renderer.placed(second);
                    if (a.drawn() && b.drawn() && a.config().placement().visible() && b.config().placement().visible()) {
                        assertFalse(overlap(a.box().rect(), b.box().rect()), size[0] + "x" + size[1] + " gui" + size[2] + ": "
                                + a.config().id() + " " + a.box().rect() + " over " + b.config().id() + " " + b.box().rect());
                    }
                }
            }
        }
    }

    @Test
    void popoverValuesNeverRunIntoTheirLabels() {
        for (int[] size : WINDOWS) {
            window(size);
            HudRect glyphs = rect("abilities");
            editor.mouseClicked(mouse(glyphs.centerX(), glyphs.centerY(), UiMouseButton.RIGHT, UiModifiers.NONE));
            for (int scroll = 0; scroll < 40; scroll++) {
                HudFakes.Draw frame = window(size);
                int left = editor.popoverX();
                int right = left + editor.popoverWidth();
                assertTrue(left >= 0 && right <= frame.width, size[0] + "x" + size[1] + " popover " + left + ".." + right);
                java.util.Map<Integer, List<int[]>> rows = new java.util.TreeMap<>();
                for (String call : frame.calls.subList(Math.max(0, frame.calls.indexOf(frame.calls.stream()
                        .filter((String entry) -> entry.startsWith("fill " + left + "," + editor.popoverY() + "," + right + ","))
                        .findFirst().orElseThrow())), frame.calls.size())) {
                    if (!call.startsWith("text ")) {
                        continue;
                    }
                    String[] position = call.split(" ")[1].split(",");
                    int x = Integer.parseInt(position[0]);
                    int y = Integer.parseInt(position[1]);
                    String shown = call.substring(call.indexOf(' ', call.indexOf(' ', 5) + 1) + 1);
                    if (x >= left && x < right && y > editor.popoverY()) {
                        rows.computeIfAbsent(y, (Integer key) -> new java.util.ArrayList<>()).add(new int[]{x, x + frame.textWidth(shown)});
                    }
                }
                for (java.util.Map.Entry<Integer, List<int[]>> row : rows.entrySet()) {
                    List<int[]> spans = row.getValue();
                    for (int[] span : spans) {
                        assertTrue(span[1] <= right, size[0] + "x" + size[1] + " row " + row.getKey() + " runs out of the popover");
                    }
                    if (spans.size() == 2) {
                        int[] a = spans.get(0);
                        int[] b = spans.get(1);
                        assertTrue(a[1] + 4 <= b[0] || b[1] + 4 <= a[0], size[0] + "x" + size[1] + " row " + row.getKey() + " label meets value");
                    }
                }
                editor.mouseScrolled(new UiScrollEvent(left + 5, editor.popoverY() + 20, 0, -1));
            }
            editor.keyPressed(new UiKeyEvent(UiKey.ESCAPE, UiModifiers.NONE));
        }
    }

    @Test
    void hoveringAGlyphNamesIt() {
        HudRenderer.Placed glyphs = editor.renderer().placed("abilities");
        assertTrue(glyphs.cellCount() > 0);
        HudRect cell = glyphs.cell(0);
        draw = new HudFakes.Draw(WIDTH, HEIGHT, 2.0, true);
        editor.render(draw, (int) cell.centerX(), (int) cell.centerY(), 0.0F);
        String name = editor.renderer().glyphLayout("abilities").shown.get(0).label();
        assertTrue(draw.texts().contains(name), draw.texts().toString());
    }
}
