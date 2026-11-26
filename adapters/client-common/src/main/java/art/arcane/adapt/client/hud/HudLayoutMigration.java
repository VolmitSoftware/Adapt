package art.arcane.adapt.client.hud;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Older widgets in current terms. Schema 1 had a separate xp feed (dropped: the skills widget now shows activity), a
 * skill tracker that always showed recent skills, resources on by default and abilities above the hotbar. Schema 2
 * glyphs were one fixed row centred 20 units under the crosshair with icon size, count and names only. Toasts before
 * schema 4 stayed 5 seconds by default. A widget or setting still at its old default takes the new default; one the
 * player moved or changed keeps their choice.
 */
final class HudLayoutMigration {
    private static final HudPlacement SKILLS_V1 = v1(HudAnchor.TOP_LEFT, 4, 4, HudOrientation.VERTICAL);
    private static final HudPlacement RESOURCES_V1 = v1(HudAnchor.BOTTOM_RIGHT, -4, -4, HudOrientation.VERTICAL);
    private static final HudPlacement ABILITIES_V1 = v1(HudAnchor.BOTTOM_CENTER, 0, -74, HudOrientation.HORIZONTAL);
    private static final HudPlacement ABILITIES_V2 = v1(HudAnchor.MIDDLE_CENTER, 0, 20, HudOrientation.HORIZONTAL);
    private static final long TOAST_DURATION_V3 = 5000L;

    private HudLayoutMigration() {
    }

    /** The current widget for one written under {@code schema}, or null when it has no place in the new layout. */
    static HudWidgetConfig migrate(int schema, String id, HudWidgetKind kind, HudPlacement placement, JsonObject options) {
        return switch (kind) {
            case SKILLS -> schema < 2 ? skills(id, placement, options)
                    : new HudWidgetConfig(id, kind, placement, HudLayoutCodec.options(kind, options));
            case RESOURCES -> schema < 2 ? resources(id, placement, options)
                    : new HudWidgetConfig(id, kind, placement, HudLayoutCodec.options(kind, options));
            case ABILITIES -> schema < 3 ? abilities(schema, id, placement, options)
                    : new HudWidgetConfig(id, kind, placement, HudLayoutCodec.options(kind, options));
            case NOTICES -> new HudWidgetConfig(id, kind, placement, HudLayoutCodec.options(kind, options));
            case TOASTS -> new HudWidgetConfig(id, kind, placement, schema < 4 ? toasts(options)
                    : HudLayoutCodec.options(kind, options));
        };
    }

    /**
     * Pinned skills stay pinned. A tracker that only followed recent skills becomes the activity widget; extra copies
     * of it would repeat the same rows, so only the base one survives.
     */
    private static HudWidgetConfig skills(String id, HudPlacement placement, JsonObject options) {
        HudOptions.Skills defaults = (HudOptions.Skills) HudDefaults.options(HudWidgetKind.SKILLS);
        List<String> pinned = HudLayoutCodec.strings(options.get("skills"), List.of());
        if (pinned.isEmpty() && !id.equals(HudWidgetKind.SKILLS.id())) {
            return null;
        }
        HudPlacement migrated = same(placement, SKILLS_V1) ? HudDefaults.placement(HudWidgetKind.SKILLS) : placement;
        return new HudWidgetConfig(id, HudWidgetKind.SKILLS, migrated, new HudOptions.Skills(pinned, defaults.maxRows(),
                defaults.lingerMillis(), defaults.showNames(), defaults.iconSize()));
    }

    /** Resources the player never touched turn off; any change they made keeps them as they were. */
    private static HudWidgetConfig resources(String id, HudPlacement placement, JsonObject options) {
        HudOptions resources = HudLayoutCodec.options(HudWidgetKind.RESOURCES, options);
        boolean untouched = id.equals(HudWidgetKind.RESOURCES.id()) && same(placement, RESOURCES_V1) && placement.visible()
                && resources.equals(HudDefaults.options(HudWidgetKind.RESOURCES));
        return new HudWidgetConfig(id, HudWidgetKind.RESOURCES, untouched ? HudDefaults.placement(HudWidgetKind.RESOURCES) : placement,
                resources);
    }

    /** Icon size, count and names carry over; everything else, and an untouched placement, takes the new defaults. */
    private static HudWidgetConfig abilities(int schema, String id, HudPlacement placement, JsonObject options) {
        HudOptions.Abilities defaults = (HudOptions.Abilities) HudDefaults.options(HudWidgetKind.ABILITIES);
        HudPlacement old = schema < 2 ? ABILITIES_V1 : ABILITIES_V2;
        HudPlacement migrated = same(placement, old) ? HudDefaults.placement(HudWidgetKind.ABILITIES).withVisible(placement.visible())
                : placement;
        int icon = schema < 2 ? defaults.iconSize() : (int) HudLayoutCodec.number(options.get("iconSize"), defaults.iconSize());
        int maxShown = (int) HudLayoutCodec.number(options.get("maxShown"), defaults.maxShown());
        boolean names = HudLayoutCodec.bool(options.get("showNames"), defaults.showNames());
        HudOptions.Abilities next = defaults.withSizes(icon, Math.min(icon, defaults.minIconSize()))
                .withLimits(Math.max(defaults.maxShown(), maxShown), defaults.maxRows(), defaults.maxColumns(), defaults.footprint())
                .withFlags(defaults.collapseGroups(), names);
        return new HudWidgetConfig(id, HudWidgetKind.ABILITIES, migrated, next);
    }

    /** A duration still at the old default takes the new one; one the player chose stays, within the new range. */
    private static HudOptions.Toasts toasts(JsonObject options) {
        HudOptions.Toasts defaults = (HudOptions.Toasts) HudDefaults.options(HudWidgetKind.TOASTS);
        HudOptions.Toasts saved = (HudOptions.Toasts) HudLayoutCodec.options(HudWidgetKind.TOASTS, options);
        long duration = saved.durationMillis() == TOAST_DURATION_V3 ? defaults.durationMillis() : saved.durationMillis();
        return new HudOptions.Toasts(duration, saved.maxVisible());
    }

    /** Same spot, size, opacity and orientation; visibility is judged separately. */
    private static boolean same(HudPlacement placement, HudPlacement reference) {
        return placement.anchor() == reference.anchor()
                && Math.abs(placement.offsetX() - reference.offsetX()) < 0.01
                && Math.abs(placement.offsetY() - reference.offsetY()) < 0.01
                && Math.abs(placement.scale() - reference.scale()) < 0.001
                && Math.abs(placement.opacity() - reference.opacity()) < 0.001
                && placement.orientation() == reference.orientation();
    }

    private static HudPlacement v1(HudAnchor anchor, double x, double y, HudOrientation orientation) {
        return new HudPlacement(anchor, x, y, 1.0, 1.0, orientation, true);
    }
}
