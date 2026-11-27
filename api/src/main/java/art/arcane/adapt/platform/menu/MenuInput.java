package art.arcane.adapt.platform.menu;

import org.jetbrains.annotations.ApiStatus;

/**
 * Text typed into a NUMBER or TEXT button. {@code context} names the viewer, session and slot; its kind is
 * {@link ClickKind#OTHER}. Delivered on the viewer's owning thread.
 */
@FunctionalInterface
@ApiStatus.AvailableSince("1.0.0")
public interface MenuInput {
  /** Receives the text the viewer typed. */
  void input(MenuClick context, String text);
}
