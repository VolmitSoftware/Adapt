package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.HudGlyph;

import java.util.List;
import java.util.Objects;

/** Widget-specific settings, one record per {@link HudWidgetKind}. */
public sealed interface HudOptions {
    HudWidgetKind kind();

    /**
     * Skill activity: a skill's row shows while it earns xp and for {@code lingerMillis} after, merged per skill.
     * {@code pinned} skills always show. {@code iconSize} is in scaled units.
     */
    record Skills(List<String> pinned, int maxRows, long lingerMillis, boolean showNames, int iconSize) implements HudOptions {
        public static final int MAX_ROWS = 8;
        public static final long MIN_LINGER = 1000L;
        public static final long MAX_LINGER = 15000L;
        public static final int MIN_ICON = 8;
        public static final int MAX_ICON = 16;

        public Skills {
            pinned = List.copyOf(pinned);
            maxRows = Math.max(1, Math.min(MAX_ROWS, maxRows));
            lingerMillis = Math.max(MIN_LINGER, Math.min(MAX_LINGER, lingerMillis));
            iconSize = Math.max(MIN_ICON, Math.min(MAX_ICON, iconSize));
        }

        @Override
        public HudWidgetKind kind() {
            return HudWidgetKind.SKILLS;
        }
    }

    record Resources(boolean power, boolean master, boolean wisdom, boolean knowledge) implements HudOptions {
        @Override
        public HudWidgetKind kind() {
            return HudWidgetKind.RESOURCES;
        }

        public int count() {
            return (power ? 1 : 0) + (master ? 1 : 0) + (wisdom ? 1 : 0) + (knowledge ? 1 : 0);
        }
    }

    /**
     * Cooldown and state glyphs. {@code iconSize} is the icon inside each glyph in scaled units; crowded widgets shrink
     * it step by step down to {@code minIconSize}, then fold the least urgent glyphs into "+n". {@code maxRows} and
     * {@code maxColumns} cap the lines and the glyphs per line, {@code footprint} the share of the screen the widget may
     * cover. The kind switches and {@code skills} (empty for all) choose what this widget shows; {@code collapseGroups}
     * folds a skill's glyphs into one with a count when crowded.
     */
    record Abilities(GlyphArrangement arrangement, GlyphGrowth growth, int iconSize, int minIconSize, int maxShown,
                     int maxRows, int maxColumns, GlyphSort sort, boolean cooldowns, boolean charges, boolean durations,
                     boolean toggles, List<String> skills, boolean collapseGroups, double footprint, boolean showNames)
            implements HudOptions {
        public static final int MAX_SHOWN = 99;
        public static final int MIN_ICON = 8;
        public static final int MAX_ICON = 24;
        public static final int MAX_LINES = 12;
        public static final int MAX_PER_LINE = 30;
        public static final double MIN_FOOTPRINT = 0.1;
        public static final double MAX_FOOTPRINT = 0.6;

        public Abilities {
            Objects.requireNonNull(arrangement, "arrangement");
            Objects.requireNonNull(growth, "growth");
            Objects.requireNonNull(sort, "sort");
            iconSize = Math.max(MIN_ICON, Math.min(MAX_ICON, iconSize));
            minIconSize = Math.max(MIN_ICON, Math.min(iconSize, minIconSize));
            maxShown = Math.max(1, Math.min(MAX_SHOWN, maxShown));
            maxRows = Math.max(1, Math.min(MAX_LINES, maxRows));
            maxColumns = Math.max(1, Math.min(MAX_PER_LINE, maxColumns));
            skills = List.copyOf(skills);
            footprint = Double.isFinite(footprint) ? Math.max(MIN_FOOTPRINT, Math.min(MAX_FOOTPRINT, footprint)) : 0.3;
        }

        @Override
        public HudWidgetKind kind() {
            return HudWidgetKind.ABILITIES;
        }

        /** Whether this widget shows glyphs of {@code glyph}'s kind and skill. */
        boolean shows(HudGlyph glyph, String skillId) {
            boolean kind = switch (glyph) {
                case COOLDOWN -> cooldowns;
                case CHARGES -> charges;
                case DURATION -> durations;
                case TOGGLE -> toggles;
            };
            return kind && (skills.isEmpty() || skills.contains(skillId));
        }

        Abilities with(GlyphArrangement value) {
            return new Abilities(value, growth, iconSize, minIconSize, maxShown, maxRows, maxColumns, sort, cooldowns, charges,
                    durations, toggles, skills, collapseGroups, footprint, showNames);
        }

        Abilities withGrowth(GlyphGrowth value) {
            return new Abilities(arrangement, value, iconSize, minIconSize, maxShown, maxRows, maxColumns, sort, cooldowns,
                    charges, durations, toggles, skills, collapseGroups, footprint, showNames);
        }

        Abilities withSizes(int icon, int minIcon) {
            return new Abilities(arrangement, growth, icon, minIcon, maxShown, maxRows, maxColumns, sort, cooldowns, charges,
                    durations, toggles, skills, collapseGroups, footprint, showNames);
        }

        Abilities withLimits(int shown, int rows, int columns, double newFootprint) {
            return new Abilities(arrangement, growth, iconSize, minIconSize, shown, rows, columns, sort, cooldowns, charges,
                    durations, toggles, skills, collapseGroups, newFootprint, showNames);
        }

        Abilities withSort(GlyphSort value) {
            return new Abilities(arrangement, growth, iconSize, minIconSize, maxShown, maxRows, maxColumns, value, cooldowns,
                    charges, durations, toggles, skills, collapseGroups, footprint, showNames);
        }

        Abilities withKinds(boolean newCooldowns, boolean newCharges, boolean newDurations, boolean newToggles) {
            return new Abilities(arrangement, growth, iconSize, minIconSize, maxShown, maxRows, maxColumns, sort, newCooldowns,
                    newCharges, newDurations, newToggles, skills, collapseGroups, footprint, showNames);
        }

        Abilities withSkills(List<String> value) {
            return new Abilities(arrangement, growth, iconSize, minIconSize, maxShown, maxRows, maxColumns, sort, cooldowns,
                    charges, durations, toggles, value, collapseGroups, footprint, showNames);
        }

        Abilities withFlags(boolean newCollapseGroups, boolean newShowNames) {
            return new Abilities(arrangement, growth, iconSize, minIconSize, maxShown, maxRows, maxColumns, sort, cooldowns,
                    charges, durations, toggles, skills, newCollapseGroups, footprint, newShowNames);
        }
    }

    record Notices(int maxLines) implements HudOptions {
        public static final int MAX_LINES = 6;

        public Notices {
            maxLines = Math.max(1, Math.min(MAX_LINES, maxLines));
        }

        @Override
        public HudWidgetKind kind() {
            return HudWidgetKind.NOTICES;
        }
    }

    record Toasts(long durationMillis, int maxVisible, boolean stackUp) implements HudOptions {
        public static final int MAX_VISIBLE = 5;

        public Toasts {
            durationMillis = Math.max(1500L, Math.min(20000L, durationMillis));
            maxVisible = Math.max(1, Math.min(MAX_VISIBLE, maxVisible));
        }

        @Override
        public HudWidgetKind kind() {
            return HudWidgetKind.TOASTS;
        }
    }
}
