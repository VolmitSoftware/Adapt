package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.platform.menu.ClickKind;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PanelClicksTest {
    private static final UiModifiers NONE = UiModifiers.NONE;
    private static final UiModifiers SHIFT = new UiModifiers(true, false, false, false);
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    private static final UiModifiers SHORTCUT = MAC ? new UiModifiers(false, false, false, true) : new UiModifiers(false, true, false, false);

    private static UiMouseEvent mouse(UiMouseButton button, UiModifiers modifiers) {
        return new UiMouseEvent(0, 0, button, modifiers);
    }

    @Test
    void mouseButtonsMapLikeAChestClick() {
        assertEquals(ClickKind.LEFT, PanelClicks.mouse(UiMouseButton.LEFT, NONE));
        assertEquals(ClickKind.RIGHT, PanelClicks.mouse(UiMouseButton.RIGHT, NONE));
        assertEquals(ClickKind.SHIFT_LEFT, PanelClicks.mouse(UiMouseButton.LEFT, SHIFT));
        assertEquals(ClickKind.SHIFT_RIGHT, PanelClicks.mouse(UiMouseButton.RIGHT, SHIFT));
        assertEquals(ClickKind.MIDDLE, PanelClicks.mouse(UiMouseButton.MIDDLE, SHIFT));
        assertNull(PanelClicks.mouse(UiMouseButton.OTHER, NONE));
    }

    @Test
    void keysMapToHotbarDropAndOffhandClicks() {
        for (UiKey key : new UiKey[]{UiKey.DIGIT_1, UiKey.DIGIT_5, UiKey.DIGIT_9}) {
            assertEquals(ClickKind.NUMBER_KEY, PanelClicks.key(key, NONE));
        }
        assertNull(PanelClicks.key(UiKey.DIGIT_0, NONE));
        assertEquals(ClickKind.DROP, PanelClicks.key(UiKey.Q, NONE));
        assertEquals(ClickKind.CONTROL_DROP, PanelClicks.key(UiKey.Q, SHORTCUT));
        assertEquals(ClickKind.SWAP_OFFHAND, PanelClicks.key(UiKey.F, NONE));
        assertNull(PanelClicks.key(UiKey.E, NONE));
        assertNull(PanelClicks.key(UiKey.ENTER, NONE));
    }

    @Test
    void secondLeftPressOnTheSameSlotInTimeIsADoubleClickOnRelease() {
        PanelClicks clicks = new PanelClicks();
        UiMouseEvent left = mouse(UiMouseButton.LEFT, NONE);
        assertEquals(ClickKind.LEFT, clicks.press(3, left, 1000));
        assertNull(clicks.release(3, left));
        assertEquals(ClickKind.LEFT, clicks.press(3, left, 1100));
        assertEquals(ClickKind.DOUBLE_CLICK, clicks.release(3, left));
        assertEquals(ClickKind.LEFT, clicks.press(3, left, 1200));
        assertNull(clicks.release(3, left));
    }

    @Test
    void slowOtherSlotOtherButtonOrShiftDoubleClicksAreNotDoubles() {
        PanelClicks clicks = new PanelClicks();
        UiMouseEvent left = mouse(UiMouseButton.LEFT, NONE);
        clicks.press(3, left, 1000);
        clicks.release(3, left);
        clicks.press(3, left, 1000 + PanelClicks.DOUBLE_CLICK_MILLIS);
        assertNull(clicks.release(3, left));
        clicks.press(4, left, 2000);
        clicks.release(4, left);
        clicks.press(5, left, 2050);
        assertNull(clicks.release(5, left));
        UiMouseEvent right = mouse(UiMouseButton.RIGHT, NONE);
        clicks.press(6, right, 3000);
        clicks.release(6, right);
        clicks.press(6, right, 3050);
        assertNull(clicks.release(6, right));
        UiMouseEvent shift = mouse(UiMouseButton.LEFT, SHIFT);
        clicks.press(7, shift, 4000);
        clicks.release(7, shift);
        assertEquals(ClickKind.SHIFT_LEFT, clicks.press(7, shift, 4050));
        assertNull(clicks.release(7, shift));
    }
}
