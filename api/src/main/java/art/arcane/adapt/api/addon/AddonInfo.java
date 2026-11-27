package art.arcane.adapt.api.addon;

import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/**
 * What anyone may know about an installed addon. Only the {@link AddonHandle} that {@link AddonRegistry#install}
 * returned to the installing code can refresh or uninstall it.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface AddonInfo {
  /** The namespace the addon was installed under. */
  String namespace();

  /**
   * Where the addon stands. An {@link AddonHandle} reads it live; the values {@link AddonRegistry#find} and
   * {@link AddonRegistry#installed} return are copies taken at the call, so ask again for a later state.
   */
  AddonState state();

  /** Why the addon is {@link AddonState#REJECTED}; empty in every other state. */
  Optional<String> rejection();
}
