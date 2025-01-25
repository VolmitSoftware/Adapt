package com.volmit.adapt.util;

import art.arcane.amulet.io.FileWatcher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.tick.TickedObject;
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
        updateChecker.unregister();
        updateChecker = null;
    }

    private static class UpdateChecker extends TickedObject {
        private final Object lock = new Object();
        private final File modelsFile;
        private final FileWatcher fw;
        private final KMap<String, CustomModel> cache = new KMap<>();
        private JsonObject json = new JsonObject();

        public UpdateChecker() {
            super("config", "config-models", 1000);
            modelsFile = instance.getDataFile("adapt", "models.json");
            fw = new FileWatcher(modelsFile);
            fw.checkModified();
            instance.getTicker().register(this);

            try {
                readFile();
            } catch (IOException e) {
                Adapt.error("Failed to read models.json");
                e.printStackTrace();
            }
        }

        @Override
        public void onTick() {
            if (!AdaptConfig.get().isHotReload())
                return;

            synchronized (lock) {
                if (!fw.checkModified() || !modelsFile.exists())
                    return;

                try {
                    readFile();
                    cache.clear();
                    Adapt.info("Hotloaded " + modelsFile.getPath());
                    fw.checkModified();
                } catch (IOException e) {
                    Adapt.error("Failed to read models.json");
                    e.printStackTrace();
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
                fw.checkModified();
            }
        }
    }
}
