package art.arcane.adapt.client.ui;

import java.util.Objects;

/** A texture file such as {@code adapt:textures/gui/panel.png} and its size in pixels. */
public record UiTexture(String id, int width, int height) {
    public UiTexture {
        Objects.requireNonNull(id, "id");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("texture size must be positive: " + width + "x" + height);
        }
    }
}
