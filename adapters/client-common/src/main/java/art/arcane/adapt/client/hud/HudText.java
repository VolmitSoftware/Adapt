package art.arcane.adapt.client.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Translated HUD strings, looked up once and kept until {@link #refresh()} (the language may change between joins). */
public final class HudText {
    private final UnaryOperator<String> translator;
    private final Map<String, String> cache = new HashMap<>();

    public HudText(UnaryOperator<String> translator) {
        this.translator = Objects.requireNonNull(translator, "translator");
    }

    public String get(String key) {
        String value = cache.get(key);
        if (value == null) {
            value = translator.apply(key);
            cache.put(key, value == null ? key : value);
        }
        return value == null ? key : value;
    }

    public void refresh() {
        cache.clear();
    }
}
