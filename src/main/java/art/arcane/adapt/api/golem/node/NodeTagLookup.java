package art.arcane.adapt.api.golem.node;

import org.bukkit.Material;

public interface NodeTagLookup {
  boolean exists(String tag);

  boolean tagged(String tag, Material material);
}
