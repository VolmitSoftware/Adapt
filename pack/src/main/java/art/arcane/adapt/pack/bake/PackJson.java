package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * The pack's JSON written the same way every time (pretty, no HTML escaping, numbers rounded to four places), the flat
 * item model shapes every slot item uses, animated strips, and the SHA-1 hashes the cache and the client check.
 */
public final class PackJson {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private PackJson() {
    }

    static byte[] json(JsonElement element) {
        return (GSON.toJson(element) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static JsonArray vector(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            double rounded = Math.round(value * 10_000.0) / 10_000.0;
            if (rounded == Math.rint(rounded)) {
                array.add((long) rounded);
            } else {
                array.add(rounded);
            }
        }
        return array;
    }

    static JsonObject spaceProvider(Map<String, Integer> advances) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "space");
        JsonObject map = new JsonObject();
        for (Map.Entry<String, Integer> entry : advances.entrySet()) {
            map.addProperty(entry.getKey(), entry.getValue());
        }
        provider.add("advances", map);
        return provider;
    }

    /** A flat model of {@code elements} under one GUI transform. */
    static JsonObject flatModel(JsonObject textures, JsonArray elements, MotionItems.Transform transform) {
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.addProperty("gui_light", "front");
        model.add("elements", elements);
        JsonObject display = new JsonObject();
        JsonObject gui = new JsonObject();
        gui.add("rotation", vector(0, 0, 0));
        gui.add("translation", vector(transform.shiftX(), transform.shiftY(), 0));
        gui.add("scale", vector(transform.scale(), transform.scale(), transform.scale()));
        display.add("gui", gui);
        model.add("display", display);
        return model;
    }

    /** One flat element showing texture {@code name} over {@code area}, for an item in the slot at ({@code slotX}, {@code slotY}). */
    static JsonObject element(String name, MotionItems.Area area, int slotX, int slotY, MotionItems.Transform transform) {
        return element(name, area, slotX, slotY, transform, 8, false);
    }

    /** As {@link #element}, at depth {@code z}, its faces tinted by tint index 0 when {@code tinted}. */
    static JsonObject element(String name, MotionItems.Area area, int slotX, int slotY, MotionItems.Transform transform, double z, boolean tinted) {
        JsonObject element = new JsonObject();
        element.add("from", vector(transform.modelX(slotX, area.x()), transform.modelY(slotY, area.y() + area.height()), z));
        element.add("to", vector(transform.modelX(slotX, area.x() + area.width()), transform.modelY(slotY, area.y()), z));
        JsonObject faces = new JsonObject();
        faces.add("south", face(name, false));
        faces.add("north", face(name, true));
        if (tinted) {
            faces.getAsJsonObject("south").addProperty("tintindex", 0);
            faces.getAsJsonObject("north").addProperty("tintindex", 0);
        }
        element.add("faces", faces);
        return element;
    }

    private static JsonObject face(String texture, boolean back) {
        JsonObject face = new JsonObject();
        face.add("uv", back ? vector(16, 0, 0, 16) : vector(0, 0, 16, 16));
        face.addProperty("texture", "#" + texture);
        return face;
    }

    static Raster strip(LayerBake.Motion motion) {
        Raster strip = new Raster(motion.width(), motion.height() * motion.frames().size());
        for (int index = 0; index < motion.frames().size(); index++) {
            strip.paste(motion.frames().get(index), 0, index * motion.height());
        }
        return strip;
    }

    static JsonObject animation(LayerBake.Motion motion) {
        JsonObject animation = new JsonObject();
        animation.addProperty("width", motion.width());
        animation.addProperty("height", motion.height());
        animation.addProperty("frametime", 1);
        JsonArray frames = new JsonArray();
        for (int[] step : motion.sequence()) {
            JsonObject frame = new JsonObject();
            frame.addProperty("index", step[0]);
            frame.addProperty("time", step[1]);
            frames.add(frame);
        }
        animation.add("frames", frames);
        JsonObject root = new JsonObject();
        root.add("animation", animation);
        return root;
    }

    /** SHA-1 over every path and its bytes, in path order. */
    static String hash(Map<String, byte[]> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (Map.Entry<String, byte[]> file : new TreeMap<>(files).entrySet()) {
                digest.update(file.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(file.getValue());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** SHA-1 of {@code bytes}, as the client checks a pack. */
    public static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
