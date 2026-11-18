package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.VirtualPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PacketPlayerDecoy {
  private final VirtualPlayer nativePlayer;
  private final long removeTabAt;
  private final int tabListRemoveDelayTicks;
  private final Map<UUID, PlayerRef> knownViewers;
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

  public void refresh(Loc anchor, boolean onGround, Collection<PlayerRef> trackedViewers, int maxViewers,
                      int maxViewerAdds, int maxLookUpdates, double eyeHeight) {
    refreshViewerState(trackedViewers, maxViewers, maxViewerAdds);
    tick();
    syncToAnchor(anchor, onGround);
    lookAtViewers(anchor.add(0, eyeHeight, 0), maxLookUpdates);
  }

  private void tick() {
    if (removeTabAt < 0 || removedFromTab || System.currentTimeMillis() < removeTabAt) {
      return;
    }

    for (PlayerRef viewer : spawnedViewerPlayers()) {
      nativePlayer.removeFromTab(viewer);
    }

    removedFromTab = true;
  }

  private void lookAtViewers(Loc origin, int maxUpdates) {
    List<PlayerRef> viewers = spawnedViewerPlayers();
    if (viewers.isEmpty()) {
      return;
    }

    int updates = Math.min(Math.max(1, maxUpdates), viewers.size());
    int start = Math.floorMod(lookCursor, viewers.size());
    for (int offset = 0; offset < updates; offset++) {
      PlayerRef viewer = viewers.get((start + offset) % viewers.size());
      Tasks.runEntity(viewer, () -> lookAtViewer(origin, viewer));
    }
    lookCursor = (start + updates) % viewers.size();
  }

  private void lookAtViewer(Loc origin, PlayerRef viewer) {
    Loc to = viewer.eyeLocation();
    if (!origin.sameWorld(to)) {
      return;
    }

    double dx = to.x() - origin.x();
    double dy = to.y() - origin.y();
    double dz = to.z() - origin.z();
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
    nativePlayer.look(yaw, pitch, viewer);
  }

  private void syncToAnchor(Loc anchor, boolean onGround) {
    long now = System.currentTimeMillis();
    double dx = Double.isFinite(lastX) ? anchor.x() - lastX : 1;
    double dy = Double.isFinite(lastY) ? anchor.y() - lastY : 1;
    double dz = Double.isFinite(lastZ) ? anchor.z() - lastZ : 1;
    double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
    float yawDiff = Float.isFinite(lastYaw) ? Math.abs(anchor.yaw() - lastYaw) : 360f;
    float pitchDiff = Float.isFinite(lastPitch) ? Math.abs(anchor.pitch() - lastPitch) : 360f;

    if (distanceSq < 0.0004 && yawDiff < 0.8f && pitchDiff < 0.8f && now - lastPositionSyncAt < 500L) {
      return;
    }

    if (nativePlayer.move(anchor, onGround, spawnedViewerPlayers())) {
      lastPositionSyncAt = now;
      lastX = anchor.x();
      lastY = anchor.y();
      lastZ = anchor.z();
      lastYaw = anchor.yaw();
      lastPitch = anchor.pitch();
    }
  }

  public void hitFrom(Loc source) {
    if (!Double.isFinite(lastX) || !Double.isFinite(lastZ)) {
      nativePlayer.hurt(0f, spawnedViewerPlayers());
      return;
    }

    double dx = source.x() - lastX;
    double dz = source.z() - lastZ;
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    nativePlayer.hurt(yaw, spawnedViewerPlayers());
  }

  public void destroy() {
    for (PlayerRef viewer : spawnedViewerPlayers()) {
      nativePlayer.destroy(viewer);
    }

    knownViewers.clear();
  }

  private void refreshViewerState(Collection<PlayerRef> trackedViewers, int maxViewers, int maxViewerAdds) {
    Set<UUID> trackedViewerIds = new HashSet<>();
    for (PlayerRef viewer : trackedViewers) {
      trackedViewerIds.add(viewer.uuid());
    }
    for (Map.Entry<UUID, PlayerRef> entry : knownViewers.entrySet()) {
      PlayerRef viewer = entry.getValue();
      if (viewer.isOnline() && trackedViewerIds.contains(entry.getKey())) {
        continue;
      }
      if (knownViewers.remove(entry.getKey(), viewer)) {
        destroyFor(viewer);
      }
    }

    int viewerLimit = Math.max(1, maxViewers);
    int remainingAdds = Math.max(1, maxViewerAdds);
    for (PlayerRef viewer : trackedViewers) {
      if (knownViewers.size() >= viewerLimit || remainingAdds <= 0) {
        return;
      }
      if (!viewer.isOnline() || knownViewers.putIfAbsent(viewer.uuid(), viewer) != null) {
        continue;
      }

      spawnFor(viewer);
      remainingAdds--;
    }
  }

  private void destroyFor(PlayerRef viewer) {
    nativePlayer.destroy(viewer);
  }

  private void spawnFor(PlayerRef viewer) {
    nativePlayer.spawn(viewer);

    if (removedFromTab) {
      Tasks.runEntity(viewer, () -> removeLateViewerFromTab(viewer), Math.max(1, tabListRemoveDelayTicks));
    }
  }

  private void removeLateViewerFromTab(PlayerRef viewer) {
    if (!viewer.isOnline() || !viewer.equals(knownViewers.get(viewer.uuid()))) {
      return;
    }
    nativePlayer.removeFromTab(viewer);
  }

  private List<PlayerRef> spawnedViewerPlayers() {
    List<PlayerRef> viewers = new ArrayList<>(knownViewers.size());
    for (PlayerRef viewer : knownViewers.values()) {
      if (viewer.isOnline()) {
        viewers.add(viewer);
      }
    }

    return viewers;
  }
}
