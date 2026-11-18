package art.arcane.adapt.platform.viewer;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.Collection;
import java.util.Map;

/**
 * Client-only player body (stealth decoy). It exists only in the packets sent to the given viewers;
 * the server has no entity for it. Each method may be called from the viewer's owning thread.
 */
public interface VirtualPlayer {
  int entityId();

  void spawn(PlayerRef viewer);

  void destroy(PlayerRef viewer);

  /** Removes the tab-list entry that spawning needs for the skin, keeping the body visible. */
  void removeFromTab(PlayerRef viewer);

  /** Returns false when the move could not be encoded (for example too far for a relative move). */
  boolean move(Loc location, boolean onGround, Collection<PlayerRef> viewers);

  void look(float yaw, float pitch, PlayerRef viewer);

  void hurt(float yaw, Collection<PlayerRef> viewers);

  void equipment(Map<EquipmentSlot, ItemRef> items, Collection<PlayerRef> viewers);
}
