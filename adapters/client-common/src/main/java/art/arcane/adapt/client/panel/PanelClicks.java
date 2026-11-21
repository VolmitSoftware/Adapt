package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.platform.menu.ClickKind;

/**
 * Maps mouse buttons and keys on a tile to the {@link ClickKind} a vanilla chest screen would send for the same input
 * on the same slot, so server handlers cannot tell the two apart. Keys use the vanilla default bindings: 1-9 hotbar
 * swap, Q drop (Ctrl/Cmd for the whole stack), F offhand swap.
 */
public final class PanelClicks {
    /** Vanilla's double-click window. */
    public static final long DOUBLE_CLICK_MILLIS = 250L;

    private int lastSlot = -1;
    private long lastTime;
    private UiMouseButton lastButton;
    private boolean pendingDouble;

    public static ClickKind mouse(UiMouseButton button, UiModifiers modifiers) {
        return switch (button) {
            case LEFT -> modifiers.shift() ? ClickKind.SHIFT_LEFT : ClickKind.LEFT;
            case RIGHT -> modifiers.shift() ? ClickKind.SHIFT_RIGHT : ClickKind.RIGHT;
            case MIDDLE -> ClickKind.MIDDLE;
            case OTHER -> null;
        };
    }

    /** The click a key press over a slot sends, or null when the key does nothing there. */
    public static ClickKind key(UiKey key, UiModifiers modifiers) {
        return switch (key) {
            case DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9 -> ClickKind.NUMBER_KEY;
            case Q -> modifiers.shortcut() ? ClickKind.CONTROL_DROP : ClickKind.DROP;
            case F -> ClickKind.SWAP_OFFHAND;
            default -> null;
        };
    }

    /**
     * A press on {@code slot} at {@code now} milliseconds: the click to send right away, or null. Remembers the press
     * so a second left press on the same slot inside {@link #DOUBLE_CLICK_MILLIS} becomes a double click on release.
     */
    public ClickKind press(int slot, UiMouseEvent event, long now) {
        pendingDouble = lastSlot == slot && lastButton == event.button() && now - lastTime < DOUBLE_CLICK_MILLIS;
        lastSlot = slot;
        lastTime = now;
        lastButton = event.button();
        return mouse(event.button(), event.modifiers());
    }

    /** A release on {@code slot}: {@link ClickKind#DOUBLE_CLICK} when it ends a plain left double click, else null. */
    public ClickKind release(int slot, UiMouseEvent event) {
        boolean doubled = pendingDouble && slot == lastSlot && event.button() == UiMouseButton.LEFT && !event.modifiers().shift();
        if (event.button() == lastButton) {
            pendingDouble = false;
        }
        if (!doubled) {
            return null;
        }
        lastSlot = -1;
        return ClickKind.DOUBLE_CLICK;
    }
}
