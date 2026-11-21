package art.arcane.adapt.clientui;

import com.google.gson.JsonObject;

final class Json {
    private Json() {
    }

    static String string(JsonObject input, String key, String fallback) {
        return input.has(key) && !input.get(key).isJsonNull() ? input.get(key).getAsString() : fallback;
    }

    static long integer(JsonObject input, String key, long fallback) {
        return input.has(key) && !input.get(key).isJsonNull() ? input.get(key).getAsLong() : fallback;
    }

    static double number(JsonObject input, String key) {
        if (!input.has(key) || input.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return input.get(key).getAsDouble();
    }
}
