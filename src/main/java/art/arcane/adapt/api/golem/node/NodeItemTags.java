package art.arcane.adapt.api.golem.node;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class NodeItemTags implements NodeTagLookup {
  private static final NodeItemTags SHARED = new NodeItemTags();

  private final Map<String, Tag<Material>> tags = new ConcurrentHashMap<>();

  private NodeItemTags() {
  }

  public static NodeItemTags shared() {
    return SHARED;
  }

  @Override
  public boolean exists(String tag) {
    return resolve(tag).isPresent();
  }

  @Override
  public boolean tagged(String tag, Material material) {
    return resolve(tag).map(resolved -> resolved.isTagged(material)).orElse(false);
  }

  private Optional<Tag<Material>> resolve(String tag) {
    Tag<Material> cached = tags.get(tag);
    if (cached != null) {
      return Optional.of(cached);
    }
    NamespacedKey key = tag.length() > 1 && tag.charAt(0) == '#' ? NamespacedKey.fromString(tag.substring(1)) : null;
    Tag<Material> resolved = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
    if (resolved != null) {
      tags.put(tag, resolved);
    }
    return Optional.ofNullable(resolved);
  }
}
