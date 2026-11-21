package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.api.notification.HudPort;
import art.arcane.adapt.api.notification.HudSegment;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;
import java.util.UUID;

/**
 * Sends segments to players with the client HUD as frames and gives everyone else to the wrapped port. The check
 * runs per call, so a player who withdraws gets the action bar again from the next publish.
 */
public final class ClientHudPort implements HudPort {
  private final HudPort delegate;

  public ClientHudPort(HudPort delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public void publish(PlayerRef player, HudSegment segment) {
    UUID playerId = player.uuid();
    if (ClientHud.capable(playerId, ClientProtocol.HUD) && ClientHud.send(playerId, ClientProtocol.HUD,
        new ServerFrame.HudPublish(segment.purpose(), segment.priority(), segment.ttlMillis(), segment.slots(),
            segment.text()))) {
      return;
    }
    delegate.publish(player, segment);
  }

  @Override
  public void clear(PlayerRef player, String purpose) {
    UUID playerId = player.uuid();
    if (ClientHud.capable(playerId, ClientProtocol.HUD)
        && ClientHud.send(playerId, ClientProtocol.HUD, new ServerFrame.HudClear(purpose))) {
      return;
    }
    delegate.clear(player, purpose);
  }

  @Override
  public void clearAll(PlayerRef player) {
    delegate.clearAll(player);
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }
}
