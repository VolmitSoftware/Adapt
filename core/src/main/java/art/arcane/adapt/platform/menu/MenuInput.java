package art.arcane.adapt.platform.menu;

/**
 * Text typed into a NUMBER or TEXT button. {@code context} names the viewer, session and slot; its kind is
 * {@link ClickKind#OTHER}. Delivered on the viewer's owning thread.
 */
@FunctionalInterface
public interface MenuInput {
  void input(MenuClick context, String text);
}
