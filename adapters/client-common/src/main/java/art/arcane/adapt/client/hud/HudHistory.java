package art.arcane.adapt.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Undo and redo for one editing session. Consecutive commits with the same non-null key (repeated nudges or wheel
 * steps on one widget) merge into one step.
 */
public final class HudHistory {
    public static final int LIMIT = 100;

    private final List<HudLayout> states = new ArrayList<>();
    private int index;
    private String lastKey;

    public HudHistory(HudLayout initial) {
        states.add(Objects.requireNonNull(initial, "initial"));
    }

    public HudLayout current() {
        return states.get(index);
    }

    public void commit(HudLayout next, String key) {
        if (next.equals(current())) {
            return;
        }
        while (states.size() > index + 1) {
            states.remove(states.size() - 1);
        }
        if (key != null && key.equals(lastKey) && index > 0) {
            states.set(index, next);
            return;
        }
        states.add(next);
        index++;
        if (states.size() > LIMIT) {
            states.remove(0);
            index--;
        }
        lastKey = key;
    }

    public boolean canUndo() {
        return index > 0;
    }

    public boolean canRedo() {
        return index < states.size() - 1;
    }

    public HudLayout undo() {
        if (canUndo()) {
            index--;
        }
        lastKey = null;
        return current();
    }

    public HudLayout redo() {
        if (canRedo()) {
            index++;
        }
        lastKey = null;
        return current();
    }
}
