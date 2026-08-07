package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.adapt.util.reflect.registries.ItemFlags;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.io.IO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.adapt.Adapt.instance;

public record CustomModel(Material material, int model,
                          NamespacedKey modelKey) {
  public static final NamespacedKey EMPTY_KEY = NamespacedKey.minecraft("empty");
  private static volatile UpdateChecker updateChecker = null;

  private static UpdateChecker checker() {
    UpdateChecker current = updateChecker;
    if (current == null) {
      synchronized (CustomModel.class) {
        current = updateChecker;
        if (current == null) {
          current = new UpdateChecker();
          updateChecker = current;
        }
      }
    }

    return current;
  }

  public static CustomModel get(Material fallback, String... path) {
    if (!AdaptConfig.get().isCustomModels())
      return new CustomModel(fallback, 0, null);

    return checker().get(fallback, path);
  }

  public static void clear() {
    updateChecker = null;
  }

  public static boolean reloadFromDisk() {
    return reloadFromDisk(false);
  }

  public static boolean reloadFromDisk(boolean quiet) {
    return checker().reloadFromDisk(quiet);
  }

  public ItemStack toItemStack() {
    return toItemStack(new ItemStack(material));
  }

  public ItemStack toItemStack(ItemStack itemStack) {
    ItemMeta meta = itemStack.getItemMeta();
    if (meta == null)
      return itemStack;

    if (model != 0) {
      Version.get().applyModel(this, meta);
    }

    // Menu tiles are decoration, so vanilla component tooltips (bee counts, potion effects,
    // shulker contents) must never bleed into the adaptation lore.
    ItemFlag hideAdditionalTooltip = ItemFlags.HIDE_ADDITIONAL_TOOLTIP;
    if (hideAdditionalTooltip != null) {
      meta.addItemFlags(hideAdditionalTooltip);
    }

    itemStack.setItemMeta(meta);
    return itemStack;
  }

  private static class UpdateChecker {
    private final Object lock = new Object();
    private final AtomicBoolean writeQueued = new AtomicBoolean(false);
    private final File modelsFile;
    private final File legacyModelsFile;
    private final KMap<String, CustomModel> cache = new KMap<>();
    private JsonObject json = new JsonObject();

    public UpdateChecker() {
      modelsFile = instance.getDataFile("adapt", "models.toml");
      legacyModelsFile = instance.getDataFile("adapt", "models.json");

      try {
        readFile();
      } catch (IOException e) {
        Adapt.error("Failed to read models.toml");
        e.printStackTrace();
      }
    }

    public boolean reloadFromDisk(boolean quiet) {
      synchronized (lock) {
        try {
          readFile();
          cache.clear();
          return true;
        } catch (IOException e) {
          if (!quiet) {
            Adapt.error("Failed to read models.toml");
            e.printStackTrace();
          }
          return false;
        }
      }
    }

    public CustomModel get(Material fallback, String... path) {
      String key = String.join("", path);
      CustomModel cached = cache.get(key);
      if (cached != null) {
        return cached;
      }

      CustomModel resolved = resolve(fallback, path);
      CustomModel raced = cache.putIfAbsent(key, resolved);
      return raced != null ? raced : resolved;
    }

    private CustomModel resolve(Material fallback, String... path) {
      synchronized (lock) {
        JsonObject node = this.json;
        for (String s : path) {
          if (!node.has(s)) {
            return set(new CustomModel(fallback, 0, EMPTY_KEY), path);
          }

          JsonElement v = node.get(s);
          if (!v.isJsonObject()) {
            Adapt.warn("Invalid json at path: " + String.join(".", path));
            return new CustomModel(fallback, 0, EMPTY_KEY);
          }
          node = v.getAsJsonObject();
        }

        return new CustomModel(
            node.has("material") ? Material.valueOf(node.get("material").getAsString()) : fallback,
            node.has("model") ? node.get("model").getAsInt() : 0,
            node.has("modelKey") ? NamespacedKey.fromString(node.get("modelKey").getAsString()) : EMPTY_KEY
        );
      }
    }

    public CustomModel set(CustomModel data, String... path) {
      synchronized (lock) {
        JsonObject node = this.json;
        for (String s : path) {
          if (!node.has(s))
            node.add(s, new JsonObject());

          JsonElement v = node.get(s);
          if (!v.isJsonObject()) {
            v = new JsonObject();
            node.add(s, v);
          }
          node = v.getAsJsonObject();
        }

        node.addProperty("material", data.material.name());
        node.addProperty("model", data.model);
        node.addProperty("modelKey", (data.modelKey == null ? EMPTY_KEY : data.modelKey).toString());
      }

      scheduleWrite();
      return data;
    }

    private void scheduleWrite() {
      if (!writeQueued.compareAndSet(false, true)) {
        return;
      }

      J.a(() -> {
        writeQueued.set(false);
        try {
          writeFile();
        } catch (IOException e) {
          Adapt.error("Failed to write models.toml");
          e.printStackTrace();
        }
      });
    }

    public void readFile() throws IOException {
      synchronized (lock) {
        if (modelsFile.exists()) {
          String raw = IO.readAll(modelsFile);
          if (raw == null || raw.isBlank()) {
            json = new JsonObject();
            ConfigFileSupport.deleteLegacyFileIfMigrated(modelsFile, legacyModelsFile, "models-config");
            return;
          }

          JsonElement parsed = ConfigFileSupport.parseToJsonElement(raw, modelsFile);
          if (parsed == null || !parsed.isJsonObject()) {
            throw new IOException("Invalid models.toml");
          }

          json = parsed.getAsJsonObject();
          ConfigFileSupport.deleteLegacyFileIfMigrated(modelsFile, legacyModelsFile, "models-config");
          return;
        }

        if (legacyModelsFile.exists()) {
          String legacyRaw = IO.readAll(legacyModelsFile);
          JsonObject legacy = Json.fromJson(legacyRaw, JsonObject.class);
          if (legacy == null) {
            throw new IOException("Invalid models.json");
          }
          json = legacy;
          IO.writeAll(modelsFile, ConfigFileSupport.serializeJsonElementToToml(legacy));
          Adapt.info("Migrated legacy config [adapt/models.json] -> [adapt/models.toml].");
          ConfigFileSupport.deleteLegacyFileIfMigrated(modelsFile, legacyModelsFile, "models-config");
          return;
        }

        json = new JsonObject();
        IO.writeAll(modelsFile, ConfigFileSupport.serializeJsonElementToToml(json));
        ConfigFileSupport.recordMissingConfigCreated();
      }
    }

    public void writeFile() throws IOException {
      synchronized (lock) {
        IO.writeAll(modelsFile, ConfigFileSupport.serializeJsonElementToToml(json));
      }
    }
  }
}
