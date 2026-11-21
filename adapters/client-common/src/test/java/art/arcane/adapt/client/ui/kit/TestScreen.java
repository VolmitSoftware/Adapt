package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseEvent;

import java.util.function.BiConsumer;

/** A kit screen whose layout is a lambda, attached to a {@link FakeHost} and laid out by a first render. */
public final class TestScreen extends KitScreen {
    private final BiConsumer<UiRect, UiDraw> layout;

    public TestScreen(UiMotion motion, BiConsumer<UiRect, UiDraw> layout) {
        super("Test", UiTheme.DEFAULT, motion);
        this.layout = layout;
    }

    @Override
    protected void layoutScreen(UiRect screen, UiDraw metrics) {
        layout.accept(screen, metrics);
    }

    public static UiMouseEvent left(double x, double y) {
        return new UiMouseEvent(x, y, UiMouseButton.LEFT, UiModifiers.NONE);
    }

    public static UiMouseEvent press(double x, double y, UiMouseButton button, UiModifiers modifiers) {
        return new UiMouseEvent(x, y, button, modifiers);
    }

    public static UiMouseEvent center(UiNode node) {
        return left(node.bounds().centerX(), node.bounds().centerY());
    }
}
