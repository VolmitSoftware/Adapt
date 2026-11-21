package art.arcane.adapt.client.server.panel;

import art.arcane.adapt.client.link.ClientSessions;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;

import java.util.UUID;
import java.util.function.Supplier;

/** What the panel host needs from the client session service. Safe to call from any thread. */
public interface PanelLink {
  PanelLink NONE = new PanelLink() {
    @Override
    public boolean capable(UUID viewer) {
      return false;
    }

    @Override
    public boolean send(UUID viewer, ServerFrame frame) {
      return false;
    }
  };

  /** True when the viewer's client negotiated {@link ClientProtocol#PANEL}. */
  boolean capable(UUID viewer);

  /** Returns false when the frame could not be sent. */
  boolean send(UUID viewer, ServerFrame frame);

  /** Reads the service lazily, so the host can be installed before the service exists; null means no clients. */
  static PanelLink of(Supplier<ClientSessions> sessions) {
    return new PanelLink() {
      @Override
      public boolean capable(UUID viewer) {
        ClientSessions active = sessions.get();
        return active != null && active.has(viewer, ClientProtocol.PANEL);
      }

      @Override
      public boolean send(UUID viewer, ServerFrame frame) {
        ClientSessions active = sessions.get();
        return active != null && active.send(viewer, frame);
      }
    };
  }
}
