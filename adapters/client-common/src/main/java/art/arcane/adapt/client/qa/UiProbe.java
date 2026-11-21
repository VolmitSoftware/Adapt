package art.arcane.adapt.client.qa;

import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What the open Adapt screen and the HUD show, as JSON, for the test bridge. Screens and the HUD publish from the
 * render thread; {@link #snapshot()} may be called from any thread and returns the latest published state. Coordinates
 * are scaled GUI units.
 */
public final class UiProbe {
    public static final String NO_SCREEN = "{\"screen\":null}";

    private static final AtomicReference<Published> LATEST = new AtomicReference<>();
    private static volatile boolean watched;
    private static volatile String hud;
    private static volatile String insight;

    private UiProbe() {
    }

    /**
     * The open screen as {@code {"screen", "title", "width", "height", "guiScale", "focused", "hovered", "state",
     * "widgets": [{"id", "kind", "label", "layer", "x", "y", "w", "h", "enabled", "focused", "hovered", "state"}]}},
     * or {@code {"screen":null}}, plus {@code "hud"} with the HUD layout once the HUD has published one and
     * {@code "insight"} with the last Insight plate. The first call also asks open screens and the HUD to keep the
     * snapshot fresh.
     */
    public static String snapshot() {
        watched = true;
        Published published = LATEST.get();
        String screen = published == null ? NO_SCREEN : published.json();
        String layout = hud;
        String plate = insight;
        if (layout == null && plate == null || !screen.endsWith("}")) {
            return screen;
        }
        StringBuilder out = new StringBuilder(screen.length() + 256).append(screen, 0, screen.length() - 1);
        if (layout != null) {
            out.append(",\"hud\":").append(layout);
        }
        if (plate != null) {
            out.append(",\"insight\":").append(plate);
        }
        return out.append('}').toString();
    }

    /** Replaces the HUD layout reported with every snapshot; null stops reporting it. */
    public static void hud(String json) {
        hud = json;
    }

    /** Replaces the Insight plate reported with every snapshot; null stops reporting it. */
    public static void insight(String json) {
        insight = json;
    }

    /** Whether anything has read a snapshot, so screens refresh it every few frames. */
    public static boolean watched() {
        return watched;
    }

    public static void publish(Object owner, String json) {
        LATEST.set(new Published(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(json, "json")));
    }

    /** Clears the snapshot if {@code owner} published it last. */
    public static void withdraw(Object owner) {
        LATEST.updateAndGet((Published current) -> current != null && current.owner() == owner ? null : current);
    }

    /** One screen as the probe reports it. Overlays are listed after the screen's own widgets. */
    public record Scene(String screen, String title, Map<String, String> state, int width, int height, double guiScale, UiNode root,
                        List<? extends UiNode> overlays, UiNode focused, UiNode hovered) {
    }

    public static String json(Scene scene) {
        StringBuilder out = new StringBuilder(2048);
        out.append("{\"screen\":");
        string(out, scene.screen());
        out.append(",\"title\":");
        string(out, scene.title());
        out.append(",\"width\":").append(scene.width());
        out.append(",\"height\":").append(scene.height());
        out.append(",\"guiScale\":").append(scene.guiScale());
        out.append(",\"focused\":");
        string(out, scene.focused() == null ? null : scene.focused().id());
        out.append(",\"hovered\":");
        string(out, scene.hovered() == null ? null : scene.hovered().id());
        out.append(",\"state\":");
        map(out, scene.state());
        out.append(",\"widgets\":[");
        UiRect screen = new UiRect(0, 0, scene.width(), scene.height());
        boolean[] first = {true};
        for (UiNode child : scene.root().children()) {
            widgets(out, child, screen, "screen", scene, first);
        }
        for (UiNode overlay : scene.overlays()) {
            widgets(out, overlay, screen, "overlay", scene, first);
        }
        out.append("]}");
        return out.toString();
    }

    private static void widgets(StringBuilder out, UiNode node, UiRect clip, String layer, Scene scene, boolean[] first) {
        if (!node.visible()) {
            return;
        }
        UiRect bounds = node.bounds();
        if (!bounds.isEmpty() && bounds.intersects(clip)) {
            if (!first[0]) {
                out.append(',');
            }
            first[0] = false;
            Map<String, String> state = new LinkedHashMap<>();
            node.describe(state);
            out.append("{\"id\":");
            string(out, node.id());
            out.append(",\"kind\":");
            string(out, node.kind());
            out.append(",\"label\":");
            string(out, node.label());
            out.append(",\"layer\":");
            string(out, layer);
            out.append(",\"x\":").append(bounds.x());
            out.append(",\"y\":").append(bounds.y());
            out.append(",\"w\":").append(bounds.width());
            out.append(",\"h\":").append(bounds.height());
            out.append(",\"enabled\":").append(node.enabled());
            out.append(",\"focused\":").append(node == scene.focused());
            out.append(",\"hovered\":").append(node == scene.hovered());
            out.append(",\"state\":");
            map(out, state);
            out.append('}');
        }
        UiRect inner = node.clipsChildren() ? clip.intersection(bounds) : clip;
        for (UiNode child : node.children()) {
            widgets(out, child, inner, layer, scene, first);
        }
    }

    private static void map(StringBuilder out, Map<String, String> values) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            string(out, entry.getKey());
            out.append(':');
            string(out, entry.getValue());
        }
        out.append('}');
    }

    private static void string(StringBuilder out, String value) {
        if (value == null) {
            out.append("null");
            return;
        }
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private record Published(Object owner, String json) {
    }
}
