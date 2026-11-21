package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiModifiers;

/** What a widget can ask of the screen it lives in. Detached widgets get {@link #DETACHED}, which does nothing. */
public interface UiServices {
    UiServices DETACHED = new UiServices() {
        @Override
        public UiTheme theme() {
            return UiTheme.DEFAULT;
        }

        @Override
        public UiMotion motion() {
            return new UiMotion(() -> 0L, true);
        }

        @Override
        public UiModifiers modifiers() {
            return UiModifiers.NONE;
        }

        @Override
        public void playClick() {
        }

        @Override
        public String clipboard() {
            return "";
        }

        @Override
        public void setClipboard(String text) {
        }

        @Override
        public UiNode focused() {
            return null;
        }

        @Override
        public void focus(UiNode node) {
        }

        @Override
        public void openOverlay(UiOverlay overlay) {
        }

        @Override
        public void closeOverlay(UiOverlay overlay) {
        }

        @Override
        public void requestLayout() {
        }
    };

    UiTheme theme();

    UiMotion motion();

    /** Modifier keys held right now. */
    UiModifiers modifiers();

    void playClick();

    String clipboard();

    void setClipboard(String text);

    UiNode focused();

    /** Moves keyboard focus; null clears it. Nodes that cannot take focus are ignored. */
    void focus(UiNode node);

    void openOverlay(UiOverlay overlay);

    void closeOverlay(UiOverlay overlay);

    /** Lays the screen out again before the next frame, for widgets whose preferred size changed. */
    void requestLayout();
}
