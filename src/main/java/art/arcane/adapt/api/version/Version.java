package art.arcane.adapt.api.version;

import org.bukkit.inventory.InventoryView;

public class Version {
  public static final boolean SET_TITLE;
  private static final IBindings bindings = new RuntimeBindings();

  static {
    boolean titleMethod = false;
    try {
      InventoryView.class.getDeclaredMethod("setTitle", String.class);
      titleMethod = true;
    } catch (Throwable ignored) {
    }
    SET_TITLE = titleMethod;
  }

  public static IBindings get() {
    return bindings;
  }
}
