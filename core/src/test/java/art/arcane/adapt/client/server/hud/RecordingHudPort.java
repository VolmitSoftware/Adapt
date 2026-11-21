package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.api.notification.HudPort;
import art.arcane.adapt.api.notification.HudSegment;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.ArrayList;
import java.util.List;

/** A {@link HudPort} that records every call as a short line. */
public final class RecordingHudPort implements HudPort {
  private final List<String> calls = new ArrayList<>();
  private final List<HudSegment> published = new ArrayList<>();

  public List<String> calls() {
    return List.copyOf(calls);
  }

  public List<HudSegment> published() {
    return List.copyOf(published);
  }

  @Override
  public void publish(PlayerRef player, HudSegment segment) {
    published.add(segment);
    calls.add("publish " + segment.purpose());
  }

  @Override
  public void clear(PlayerRef player, String purpose) {
    calls.add("clear " + purpose);
  }

  @Override
  public void clearAll(PlayerRef player) {
    calls.add("clearAll");
  }

  @Override
  public void shutdown() {
    calls.add("shutdown");
  }
}
