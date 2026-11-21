package art.arcane.adapt.client.link;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.client.protocol.FrameLimits;

import java.util.UUID;

/**
 * The platform transport for {@code adapt:ui} frames. Bukkit sends plugin messages; the mod sends an optional custom
 * payload. Installed through {@link AdaptPorts}; {@link #NONE} has no client.
 */
public interface ClientLink {
  ClientLink NONE = new ClientLink() {
    @Override
    public boolean open(UUID playerId) {
      return false;
    }

    @Override
    public void send(UUID playerId, byte[] frame) {
    }
  };

  static ClientLink current() {
    return AdaptPorts.get(ClientLink.class, NONE);
  }

  /** True when the player's connection has the channel registered and can receive frames now. */
  boolean open(UUID playerId);

  /** Sends one payload: a whole frame or a fragment of one. Frames to one player arrive in call order. */
  void send(UUID playerId, byte[] frame);

  /** The largest payload this transport delivers to a client; larger frames are sent as fragments. */
  default int maxPayloadBytes() {
    return FrameLimits.MAX_S2C_BYTES;
  }
}
