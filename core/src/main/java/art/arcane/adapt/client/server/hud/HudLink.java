package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.client.link.ClientSessions;
import art.arcane.adapt.client.protocol.ServerFrame;

import java.util.Objects;
import java.util.UUID;

/** What the HUD needs from the client session: a capability check and a frame send. */
public interface HudLink {
  HudLink NONE = new HudLink() {
    @Override
    public boolean capable(UUID playerId, int capabilityBit) {
      return false;
    }

    @Override
    public boolean send(UUID playerId, ServerFrame frame) {
      return false;
    }
  };

  static HudLink of(ClientSessions sessions) {
    Objects.requireNonNull(sessions, "sessions");
    return new HudLink() {
      @Override
      public boolean capable(UUID playerId, int capabilityBit) {
        return sessions.has(playerId, capabilityBit);
      }

      @Override
      public boolean send(UUID playerId, ServerFrame frame) {
        return sessions.send(playerId, frame);
      }
    };
  }

  /** True when the player negotiated the capability bit and has not withdrawn. Must be cheap. */
  boolean capable(UUID playerId, int capabilityBit);

  /** Sends one frame; false when the player has no open client channel. */
  boolean send(UUID playerId, ServerFrame frame);
}
