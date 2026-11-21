package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules every control shares: text position, state shapes, focus ring, hit areas and focus order. */
class ControlFamilyTest {
    private static final UiTheme THEME = UiTheme.DEFAULT;
    private static final UiRect BOX = new UiRect(20, 30, 90, 14);

    private final ManualClock clock = new ManualClock();
    private final FakeHost host = new FakeHost(427, 240);

    private TestScreen show(UiNode node, UiRect bounds) {
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, UiDraw metrics) -> node.place(bounds, metrics));
        screen.root().add(node);
        host.open(screen);
        clock.advance(KitScreen.OPEN_MILLIS);
        screen.render(new KitDraw(), 0, 0, 0.0F);
        return screen;
    }

    /** What {@code node} draws once its highlights have settled, with colours dropped: shapes and text positions only. */
    private List<String> shapes(UiNode node, UiNode hovered, UiNode focused, UiNode pressed, boolean focusVisible) {
        node.render(new UiFrame(new KitDraw(), THEME, clock.motion(), 90, 35, hovered, focused, pressed, focusVisible));
        clock.advance(500);
        KitDraw draw = new KitDraw();
        node.render(new UiFrame(draw, THEME, clock.motion(), 90, 35, hovered, focused, pressed, focusVisible));
        List<String> shapes = new ArrayList<>();
        for (String call : draw.calls()) {
            shapes.add(call.startsWith("fill ") ? call.substring(0, call.lastIndexOf(' ')) : call);
        }
        return shapes;
    }

    private static Map<String, Supplier<UiNode>> controls() {
        Map<String, Supplier<UiNode>> controls = new LinkedHashMap<>();
        controls.put("button", () -> new Button("b", "Learn", () -> {
        }));
        controls.put("primary", () -> new Button("b", "Learn", Button.Style.PRIMARY, () -> {
        }));
        controls.put("destructive", () -> new Button("b", "Unlearn", Button.Style.DESTRUCTIVE, () -> {
        }));
        controls.put("quiet", () -> new Button("b", "Reset", Button.Style.QUIET, () -> {
        }));
        controls.put("glyph", () -> new Button("b", UiTome.Glyph.BACK, () -> {
        }));
        controls.put("toggle", () -> new Toggle("t", "Sound", true, (Boolean on) -> {
        }));
        controls.put("field", () -> new TextField("f", 32).setText("Steve"));
        controls.put("slider", () -> new Slider("s", 0, 10, 1, 4, (double value) -> "4", (double value) -> {
        }));
        controls.put("tabs", () -> new Tabs("tabs", List.of("One", "Two"), 0, (int index) -> {
        }));
        return controls;
    }

    @Test
    void restHoverPressAndDisabledDifferInShapeNotOnlyColour() {
        for (Map.Entry<String, Supplier<UiNode>> entry : controls().entrySet()) {
            UiNode node = entry.getValue().get();
            show(node, BOX);
            List<String> rest = shapes(node, null, null, null, false);
            List<String> hover = shapes(node, node, null, null, false);
            List<String> pressed = shapes(node, node, null, node, false);
            node.setEnabled(false);
            List<String> disabled = shapes(node, null, null, null, false);
            assertNotEquals(rest, hover, entry.getKey() + " hover");
            assertNotEquals(rest, disabled, entry.getKey() + " disabled");
            assertNotEquals(hover, disabled, entry.getKey() + " hover/disabled");
            if (!entry.getKey().equals("field")) {
                assertNotEquals(hover, pressed, entry.getKey() + " pressed");
            }
        }
    }

    @Test
    void keyboardFocusAddsARingOutsideTheControl() {
        for (Map.Entry<String, Supplier<UiNode>> entry : controls().entrySet()) {
            if (entry.getKey().equals("toggle") || entry.getKey().equals("tabs")) {
                continue;
            }
            UiNode node = entry.getValue().get();
            show(node, BOX);
            List<String> focused = shapes(node, null, node, null, true);
            assertTrue(focused.contains("fill 20,29,110,30"), entry.getKey() + " top of ring: " + focused);
            assertTrue(focused.contains("fill 19,30,20,44"), entry.getKey() + " left of ring: " + focused);
            assertTrue(focused.contains("fill 110,30,111,44"), entry.getKey() + " right of ring: " + focused);
            assertTrue(focused.contains("fill 20,44,110,45"), entry.getKey() + " bottom of ring: " + focused);
        }
        Toggle toggle = new Toggle("t", "Sound", false, (Boolean on) -> {
        });
        show(toggle, BOX);
        List<String> ring = shapes(toggle, null, toggle, null, true);
        assertTrue(ring.contains("fill 99,31,109,32"), "ring hugs the checkbox: " + ring);
    }

    @Test
    void textSitsOnTheSameRowInEveryControl() {
        int row = UiControl.textY(BOX);
        assertEquals(33, row);
        Map<String, Supplier<UiNode>> textual = controls();
        textual.remove("glyph");
        textual.put("chip", () -> new StatChip("c", null, "12", ""));
        textual.put("progress", () -> new ProgressBar("p", 0.5F).setText("5/10"));
        for (Map.Entry<String, Supplier<UiNode>> entry : textual.entrySet()) {
            UiNode node = entry.getValue().get();
            show(node, BOX);
            KitDraw draw = new KitDraw();
            node.render(new UiFrame(draw, THEME, clock.motion(), 0, 0, null, null, null, false));
            List<String> text = draw.calls("text ");
            assertTrue(!text.isEmpty(), entry.getKey() + " draws text");
            for (String call : text) {
                assertEquals("33", call.substring(call.indexOf(',') + 1, call.indexOf(' ', 5)), entry.getKey() + ": " + call);
            }
        }
    }

    @Test
    void capitalsAreCentredWithTheSpareUnitBelow() {
        for (int height = UiControl.CAP_HEIGHT; height <= 24; height++) {
            int above = UiControl.textY(100, height) - 100;
            int below = height - above - UiControl.CAP_HEIGHT;
            assertTrue(below - above == 0 || below - above == 1, "height " + height + ": " + above + " above, " + below + " below");
        }
    }

    @Test
    void pressedContentSinksOneUnit() {
        Button button = new Button("b", "Learn", () -> {
        });
        show(button, BOX);
        assertTrue(shapes(button, button, null, null, false).contains("text 50,33 Learn"));
        assertTrue(shapes(button, button, null, button, false).contains("text 50,34 Learn"));
    }

    @Test
    void everythingAControlDrawsIsInsideWhereItTakesClicks() {
        Map<String, Supplier<UiNode>> all = controls();
        all.put("chip", () -> new StatChip("c", IconRef.item("minecraft:book"), "12", ""));
        for (Map.Entry<String, Supplier<UiNode>> entry : all.entrySet()) {
            UiNode node = entry.getValue().get();
            UiRect bounds = new UiRect(20, 30, 90, node instanceof StatChip ? 16 : 14);
            TestScreen screen = show(node, bounds);
            for (String shape : shapes(node, node, null, null, false)) {
                if (!shape.startsWith("fill ")) {
                    continue;
                }
                String[] edges = shape.substring(5).split(",");
                int left = Integer.parseInt(edges[0]);
                int top = Integer.parseInt(edges[1]);
                int right = Integer.parseInt(edges[2]);
                int bottom = Integer.parseInt(edges[3]);
                assertTrue(left >= bounds.x() && top >= bounds.y() && right <= bounds.right() && bottom <= bounds.bottom(),
                        entry.getKey() + " draws outside its bounds: " + shape);
            }
            assertSame(node, screen.root().nodeAt(bounds.x(), bounds.y()), entry.getKey() + " top left corner");
            assertSame(node, screen.root().nodeAt(bounds.right() - 1, bounds.bottom() - 1), entry.getKey() + " bottom right corner");
        }
    }

    @Test
    void aChipWithAnIconKeepsItsTagOneControlTall() {
        StatChip chip = new StatChip("c", IconRef.item("minecraft:book"), "12", "");
        show(chip, new UiRect(20, 30, 60, 16));
        assertEquals(new UiRect(20, 31, 60, 14), chip.tag());
        KitDraw draw = new KitDraw();
        chip.render(new UiFrame(draw, THEME, clock.motion(), 0, 0, null, null, null, false));
        assertTrue(draw.calls().stream().anyMatch((String call) -> call.startsWith("item minecraft:book") && call.endsWith(" 24,30")),
                draw.calls().toString());
        assertTrue(draw.calls().contains("text 63,34 12"), "the value ends at the right inset: " + draw.calls());
    }

    @Test
    void aTileLeavesOutACornerValueItCannotShowWhole() {
        IconTile tile = new IconTile("tile", IconRef.item("minecraft:paper"), "Paper");
        tile.setCornerText("1001");
        show(tile, new UiRect(10, 10, 18, 18));
        KitDraw small = new KitDraw();
        tile.render(new UiFrame(small, THEME, clock.motion(), 0, 0, null, null, null, false));
        assertTrue(small.calls("text ").isEmpty(), small.calls("text ").toString());
        tile.place(new UiRect(10, 10, 30, 30), small);
        KitDraw large = new KitDraw();
        tile.render(new UiFrame(large, THEME, clock.motion(), 0, 0, null, null, null, false));
        assertEquals(List.of("text 15,32 1001"), large.calls("text "));
    }

    @Test
    void aStepperTabsThroughMinusValuePlusAndRightAlignsItsDigits() {
        NumberStepper stepper = NumberStepper.ranged("n", 0, 99, 8, (int value) -> {
        });
        show(stepper, new UiRect(20, 30, 110, 14));
        assertEquals(List.of(stepper.minus(), stepper.field(), stepper.plus()), UiFocus.order(stepper));
        KitDraw draw = new KitDraw();
        stepper.render(new UiFrame(draw, THEME, clock.motion(), 0, 0, null, null, null, false));
        UiRect field = stepper.field().bounds();
        assertTrue(draw.calls().contains("text " + (field.right() - UiControl.TEXT_INSET - 1 - 6) + ",33 8"), draw.calls().toString());
    }

    @Test
    void theProgressFillStandsThreeToOneAgainstItsTrough() {
        for (int color : new int[] {THEME.accent(), 0xFFE8D44A, 0xFF9AD0F0, 0xFF2A4A18}) {
            assertTrue(UiContrast.ratio(ProgressBar.fillColor(color, THEME), THEME.tooltip()) >= UiContrast.GRAPHIC,
                    Integer.toHexString(color));
        }
    }

    @Test
    void theSlimBarShowsFillAndTrackByThickness() {
        KitDraw draw = new KitDraw();
        ProgressBar.slim(draw, THEME, 10, 20, 110, 2, 0.5F, 0xFFE8D44A);
        List<String> fills = draw.calls("fill ");
        assertTrue(fills.get(0).startsWith("fill 10,21,110,22 "), "a one unit hairline for the track: " + fills);
        assertTrue(fills.get(1).startsWith("fill 10,20,60,22 "), "the fill at full height: " + fills);
        int fill = (int) Long.parseLong(fills.get(1).substring(fills.get(1).lastIndexOf(' ') + 1), 16);
        assertTrue(UiContrast.ratio(fill, THEME.backdrop()) >= UiContrast.GRAPHIC, "fill on paper");
        assertEquals(1, ProgressBar.filled(100, 0.001F));
        assertEquals(0, ProgressBar.filled(100, 0.0F));
    }

    @Test
    void pipsShowStepsLikeTheSlimBar() {
        KitDraw draw = new KitDraw();
        ProgressBar.pips(draw, THEME, 10, 20, 6, 2, 4, 2, 0xFFE8D44A);
        List<String> fills = draw.calls("fill ");
        assertEquals(4, fills.size());
        assertTrue(fills.get(0).startsWith("fill 10,20,16,22 ") && fills.get(1).startsWith("fill 17,20,23,22 "), "filled steps at full height: " + fills);
        assertTrue(fills.get(2).startsWith("fill 24,21,30,22 ") && fills.get(3).startsWith("fill 31,21,37,22 "), "a hairline for the rest: " + fills);
        assertTrue(fills.get(2).endsWith(" " + Integer.toHexString(ProgressBar.rail(THEME))), "the slim bar's rail: " + fills);
    }

    @Test
    void everyDisabledButtonIsTheSameStitchedOutline() {
        List<List<String>> looks = new ArrayList<>();
        for (Button.Style style : List.of(Button.Style.NORMAL, Button.Style.PRIMARY, Button.Style.DESTRUCTIVE)) {
            Button button = new Button("b", "Learn", style, () -> {
            });
            show(button, BOX);
            button.setEnabled(false);
            List<String> shapes = shapes(button, null, null, null, false);
            assertTrue(shapes.stream().noneMatch((String call) -> call.startsWith("blit ")), style + " draws a texture while disabled");
            looks.add(shapes);
        }
        assertEquals(looks.get(0), looks.get(1));
        assertEquals(looks.get(0), looks.get(2));
        Button glyph = new Button("b", UiTome.Glyph.BACK, () -> {
        });
        show(glyph, new UiRect(20, 30, 14, 14));
        glyph.setEnabled(false);
        List<String> edge = shapes(glyph, null, null, null, false);
        assertTrue(edge.contains("fill 21,30,22,31") && looks.get(0).contains("fill 21,30,22,31"), "both start their stitches one unit in");
    }

    @Test
    void balancedWrappingKeepsTheLineCountAndNeverWidens() {
        KitDraw draw = new KitDraw();
        String text = "Hold shift while mid-air against a wall to latch, then release.";
        int lines = draw.wrappedHeight(text, 300);
        int balanced = UiControl.balancedWidth(draw, text, 300);
        assertTrue(balanced <= 300 && balanced >= 225, "balanced " + balanced);
        assertEquals(lines, draw.wrappedHeight(text, balanced));
        assertEquals(300, UiControl.balancedWidth(draw, "Short", 300));
    }

    @Test
    void reducedMotionShowsHoverAtOnce() {
        Button button = new Button("b", "Learn", () -> {
        });
        show(button, BOX);
        UiMotion reduced = new UiMotion(clock, true);
        KitDraw first = new KitDraw();
        button.render(new UiFrame(first, THEME, reduced, 0, 0, button, null, null, false));
        KitDraw settled = new KitDraw();
        clock.advance(500);
        button.render(new UiFrame(settled, THEME, reduced, 0, 0, button, null, null, false));
        assertEquals(settled.calls(), first.calls());
    }
}
