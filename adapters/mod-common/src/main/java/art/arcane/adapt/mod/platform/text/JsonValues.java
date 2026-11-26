package art.arcane.adapt.mod.platform.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON text to and from plain Java values: maps, lists, strings, numbers, booleans and null. The mod jar relocates its
 * Gson, so a Gson tree built here is not the type Minecraft's JSON codecs read; plain values cross that boundary instead.
 */
final class JsonValues {
    private JsonValues() {
    }

    /** The value {@code json} holds; throws a {@link com.google.gson.JsonParseException} when it is not JSON. */
    static Object read(String json) {
        return value(JsonParser.parseString(json));
    }

    /** {@code value} as compact JSON; throws {@link IllegalArgumentException} for a value JSON cannot hold. */
    static String write(Object value) {
        return element(value).toString();
    }

    private static Object value(JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), value(entry.getValue()));
            }
            return map;
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement entry : element.getAsJsonArray()) {
                list.add(value(entry));
            }
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsNumber();
        }
        return primitive.getAsString();
    }

    private static JsonElement element(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object key is not a string: " + entry.getKey());
                }
                object.add(key, element(entry.getValue()));
            }
            return object;
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object entry : list) {
                array.add(element(entry));
            }
            return array;
        }
        if (value instanceof String text) {
            return new JsonPrimitive(text);
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (value instanceof Boolean flag) {
            return new JsonPrimitive(flag);
        }
        throw new IllegalArgumentException("Not a JSON value: " + value.getClass().getName());
    }
}
