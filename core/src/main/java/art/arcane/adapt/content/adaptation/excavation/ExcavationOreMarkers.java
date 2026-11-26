package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.Brightness;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.common.world.BlockScanScheduler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Glowing ore outlines only their finder can see. Each marker is a temporary block display that removes itself after
 * its duration, when its finder stops qualifying, or when the owning adaptation closes.
 */
public final class ExcavationOreMarkers {
  private final Adaptation<?> owner;
  private final Predicate<PlayerRef> viewerQualifies;
  private final Map<UUID, DisplayHandle> activeMarkers = new ConcurrentHashMap<>();
  private final AtomicBoolean acceptingMarkers = new AtomicBoolean(true);
  private final Object markerLifecycleLock = new Object();

  public ExcavationOreMarkers(Adaptation<?> owner, Predicate<PlayerRef> viewerQualifies) {
    this.owner = owner;
    this.viewerQualifies = viewerQualifies;
  }

  /** The outline settings for one marker. {@code verify} drops the marker unless the ore is still in place. */
  public record Style(Rgb glow, float viewRange, int durationTicks, boolean verify) {
  }

  public void show(PlayerRef p, WorldRef world, BlockScanScheduler.Match match, Style style) {
    Loc markerLocation = new Loc(world, match.x(), match.y(), match.z());
    Tasks.runAt(markerLocation, () -> showAtRegion(p, world, match, markerLocation, style));
  }

  private void showAtRegion(PlayerRef p, WorldRef world, BlockScanScheduler.Match match, Loc markerLocation,
                            Style style) {
    if (!acceptingMarkers.get()) {
      return;
    }
    if (!world.isChunkLoaded(match.x() >> 4, match.z() >> 4)) {
      return;
    }
    if (style.verify() && !world.blockAt(match.x(), match.y(), match.z()).type().equals(match.material())) {
      return;
    }
    BlockStateRef block = AdaptCore.platform().host().items().blockState(match.material());
    if (block == null) {
      return;
    }

    BlockDisplayRef marker;
    try {
      marker = world.spawnBlockDisplay(markerLocation, block, (BlockDisplayRef display) -> {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setSilent(true);
        display.setVisibleByDefault(false);
        display.setViewRange(style.viewRange());
        display.setShadowRadius(0f);
        display.setBrightness(new Brightness(15, 15));
        display.setBlock(block);
        display.setGlowColorOverride(style.glow());
        display.setGlowing(true);
      });
    } catch (Throwable error) {
      AdaptLog.verbose("Failed to spawn glowing marker for " + owner.getName() + ": "
          + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
      AdaptLog.error(error);
      return;
    }
    if (marker == null) {
      return;
    }

    UUID markerId = marker.uuid();
    DisplayHandle handle = new DisplayHandle(marker, markerLocation, p.uuid());
    synchronized (markerLifecycleLock) {
      if (!acceptingMarkers.get()) {
        removeDisplayOwned(marker);
        return;
      }
      activeMarkers.put(markerId, handle);
    }
    if (!Tasks.runEntity(p, () -> showToPlayer(p, markerId, marker))) {
      remove(markerId);
      return;
    }
    if (!Tasks.runEntity(marker, () -> removeOwned(markerId, marker), style.durationTicks())) {
      remove(markerId);
    }
  }

  private void showToPlayer(PlayerRef player, UUID markerId, BlockDisplayRef marker) {
    if (!player.isOnline() || !viewerQualifies.test(player) || !activeMarkers.containsKey(markerId)) {
      remove(markerId);
      return;
    }
    AdaptCore.platform().viewers().showEntity(player, marker);
  }

  /** Removes every marker shown to {@code playerId}. */
  public void clearFor(UUID playerId) {
    for (Map.Entry<UUID, DisplayHandle> entry : activeMarkers.entrySet()) {
      if (entry.getValue().owner().equals(playerId)) {
        remove(entry.getKey());
      }
    }
  }

  /** Stops tracking a display entity that the world removed. */
  public void forget(UUID entityId) {
    activeMarkers.remove(entityId);
  }

  /** Refuses new markers and removes every live one. */
  public void close() {
    acceptingMarkers.set(false);
    List<DisplayHandle> handles;
    synchronized (markerLifecycleLock) {
      handles = List.copyOf(activeMarkers.values());
      activeMarkers.clear();
    }
    for (DisplayHandle handle : handles) {
      if (!Tasks.runEntity(handle.display(), () -> removeDisplayOwned(handle.display()))) {
        Tasks.runAt(handle.anchor(), () -> removeDisplayOwned(handle.display()));
      }
    }
  }

  private void remove(UUID markerId) {
    DisplayHandle handle = activeMarkers.remove(markerId);
    if (handle == null) {
      return;
    }
    if (!Tasks.runEntity(handle.display(), () -> removeDisplayOwned(handle.display()))) {
      Tasks.runAt(handle.anchor(), () -> removeDisplayOwned(handle.display()));
    }
  }

  private void removeOwned(UUID markerId, BlockDisplayRef marker) {
    activeMarkers.remove(markerId);
    removeDisplayOwned(marker);
  }

  private static void removeDisplayOwned(BlockDisplayRef marker) {
    if (marker.isValid()) {
      marker.remove();
    }
  }

  private record DisplayHandle(BlockDisplayRef display, Loc anchor, UUID owner) {
  }
}
