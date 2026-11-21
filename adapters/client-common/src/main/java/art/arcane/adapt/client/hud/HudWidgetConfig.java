package art.arcane.adapt.client.hud;

import java.util.Objects;

/** One widget instance in a profile. {@code id} is unique within the profile. */
public record HudWidgetConfig(String id, HudWidgetKind kind, HudPlacement placement, HudOptions options) {
    public HudWidgetConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(options, "options");
        if (options.kind() != kind) {
            throw new IllegalArgumentException("options for " + options.kind() + " on a " + kind + " widget");
        }
    }

    public HudWidgetConfig withPlacement(HudPlacement newPlacement) {
        return new HudWidgetConfig(id, kind, newPlacement, options);
    }

    public HudWidgetConfig withOptions(HudOptions newOptions) {
        return new HudWidgetConfig(id, kind, placement, newOptions);
    }
}
