package com.volmit.adapt.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.util.collection.KMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static com.volmit.adapt.Adapt.instance;

public record CustomModel(Material material, int model, NamespacedKey modelKey) {
    public static final NamespacedKey EMPTY_KEY = NamespacedKey.minecraft("empty");
    private static UpdateChecker updateChecker = null;
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    public ItemStack toItemStack() {
        return toItemStack(new ItemStack(material));
    }

    public ItemStack toItemStack(ItemStack itemStack) {
        var meta = itemStack.getItemMeta();
        if (meta == null || model == 0)
            return itemStack;

        Version.get().applyModel(this, meta);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static CustomModel get(Material fallback, String... path) {
        if (!AdaptConfig.get().isCustomModels())
            return new CustomModel(fallback, 0, null);

        if (updateChecker == null)
            updateChecker = new UpdateChecker();
        return updateChecker.get(fallback, path);
    }

    public static void clear() {
        if (updateChecker == null)
            return;
        updateChecker = null;
    }

    public static boolean reloadFromDisk() {
        if (updateChecker == null)
            updateChecker = new UpdateChecker();

        return updateChecker.reloadFromDisk();
    }

    private static class UpdateChecker {
        private final Object lock = new Object();
        private final File modelsFile;
        private final KMap<String, CustomModel> cache = new KMap<>();
        private JsonObject json = new JsonObject();

        public UpdateChecker() {
            modelsFile = instance.getDataFile("adapt", "models.json");

            try {
                readFile();
            } catch (IOException e) {
                Adapt.error("Failed to read models.json");
                e.printStackTrace();
            }
        }

        public boolean reloadFromDisk() {
            synchronized (lock) {
                try {
                    readFile();
                    cache.clear();
                    return true;
                } catch (IOException e) {
                    Adapt.error("Failed to read models.json");
                    e.printStackTrace();
                    return false;
                }
            }
        }

        public CustomModel get(Material fallback, String... path) {
            return cache.computeIfAbsent(String.join("", path), k -> {
                var json = this.json;
                for (var s : path) {
                    if (!json.has(s))
                        return set(new CustomModel(fallback, 0, EMPTY_KEY), path);
                    var v = json.get(s);
                    if (!v.isJsonObject()) {
                        Adapt.warn("Invalid json at path: " + String.join(".", path));
                        return new CustomModel(fallback, 0, EMPTY_KEY);
                    }
                    json = v.getAsJsonObject();
                }

                return new CustomModel(
                        json.has("material") ? Material.valueOf(json.get("material").getAsString()) : fallback,
                        json.has("model") ? json.get("model").getAsInt() : 0,
                        json.has("modelKey") ? NamespacedKey.fromString(json.get("modelKey").getAsString()) : EMPTY_KEY
                );
            });
        }

        public CustomModel set(CustomModel data, String... path) {
            var json = this.json;
            for (var s : path) {
                if (!json.has(s))
                    json.add(s, new JsonObject());

                var v = json.get(s);
                if (!v.isJsonObject()) {
                    v = new JsonObject();
                    json.add(s, v);
                }
                json = v.getAsJsonObject();
            }

            json.addProperty("material", data.material.name());
            json.addProperty("model", data.model);
            json.addProperty("modelKey", data.modelKey.toString());

            try {
                writeFile();
            } catch (IOException e) {
                Adapt.error("Failed to write models.json");
                e.printStackTrace();
            }
            return data;
        }

        public void readFile() throws IOException {
            synchronized (lock) {
                if (!modelsFile.exists()) {
                    json = new JsonObject();
                    return;
                }
                try (FileReader reader = new FileReader(modelsFile)) {
                    json = GSON.fromJson(reader, JsonObject.class);
                }
            }
        }

        public void writeFile() throws IOException {
            synchronized (lock) {
                var s = GSON.toJson(json);
                IO.writeAll(modelsFile, s);
            }
        }
    }
}
