package art.arcane.adapt.client.hud;

/**
 * How much of the HUD a small screen keeps, judged from its size in scaled GUI units divided by the profile scale.
 * The default layout needs about 410 x 340; below that skill rows are capped and notices and toasts keep one entry,
 * then resources drop out and skill rows leave narrow screens (boss bars); glyph widgets size themselves to the room
 * their region leaves (see {@link GlyphLayout}), so the defaults never cover chat, the hotbar, boss bars or the scoreboard. Toasts and notices never drop out: below 380 x 228 they turn into one-line pills that
 * {@link HudCompact} places in free space. A profile with auto-fit off always gets {@link Level#FULL}.
 */
public final class HudFit {
    static final int FULL_WIDTH = 410;
    static final int FULL_HEIGHT = 340;
    static final int COMPACT_HEIGHT = 256;
    static final int SMALL_WIDTH = 380;
    static final int SMALL_HEIGHT = 228;
    /** Below this the top-left skill rows would run under the boss bars. */
    static final int SKILLS_WIDTH = 330;

    enum Level {
        FULL,
        COMPACT,
        SMALL,
        TINY
    }

    private Level level = Level.FULL;
    private boolean skillsFit = true;

    void update(boolean enabled, double newWidth, double newHeight) {
        if (!enabled || newWidth >= FULL_WIDTH && newHeight >= FULL_HEIGHT) {
            level = Level.FULL;
        } else if (newWidth >= FULL_WIDTH && newHeight >= COMPACT_HEIGHT) {
            level = Level.COMPACT;
        } else if (newWidth >= SMALL_WIDTH && newHeight >= SMALL_HEIGHT) {
            level = Level.SMALL;
        } else {
            level = Level.TINY;
        }
        skillsFit = !enabled || newWidth >= SKILLS_WIDTH;
    }

    Level level() {
        return level;
    }

    boolean shows(HudWidgetKind kind) {
        return switch (kind) {
            case RESOURCES -> level == Level.FULL || level == Level.COMPACT;
            case SKILLS -> skillsFit;
            case ABILITIES, NOTICES, TOASTS -> true;
        };
    }

    /** Whether toasts and notices take their one-line pill form, placed clear of vanilla and the other widgets. */
    boolean compact() {
        return level == Level.TINY;
    }

    /** Skill rows stop well above chat on short screens. */
    int skillRows(int chosen) {
        return switch (level) {
            case FULL -> chosen;
            case COMPACT -> Math.min(chosen, 3);
            case SMALL, TINY -> Math.min(chosen, 2);
        };
    }

    int noticeLines(int chosen) {
        return level == Level.FULL ? chosen : Math.min(chosen, 1);
    }

    int toasts(int chosen) {
        return level == Level.FULL ? chosen : Math.min(chosen, 1);
    }
}
