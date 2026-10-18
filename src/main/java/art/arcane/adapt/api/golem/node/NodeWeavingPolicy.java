package art.arcane.adapt.api.golem.node;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface NodeWeavingPolicy {
  int level(Player player);

  int nodeCap(int level);

  int maxAreaRadius(int level);

  int filterSlots(int level);

  boolean canPlace(Player player, Location anchor, Location placement);

  boolean canAccessContainer(Player player, Location container);
}
