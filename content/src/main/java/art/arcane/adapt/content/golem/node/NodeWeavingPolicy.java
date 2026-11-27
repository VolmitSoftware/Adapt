package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

public interface NodeWeavingPolicy {
  int level(PlayerRef player);

  int nodeCap(int level);

  int maxAreaRadius(int level);

  int filterSlots(int level);

  boolean canPlace(PlayerRef player, Loc anchor, Loc placement);

  boolean canAccessContainer(PlayerRef player, Loc container);

  boolean crookOnlyReveal(PlayerRef viewer);

  boolean hidesOutline(PlayerRef viewer);
}
