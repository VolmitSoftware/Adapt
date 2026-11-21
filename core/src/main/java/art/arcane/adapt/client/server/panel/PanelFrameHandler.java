package art.arcane.adapt.client.server.panel;

import art.arcane.adapt.client.protocol.ClientFrame;

import java.util.UUID;

/**
 * Inbound panel traffic for {@link PanelSessions}. Calls may arrive on any thread; work runs on the viewer's owning
 * thread.
 */
public interface PanelFrameHandler {
  void click(UUID viewer, ClientFrame.PanelClick click);

  void input(UUID viewer, ClientFrame.PanelInput input);

  void closed(UUID viewer, ClientFrame.PanelClosed closed);

  /** The viewer left the server. Call from the quit event, on the viewer's owning thread. */
  void quit(UUID viewer);

  /** The viewer's client withdrew the panel capability; an open panel continues as a chest menu. */
  void withdrawn(UUID viewer);
}
