package art.arcane.adapt.api.addon;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

/** The installed addons. Available at any time, also before Adapt itself has started. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface AddonRegistry {
  /**
   * Installs an addon and, if a core is running, registers its content at once.
   *
   * @throws IllegalArgumentException when the namespace is malformed, reserved, or already installed; the message
   *                                  names the addon that holds it
   */
  AddonHandle install(AdaptAddon addon);

  /** The installed addon with exactly this namespace; empty when none is installed. */
  Optional<AddonInfo> find(String namespace);

  /** Installed addons in the order their content is applied: {@code adapt} first, then install order. */
  List<AddonInfo> installed();
}
