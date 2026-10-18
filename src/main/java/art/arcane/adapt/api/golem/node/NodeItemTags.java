package art.arcane.adapt.api.golem.node;

import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class NodeItemTags implements NodeTagLookup {
  private static final NodeItemTags SHARED = new NodeItemTags();

  private final Map<String, Optional<Tag<Material>>> tags = new ConcurrentHashMap<>();

  private NodeItemTags() {
  }

  public static NodeItemTags shared() {
    return SHARED;
  }

  @Override
  public boolean exists(String tag) {
    Optional<Tag<Material>> cached = tags.get(tag);
    if (cached != null) {
      return cached.isPresent();
    }
    Optional<Tag<Material>> resolved = lookup(tag);
    resolved.ifPresent(found -> tags.putIfAbsent(tag, resolved));
    return resolved.isPresent();
  }

  @Override
  public boolean tagged(String tag, Material material) {
    return resolve(tag).map(resolved -> resolved.isTagged(material)).orElse(false);
  }

  public void clear() {
    tags.clear();
  }

  private Optional<Tag<Material>> resolve(String tag) {
    return tags.computeIfAbsent(tag, NodeItemTags::lookup);
  }

  private static Optional<Tag<Material>> lookup(String tag) {
    NamespacedKey key = tag.length() > 1 && tag.charAt(0) == '#' ? NamespacedKey.fromString(tag.substring(1)) : null;
    return key == null ? Optional.empty() : Optional.ofNullable(Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class));
  }

  public static final class ReloadListener implements Listener {
    @EventHandler
    public void on(ServerResourcesReloadedEvent e) {
      SHARED.clear();
    }
  }
}
