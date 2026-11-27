package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** An addon's content was registered in the running core. Fired again after each refresh and each core start. */
@ApiStatus.AvailableSince("1.0.0")
public final class AddonInstalledEvent extends AdaptEvent {
  private final String namespace;

  public AddonInstalledEvent(String namespace) {
    this.namespace = Objects.requireNonNull(namespace, "namespace");
  }

  /** The namespace of the addon whose content was registered. */
  public String namespace() {
    return namespace;
  }
}
