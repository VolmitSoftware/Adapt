package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.entity.VirtualPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PacketPlayerDecoy {
  private final VirtualPlayer nativePlayer;
  private final long removeTabAt;
  private final int tabListRemoveDelayTicks;
  private final Map<UUID, Player> knownViewers;
  private boolean removedFromTab;
  private long lastPositionSyncAt;
  private double lastX;
  private double lastY;
  private double lastZ;
  private float lastYaw;
  private float lastPitch;
  private int lookCursor;

  PacketPlayerDecoy(VirtualPlayer nativePlayer, int tabListRemoveDelayTicks) {
    this.nativePlayer = nativePlayer;
    this.removeTabAt = tabRemovalDeadline(System.currentTimeMillis(), tabListRemoveDelayTicks);
    this.tabListRemoveDelayTicks = tabListRemoveDelayTicks;
    this.knownViewers = new ConcurrentHashMap<>();
    this.removedFromTab = false;
    this.lastPositionSyncAt = 0L;
    this.lastX = Double.NaN;
    this.lastY = Double.NaN;
    this.lastZ = Double.NaN;
    this.lastYaw = Float.NaN;
    this.lastPitch = Float.NaN;
    this.lookCursor = 0;
  }

  static long tabRemovalDeadline(long now, int delayTicks) {
    return delayTicks < 0 ? -1L : now + (delayTicks * 50L);
  }

  public void refresh(Location anchor, boolean onGround, Set<Player> trackedViewers, int maxViewers,
                      int maxViewerAdds, int maxLookUpdates, double eyeHeight) {
    refreshViewerState(trackedViewers, maxViewers, maxViewerAdds);
    tick();
    syncToAnchor(anchor, onGround);
    lookAtViewers(anchor.clone().add(0, eyeHeight, 0), maxLookUpdates);
  }

  private void tick() {
    if (removeTabAt < 0 || removedFromTab || System.currentTimeMillis() < removeTabAt) {
      return;
    }

    for (Player viewer : spawnedViewerPlayers()) {
      nativePlayer.removeFromTab(viewer);
    }

    removedFromTab = true;
  }

  private void lookAtViewers(Location origin, int maxUpdates) {
    List<Player> viewers = spawnedViewerPlayers();
    if (viewers.isEmpty()) {
      return;
    }

    int updates = Math.min(Math.max(1, maxUpdates), viewers.size());
    int start = Math.floorMod(lookCursor, viewers.size());
    for (int offset = 0; offset < updates; offset++) {
      Player viewer = viewers.get((start + offset) % viewers.size());
      J.runEntity(viewer, () -> lookAtViewer(origin, viewer));
    }
    lookCursor = (start + updates) % viewers.size();
  }

  private void lookAtViewer(Location origin, Player viewer) {
    Location to = viewer.getEyeLocation();
    if (origin.getWorld() != to.getWorld()) {
      return;
    }

    double dx = to.getX() - origin.getX();
    double dy = to.getY() - origin.getY();
    double dz = to.getZ() - origin.getZ();
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
    nativePlayer.look(yaw, pitch, viewer);
  }

  private void syncToAnchor(Location anchor, boolean onGround) {
    long now = System.currentTimeMillis();
    double dx = Double.isFinite(lastX) ? anchor.getX() - lastX : 1;
    double dy = Double.isFinite(lastY) ? anchor.getY() - lastY : 1;
    double dz = Double.isFinite(lastZ) ? anchor.getZ() - lastZ : 1;
    double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
    float yawDiff = Float.isFinite(lastYaw) ? Math.abs(anchor.getYaw() - lastYaw) : 360f;
    float pitchDiff = Float.isFinite(lastPitch) ? Math.abs(anchor.getPitch() - lastPitch) : 360f;

    if (distanceSq < 0.0004 && yawDiff < 0.8f && pitchDiff < 0.8f && now - lastPositionSyncAt < 500L) {
      return;
    }

    if (nativePlayer.move(anchor, onGround, spawnedViewerPlayers())) {
      lastPositionSyncAt = now;
      lastX = anchor.getX();
      lastY = anchor.getY();
      lastZ = anchor.getZ();
      lastYaw = anchor.getYaw();
      lastPitch = anchor.getPitch();
    }
  }

  public void hitFrom(Location source) {
    if (!Double.isFinite(lastX) || !Double.isFinite(lastZ)) {
      nativePlayer.hurt(0f, spawnedViewerPlayers());
      return;
    }

    double dx = source.getX() - lastX;
    double dz = source.getZ() - lastZ;
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    nativePlayer.hurt(yaw, spawnedViewerPlayers());
  }

  public void destroy() {
    for (Player viewer : spawnedViewerPlayers()) {
      nativePlayer.destroy(viewer);
    }

    knownViewers.clear();
  }

  private void refreshViewerState(Set<Player> trackedViewers, int maxViewers, int maxViewerAdds) {
    for (Map.Entry<UUID, Player> entry : knownViewers.entrySet()) {
      Player viewer = entry.getValue();
      if (viewer.isOnline() && trackedViewers.contains(viewer)) {
        continue;
      }
      if (knownViewers.remove(entry.getKey(), viewer)) {
        destroyFor(viewer);
      }
    }

    int viewerLimit = Math.max(1, maxViewers);
    int remainingAdds = Math.max(1, maxViewerAdds);
    for (Player viewer : trackedViewers) {
      if (knownViewers.size() >= viewerLimit || remainingAdds <= 0) {
        return;
      }
      if (!viewer.isOnline() || knownViewers.putIfAbsent(viewer.getUniqueId(), viewer) != null) {
        continue;
      }

      spawnFor(viewer);
      remainingAdds--;
    }
  }

  private void destroyFor(Player viewer) {
    nativePlayer.destroy(viewer);
  }

  private void spawnFor(Player viewer) {
    nativePlayer.spawn(viewer);

    if (removedFromTab) {
      J.runEntity(viewer, () -> removeLateViewerFromTab(viewer), Math.max(1, tabListRemoveDelayTicks));
    }
  }

  private void removeLateViewerFromTab(Player viewer) {
    if (!viewer.isOnline() || knownViewers.get(viewer.getUniqueId()) != viewer) {
      return;
    }
    nativePlayer.removeFromTab(viewer);
  }

  private List<Player> spawnedViewerPlayers() {
    List<Player> viewers = new ArrayList<>(knownViewers.size());
    for (Player viewer : knownViewers.values()) {
      if (viewer.isOnline()) {
        viewers.add(viewer);
      }
    }

    return viewers;
  }
}
