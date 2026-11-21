package art.arcane.adapt.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A named HUD arrangement. {@code scale} multiplies every widget's own scale. With {@code autoFit} the HUD compacts and
 * drops low-priority widgets on small screens (see {@link HudFit}). {@code protect} sizes the protected view zone, a
 * centred box of that share of the screen's width and height that no widget is placed or grown into (0 turns it off).
 */
public record HudProfile(String name, double scale, boolean autoFit, double protect, List<HudWidgetConfig> widgets) {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 3.0;
    public static final int MAX_NAME_LENGTH = 32;
    public static final double DEFAULT_PROTECT = 0.15;
    public static final double MAX_PROTECT = 0.4;

    public HudProfile {
        Objects.requireNonNull(name, "name");
        scale = Double.isFinite(scale) ? Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)) : 1.0;
        protect = Double.isFinite(protect) ? Math.max(0.0, Math.min(MAX_PROTECT, protect)) : DEFAULT_PROTECT;
        widgets = List.copyOf(widgets);
    }

    public HudWidgetConfig widget(String id) {
        for (int index = 0; index < widgets.size(); index++) {
            if (widgets.get(index).id().equals(id)) {
                return widgets.get(index);
            }
        }
        return null;
    }

    public HudProfile withName(String newName) {
        return new HudProfile(newName, scale, autoFit, protect, widgets);
    }

    public HudProfile withScale(double newScale) {
        return new HudProfile(name, newScale, autoFit, protect, widgets);
    }

    public HudProfile withAutoFit(boolean value) {
        return new HudProfile(name, scale, value, protect, widgets);
    }

    public HudProfile withProtect(double value) {
        return new HudProfile(name, scale, autoFit, value, widgets);
    }

    /** Replaces the widget with the same id, or appends it. */
    public HudProfile with(HudWidgetConfig widget) {
        List<HudWidgetConfig> next = new ArrayList<>(widgets.size() + 1);
        boolean replaced = false;
        for (HudWidgetConfig current : widgets) {
            if (current.id().equals(widget.id())) {
                next.add(widget);
                replaced = true;
            } else {
                next.add(current);
            }
        }
        if (!replaced) {
            next.add(widget);
        }
        return new HudProfile(name, scale, autoFit, protect, next);
    }

    public HudProfile without(String id) {
        List<HudWidgetConfig> next = new ArrayList<>(widgets.size());
        for (HudWidgetConfig current : widgets) {
            if (!current.id().equals(id)) {
                next.add(current);
            }
        }
        return new HudProfile(name, scale, autoFit, protect, next);
    }

    /** A widget id of the form {@code <kind>_<n>} that no widget in this profile uses yet. */
    public String freshId(HudWidgetKind kind) {
        int number = 2;
        while (widget(kind.id() + "_" + number) != null) {
            number++;
        }
        return kind.id() + "_" + number;
    }
}
