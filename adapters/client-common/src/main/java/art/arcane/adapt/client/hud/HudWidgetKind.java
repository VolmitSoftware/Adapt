package art.arcane.adapt.client.hud;

import java.util.Locale;

public enum HudWidgetKind {
    SKILLS,
    RESOURCES,
    ABILITIES,
    NOTICES,
    TOASTS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "adapt.hud.widget." + id();
    }

    /** Whether the player may add more instances of this widget. */
    public boolean splittable() {
        return this == RESOURCES || this == SKILLS || this == ABILITIES;
    }

    public static HudWidgetKind parse(String id) {
        for (HudWidgetKind kind : values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
