package art.arcane.adapt.client.hud;

import java.util.Locale;

public enum HudOrientation {
    VERTICAL,
    HORIZONTAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public HudOrientation flipped() {
        return this == VERTICAL ? HORIZONTAL : VERTICAL;
    }

    public static HudOrientation parse(String id, HudOrientation fallback) {
        for (HudOrientation orientation : values()) {
            if (orientation.id().equals(id)) {
                return orientation;
            }
        }
        return fallback;
    }
}
