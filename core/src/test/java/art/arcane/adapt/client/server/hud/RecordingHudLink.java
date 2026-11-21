package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.client.protocol.ServerFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A {@link HudLink} that records frames and lets a test grant or withdraw capability bits per player. */
public final class RecordingHudLink implements HudLink {
  private final Map<UUID, Integer> masks = new ConcurrentHashMap<>();
  private final List<ServerFrame> frames = new ArrayList<>();
  private boolean open = true;

  public void grant(UUID playerId, int mask) {
    masks.put(playerId, mask);
  }

  public void withdraw(UUID playerId) {
    masks.remove(playerId);
  }

  public void close() {
    open = false;
  }

  public synchronized List<ServerFrame> frames() {
    return List.copyOf(frames);
  }

  @Override
  public boolean capable(UUID playerId, int capabilityBit) {
    Integer mask = masks.get(playerId);
    return mask != null && (mask & capabilityBit) == capabilityBit;
  }

  @Override
  public synchronized boolean send(UUID playerId, ServerFrame frame) {
    if (!open || !masks.containsKey(playerId)) {
      return false;
    }
    frames.add(frame);
    return true;
  }
}
