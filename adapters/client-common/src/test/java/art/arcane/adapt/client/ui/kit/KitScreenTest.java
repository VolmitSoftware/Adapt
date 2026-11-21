package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiScrollEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitScreenTest {
    private static final UiModifiers SHIFT = new UiModifiers(true, false, false, false);

    private final ManualClock clock = new ManualClock();
    private final FakeHost host = new FakeHost(427, 240);
    private final KitDraw draw = new KitDraw();
    private final List<String> events = new ArrayList<>();

    private TestScreen open(TestScreen screen) {
        host.open(screen);
        clock.advance(KitScreen.OPEN_MILLIS);
        screen.render(draw, 0, 0, 0.0F);
        return screen;
    }

    private static UiKeyEvent key(UiKey key) {
        return new UiKeyEvent(key, UiModifiers.NONE);
    }

    @Test
    void pointerEventsHitTheTopmostNodeAndBubbleToParents() {
        Panel[] panel = new Panel[1];
        Button[] button = new Button[1];
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) -> {
            panel[0].place(new UiRect(10, 10, 200, 100), metrics);
            button[0].place(new UiRect(20, 40, 60, 20), metrics);
        });
        panel[0] = screen.root().add(new Panel("panel", "Title"));
        button[0] = panel[0].add(new Button("ok", "OK", () -> events.add("ok")));
        open(screen);

        assertSame(button[0], screen.hit(30, 45));
        assertSame(panel[0], screen.hit(150, 90));
        assertNull(screen.hit(300, 200));
        assertTrue(screen.mouseClicked(TestScreen.left(30, 45)));
        assertEquals(List.of("ok"), events);
        assertEquals(1, host.clicks());
        assertSame(button[0], screen.focused());
        assertTrue(screen.mouseClicked(TestScreen.left(150, 90)));
        assertNull(screen.focused());
        assertFalse(screen.mouseClicked(TestScreen.left(300, 200)));
    }

    @Test
    void unconsumedPressesBubbleAndCaptureLaterDragAndRelease() {
        List<String> seen = new ArrayList<>();
        UiNode parent = new UiGroup("parent") {
            @Override
            public boolean hittable() {
                return true;
            }

            @Override
            public boolean mousePressed(UiMouseEvent event) {
                seen.add("parent-press");
                return true;
            }

            @Override
            public boolean mouseDragged(UiMouseEvent event, double dragX, double dragY) {
                seen.add("parent-drag");
                return true;
            }

            @Override
            public boolean mouseReleased(UiMouseEvent event) {
                seen.add("parent-release");
                return true;
            }
        };
        Label child = new Label("child", "text") {
            @Override
            public boolean hittable() {
                return true;
            }
        };
        parent.add(child);
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                parent.place(new UiRect(0, 0, 100, 100), metrics));
        screen.root().add(parent);
        open(screen);

        screen.mouseClicked(TestScreen.left(5, 5));
        screen.mouseDragged(TestScreen.left(300, 300), 295, 295);
        screen.mouseReleased(TestScreen.left(300, 300));
        assertEquals(List.of("parent-press", "parent-drag", "parent-release"), seen);
        assertFalse(screen.mouseReleased(TestScreen.left(5, 5)));
    }

    @Test
    void clippedChildrenOutsideTheirContainerCannotBeHit() {
        Button inner = new Button("inner", "x", () -> {
        });
        ScrollArea area = new ScrollArea("scroll", new UiGroup("content", UiLayoutPolicy.column(0)));
        ((UiGroup) area.content()).add(inner);
        TestScreen screen = new TestScreen(clock.motion(), (UiRect rect, art.arcane.adapt.client.ui.UiDraw metrics) ->
                area.place(new UiRect(0, 0, 50, 50), metrics));
        screen.root().add(area);
        open(screen);
        assertSame(inner, screen.root().nodeAt(10, 5));
        inner.offset(0, 100);
        assertNull(screen.root().nodeAt(10, 105));
    }

    @Test
    void tabCyclesFocusInTreeOrderAndShiftTabGoesBack() {
        Button[] buttons = new Button[3];
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) -> {
            for (int index = 0; index < 3; index++) {
                buttons[index].place(new UiRect(10 + index * 50, 10, 40, 20), metrics);
            }
        });
        for (int index = 0; index < 3; index++) {
            buttons[index] = screen.root().add(new Button("b" + index, "B" + index, () -> {
            }));
        }
        open(screen);

        screen.keyPressed(key(UiKey.TAB));
        assertSame(buttons[0], screen.focused());
        screen.keyPressed(key(UiKey.TAB));
        screen.keyPressed(key(UiKey.TAB));
        assertSame(buttons[2], screen.focused());
        screen.keyPressed(key(UiKey.TAB));
        assertSame(buttons[0], screen.focused());
        screen.keyPressed(new UiKeyEvent(UiKey.TAB, SHIFT));
        assertSame(buttons[2], screen.focused());
    }

    @Test
    void arrowsMoveFocusToTheNearestNodeInThatDirection() {
        Button[] grid = new Button[4];
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) -> {
            grid[0].place(new UiRect(0, 0, 20, 20), metrics);
            grid[1].place(new UiRect(30, 0, 20, 20), metrics);
            grid[2].place(new UiRect(0, 30, 20, 20), metrics);
            grid[3].place(new UiRect(30, 30, 20, 20), metrics);
        });
        for (int index = 0; index < 4; index++) {
            grid[index] = screen.root().add(new Button("g" + index, "", () -> {
            }));
        }
        open(screen);

        screen.keyPressed(key(UiKey.RIGHT));
        assertSame(grid[0], screen.focused());
        screen.keyPressed(key(UiKey.RIGHT));
        assertSame(grid[1], screen.focused());
        screen.keyPressed(key(UiKey.DOWN));
        assertSame(grid[3], screen.focused());
        screen.keyPressed(key(UiKey.LEFT));
        assertSame(grid[2], screen.focused());
        screen.keyPressed(key(UiKey.LEFT));
        assertSame(grid[2], screen.focused());
        screen.keyPressed(key(UiKey.UP));
        assertSame(grid[0], screen.focused());
    }

    @Test
    void enterActivatesTheFocusedNodeAndEscapeIsLeftToVanilla() {
        Button button = new Button("b", "B", () -> events.add("pressed"));
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                button.place(new UiRect(0, 0, 40, 20), metrics));
        screen.root().add(button);
        open(screen);

        assertFalse(screen.keyPressed(key(UiKey.ENTER)));
        screen.focus(button);
        assertTrue(screen.keyPressed(key(UiKey.ENTER)));
        assertTrue(screen.keyPressed(key(UiKey.SPACE)));
        assertEquals(List.of("pressed", "pressed"), events);
        assertFalse(screen.keyPressed(key(UiKey.ESCAPE)));
    }

    @Test
    void disabledNodesTakeNeitherFocusNorPresses() {
        Button button = new Button("b", "B", () -> events.add("pressed"));
        button.setEnabled(false);
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                button.place(new UiRect(0, 0, 40, 20), metrics));
        screen.root().add(button);
        open(screen);
        screen.mouseClicked(TestScreen.left(5, 5));
        screen.keyPressed(key(UiKey.TAB));
        assertNull(screen.focused());
        assertTrue(events.isEmpty());
    }

    @Test
    void modalOverlayBlocksTheScreenAndEscapeCancelsIt() {
        Button behind = new Button("behind", "Behind", () -> events.add("behind"));
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                behind.place(new UiRect(0, 0, 427, 240), metrics));
        screen.root().add(behind);
        open(screen);
        ConfirmOverlay confirm = new ConfirmOverlay("confirm", "Reset?", "This cannot be undone.", "Reset", "Cancel",
                Button.Style.DESTRUCTIVE, () -> events.add("confirmed"), () -> events.add("cancelled"));
        screen.openOverlay(confirm);
        screen.render(draw, 0, 0, 0.0F);

        assertSame(confirm.cancelButton(), screen.focused());
        assertTrue(screen.mouseClicked(TestScreen.left(2, 2)));
        assertTrue(events.isEmpty());
        assertTrue(screen.keyPressed(key(UiKey.ESCAPE)));
        assertEquals(List.of("cancelled"), events);
        assertTrue(screen.overlays().isEmpty());
        assertTrue(draw.calls().contains("nextLayer"));
    }

    @Test
    void confirmButtonClosesTheOverlayAndRestoresFocus() {
        Button behind = new Button("behind", "Behind", () -> {
        });
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                behind.place(new UiRect(0, 0, 40, 20), metrics));
        screen.root().add(behind);
        open(screen);
        screen.focus(behind);
        ConfirmOverlay confirm = new ConfirmOverlay("confirm", "Learn?", "Spend 3 knowledge.", "Learn", "Cancel", Button.Style.PRIMARY,
                () -> events.add("confirmed"), () -> events.add("cancelled"));
        screen.openOverlay(confirm);
        screen.render(draw, 0, 0, 0.0F);
        assertSame(confirm.confirmButton(), screen.focused());
        screen.mouseClicked(TestScreen.center(confirm.confirmButton()));
        assertEquals(List.of("confirmed"), events);
        assertSame(behind, screen.focused());
    }

    @Test
    void popoverClosesOnAnOutsideClickWithoutPassingItThrough() {
        Button behind = new Button("behind", "Behind", () -> events.add("behind"));
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                behind.place(new UiRect(0, 200, 40, 20), metrics));
        screen.root().add(behind);
        open(screen);
        Popover popover = new Popover("pop", "Edit", new UiRect(100, 50, 20, 20), 120, new TextField("field", 10));
        popover.setOnDismiss(() -> events.add("dismissed"));
        screen.openOverlay(popover);
        screen.render(draw, 0, 0, 0.0F);

        assertSame(popover.content(), screen.focused());
        assertTrue(screen.mouseClicked(TestScreen.left(10, 210)));
        assertEquals(List.of("dismissed"), events);
        assertTrue(screen.overlays().isEmpty());
    }

    @Test
    void popoverPlacementStaysOnScreen() {
        assertEquals(new UiRect(50, 72, 120, 40), Popover.place(new UiRect(100, 50, 20, 20), 120, 40, 427, 240));
        assertEquals(new UiRect(100, 178, 120, 40), Popover.place(new UiRect(150, 220, 20, 20), 120, 40, 427, 240));
        assertEquals(new UiRect(303, 72, 120, 40), Popover.place(new UiRect(400, 50, 20, 20), 120, 40, 427, 240));
    }

    @Test
    void scrollEventsBubbleFromTheHoveredNode() {
        UiGroup content = new UiGroup("content", UiLayoutPolicy.column(0));
        for (int index = 0; index < 20; index++) {
            content.add(new Label("l" + index, "Line " + index));
        }
        ScrollArea area = new ScrollArea("scroll", content);
        TestScreen screen = new TestScreen(clock.motion(), (UiRect rect, art.arcane.adapt.client.ui.UiDraw metrics) ->
                area.place(new UiRect(0, 0, 100, 50), metrics));
        screen.root().add(area);
        open(screen);
        assertTrue(screen.mouseScrolled(new UiScrollEvent(10, 10, 0, -1)));
        assertEquals(ScrollArea.WHEEL_STEP, area.scroll());
    }

    @Test
    void openTransitionSlidesTheTreeInOnceAndReducedMotionSkipsIt() {
        Label label = new Label("l", "hi");
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                label.place(new UiRect(area.x(), area.y(), 50, 10), metrics));
        screen.root().add(label);
        host.open(screen);
        screen.render(draw, 0, 0, 0.0F);
        assertEquals(KitScreen.OPEN_DISTANCE, label.bounds().y());
        clock.advance(KitScreen.OPEN_MILLIS / 2);
        screen.render(draw, 0, 0, 0.0F);
        assertTrue(label.bounds().y() > 0 && label.bounds().y() < KitScreen.OPEN_DISTANCE);
        clock.advance(KitScreen.OPEN_MILLIS);
        screen.render(draw, 0, 0, 0.0F);
        assertEquals(0, label.bounds().y());

        Label still = new Label("s", "hi");
        TestScreen reduced = new TestScreen(new UiMotion(clock, true), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                still.place(new UiRect(area.x(), area.y(), 50, 10), metrics));
        reduced.root().add(still);
        host.open(reduced);
        reduced.render(draw, 0, 0, 0.0F);
        assertEquals(0, still.bounds().y());
    }

    @Test
    void hoveredTooltipRendersOnItsOwnLayerAndNotWhilePressed() {
        Button button = new Button("b", "B", () -> {
        });
        button.setTooltip(new Tooltip("§bName", List.of("§7Lore")));
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                button.place(new UiRect(10, 10, 40, 20), metrics));
        screen.root().add(button);
        open(screen);
        draw.calls().clear();
        screen.render(draw, 20, 20, 0.0F);
        assertTrue(draw.calls().contains("nextLayer"));
        assertTrue(draw.calls().stream().anyMatch((String call) -> call.contains("§bName")));
        screen.mouseClicked(TestScreen.left(20, 20));
        draw.calls().clear();
        screen.render(draw, 20, 20, 0.0F);
        assertFalse(draw.calls().stream().anyMatch((String call) -> call.contains("§bName")));
    }

    @Test
    void tooltipFlipsLeftNearTheRightEdgeAndStaysOnScreen() {
        assertEquals(new UiRect(112, 88, 80, 30), Tooltip.place(80, 30, 100, 100, 427, 240));
        assertEquals(new UiRect(308, 88, 80, 30), Tooltip.place(80, 30, 400, 100, 427, 240));
        assertEquals(new UiRect(112, 206, 80, 30), Tooltip.place(80, 30, 100, 235, 427, 240));
        assertEquals(new UiRect(4, 4, 80, 30), Tooltip.place(80, 30, 2, 2, 90, 240));
    }

    @Test
    void focusSurvivesReplacingANodeWithTheSameId() {
        UiGroup holder = new UiGroup("holder", UiLayoutPolicy.column(0));
        Button first = holder.add(new Button("same", "A", () -> {
        }));
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                holder.place(new UiRect(0, 0, 100, 100), metrics));
        screen.root().add(holder);
        open(screen);
        screen.focus(first);
        Button second = new Button("same", "B", () -> {
        });
        holder.replace(first, second);
        screen.render(draw, 0, 0, 0.0F);
        assertSame(second, screen.focused());
    }

    @Test
    void probeIsPublishedAndWithdrawnWithTheScreen() {
        Button button = new Button("b", "Go", () -> {
        });
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                button.place(new UiRect(10, 10, 40, 20), metrics));
        screen.root().add(button);
        open(screen);
        String snapshot = art.arcane.adapt.client.qa.UiProbe.snapshot();
        assertTrue(snapshot.contains("\"screen\":\"kit\""), snapshot);
        assertTrue(snapshot.contains("\"id\":\"b\""), snapshot);
        host.close();
        assertTrue(art.arcane.adapt.client.qa.UiProbe.snapshot().startsWith("{\"screen\":null"));
    }
}
