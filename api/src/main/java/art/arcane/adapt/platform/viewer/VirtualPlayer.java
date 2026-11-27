package art.arcane.adapt.platform.viewer;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Map;

/**
 * Client-only player body (stealth decoy). It exists only in the packets sent to the given viewers;
 * the server has no entity for it. Each method may be called from the viewer's owning thread.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface VirtualPlayer {
  /** The client-side entity id of the body. */
  int entityId();

  /** Sends the body to {@code viewer}. */
  void spawn(PlayerRef viewer);

  /** Removes the body from {@code viewer}'s client. */
  void destroy(PlayerRef viewer);

  /** Removes the tab-list entry that spawning needs for the skin, keeping the body visible. */
  void removeFromTab(PlayerRef viewer);

  /** Returns false when the move could not be encoded (for example too far for a relative move). */
  boolean move(Loc location, boolean onGround, Collection<PlayerRef> viewers);

  /** Turns the body and head for {@code viewer}. */
  void look(float yaw, float pitch, PlayerRef viewer);

  /** Plays the hurt animation; {@code yaw} is the direction the hit came from. */
  void hurt(float yaw, Collection<PlayerRef> viewers);

  /** Shows the items in the given slots on the body; slots not in the map keep what they show. */
  void equipment(Map<EquipmentSlot, ItemRef> items, Collection<PlayerRef> viewers);
}
