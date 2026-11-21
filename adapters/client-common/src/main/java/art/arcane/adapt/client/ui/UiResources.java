package art.arcane.adapt.client.ui;

import java.io.IOException;
import java.io.InputStream;

/** The client's resource packs as {@link UiSpriteCache} reads them. */
public interface UiResources {
    UiResources NONE = new UiResources() {
        @Override
        public InputStream open(String id) {
            return null;
        }

        @Override
        public int revision() {
            return 0;
        }
    };

    /** Opens the top-most pack's file for an id such as {@code adapt:textures/gui/skill/agility.png}; null when no pack has it. */
    InputStream open(String id) throws IOException;

    /** A value that changes whenever the client reloads its resource packs. */
    int revision();
}
