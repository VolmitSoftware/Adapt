package art.arcane.adapt.api.fx;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ViewerGlowCoordinator {
  private final Map<GlowKey, GlowState> glows = new HashMap<>();
  private final PacketSink packetSink;
  private final ViewerExecutor viewerExecutor;
  private final AtomicBoolean failureLogged = new AtomicBoolean();

  public ViewerGlowCoordinator(GlowingEntities glowingEntities) {
    this(glowingEntities == null ? null : new GlowingPacketSink(glowingEntities), new ScheduledViewerExecutor());
  }

  ViewerGlowCoordinator(PacketSink packetSink) {
    this(packetSink, new DirectViewerExecutor());
  }

  ViewerGlowCoordinator(PacketSink packetSink, ViewerExecutor viewerExecutor) {
    this.packetSink = packetSink;
    this.viewerExecutor = viewerExecutor;
  }

  public synchronized boolean isAvailable() {
    return packetSink != null;
  }

  public synchronized boolean set(Layer layer, Entity entity, Player viewer, ChatColor color) {
    if (layer == null || entity == null || viewer == null || color == null || packetSink == null) {
      return false;
    }

    GlowKey key = new GlowKey(viewer.getUniqueId(), entity.getUniqueId());
    GlowState state = glows.get(key);
    int runtimeEntityId = entity.getEntityId();
    if (state != null && (state.runtimeEntityId != runtimeEntityId || state.entity != entity)) {
      if (!unsetPacket(state.runtimeEntityId, viewer)) {
        return false;
      }
      glows.remove(key);
      state = null;
    }
    if (state == null) {
      state = new GlowState(entity, viewer, runtimeEntityId);
      glows.put(key, state);
    }

    ChatColor previousVisibleColor = state.visibleColor();
    ChatColor previousLayerColor = state.colors.put(layer, color);
    ChatColor desiredVisibleColor = state.visibleColor();
    if (previousVisibleColor == desiredVisibleColor) {
      return true;
    }
    if (setPacket(entity, viewer, desiredVisibleColor)) {
      return true;
    }

    restoreLayer(state, layer, previousLayerColor);
    if (state.colors.isEmpty()) {
      glows.remove(key);
    }
    return false;
  }

  public synchronized boolean unset(Layer layer, Entity entity, Player viewer) {
    if (entity == null) {
      return false;
    }
    return unset(layer, entity.getUniqueId(), entity.getEntityId(), viewer);
  }

  public synchronized boolean unset(Layer layer, UUID targetId, int runtimeEntityId, Player viewer) {
    if (layer == null || targetId == null || viewer == null) {
      return false;
    }

    GlowKey key = new GlowKey(viewer.getUniqueId(), targetId);
    GlowState state = glows.get(key);
    if (state == null || state.runtimeEntityId != runtimeEntityId) {
      return true;
    }

    ChatColor previousVisibleColor = state.visibleColor();
    ChatColor removedColor = state.colors.remove(layer);
    if (removedColor == null) {
      return true;
    }
    if (packetSink == null) {
      restoreLayer(state, layer, removedColor);
      return false;
    }
    if (state.colors.isEmpty()) {
      if (unsetPacket(state.runtimeEntityId, viewer)) {
        glows.remove(key);
        return true;
      }
      restoreLayer(state, layer, removedColor);
      return false;
    }

    ChatColor desiredVisibleColor = state.visibleColor();
    if (previousVisibleColor == desiredVisibleColor || setPacket(state.entity, viewer, desiredVisibleColor)) {
      return true;
    }
    restoreLayer(state, layer, removedColor);
    return false;
  }

  public void clearLayer(Layer layer) {
    if (layer == null) {
      return;
    }
    List<ClearRequest> requests;
    synchronized (this) {
      requests = new ArrayList<>();
      for (Map.Entry<GlowKey, GlowState> entry : glows.entrySet()) {
        GlowState state = entry.getValue();
        if (state.colors.containsKey(layer)) {
          requests.add(new ClearRequest(entry.getKey().targetId(), state.runtimeEntityId, state.viewer));
        }
      }
    }
    for (ClearRequest request : requests) {
      if (!viewerExecutor.execute(request.viewer(),
          () -> unset(layer, request.targetId(), request.runtimeEntityId(), request.viewer()))) {
        discardLayer(layer, request);
      }
    }
  }

  public synchronized void discardViewer(UUID viewerId) {
    if (viewerId != null) {
      glows.keySet().removeIf(key -> key.viewerId().equals(viewerId));
    }
  }

  public synchronized void clear() {
    glows.clear();
  }

  public boolean clearAndAwait(long timeoutMillis) {
    Collection<Player> viewers;
    synchronized (this) {
      Map<UUID, Player> uniqueViewers = new HashMap<>();
      for (Map.Entry<GlowKey, GlowState> entry : glows.entrySet()) {
        uniqueViewers.put(entry.getKey().viewerId(), entry.getValue().viewer);
      }
      viewers = List.copyOf(uniqueViewers.values());
    }
    List<CompletableFuture<Boolean>> completions = new ArrayList<>(viewers.size());
    for (Player viewer : viewers) {
      CompletableFuture<Boolean> completion = new CompletableFuture<>();
      completions.add(completion);
      if (!viewerExecutor.execute(viewer, () -> {
        completion.complete(clearViewer(viewer));
      })) {
        discardViewer(viewer.getUniqueId());
        completion.complete(false);
      }
    }
    if (completions.isEmpty()) {
      return true;
    }

    CompletableFuture<Void> all = CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
    try {
      all.get(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      clear();
      return false;
    } catch (ExecutionException | TimeoutException error) {
      clear();
      return false;
    }
    boolean successful = completions.stream().allMatch(CompletableFuture::join);
    clear();
    return successful;
  }

  synchronized Layer visibleLayer(UUID viewerId, UUID targetId) {
    GlowState state = glows.get(new GlowKey(viewerId, targetId));
    return state == null ? null : state.visibleLayer();
  }

  private void restoreLayer(GlowState state, Layer layer, ChatColor color) {
    if (color == null) {
      state.colors.remove(layer);
    } else {
      state.colors.put(layer, color);
    }
  }

  private synchronized void discardLayer(Layer layer, ClearRequest request) {
    GlowKey key = new GlowKey(request.viewer().getUniqueId(), request.targetId());
    GlowState state = glows.get(key);
    if (state == null || state.runtimeEntityId != request.runtimeEntityId()) {
      return;
    }
    state.colors.remove(layer);
    if (state.colors.isEmpty()) {
      glows.remove(key);
    }
  }

  private synchronized boolean clearViewer(Player viewer) {
    UUID viewerId = viewer.getUniqueId();
    List<GlowKey> viewerGlows = new ArrayList<>();
    for (GlowKey key : glows.keySet()) {
      if (key.viewerId().equals(viewerId)) {
        viewerGlows.add(key);
      }
    }
    boolean successful = true;
    for (GlowKey key : viewerGlows) {
      GlowState state = glows.get(key);
      if (state != null && unsetPacket(state.runtimeEntityId, viewer)) {
        glows.remove(key);
      } else {
        successful = false;
      }
    }
    return successful;
  }

  private boolean setPacket(Entity entity, Player viewer, ChatColor color) {
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        packetSink.set(entity, viewer, color);
      }
      return true;
    } catch (ReflectiveOperationException | IllegalStateException error) {
      reportFailure(error);
      return false;
    }
  }

  private boolean unsetPacket(int runtimeEntityId, Player viewer) {
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        packetSink.unset(runtimeEntityId, viewer);
      }
      return true;
    } catch (ReflectiveOperationException | IllegalStateException error) {
      reportFailure(error);
      return false;
    }
  }

  private void reportFailure(Exception error) {
    if (failureLogged.compareAndSet(false, true)) {
      Adapt.error("Failed to update a private viewer glow.");
      error.printStackTrace();
    }
  }

  public enum Layer {
    STEALTH_SIGHT(100),
    TRAGOUL_DEATH_SENSE(200),
    RANGED_TRAJECTORY_SIGHT(300),
    STEALTH_THREAT(400),
    MUTATION_UMBRAL_ECHO(500),
    TAMING_ALPHAS_COMMAND(600),
    RANGED_HEARTSEEKER(700);

    private final int priority;

    Layer(int priority) {
      this.priority = priority;
    }

    public int priority() {
      return priority;
    }
  }

  interface PacketSink {
    void set(Entity entity, Player viewer, ChatColor color) throws ReflectiveOperationException;

    void unset(int runtimeEntityId, Player viewer) throws ReflectiveOperationException;
  }

  interface ViewerExecutor {
    boolean execute(Player viewer, Runnable task);
  }

  private record GlowKey(UUID viewerId, UUID targetId) {
  }

  private record ClearRequest(UUID targetId, int runtimeEntityId, Player viewer) {
  }

  private static final class GlowState {
    private final Entity entity;
    private final Player viewer;
    private final int runtimeEntityId;
    private final EnumMap<Layer, ChatColor> colors = new EnumMap<>(Layer.class);

    private GlowState(Entity entity, Player viewer, int runtimeEntityId) {
      this.entity = entity;
      this.viewer = viewer;
      this.runtimeEntityId = runtimeEntityId;
    }

    private Layer visibleLayer() {
      Layer visible = null;
      for (Layer layer : colors.keySet()) {
        if (visible == null
            || layer.priority() > visible.priority()
            || (layer.priority() == visible.priority() && layer.ordinal() > visible.ordinal())) {
          visible = layer;
        }
      }
      return visible;
    }

    private ChatColor visibleColor() {
      Layer visible = visibleLayer();
      return visible == null ? null : colors.get(visible);
    }
  }

  private record GlowingPacketSink(GlowingEntities glowingEntities) implements PacketSink {
    @Override
    public void set(Entity entity, Player viewer, ChatColor color) throws ReflectiveOperationException {
      glowingEntities.setGlowing(entity, viewer, color);
    }

    @Override
    public void unset(int runtimeEntityId, Player viewer) throws ReflectiveOperationException {
      glowingEntities.unsetGlowing(runtimeEntityId, viewer);
    }
  }

  private static final class ScheduledViewerExecutor implements ViewerExecutor {
    @Override
    public boolean execute(Player viewer, Runnable task) {
      if (J.isOwnedByCurrentRegion(viewer)) {
        task.run();
        return true;
      }
      return J.runEntity(viewer, task);
    }
  }

  private static final class DirectViewerExecutor implements ViewerExecutor {
    @Override
    public boolean execute(Player viewer, Runnable task) {
      task.run();
      return true;
    }
  }
}
