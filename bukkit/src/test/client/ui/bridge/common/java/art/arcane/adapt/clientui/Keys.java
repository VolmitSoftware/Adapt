package art.arcane.adapt.clientui;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Keys {
    private static final Set<Integer> HELD = ConcurrentHashMap.newKeySet();

    private Keys() {
    }

    static int code(String name) {
        String key = name.startsWith("key.") ? name : "key.keyboard." + name;
        int value = InputConstants.getKey(key).getValue();
        if (value == InputConstants.UNKNOWN.getValue()) {
            throw new IllegalArgumentException("Unknown key " + name);
        }
        return value;
    }

    static void hold(int key, boolean down) {
        if (down) {
            HELD.add(key);
        } else {
            HELD.remove(key);
        }
    }

    static void clear() {
        HELD.clear();
    }

    public static boolean held(int key) {
        return HELD.contains(key);
    }
}
