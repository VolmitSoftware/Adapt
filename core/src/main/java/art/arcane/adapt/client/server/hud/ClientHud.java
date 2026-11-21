package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.client.protocol.ServerFrame;

import java.util.Objects;
import java.util.UUID;

/**
 * The installed {@link HudLink}. {@link HudLink#NONE} until the client session service installs one, so every
 * check is a volatile read and a false for servers and players without the client.
 */
public final class ClientHud {
  private static volatile HudLink link = HudLink.NONE;

  private ClientHud() {
  }

  public static void install(HudLink installed) {
    link = Objects.requireNonNull(installed, "installed");
  }

  public static void uninstall() {
    link = HudLink.NONE;
  }

  public static boolean capable(UUID playerId, int capabilityBit) {
    return link.capable(playerId, capabilityBit);
  }

  /** Sends when the player has the capability; false means the caller keeps its fallback output. */
  public static boolean send(UUID playerId, int capabilityBit, ServerFrame frame) {
    HudLink active = link;
    return active.capable(playerId, capabilityBit) && active.send(playerId, frame);
  }
}
