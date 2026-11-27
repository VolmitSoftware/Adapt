package art.arcane.adapt.api.content;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/**
 * A runtime block on one skill or adaptation, placed by an addon. While it stands the content is treated as
 * disabled: hidden from menus, not learnable, inactive for players who hold it. Players keep what they learned.
 *
 * <p>A veto is never saved. It ends when it is lifted, when the addon that placed it is uninstalled, or when the
 * core stops. Admins can forbid vetoes per namespace in {@code adapt.toml}.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface ContentVeto {
  /** The id of the vetoed skill or adaptation. */
  Id content();

  /** The namespace of the addon that placed it. */
  String by();

  /** Whether the veto still stands; false once it ended for any reason. */
  boolean isActive();

  /** Ends the veto. Safe to call twice. */
  void lift();
}
