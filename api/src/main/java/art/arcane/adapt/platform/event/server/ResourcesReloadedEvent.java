package art.arcane.adapt.platform.event.server;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

/** Data packs, tags and recipes were reloaded. Posted on Paper and modded platforms, not on Spigot. Read-only. */
@ApiStatus.AvailableSince("1.0.0")
public class ResourcesReloadedEvent extends AdaptEvent {
  private final Cause cause;

  public ResourcesReloadedEvent(Cause cause) {
    this.cause = cause == null ? Cause.UNKNOWN : cause;
  }

  /** What started the reload. */
  public Cause cause() {
    return cause;
  }

  /**
   * What started a resource reload.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Cause {
    /** A reload command. */
    COMMAND,
    /** Any other request, such as a plugin's. */
    PLUGIN,
    /** The platform did not say. */
    UNKNOWN
  }
}
