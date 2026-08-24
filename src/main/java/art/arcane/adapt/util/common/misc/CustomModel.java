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

  private static UpdateChecker passiveChecker() {
    UpdateChecker current = updateChecker;
    if (current == null) {
      synchronized (CustomModel.class) {
        current = updateChecker;
        if (current == null) {
          current = new UpdateChecker(true);
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
    return checker().reloadFromDisk(false);
  }

  public static boolean reloadFromDiskPassive() {
    return passiveChecker().reloadFromDisk(true);
  }

  public static boolean reloadSnapshot(String raw, File sourceFile) {
    UpdateChecker current = updateChecker;
    if (current == null) {
      synchronized (CustomModel.class) {
        current = updateChecker;
        if (current == null) {
          try {
            current = new UpdateChecker(raw, sourceFile);
            updateChecker = current;
            return true;
          } catch (IOException error) {
            Adapt.error("Failed to apply models config snapshot");
            Adapt.error(error);
            return false;
          }
        }
      }
    }
    return current.reloadSnapshot(raw, sourceFile);
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
    private final KMap<String, CustomModel> cache = new KMap<>();
    private JsonObject json = new JsonObject();

    public UpdateChecker() {
      this(false);
    }

    private UpdateChecker(boolean passive) {
      modelsFile = instance.getDataFile("models.toml");

      try {
        readFile(passive);
      } catch (IOException e) {
        Adapt.error("Failed to read models.toml");
        Adapt.error(e);
      }
    }

    private UpdateChecker(String raw, File sourceFile) throws IOException {
      modelsFile = instance.getDataFile("models.toml");
      json = parseSnapshot(raw, sourceFile);
    }

    public boolean reloadFromDisk(boolean passive) {
      synchronized (lock) {
        try {
          readFile(passive);
          cache.clear();
          return true;
        } catch (IOException e) {
          Adapt.error("Failed to read models.toml");
          Adapt.error(e);
          return false;
        }
      }
    }

    public boolean reloadSnapshot(String raw, File sourceFile) {
      synchronized (lock) {
        try {
          JsonObject loaded = parseSnapshot(raw, sourceFile);
          json = loaded;
          cache.clear();
          return true;
        } catch (IOException error) {
          Adapt.error("Failed to apply models config snapshot");
          Adapt.error(error);
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
          Adapt.error(e);
        }
      });
    }

    public void readFile(boolean passive) throws IOException {
      synchronized (lock) {
        if (modelsFile.exists()) {
          String raw = IO.readAll(modelsFile);
          if (raw == null || raw.isBlank()) {
            json = new JsonObject();
            return;
          }

          JsonElement parsed = ConfigFileSupport.parseToJsonElement(raw, modelsFile);
          if (parsed == null || !parsed.isJsonObject()) {
            throw new IOException("Invalid models.toml");
          }

          json = parsed.getAsJsonObject();
          return;
        }

        if (passive) {
          throw new IOException("models.toml is missing");
        }

        json = new JsonObject();
        IO.writeAll(modelsFile, ConfigFileSupport.serializeJsonElementToToml(json));
        ConfigFileSupport.recordMissingConfigCreated();
      }
    }

    private JsonObject parseSnapshot(String raw, File sourceFile) throws IOException {
      if (raw == null) {
        throw new IOException("Models config snapshot is missing");
      }
      if (raw.isBlank()) {
        return new JsonObject();
      }
      JsonElement parsed = ConfigFileSupport.parseToJsonElement(raw, sourceFile);
      if (parsed == null || !parsed.isJsonObject()) {
        throw new IOException("Invalid models config snapshot");
      }
      return parsed.getAsJsonObject();
    }

    public void writeFile() throws IOException {
      synchronized (lock) {
        IO.writeAll(modelsFile, ConfigFileSupport.serializeJsonElementToToml(json));
      }
    }
  }
}
