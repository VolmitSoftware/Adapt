package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.adapt.util.config.ConfigHotload;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.io.IO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public record CustomModel(Id material, int model, Id modelKey, String headTexture) {
  public static final Id EMPTY_KEY = Id.minecraft("empty");
  private static final Map<String, URL> SKIN_URLS = new ConcurrentHashMap<>();
  private static final AtomicLong GENERATION = new AtomicLong();
  private static volatile UpdateChecker updateChecker = null;

  public CustomModel {
    if (headTexture != null && !headTexture.isBlank()) {
      try {
        skinUrl(headTexture);
      } catch (IOException error) {
        AdaptLog.warn("Invalid player head texture: " + error.getMessage());
        headTexture = null;
      }
    }
  }

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

  public static CustomModel get(Id fallback, String... path) {
    if (!AdaptConfig.get().isCustomModels())
      return new CustomModel(fallback, 0, null, null);

    return checker().get(fallback, path);
  }

  public static long generation() {
    return GENERATION.get();
  }

  public static void clear() {
    updateChecker = null;
    GENERATION.incrementAndGet();
  }

  public static boolean reloadFromDisk() {
    try {
      return checker().reloadFromDisk(false);
    } finally {
      GENERATION.incrementAndGet();
    }
  }

  public static boolean reloadFromDiskPassive() {
    try {
      return passiveChecker().reloadFromDisk(true);
    } finally {
      GENERATION.incrementAndGet();
    }
  }

  public static boolean reloadSnapshot(String raw, File sourceFile) {
    try {
      return applySnapshot(raw, sourceFile);
    } finally {
      GENERATION.incrementAndGet();
    }
  }

  private static boolean applySnapshot(String raw, File sourceFile) {
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
            AdaptLog.error("Failed to apply models config snapshot");
            AdaptLog.error(error);
            return false;
          }
        }
      }
    }
    return current.reloadSnapshot(raw, sourceFile);
  }

  public ItemRef toItemStack() {
    return toItemStack(AdaptCore.platform().host().items().create(material));
  }

  public ItemRef toItemStack(ItemRef itemStack) {
    if (itemStack == null || itemStack.isEmpty()) {
      return itemStack;
    }

    CustomModelPort port = AdaptPorts.get(CustomModelPort.class, CustomModelPort.NONE);
    if (modelKey != null && !EMPTY_KEY.equals(modelKey)) {
      itemStack.setItemModel(modelKey);
    } else if (model != 0) {
      port.setCustomModelData(itemStack, model);
    }
    if (headTexture != null && !headTexture.isBlank()) {
      URL skin = SKIN_URLS.get(headTexture);
      if (skin != null) {
        port.setHeadTexture(itemStack, headTexture, skin);
      }
    }

    // Menu tiles are decoration, so vanilla component tooltips (bee counts, potion effects,
    // shulker contents) must never bleed into the adaptation lore.
    port.hideAdditionalTooltip(itemStack);
    return itemStack;
  }

  /**
   * Reads a {@code material} config value as Bukkit {@code Material.valueOf} did: exactly a constant name such as
   * {@code DIAMOND_SWORD}, or the full id of a modded item.
   */
  static Id parseMaterial(String raw) {
    Id material = MaterialNames.exact(raw);
    if (material == null) {
      material = MaterialNames.modded(raw);
    }
    if (material == null || material.isAir() || !isItem(material)) {
      throw new IllegalArgumentException("Material must be a usable item");
    }
    return material;
  }

  /** The {@code material} config value for an id: the legacy constant name for vanilla ids, the full id otherwise. */
  static String materialName(Id material) {
    return material.isMinecraft() ? material.legacyName() : material.toString();
  }

  private static boolean isItem(Id material) {
    AdaptCore core = AdaptCore.current();
    if (core == null) {
      return true;
    }
    ItemOps items = core.getPlatform().host().items();
    return items.isItem(material);
  }

  private static URL skinUrl(String texture) throws IOException {
    URL cached = SKIN_URLS.get(texture);
    if (cached != null) {
      return cached;
    }
    try {
      String decoded = new String(Base64.getDecoder().decode(texture), StandardCharsets.UTF_8);
      JsonObject skin = Json.fromJson(decoded, JsonObject.class)
          .getAsJsonObject("textures").getAsJsonObject("SKIN");
      URI uri = URI.create(skin.get("url").getAsString());
      if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
          || !"textures.minecraft.net".equals(uri.getHost()) || uri.getUserInfo() != null) {
        throw new IllegalArgumentException("Skin URL must use textures.minecraft.net");
      }
      URL url = uri.toURL();
      SKIN_URLS.put(texture, url);
      return url;
    } catch (RuntimeException error) {
      throw new IOException("Expected a base64 Minecraft skin texture", error);
    }
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
      modelsFile = AdaptEnvironment.dataFile("models.toml");

      try {
        readFile(passive);
      } catch (IOException e) {
        AdaptLog.error("Failed to read models.toml");
        AdaptLog.error(e);
      }
    }

    private UpdateChecker(String raw, File sourceFile) throws IOException {
      modelsFile = AdaptEnvironment.dataFile("models.toml");
      json = parseSnapshot(raw, sourceFile);
    }

    public boolean reloadFromDisk(boolean passive) {
      synchronized (lock) {
        try {
          readFile(passive);
          cache.clear();
          return true;
        } catch (IOException e) {
          AdaptLog.error("Failed to read models.toml");
          AdaptLog.error(e);
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
          AdaptLog.error("Failed to apply models config snapshot");
          AdaptLog.error(error);
          return false;
        }
      }
    }

    public CustomModel get(Id fallback, String... path) {
      String key = String.join("", path);
      CustomModel cached = cache.get(key);
      if (cached != null) {
        return cached;
      }

      CustomModel resolved = resolve(fallback, path);
      CustomModel raced = cache.putIfAbsent(key, resolved);
      return raced != null ? raced : resolved;
    }

    private CustomModel resolve(Id fallback, String... path) {
      synchronized (lock) {
        JsonObject node = this.json;
        for (String s : path) {
          if (!node.has(s)) {
            return set(new CustomModel(fallback, 0, EMPTY_KEY, null), path);
          }

          JsonElement v = node.get(s);
          if (!v.isJsonObject()) {
            AdaptLog.warn("Invalid json at path: " + String.join(".", path));
            return new CustomModel(fallback, 0, EMPTY_KEY, null);
          }
          node = v.getAsJsonObject();
        }

        try {
          Id material = node.has("material") ? parseMaterial(node.get("material").getAsString()) : fallback;
          String texture = node.has("headTexture") ? node.get("headTexture").getAsString() : null;
          if (texture != null && !texture.isBlank()) {
            skinUrl(texture);
          }
          return new CustomModel(
              material,
              node.has("model") ? node.get("model").getAsInt() : 0,
              node.has("modelKey") ? Id.tryParse(node.get("modelKey").getAsString()) : EMPTY_KEY,
              texture
          );
        } catch (RuntimeException | IOException error) {
          AdaptLog.warn("Invalid model at " + String.join(".", path) + ": " + error.getMessage());
          return new CustomModel(fallback, 0, EMPTY_KEY, null);
        }
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

        node.addProperty("material", materialName(data.material));
        node.addProperty("model", data.model);
        node.addProperty("modelKey", (data.modelKey == null ? EMPTY_KEY : data.modelKey).toString());
        node.addProperty("headTexture", data.headTexture == null ? "" : data.headTexture);
      }

      scheduleWrite();
      return data;
    }

    private void scheduleWrite() {
      if (!writeQueued.compareAndSet(false, true)) {
        return;
      }

      Tasks.a(() -> {
        writeQueued.set(false);
        try {
          writeFile();
        } catch (IOException e) {
          AdaptLog.error("Failed to write models.toml");
          AdaptLog.error(e);
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

          json = parseSnapshot(raw, modelsFile);
          return;
        }

        if (passive) {
          throw new IOException("models.toml is missing");
        }

        json = new JsonObject();
        scheduleWrite();
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
      JsonObject loaded = parsed.getAsJsonObject();
      validateModels(loaded, "models");
      return loaded;
    }

    private void validateModels(JsonObject node, String path) throws IOException {
      try {
        if (node.has("material")) {
          parseMaterial(node.get("material").getAsString());
        }
        if (node.has("model")) {
          node.get("model").getAsInt();
        }
        if (node.has("modelKey") && Id.tryParse(node.get("modelKey").getAsString()) == null) {
          throw new IllegalArgumentException("Invalid item model key");
        }
        if (node.has("headTexture") && !node.get("headTexture").getAsString().isBlank()) {
          skinUrl(node.get("headTexture").getAsString());
        }
        for (Map.Entry<String, JsonElement> entry : node.entrySet()) {
          if (entry.getValue().isJsonObject()) {
            validateModels(entry.getValue().getAsJsonObject(), path + "." + entry.getKey());
          }
        }
      } catch (RuntimeException | IOException error) {
        throw new IOException("Invalid custom model at " + path, error);
      }
    }

    public void writeFile() throws IOException {
      try {
        ConfigHotload.<Void>writeTracked(modelsFile, () -> new ConfigHotloadEngine.Written<Void>(writeSerialized(), null));
      } catch (IOException | RuntimeException error) {
        throw error;
      } catch (Exception error) {
        throw new IOException("Failed to write models.toml", error);
      }
    }

    private String writeSerialized() throws IOException {
      synchronized (lock) {
        String toml = ConfigFileSupport.serializeJsonElementToToml(json);
        IO.writeAll(modelsFile, toml);
        return toml;
      }
    }
  }
}
