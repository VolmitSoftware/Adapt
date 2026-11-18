package art.arcane.adapt.platform.event.server;

import art.arcane.adapt.platform.event.AdaptEvent;

/** Data packs, tags and recipes were reloaded. Read-only. */
public class ResourcesReloadedEvent extends AdaptEvent {
  private final Cause cause;

  public ResourcesReloadedEvent(Cause cause) {
    this.cause = cause == null ? Cause.UNKNOWN : cause;
  }

  public Cause cause() {
    return cause;
  }

  public enum Cause {
    COMMAND,
    PLUGIN,
    UNKNOWN
  }
}
