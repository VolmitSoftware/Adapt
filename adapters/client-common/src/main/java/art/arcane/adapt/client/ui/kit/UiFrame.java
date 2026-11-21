package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

/**
 * Everything a widget needs to paint one frame.
 *
 * @param hovered      the topmost node under the pointer, or null
 * @param focusVisible whether focus rings show (after keyboard use, like a browser's :focus-visible)
 */
public record UiFrame(UiDraw draw, UiTheme theme, UiMotion motion, int mouseX, int mouseY, UiNode hovered, UiNode focused,
                      UiNode pressed, boolean focusVisible) {
    public boolean isHovered(UiNode node) {
        return hovered == node;
    }

    public boolean isFocused(UiNode node) {
        return focused == node;
    }

    public boolean showsFocus(UiNode node) {
        return focusVisible && focused == node;
    }

    public boolean isPressed(UiNode node) {
        return pressed == node;
    }
}
