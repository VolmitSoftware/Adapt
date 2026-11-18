package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Layered per-viewer glow on {@code ViewerHost}: the highest layer's colour is sent, removing it reveals the next. */
public final class LayeredViewerGlow implements ViewerGlowPort {
  public static final LayeredViewerGlow INSTANCE = new LayeredViewerGlow();
  private final Map<GlowKey, GlowState> glows = new HashMap<>();

  private LayeredViewerGlow() {
  }

  @Override
  public boolean isAvailable() {
    return AdaptCore.current() != null;
  }

  @Override
  public synchronized boolean set(Layer layer, EntityRef entity, PlayerRef viewer, GlowColor color) {
    if (layer == null || entity == null || viewer == null || color == null || !isAvailable()) {
      return false;
    }
    GlowKey key = new GlowKey(viewer.uuid(), entity.uuid());
    GlowState state = glows.get(key);
    int runtimeEntityId = entity.entityId();
    if (state != null && state.runtimeEntityId != runtimeEntityId) {
      AdaptCore.platform().viewers().clearGlow(viewer, state.runtimeEntityId);
      glows.remove(key);
      state = null;
    }
    if (state == null) {
      state = new GlowState(entity, runtimeEntityId);
      glows.put(key, state);
    }
    GlowColor previous = state.visibleColor();
    state.colors.put(layer, color);
    GlowColor desired = state.visibleColor();
    if (previous != desired) {
      AdaptCore.platform().viewers().setGlow(viewer, entity, desired);
    }
    return true;
  }

  @Override
  public synchronized boolean unset(Layer layer, EntityRef entity, PlayerRef viewer) {
    return entity != null && unset(layer, entity.uuid(), entity.entityId(), viewer);
  }

  @Override
  public synchronized boolean unset(Layer layer, UUID targetId, int runtimeEntityId, PlayerRef viewer) {
    if (layer == null || targetId == null || viewer == null) {
      return false;
    }
    GlowKey key = new GlowKey(viewer.uuid(), targetId);
    GlowState state = glows.get(key);
    if (state == null || state.runtimeEntityId != runtimeEntityId) {
      return true;
    }
    GlowColor previous = state.visibleColor();
    if (state.colors.remove(layer) == null) {
      return true;
    }
    if (!isAvailable()) {
      return false;
    }
    if (state.colors.isEmpty()) {
      glows.remove(key);
      AdaptCore.platform().viewers().clearGlow(viewer, runtimeEntityId);
      return true;
    }
    GlowColor desired = state.visibleColor();
    if (previous != desired) {
      AdaptCore.platform().viewers().setGlow(viewer, state.entity, desired);
    }
    return true;
  }

  @Override
  public void clearLayer(Layer layer) {
    if (layer == null) {
      return;
    }
    List<Clear> requests = new ArrayList<>();
    synchronized (this) {
      for (Map.Entry<GlowKey, GlowState> entry : glows.entrySet()) {
        if (entry.getValue().colors.containsKey(layer)) {
          requests.add(new Clear(entry.getKey().viewerId(), entry.getKey().targetId(), entry.getValue().runtimeEntityId));
        }
      }
    }
    for (Clear request : requests) {
      PlayerRef viewer = isAvailable() ? AdaptCore.platform().host().player(request.viewerId()) : null;
      if (viewer == null || !viewer.isOnline()) {
        discard(layer, request);
        continue;
      }
      Runnable unset = () -> unset(layer, request.targetId(), request.runtimeEntityId(), viewer);
      if (Tasks.isOwnedByCurrentRegion(viewer)) {
        unset.run();
      } else if (!Tasks.runEntity(viewer, unset)) {
        discard(layer, request);
      }
    }
  }

  @Override
  public synchronized void discardViewer(UUID viewerId) {
    if (viewerId != null) {
      glows.keySet().removeIf((GlowKey key) -> key.viewerId().equals(viewerId));
    }
  }

  private synchronized void discard(Layer layer, Clear request) {
    GlowKey key = new GlowKey(request.viewerId(), request.targetId());
    GlowState state = glows.get(key);
    if (state == null || state.runtimeEntityId != request.runtimeEntityId()) {
      return;
    }
    state.colors.remove(layer);
    if (state.colors.isEmpty()) {
      glows.remove(key);
    }
  }

  private record GlowKey(UUID viewerId, UUID targetId) {
  }

  private record Clear(UUID viewerId, UUID targetId, int runtimeEntityId) {
  }

  private static final class GlowState {
    private final EntityRef entity;
    private final int runtimeEntityId;
    private final EnumMap<Layer, GlowColor> colors = new EnumMap<>(Layer.class);

    private GlowState(EntityRef entity, int runtimeEntityId) {
      this.entity = entity;
      this.runtimeEntityId = runtimeEntityId;
    }

    private GlowColor visibleColor() {
      Layer visible = null;
      for (Layer layer : colors.keySet()) {
        if (visible == null || layer.priority() > visible.priority()
            || (layer.priority() == visible.priority() && layer.ordinal() > visible.ordinal())) {
          visible = layer;
        }
      }
      return visible == null ? null : colors.get(visible);
    }
  }
}
