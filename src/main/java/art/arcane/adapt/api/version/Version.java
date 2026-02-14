package art.arcane.adapt.api.version;

import org.bukkit.inventory.InventoryView;

public class Version {
    private static final IBindings bindings = new RuntimeBindings();
    public static final boolean SET_TITLE;

    public static IBindings get() {
        return bindings;
    }

    static {
        boolean titleMethod = false;
        try {
            InventoryView.class.getDeclaredMethod("setTitle", String.class);
            titleMethod = true;
        } catch (Throwable ignored) {}
        SET_TITLE = titleMethod;
    }
}
