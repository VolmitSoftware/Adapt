package art.arcane.adapt.client.protocol;

import java.util.Objects;

/** {@code capabilityMask} is what the server supports; the negotiated mask is its intersection with the client's. */
public record ServerInfo(String platform, String minecraftVersion, int capabilityMask) {
  public ServerInfo {
    Objects.requireNonNull(platform, "platform");
    Objects.requireNonNull(minecraftVersion, "minecraftVersion");
  }
}
