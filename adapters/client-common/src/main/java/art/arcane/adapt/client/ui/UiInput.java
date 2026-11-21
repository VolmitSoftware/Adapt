package art.arcane.adapt.client.ui;

/** Current pointer and modifier state, in scaled GUI units. */
public interface UiInput {
    double mouseX();

    double mouseY();

    boolean isMouseDown(UiMouseButton button);

    UiModifiers modifiers();
}
