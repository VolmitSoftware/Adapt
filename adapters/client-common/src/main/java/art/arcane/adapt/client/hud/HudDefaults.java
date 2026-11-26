package art.arcane.adapt.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * The default arrangement, in scaled GUI units. Nothing stays on screen by itself: skill rows appear in the top left
 * only while a skill earns xp, ability glyphs sit just under the protected view zone around the crosshair only while
 * something is cooling down or running, and toasts and notices sit under the boss bars (two bars end at y 50). Resources are off until the player
 * turns them on (bottom right). That keeps clear of the hotbar and status rows, chat, vanilla toasts and effect icons
 * (top right) and the scoreboard (middle right). {@link HudFit} trims it on small screens.
 */
public final class HudDefaults {
    public static final String PROFILE_NAME = "Default";

    private HudDefaults() {
    }

    public static HudLayout layout() {
        return new HudLayout(PROFILE_NAME, false, List.of(profile(PROFILE_NAME)));
    }

    public static HudProfile profile(String name) {
        List<HudWidgetConfig> widgets = new ArrayList<>();
        for (HudWidgetKind kind : HudWidgetKind.values()) {
            widgets.add(widget(kind));
        }
        return new HudProfile(name, 1.0, true, HudProfile.DEFAULT_PROTECT, widgets);
    }

    /** The base instance of {@code kind}, whose id is the kind's id. */
    public static HudWidgetConfig widget(HudWidgetKind kind) {
        return new HudWidgetConfig(kind.id(), kind, placement(kind), options(kind));
    }

    public static HudPlacement placement(HudWidgetKind kind) {
        return switch (kind) {
            case SKILLS -> placement(HudAnchor.TOP_LEFT, 4, 4, HudOrientation.VERTICAL);
            case RESOURCES -> new HudPlacement(HudAnchor.BOTTOM_RIGHT, -4, -4, 1.0, 1.0, HudOrientation.VERTICAL, false);
            case ABILITIES -> GlyphArrangement.ROW.preset(placement(HudAnchor.MIDDLE_CENTER, 0, 0, HudOrientation.HORIZONTAL));
            case NOTICES -> placement(HudAnchor.TOP_CENTER, 0, 90, HudOrientation.VERTICAL);
            case TOASTS -> placement(HudAnchor.TOP_CENTER, 0, 54, HudOrientation.VERTICAL);
        };
    }

    public static HudOptions options(HudWidgetKind kind) {
        return switch (kind) {
            case SKILLS -> new HudOptions.Skills(List.of(), 4, 3000L, false, 10);
            case RESOURCES -> new HudOptions.Resources(true, true, true, true);
            case ABILITIES -> new HudOptions.Abilities(GlyphArrangement.ROW, GlyphGrowth.AUTO, 16, 10, 40, 4, 16,
                    GlyphSort.SKILL, true, true, true, true, List.of(), false, 0.3, false);
            case NOTICES -> new HudOptions.Notices(2);
            case TOASTS -> new HudOptions.Toasts(3000L, 1);
        };
    }

    private static HudPlacement placement(HudAnchor anchor, double x, double y, HudOrientation orientation) {
        return new HudPlacement(anchor, x, y, 1.0, 1.0, orientation, true);
    }
}
