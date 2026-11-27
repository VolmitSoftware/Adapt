package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.asset.AssetEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An asset bundle held in memory, laid out like an addon jar below {@code assets/<namespace>/}. */
final class MemoryBundle implements AssetBundle {
  private final String namespace;
  private final Map<String, byte[]> files = new LinkedHashMap<>();

  MemoryBundle(String namespace) {
    this.namespace = namespace;
  }

  MemoryBundle file(String path, byte[] bytes) {
    files.put(path, bytes);
    return this;
  }

  static byte[] png(int width, int height, int argb) {
    int[] pixels = new int[width * height];
    Arrays.fill(pixels, argb);
    return PngImages.encode(width, height, pixels);
  }

  @Override
  public String namespace() {
    return namespace;
  }

  @Override
  public List<AssetEntry> entries() {
    List<AssetEntry> entries = new ArrayList<>();
    for (String path : files.keySet()) {
      entries.add(AssetBundle.classify(namespace, path));
    }
    return entries;
  }

  @Override
  public byte[] read(AssetEntry entry) {
    byte[] bytes = files.get(entry.path());
    if (bytes == null) {
      throw new IllegalArgumentException("no file " + entry.path());
    }
    return bytes;
  }
}
