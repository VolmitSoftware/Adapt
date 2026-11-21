package art.arcane.adapt.client.ui;

import java.util.Locale;

/** Held modifier keys. {@code command} is the Command key on macOS and the Super/Windows key elsewhere. */
public record UiModifiers(boolean shift, boolean control, boolean alt, boolean command) {
    public static final UiModifiers NONE = new UiModifiers(false, false, false, false);
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    /** The modifier for copy, paste and select-all: Command on macOS, Control elsewhere. */
    public boolean shortcut() {
        return MAC ? command : control;
    }
}
