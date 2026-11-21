package art.arcane.adapt.client;

/**
 * The rule for a binding whose default is Shift plus a key, on loaders without key modifiers: Shift belongs to the
 * binding only while it sits on its default key, so a player who rebinds it gets exactly the key they pressed.
 */
public final class ShiftChord {
    public static final String LABEL = "key.adapt.shift_chord";

    private ShiftChord() {
    }

    /**
     * Whether a press of the binding's own key, dispatched to key bindings, belongs to the binding instead of whatever
     * else uses that key.
     */
    public static boolean claims(boolean atDefault, boolean shiftDown) {
        return atDefault && shiftDown;
    }

    /** Whether the binding sits in the vanilla key table, where a plain press of its key reaches it. */
    public static boolean plainKey(boolean atDefault) {
        return !atDefault;
    }
}
