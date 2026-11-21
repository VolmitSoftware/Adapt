package art.arcane.adapt.client;

import java.util.function.Consumer;

/** Source of key binding presses since the last poll. */
public interface ClientKeys {
    void poll(Consumer<AdaptKey> pressed);
}
