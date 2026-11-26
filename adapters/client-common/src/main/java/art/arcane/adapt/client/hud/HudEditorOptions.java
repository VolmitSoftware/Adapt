package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** The rows of the editor's per-widget options popover. */
final class HudEditorOptions {
    /**
     * One row: {@code value} null makes it an action (or a heading when both actions are null). {@code next} runs on
     * a left click, {@code previous} on a right click.
     */
    record Row(String label, String value, Runnable next, Runnable previous) {
        boolean heading() {
            return value == null && next == null;
        }
    }

    /** Applies a change to the active profile as one undo step. */
    interface Edits {
        void profile(UnaryOperator<HudProfile> change);
    }

    private final HudText text;
    private final Edits edits;

    HudEditorOptions(HudText text, Edits edits) {
        this.text = text;
        this.edits = edits;
    }

    /**
     * {@code autoFit} and {@code protect} (the protected view zone) are profile settings, shown on every widget because
     * they decide which widgets small screens keep and where widgets may grow.
     */
    List<Row> rows(HudWidgetConfig config, boolean autoFit, double protect, List<SkillView> catalog) {
        List<Row> rows = new ArrayList<>();
        HudPlacement placement = config.placement();
        rows.add(toggle("adapt.hud.option.visible", placement.visible(),
                () -> place(config, placement.withVisible(!placement.visible()))));
        rows.add(toggle("adapt.hud.option.auto_fit", autoFit,
                () -> edits.profile((HudProfile profile) -> profile.withAutoFit(!autoFit))));
        rows.add(stepped("adapt.hud.option.protect", protect <= 0.0 ? text.get("adapt.hud.option.off") : percent(protect),
                () -> edits.profile((HudProfile profile) -> profile.withProtect(cycle(profile.protect(), 0.0, HudProfile.MAX_PROTECT, 0.05))),
                () -> edits.profile((HudProfile profile) -> profile.withProtect(cycle(profile.protect(), 0.0, HudProfile.MAX_PROTECT, -0.05)))));
        rows.add(stepped("adapt.hud.option.opacity", percent(placement.opacity()),
                () -> place(config, placement.withOpacity(cycle(placement.opacity(), 0.1, 1.0, 0.1))),
                () -> place(config, placement.withOpacity(cycle(placement.opacity(), 0.1, 1.0, -0.1)))));
        rows.add(stepped("adapt.hud.option.scale", percent(placement.scale()),
                () -> place(config, placement.withScale(cycle(placement.scale(), 0.5, 3.0, 0.25))),
                () -> place(config, placement.withScale(cycle(placement.scale(), 0.5, 3.0, -0.25)))));
        if (config.kind() == HudWidgetKind.RESOURCES || config.kind() == HudWidgetKind.ABILITIES) {
            Runnable flip = () -> place(config, placement.withOrientation(placement.orientation().flipped()));
            rows.add(new Row(text.get("adapt.hud.option.orientation"), text.get("adapt.hud.option." + placement.orientation().id()), flip, flip));
        }
        switch (config.options()) {
            case HudOptions.Skills skills -> {
                rows.add(stepped("adapt.hud.option.linger", seconds(skills.lingerMillis()),
                        () -> options(config, new HudOptions.Skills(skills.pinned(), skills.maxRows(), wrapMillis(skills.lingerMillis(), 1000L, HudOptions.Skills.MIN_LINGER, HudOptions.Skills.MAX_LINGER), skills.showNames(), skills.iconSize())),
                        () -> options(config, new HudOptions.Skills(skills.pinned(), skills.maxRows(), wrapMillis(skills.lingerMillis(), -1000L, HudOptions.Skills.MIN_LINGER, HudOptions.Skills.MAX_LINGER), skills.showNames(), skills.iconSize()))));
                rows.add(stepped("adapt.hud.option.max_rows", Integer.toString(skills.maxRows()),
                        () -> options(config, new HudOptions.Skills(skills.pinned(), wrap(skills.maxRows() + 1, 1, HudOptions.Skills.MAX_ROWS), skills.lingerMillis(), skills.showNames(), skills.iconSize())),
                        () -> options(config, new HudOptions.Skills(skills.pinned(), wrap(skills.maxRows() - 1, 1, HudOptions.Skills.MAX_ROWS), skills.lingerMillis(), skills.showNames(), skills.iconSize()))));
                rows.add(stepped("adapt.hud.option.icon_size", Integer.toString(skills.iconSize()),
                        () -> options(config, new HudOptions.Skills(skills.pinned(), skills.maxRows(), skills.lingerMillis(), skills.showNames(), wrapStep(skills.iconSize(), 2, HudOptions.Skills.MIN_ICON, HudOptions.Skills.MAX_ICON))),
                        () -> options(config, new HudOptions.Skills(skills.pinned(), skills.maxRows(), skills.lingerMillis(), skills.showNames(), wrapStep(skills.iconSize(), -2, HudOptions.Skills.MIN_ICON, HudOptions.Skills.MAX_ICON)))));
                rows.add(toggle("adapt.hud.option.show_names", skills.showNames(),
                        () -> options(config, new HudOptions.Skills(skills.pinned(), skills.maxRows(), skills.lingerMillis(), !skills.showNames(), skills.iconSize()))));
                rows.add(new Row(text.get("adapt.hud.option.pinned"), null, null, null));
                for (SkillView skill : catalog) {
                    boolean pinned = skills.pinned().contains(skill.id());
                    rows.add(toggleRow(skill.name(), pinned,
                            () -> options(config, new HudOptions.Skills(pin(skills.pinned(), skill.id(), !pinned), skills.maxRows(), skills.lingerMillis(), skills.showNames(), skills.iconSize()))));
                }
            }
            case HudOptions.Resources resources -> {
                rows.add(toggle("adapt.hud.resource.power", resources.power(),
                        () -> options(config, new HudOptions.Resources(!resources.power(), resources.master(), resources.wisdom(), resources.knowledge()))));
                rows.add(toggle("adapt.hud.resource.master", resources.master(),
                        () -> options(config, new HudOptions.Resources(resources.power(), !resources.master(), resources.wisdom(), resources.knowledge()))));
                rows.add(toggle("adapt.hud.resource.wisdom", resources.wisdom(),
                        () -> options(config, new HudOptions.Resources(resources.power(), resources.master(), !resources.wisdom(), resources.knowledge()))));
                rows.add(toggle("adapt.hud.resource.knowledge", resources.knowledge(),
                        () -> options(config, new HudOptions.Resources(resources.power(), resources.master(), resources.wisdom(), !resources.knowledge()))));
                if (resources.count() > 1) {
                    rows.add(action("adapt.hud.option.split", () -> edits.profile((HudProfile profile) -> split(profile, config, resources))));
                }
            }
            case HudOptions.Abilities glyphs -> {
                Runnable nextArrangement = () -> arrange(config, glyphs.with(glyphs.arrangement().next(1)));
                Runnable previousArrangement = () -> arrange(config, glyphs.with(glyphs.arrangement().next(-1)));
                rows.add(new Row(text.get("adapt.hud.option.arrangement"), text.get(glyphs.arrangement().translationKey()),
                        nextArrangement, previousArrangement));
                Runnable growth = () -> options(config, glyphs.withGrowth(glyphs.growth() == GlyphGrowth.AUTO ? GlyphGrowth.REVERSED : GlyphGrowth.AUTO));
                rows.add(new Row(text.get("adapt.hud.option.growth"), text.get("adapt.hud.option.growth." + glyphs.growth().id()), growth, growth));
                rows.add(stepped("adapt.hud.option.icon_size", Integer.toString(glyphs.iconSize()),
                        () -> options(config, glyphs.withSizes(wrapStep(glyphs.iconSize(), 2, HudOptions.Abilities.MIN_ICON, HudOptions.Abilities.MAX_ICON), glyphs.minIconSize())),
                        () -> options(config, glyphs.withSizes(wrapStep(glyphs.iconSize(), -2, HudOptions.Abilities.MIN_ICON, HudOptions.Abilities.MAX_ICON), glyphs.minIconSize()))));
                rows.add(stepped("adapt.hud.option.min_icon_size", Integer.toString(glyphs.minIconSize()),
                        () -> options(config, glyphs.withSizes(glyphs.iconSize(), wrapStep(glyphs.minIconSize(), 2, HudOptions.Abilities.MIN_ICON, glyphs.iconSize()))),
                        () -> options(config, glyphs.withSizes(glyphs.iconSize(), wrapStep(glyphs.minIconSize(), -2, HudOptions.Abilities.MIN_ICON, glyphs.iconSize())))));
                rows.add(stepped("adapt.hud.option.max_shown", Integer.toString(glyphs.maxShown()),
                        () -> options(config, glyphs.withLimits(wrap(glyphs.maxShown() + 4, 4, HudOptions.Abilities.MAX_SHOWN), glyphs.maxRows(), glyphs.maxColumns(), glyphs.footprint())),
                        () -> options(config, glyphs.withLimits(wrap(glyphs.maxShown() - 4, 4, HudOptions.Abilities.MAX_SHOWN), glyphs.maxRows(), glyphs.maxColumns(), glyphs.footprint()))));
                rows.add(stepped("adapt.hud.option.max_rows", Integer.toString(glyphs.maxRows()),
                        () -> options(config, glyphs.withLimits(glyphs.maxShown(), wrap(glyphs.maxRows() + 1, 1, HudOptions.Abilities.MAX_LINES), glyphs.maxColumns(), glyphs.footprint())),
                        () -> options(config, glyphs.withLimits(glyphs.maxShown(), wrap(glyphs.maxRows() - 1, 1, HudOptions.Abilities.MAX_LINES), glyphs.maxColumns(), glyphs.footprint()))));
                rows.add(stepped("adapt.hud.option.max_columns", Integer.toString(glyphs.maxColumns()),
                        () -> options(config, glyphs.withLimits(glyphs.maxShown(), glyphs.maxRows(), wrap(glyphs.maxColumns() + 1, 1, HudOptions.Abilities.MAX_PER_LINE), glyphs.footprint())),
                        () -> options(config, glyphs.withLimits(glyphs.maxShown(), glyphs.maxRows(), wrap(glyphs.maxColumns() - 1, 1, HudOptions.Abilities.MAX_PER_LINE), glyphs.footprint()))));
                rows.add(stepped("adapt.hud.option.footprint", percent(glyphs.footprint()),
                        () -> options(config, glyphs.withLimits(glyphs.maxShown(), glyphs.maxRows(), glyphs.maxColumns(), cycle(glyphs.footprint(), HudOptions.Abilities.MIN_FOOTPRINT, HudOptions.Abilities.MAX_FOOTPRINT, 0.05))),
                        () -> options(config, glyphs.withLimits(glyphs.maxShown(), glyphs.maxRows(), glyphs.maxColumns(), cycle(glyphs.footprint(), HudOptions.Abilities.MIN_FOOTPRINT, HudOptions.Abilities.MAX_FOOTPRINT, -0.05)))));
                rows.add(new Row(text.get("adapt.hud.option.sort"), text.get("adapt.hud.option.sort." + glyphs.sort().id()),
                        () -> options(config, glyphs.withSort(glyphs.sort().next(1))), () -> options(config, glyphs.withSort(glyphs.sort().next(-1)))));
                rows.add(toggle("adapt.hud.option.collapse_groups", glyphs.collapseGroups(),
                        () -> options(config, glyphs.withFlags(!glyphs.collapseGroups(), glyphs.showNames()))));
                rows.add(toggle("adapt.hud.option.show_names", glyphs.showNames(),
                        () -> options(config, glyphs.withFlags(glyphs.collapseGroups(), !glyphs.showNames()))));
                rows.add(new Row(text.get("adapt.hud.option.kinds"), null, null, null));
                rows.add(toggle("adapt.hud.option.kind.cooldowns", glyphs.cooldowns(),
                        () -> options(config, glyphs.withKinds(!glyphs.cooldowns(), glyphs.charges(), glyphs.durations(), glyphs.toggles()))));
                rows.add(toggle("adapt.hud.option.kind.charges", glyphs.charges(),
                        () -> options(config, glyphs.withKinds(glyphs.cooldowns(), !glyphs.charges(), glyphs.durations(), glyphs.toggles()))));
                rows.add(toggle("adapt.hud.option.kind.durations", glyphs.durations(),
                        () -> options(config, glyphs.withKinds(glyphs.cooldowns(), glyphs.charges(), !glyphs.durations(), glyphs.toggles()))));
                rows.add(toggle("adapt.hud.option.kind.toggles", glyphs.toggles(),
                        () -> options(config, glyphs.withKinds(glyphs.cooldowns(), glyphs.charges(), glyphs.durations(), !glyphs.toggles()))));
                rows.add(new Row(text.get("adapt.hud.option.skill_filter"), null, null, null));
                for (SkillView skill : catalog) {
                    boolean only = glyphs.skills().contains(skill.id());
                    rows.add(toggleRow(skill.name(), only,
                            () -> options(config, glyphs.withSkills(pin(glyphs.skills(), skill.id(), !only)))));
                }
            }
            case HudOptions.Notices notices -> rows.add(stepped("adapt.hud.option.max_lines", Integer.toString(notices.maxLines()),
                    () -> options(config, new HudOptions.Notices(wrap(notices.maxLines() + 1, 1, HudOptions.Notices.MAX_LINES))),
                    () -> options(config, new HudOptions.Notices(wrap(notices.maxLines() - 1, 1, HudOptions.Notices.MAX_LINES)))));
            case HudOptions.Toasts toasts -> {
                rows.add(stepped("adapt.hud.option.duration", seconds(toasts.durationMillis()),
                        () -> options(config, new HudOptions.Toasts(wrapMillis(toasts.durationMillis(), 500L, HudOptions.Toasts.MIN_DURATION, HudOptions.Toasts.MAX_DURATION), toasts.maxVisible())),
                        () -> options(config, new HudOptions.Toasts(wrapMillis(toasts.durationMillis(), -500L, HudOptions.Toasts.MIN_DURATION, HudOptions.Toasts.MAX_DURATION), toasts.maxVisible()))));
                rows.add(stepped("adapt.hud.option.max_visible", Integer.toString(toasts.maxVisible()),
                        () -> options(config, new HudOptions.Toasts(toasts.durationMillis(), wrap(toasts.maxVisible() + 1, 1, HudOptions.Toasts.MAX_VISIBLE))),
                        () -> options(config, new HudOptions.Toasts(toasts.durationMillis(), wrap(toasts.maxVisible() - 1, 1, HudOptions.Toasts.MAX_VISIBLE)))));
            }
        }
        if (config.kind() == HudWidgetKind.SKILLS) {
            rows.add(action("adapt.hud.option.add", () -> edits.profile((HudProfile profile) -> addTracker(profile, config))));
        }
        if (config.kind() == HudWidgetKind.ABILITIES) {
            rows.add(action("adapt.hud.option.add_glyphs", () -> edits.profile(HudEditorOptions::addGlyphs)));
        }
        rows.add(action("adapt.hud.option.reset", () -> edits.profile((HudProfile profile) -> profile.with(reset(config)))));
        if (!config.id().equals(config.kind().id())) {
            rows.add(action("adapt.hud.option.remove", () -> edits.profile((HudProfile profile) -> profile.without(config.id()))));
        }
        return rows;
    }

    /** Keeps the first enabled resource on {@code config} and moves each other one into a new widget below it. */
    static HudProfile split(HudProfile profile, HudWidgetConfig config, HudOptions.Resources resources) {
        boolean[] enabled = {resources.power(), resources.master(), resources.wisdom(), resources.knowledge()};
        HudProfile next = profile;
        boolean kept = false;
        int added = 0;
        HudPlacement placement = config.placement();
        double step = (ResourcesWidget.ROW_HEIGHT + 10) * placement.scale() * (placement.anchor().bottom() ? -1.0 : 1.0);
        for (int entry = 0; entry < enabled.length; entry++) {
            if (!enabled[entry]) {
                continue;
            }
            HudOptions.Resources only = new HudOptions.Resources(entry == 0, entry == 1, entry == 2, entry == 3);
            if (!kept) {
                next = next.with(config.withOptions(only));
                kept = true;
                continue;
            }
            added++;
            HudPlacement moved = placement.withAnchor(placement.anchor(), placement.offsetX(), placement.offsetY() + step * added);
            next = next.with(new HudWidgetConfig(next.freshId(HudWidgetKind.RESOURCES), HudWidgetKind.RESOURCES, moved, only));
        }
        return next;
    }

    static HudProfile addTracker(HudProfile profile, HudWidgetConfig config) {
        HudPlacement placement = config.placement();
        HudPlacement moved = placement.withAnchor(placement.anchor(), placement.offsetX() + 24.0, placement.offsetY() + 24.0);
        return profile.with(new HudWidgetConfig(profile.freshId(HudWidgetKind.SKILLS), HudWidgetKind.SKILLS, moved,
                HudDefaults.options(HudWidgetKind.SKILLS)));
    }

    /**
     * A second glyph widget in the bottom right corner showing charges and stances, and the base widget keeping the
     * rest, so the split is useful at once and either can be changed.
     */
    static HudProfile addGlyphs(HudProfile profile) {
        HudOptions.Abilities defaults = (HudOptions.Abilities) HudDefaults.options(HudWidgetKind.ABILITIES);
        HudOptions.Abilities corner = defaults.with(GlyphArrangement.GRID).withKinds(false, true, false, true);
        HudPlacement placement = GlyphArrangement.GRID.preset(HudDefaults.placement(HudWidgetKind.ABILITIES));
        HudProfile next = profile;
        HudWidgetConfig base = profile.widget(HudWidgetKind.ABILITIES.id());
        if (base != null && base.options() instanceof HudOptions.Abilities rest) {
            next = next.with(base.withOptions(rest.withKinds(rest.cooldowns(), false, rest.durations(), false)));
        }
        return next.with(new HudWidgetConfig(next.freshId(HudWidgetKind.ABILITIES), HudWidgetKind.ABILITIES, placement, corner));
    }

    private void arrange(HudWidgetConfig config, HudOptions.Abilities options) {
        edits.profile((HudProfile profile) -> profile.with(new HudWidgetConfig(config.id(), config.kind(),
                options.arrangement().preset(config.placement()), options)));
    }

    static HudWidgetConfig reset(HudWidgetConfig config) {
        HudWidgetConfig defaults = HudDefaults.widget(config.kind());
        return new HudWidgetConfig(config.id(), config.kind(), defaults.placement(), defaults.options());
    }

    private void place(HudWidgetConfig config, HudPlacement placement) {
        edits.profile((HudProfile profile) -> profile.with(config.withPlacement(placement)));
    }

    private void options(HudWidgetConfig config, HudOptions options) {
        edits.profile((HudProfile profile) -> profile.with(config.withOptions(options)));
    }

    private Row toggle(String key, boolean value, Runnable flip) {
        return toggleRow(text.get(key), value, flip);
    }

    private Row toggleRow(String label, boolean value, Runnable flip) {
        return new Row(label, text.get(value ? "adapt.hud.option.on" : "adapt.hud.option.off"), flip, flip);
    }

    private Row stepped(String key, String value, Runnable next, Runnable previous) {
        return new Row(text.get(key), value, next, previous);
    }

    private Row action(String key, Runnable action) {
        return new Row(text.get(key), null, action, action);
    }

    private static List<String> pin(List<String> pinned, String skillId, boolean add) {
        List<String> next = new ArrayList<>(pinned);
        next.remove(skillId);
        if (add) {
            next.add(skillId);
        }
        return next;
    }

    static int wrapStep(int value, int step, int min, int max) {
        return wrap(value + step, min, max);
    }

    static int wrap(int value, int min, int max) {
        if (value > max) {
            return min;
        }
        return value < min ? max : value;
    }

    private static long wrapMillis(long value, long step, long min, long max) {
        long next = value + step;
        if (next > max) {
            return min;
        }
        return next < min ? max : next;
    }

    private static double cycle(double value, double min, double max, double step) {
        double next = Math.round((value + step) * 100.0) / 100.0;
        if (next > max + 1e-6) {
            return min;
        }
        return next < min - 1e-6 ? max : next;
    }

    static String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private static String seconds(long millis) {
        return millis % 1000L == 0L ? (millis / 1000L) + "s" : (millis / 1000L) + "." + (millis % 1000L / 100L) + "s";
    }
}
