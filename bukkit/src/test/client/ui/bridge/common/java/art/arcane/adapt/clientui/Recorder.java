package art.arcane.adapt.clientui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Keeps the last vanilla action bar messages and toasts the client showed, for checks on what reached vanilla UI. */
public final class Recorder {
    private static final int LIMIT = 64;
    private static final Deque<JsonObject> ACTION_BAR = new ArrayDeque<>();
    private static final Deque<JsonObject> TOASTS = new ArrayDeque<>();

    private Recorder() {
    }

    public static synchronized void actionBar(Component message, boolean animate) {
        JsonObject entry = stamp();
        entry.addProperty("text", message == null ? "" : message.getString());
        entry.addProperty("animated", animate);
        add(ACTION_BAR, entry);
    }

    public static synchronized void toast(Toast toast) {
        JsonObject entry = stamp();
        entry.addProperty("kind", kind(toast));
        entry.addProperty("type", toast.getClass().getSimpleName());
        entry.addProperty("class", toast.getClass().getName());
        String[] text = ToastText.describe(toast);
        if (text != null) {
            entry.addProperty("title", text[0]);
            entry.addProperty("description", text[1]);
        }
        add(TOASTS, entry);
    }

    private static String kind(Toast toast) {
        if (toast instanceof AdvancementToast) {
            return "advancement";
        }
        if (toast instanceof SystemToast) {
            return "system";
        }
        if (toast instanceof RecipeToast) {
            return "recipe";
        }
        if (toast instanceof TutorialToast) {
            return "tutorial";
        }
        return "other";
    }

    static String plain(List<FormattedCharSequence> lines) {
        if (lines == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (FormattedCharSequence line : lines) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            line.accept((index, style, codePoint) -> {
                builder.appendCodePoint(codePoint);
                return true;
            });
        }
        return builder.toString();
    }

    static String plain(Component component) {
        return component == null ? "" : component.getString();
    }

    static synchronized JsonObject snapshot() {
        JsonObject events = new JsonObject();
        events.add("actionbar", array(ACTION_BAR));
        events.add("toasts", array(TOASTS));
        return events;
    }

    static synchronized void clear() {
        ACTION_BAR.clear();
        TOASTS.clear();
    }

    private static JsonObject stamp() {
        JsonObject entry = new JsonObject();
        entry.addProperty("time", System.currentTimeMillis());
        entry.addProperty("tick", UiBridge.ticks());
        return entry;
    }

    private static synchronized void add(Deque<JsonObject> events, JsonObject entry) {
        events.addLast(entry);
        while (events.size() > LIMIT) {
            events.pollFirst();
        }
    }

    private static JsonArray array(Deque<JsonObject> events) {
        JsonArray result = new JsonArray();
        events.forEach(result::add);
        return result;
    }
}
