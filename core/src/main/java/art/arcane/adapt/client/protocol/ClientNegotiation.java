package art.arcane.adapt.client.protocol;

import java.util.Optional;

public final class ClientNegotiation {
  private ClientNegotiation() {
  }

  /** Empty when the client withdrew (version 0 or less); otherwise the lower version and the shared capability bits. */
  public static Optional<ClientCapability> accept(int clientVersion, int clientMask, int serverMask) {
    if (clientVersion < 1) {
      return Optional.empty();
    }
    return Optional.of(new ClientCapability(Math.min(clientVersion, ClientProtocol.VERSION),
        clientMask & serverMask & ClientProtocol.ALL));
  }
}
