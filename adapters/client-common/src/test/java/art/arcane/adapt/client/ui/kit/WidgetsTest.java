package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiScrollEvent;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetsTest {
    private static final UiModifiers SHIFT = new UiModifiers(true, false, false, false);

    private final ManualClock clock = new ManualClock();
    private final FakeHost host = new FakeHost(427, 240);
    private final KitDraw draw = new KitDraw();

    private TestScreen show(UiNode node, UiRect bounds) {
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) -> node.place(bounds, metrics));
        screen.root().add(node);
        host.open(screen);
        clock.advance(KitScreen.OPEN_MILLIS);
        screen.render(draw, 0, 0, 0.0F);
        return screen;
    }

    @Test
    void toggleFlipsOnClickAndKeyboardAndReportsTheValue() {
        List<Boolean> values = new ArrayList<>();
        Toggle toggle = new Toggle("t", "Sound", false, values::add);
        TestScreen screen = show(toggle, new UiRect(0, 0, 100, 20));
        screen.mouseClicked(TestScreen.center(toggle));
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, UiModifiers.NONE));
        assertEquals(List.of(true, false), values);
        assertEquals(2, host.clicks());
        toggle.setOn(true);
        assertTrue(toggle.on());
        assertEquals(List.of(true, false), values);
    }

    @Test
    void sliderSnapsClampsAndSteps() {
        List<Double> values = new ArrayList<>();
        Slider slider = new Slider("s", 0, 10, 0.5, 3.3, (double value) -> "Volume: " + value, values::add);
        TestScreen screen = show(slider, new UiRect(0, 0, 106, 20));
        assertEquals(3.5, slider.value());
        screen.focus(slider);
        screen.keyPressed(new UiKeyEvent(UiKey.RIGHT, UiModifiers.NONE));
        assertEquals(4.0, slider.value());
        screen.keyPressed(new UiKeyEvent(UiKey.RIGHT, SHIFT));
        assertEquals(9.0, slider.value());
        screen.keyPressed(new UiKeyEvent(UiKey.END, UiModifiers.NONE));
        assertEquals(10.0, slider.value());
        screen.mouseClicked(TestScreen.left(3, 10));
        assertEquals(0.0, slider.value());
        screen.mouseDragged(TestScreen.left(53, 10), 50, 0);
        assertEquals(5.0, slider.value());
        assertTrue(slider.mouseScrolled(new UiScrollEvent(0, 0, 0, -1)));
        assertEquals(4.5, slider.value());
        assertEquals(List.of(4.0, 9.0, 10.0, 0.0, 5.0, 4.5), values);
    }

    @Test
    void tabsSelectByClickAndArrows() {
        List<Integer> selected = new ArrayList<>();
        Tabs tabs = new Tabs("tabs", List.of("Skills", "Stats", "Settings"), 0, selected::add);
        TestScreen screen = show(tabs, new UiRect(0, 0, 90, 20));
        screen.mouseClicked(TestScreen.left(75, 5));
        assertEquals(2, tabs.selected());
        screen.keyPressed(new UiKeyEvent(UiKey.LEFT, UiModifiers.NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.LEFT, UiModifiers.NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.LEFT, UiModifiers.NONE));
        assertEquals(List.of(2, 1, 0), selected);
        assertEquals(-1, tabs.tabAt(200));
    }

    @Test
    void rangedStepperClampsStepsAndRevertsBadInput() {
        List<Integer> values = new ArrayList<>();
        NumberStepper stepper = NumberStepper.ranged("n", 0, 20, 5, values::add);
        TestScreen screen = show(stepper, new UiRect(0, 0, 120, 20));
        screen.mouseClicked(TestScreen.center(stepper.plus()));
        host.setModifiers(SHIFT);
        screen.mouseClicked(TestScreen.center(stepper.plus()));
        host.setModifiers(UiModifiers.NONE);
        assertEquals("16", stepper.value());
        screen.mouseClicked(TestScreen.center(stepper.field()));
        stepper.field().setText("abc");
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, UiModifiers.NONE));
        assertEquals("16", stepper.value());
        stepper.field().setText("99");
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, UiModifiers.NONE));
        assertEquals("20", stepper.value());
        assertEquals(List.of(6, 16, 20), values);
    }

    @Test
    void stepperKeepsTypedTextWhileTheUserEdits() {
        NumberStepper stepper = new NumberStepper("n", "3", new NumberStepper.Listener() {
            @Override
            public void step(int direction, UiModifiers modifiers) {
            }

            @Override
            public void commit(String text) {
            }
        });
        TestScreen screen = show(stepper, new UiRect(0, 0, 120, 20));
        screen.focus(stepper.field());
        stepper.field().insert("4");
        stepper.setValue("7");
        assertEquals("34", stepper.value());
        screen.focus(null);
        stepper.setValue("7");
        assertEquals("7", stepper.value());
    }

    @Test
    void progressEasesTowardNewValuesAndJumpsWhenReduced() {
        ProgressBar bar = new ProgressBar("p", 0.0F);
        show(bar, new UiRect(0, 0, 102, 6));
        bar.setProgress(1.0F);
        draw.calls().clear();
        bar.render(new UiFrame(draw, UiTheme.DEFAULT, clock.motion(), 0, 0, null, null, null, false));
        String full = "fill 1,1,101,5 " + Integer.toHexString(ProgressBar.fillColor(UiTheme.DEFAULT.accent(), UiTheme.DEFAULT));
        assertFalse(draw.calls().contains(full));
        clock.advance(1000);
        bar.render(new UiFrame(draw, UiTheme.DEFAULT, clock.motion(), 0, 0, null, null, null, false));
        assertTrue(draw.calls().contains(full), draw.calls().toString());
        assertEquals(1.0F, bar.progress());
    }

    @Test
    void iconTileDrawsGlintCustomModelAndDecorationsAboveTheItem() {
        IconTile tile = new IconTile("tile", new IconRef("minecraft:paper", 1001, null, null), "Paper");
        tile.setAmount(12).setGlint(true).setSwitch(true).setLocked(true).setBadge("3", 0);
        show(tile, new UiRect(10, 10, 20, 20));
        List<String> calls = draw.calls();
        int item = calls.indexOf("item minecraft:paper cmd=1001 glint=true 12,12");
        int depth = calls.indexOf("depth 200.0");
        int amount = calls.indexOf("text 17,22 12");
        assertTrue(item >= 0 && depth > item && amount > depth, calls.toString());
    }

    @Test
    void largeTilesDrawTheIconAtDoubleSize() {
        IconTile tile = new IconTile("tile", IconRef.item("minecraft:stone"), "Stone");
        show(tile, new UiRect(0, 0, 40, 40));
        assertTrue(draw.calls().contains("scale 2.0,2.0"));
        assertTrue(draw.calls().contains("item minecraft:stone cmd=0 glint=false 0,0"));
    }

    @Test
    void iconTileForwardsEveryButtonButLockedTilesIgnoreThem() {
        List<String> seen = new ArrayList<>();
        IconTile tile = new IconTile("tile", IconRef.item("minecraft:stone"), "Stone");
        tile.setListener(new IconTile.Listener() {
            @Override
            public void pressed(IconTile pressed, UiMouseEvent event) {
                seen.add("press " + event.button());
            }

            @Override
            public void activated(IconTile activated) {
                seen.add("activate");
            }
        });
        TestScreen screen = show(tile, new UiRect(0, 0, 20, 20));
        screen.mouseClicked(TestScreen.press(5, 5, UiMouseButton.RIGHT, UiModifiers.NONE));
        screen.mouseClicked(TestScreen.press(5, 5, UiMouseButton.MIDDLE, UiModifiers.NONE));
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, UiModifiers.NONE));
        tile.setLocked(true);
        screen.mouseClicked(TestScreen.left(5, 5));
        assertEquals(List.of("press RIGHT", "press MIDDLE", "activate"), seen);
    }

    @Test
    void labelsTruncateWithAnEllipsisWithoutBreakingColourCodes() {
        assertEquals("§aabc...", LegacyText.truncate(draw, "§aabcdefgh", 36));
        assertEquals("short", LegacyText.truncate(draw, "short", 36));
        assertEquals("", LegacyText.truncate(draw, "abcdef", 12));
        assertEquals("Name", LegacyText.strip("§b§lName"));
        assertTrue(LegacyText.isBlank("§7 "));
        Label label = new Label("l", "§aabcdefgh");
        show(label, new UiRect(0, 0, 36, 9));
        assertTrue(draw.calls().contains("text 0,1 §aabc..."), draw.calls().toString());
    }

    @Test
    void wrappingLabelsReportTheirHeight() {
        Label label = new Label("l", "aaaaaaaaaa").setWrap(true);
        assertEquals(18, label.preferredHeight(30, draw));
        assertEquals(9, new Label("m", "aaaaaaaaaa").preferredHeight(30, draw));
    }

    @Test
    void hoverFadeEasesAndSnapsUnderReducedMotion() {
        UiFade fade = new UiFade(100);
        UiMotion motion = clock.motion();
        assertEquals(0.0F, fade.update(motion, true));
        clock.advance(50);
        float half = fade.update(motion, true);
        assertTrue(half > 0.5F && half < 1.0F);
        clock.advance(100);
        assertEquals(1.0F, fade.update(motion, true));
        assertEquals(0.0F, new UiFade(100).update(new UiMotion(clock, true), false));
        assertEquals(1.0F, new UiFade(100).update(new UiMotion(clock, true), true));
        assertEquals(0xFF808080, UiEase.mix(0xFF000000, 0xFFFFFFFF, 0.5F));
        assertEquals(0x80FFFFFF, UiEase.fade(0xFFFFFFFF, 0.5F));
    }
}
