package art.arcane.adapt.pack.bake;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The locales the 26.x clients ship, read from {@code design/languages.json}, which {@code LanguageIndex} writes from
 * the clients' asset indexes. The pack blanks the client's "Inventory" label in each of them, since pack
 * pages use the player rows as page and nothing may print over them.
 */
final class Languages {
    static final String RESOURCE = "/art/arcane/adapt/pack/design/languages.json";
    /** The translation key of the label the client writes over the player rows of a container. */
    static final String INVENTORY_LABEL = "container.inventory";

    private Languages() {
    }

    /** The locale codes ({@code en_us} ...) of the pack's clients. */
    static List<String> locales() {
        try (InputStream raw = Languages.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> locales = new ArrayList<>();
            for (JsonElement locale : root.getAsJsonArray("locales")) {
                locales.add(locale.getAsString());
            }
            return locales;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
