package art.arcane.adapt.client.protocol;

/** Channel, version and capability bits of the optional Adapt client protocol. */
public final class ClientProtocol {
  public static final String NAMESPACE = "adapt";
  public static final String PATH = "ui";
  public static final String CHANNEL = NAMESPACE + ":" + PATH;
  public static final int VERSION = 1;

  public static final int SCREENS = 1;
  public static final int PANEL = 1 << 1;
  public static final int HUD = 1 << 2;
  public static final int TOASTS = 1 << 3;
  public static final int ITEMS = 1 << 4;
  public static final int INSIGHT = 1 << 5;
  /** The client reports its jump key ({@link ClientFrame.JumpKey}); only served where the server sees no jump input. */
  public static final int JUMPS = 1 << 6;
  public static final int ALL = SCREENS | PANEL | HUD | TOASTS | ITEMS | INSIGHT | JUMPS;

  private ClientProtocol() {
  }
}
