package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** An addon's content was removed from the running core. */
@ApiStatus.AvailableSince("1.0.0")
public final class AddonUninstalledEvent extends AdaptEvent {
  private final String namespace;

  public AddonUninstalledEvent(String namespace) {
    this.namespace = Objects.requireNonNull(namespace, "namespace");
  }

  /** The namespace of the addon whose content was removed. */
  public String namespace() {
    return namespace;
  }
}
