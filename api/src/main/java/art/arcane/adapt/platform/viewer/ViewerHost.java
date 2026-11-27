package art.arcane.adapt.platform.viewer;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Map;

/**
 * Per-viewer client state: entity visibility, glow outlines, fake equipment and virtual players.
 * Calls are packet-level and must run on the viewer's owning thread. Nothing here changes what the
 * server or other players see.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface ViewerHost {
  /** Shows an entity that was hidden from {@code viewer}. */
  void showEntity(PlayerRef viewer, EntityRef entity);

  /** Hides the entity from {@code viewer}'s client only. */
  void hideEntity(PlayerRef viewer, EntityRef entity);

  /** True when the entity is not hidden from {@code viewer}. */
  boolean canSee(PlayerRef viewer, EntityRef entity);

  /** Shows {@code entity} glowing in {@code color} to {@code viewer} only. */
  void setGlow(PlayerRef viewer, EntityRef entity, GlowColor color);

  /** Clears a per-viewer glow. Takes the entity id so it works after the entity is gone. */
  void clearGlow(PlayerRef viewer, int entityId);

  /** Sends equipment for {@code entity} to {@code viewer} without changing the real equipment. */
  void sendEquipment(PlayerRef viewer, EntityRef entity, Map<EquipmentSlot, ItemRef> items);

  /**
   * Creates a virtual player that copies {@code skinSource}'s skin; nothing is sent until spawn.
   * {@code skinLayerMask} is the vanilla skin-parts bit mask (127 shows every layer). Null when the skin source is
   * offline or the platform could not create one.
   */
  VirtualPlayer createVirtualPlayer(PlayerRef skinSource, Loc location, int skinLayerMask);

  /**
   * Whether clients see {@code entity} unless shown to them with {@link #showEntity}. Hidden by default, the entity
   * still exists for the server (mobs can target it) but no client receives it. Call it in a spawn callback to start
   * hidden.
   */
  void setVisibleByDefault(EntityRef entity, boolean visible);

  /** Players whose clients currently track {@code entity}. */
  Collection<PlayerRef> trackingPlayers(EntityRef entity);

  /** Players whose clients have the chunk loaded. */
  Collection<PlayerRef> chunkViewers(WorldRef world, int chunkX, int chunkZ);
}
