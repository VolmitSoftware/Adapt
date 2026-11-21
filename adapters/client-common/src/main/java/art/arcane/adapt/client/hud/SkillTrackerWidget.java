package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill activity: one tight parchment line per skill that is earning xp (icon, merged gain in the skill's ink, thin
 * progress bar, level only when it changes), in the order the skills started, fading out once the skill goes quiet. Pinned skills stay as a line with
 * their level. Nothing shows while nothing happens.
 */
final class SkillTrackerWidget implements HudWidget {
    static final int PAD = 2;
    static final int GAP = 3;
    static final int BAR_WIDTH = 28;
    static final int BAR_HEIGHT = 2;
    static final long ENTER_MILLIS = 180L;
    static final long LEVEL_MILLIS = 2500L;
    private static final int LEVEL_GOLD = 0xFFF2D98A;
    private static final int LEVEL_INK = 0xFF6B440C;
    private static final int SLIDE = 8;
    private static final Comparator<HudXpLine> BY_START = Comparator.comparingLong(HudXpLine::createdAt);

    private final List<HudXpLine> active = new ArrayList<>(HudModel.MAX_XP_LINES);
    private final List<String> pinnedShown = new ArrayList<>(HudOptions.Skills.MAX_ROWS);
    private final Map<String, String> names = new HashMap<>();
    private int namesRevision = Integer.MIN_VALUE;

    @Override
    public void measure(UiDraw draw, Frame frame, HudWidgetConfig config, Size out) {
        HudOptions.Skills options = (HudOptions.Skills) config.options();
        collect(frame, options);
        if (active.isEmpty() && pinnedShown.isEmpty()) {
            out.set(0, 0);
            return;
        }
        int slot = slotHeight(draw, options);
        int width = 0;
        double height = 0.0;
        for (int index = 0; index < pinnedShown.size(); index++) {
            width = Math.max(width, pinnedWidth(draw, frame, options, pinnedShown.get(index)));
            height += slot;
        }
        for (int index = 0; index < active.size(); index++) {
            HudXpLine line = active.get(index);
            width = Math.max(width, lineWidth(draw, frame, options, line));
            height += slot * collapse(frame, line);
        }
        out.set(width, (int) Math.ceil(height));
    }

    @Override
    public void render(UiDraw draw, Frame frame, HudWidgetConfig config, Size size) {
        HudOptions.Skills options = (HudOptions.Skills) config.options();
        int slot = slotHeight(draw, options);
        boolean bottomUp = frame.anchor().bottom();
        double y = bottomUp ? size.height : 0.0;
        for (int index = 0; index < pinnedShown.size(); index++) {
            if (bottomUp) {
                y -= slot;
            }
            drawPinned(draw, frame, options, pinnedShown.get(index), size.width, (int) Math.round(y), slot - 1);
            if (!bottomUp) {
                y += slot;
            }
        }
        for (int index = 0; index < active.size(); index++) {
            HudXpLine line = active.get(index);
            line.animate(frame.now(), frame.reducedMotion());
            double height = slot * collapse(frame, line);
            if (bottomUp) {
                y -= height;
            }
            drawLine(draw, frame, options, line, size.width, (int) Math.round(y), slot - 1);
            if (!bottomUp) {
                y += height;
            }
        }
    }

    private void drawLine(UiDraw draw, Frame frame, HudOptions.Skills options, HudXpLine line, int widgetWidth, int y, int height) {
        long now = frame.now();
        double opacity = frame.opacity() * presence(frame, line);
        if (opacity <= 0.01) {
            return;
        }
        int width = lineWidth(draw, frame, options, line);
        int x = align(frame, widgetWidth, width);
        if (!frame.reducedMotion()) {
            int direction = frame.anchor().column() == 2 ? 1 : -1;
            x += (int) Math.round((1.0 - HudMotion.easeOutCubic(HudMotion.progress(now - line.createdAt, ENTER_MILLIS))) * SLIDE * direction);
        }
        HudModel model = frame.model();
        int ink = HudPaint.inked(model.skillColor(line.skillId));
        double flourish = flourish(frame, line.levelUpAt);
        HudPaint.paper(draw, x, y, width, height, paper(flourish), opacity);
        int iconSize = options.iconSize();
        int cursor = x + PAD;
        HudPaint.icon(draw, model.skillIcon(model.skill(line.skillId)), cursor, y + (height - iconSize) / 2, iconSize, opacity);
        cursor += iconSize + GAP;
        int textY = y + (height - draw.lineHeight()) / 2 + 1;
        HudPaint.ink(draw, line.label, cursor, textY, ink, opacity);
        cursor += draw.textWidth(line.label);
        if (options.showNames()) {
            String name = name(model, line.skillId);
            HudPaint.ink(draw, name, cursor + GAP, textY, HudPaint.INK_MUTED, opacity);
            cursor += GAP + draw.textWidth(name);
        }
        cursor += GAP;
        int barY = y + (height - BAR_HEIGHT) / 2;
        HudPaint.inkBar(draw, cursor, barY, BAR_WIDTH, BAR_HEIGHT, line.shownProgress, ink, opacity);
        if (flourish > 0.0 && !frame.reducedMotion()) {
            shine(draw, now, line.levelUpAt, cursor, barY, opacity);
        }
        cursor += BAR_WIDTH;
        if (showsLevel(frame, line.levelUpAt)) {
            String level = levelLabel(frame, line.levelText);
            HudPaint.ink(draw, level, cursor + GAP, textY, HudPaint.mix(HudPaint.INK, LEVEL_INK, Math.min(1.0, 0.4 + flourish)), opacity);
        }
    }

    private void drawPinned(UiDraw draw, Frame frame, HudOptions.Skills options, String skillId, int widgetWidth, int y, int height) {
        HudModel model = frame.model();
        HudSkillState state = model.skillState(skillId);
        double opacity = frame.opacity();
        int width = pinnedWidth(draw, frame, options, skillId);
        int x = align(frame, widgetWidth, width);
        int ink = HudPaint.inked(model.skillColor(skillId));
        double flourish = flourish(frame, state.levelUpAt());
        HudPaint.paper(draw, x, y, width, height, paper(flourish), opacity);
        int iconSize = options.iconSize();
        int cursor = x + PAD;
        HudPaint.icon(draw, model.skillIcon(model.skill(skillId)), cursor, y + (height - iconSize) / 2, iconSize, opacity);
        cursor += iconSize + GAP;
        int textY = y + (height - draw.lineHeight()) / 2 + 1;
        if (options.showNames()) {
            String name = name(model, skillId);
            HudPaint.ink(draw, name, cursor, textY, ink, opacity);
            cursor += draw.textWidth(name) + GAP;
        }
        HudPaint.inkBar(draw, cursor, y + (height - BAR_HEIGHT) / 2, BAR_WIDTH, BAR_HEIGHT, state.progress(), ink, opacity);
        cursor += BAR_WIDTH + GAP;
        int levelColor = flourish > 0.0 ? HudPaint.mix(HudPaint.INK_MUTED, LEVEL_INK, Math.min(1.0, flourish * 2.0)) : HudPaint.INK_MUTED;
        HudPaint.ink(draw, state.levelText(), cursor, textY, levelColor, opacity);
    }

    private static void shine(UiDraw draw, long now, long levelUpAt, int barLeft, int barY, double opacity) {
        double sweep = HudMotion.progress(now - levelUpAt, 700L);
        if (sweep >= 1.0) {
            return;
        }
        int shine = barLeft + (int) Math.round((BAR_WIDTH + 8) * sweep) - 8;
        int left = Math.max(barLeft, shine);
        int right = Math.min(barLeft + BAR_WIDTH, shine + 8);
        if (right > left) {
            draw.fill(left, barY, right, barY + BAR_HEIGHT, HudPaint.fade(0xC0FFF8E8, opacity));
        }
    }

    private static int align(Frame frame, int widgetWidth, int width) {
        int column = frame.anchor().column();
        return column == 0 ? 0 : column == 2 ? widgetWidth - width : (widgetWidth - width) / 2;
    }

    /** Parchment, washed with gold while a level-up flourish plays. */
    private static int paper(double flourish) {
        return flourish > 0.0 ? HudPaint.mix(HudPaint.PAPER, LEVEL_GOLD | 0xE6000000, flourish) : HudPaint.PAPER;
    }

    /** One row's height including its 1-unit gap. */
    static int slotHeight(UiDraw draw, HudOptions.Skills options) {
        return Math.max(options.iconSize(), draw.lineHeight()) + 3;
    }

    private int lineWidth(UiDraw draw, Frame frame, HudOptions.Skills options, HudXpLine line) {
        int width = PAD + options.iconSize() + GAP + draw.textWidth(line.label) + GAP + BAR_WIDTH + PAD;
        if (options.showNames()) {
            width += GAP + draw.textWidth(name(frame.model(), line.skillId));
        }
        if (showsLevel(frame, line.levelUpAt)) {
            width += GAP + draw.textWidth(levelLabel(frame, line.levelText));
        }
        return width;
    }

    private int pinnedWidth(UiDraw draw, Frame frame, HudOptions.Skills options, String skillId) {
        HudSkillState state = frame.model().skillState(skillId);
        int width = PAD + options.iconSize() + GAP + BAR_WIDTH + GAP + draw.textWidth(state.levelText()) + PAD;
        if (options.showNames()) {
            width += draw.textWidth(name(frame.model(), skillId)) + GAP;
        }
        return width;
    }

    private static boolean showsLevel(Frame frame, long levelUpAt) {
        return levelUpAt != Long.MIN_VALUE && frame.now() - levelUpAt < LEVEL_MILLIS;
    }

    private static String levelLabel(Frame frame, String levelText) {
        return frame.text().get("adapt.hud.level_short") + " " + levelText;
    }

    private static double presence(Frame frame, HudXpLine line) {
        long leavesAt = line.updatedAt + frame.model().xpWindow();
        long fadeIn = frame.reducedMotion() ? 1L : ENTER_MILLIS;
        return HudMotion.presence(frame.now(), line.createdAt, leavesAt, fadeIn, HudModel.XP_FADE_MILLIS);
    }

    private static double collapse(Frame frame, HudXpLine line) {
        long leavesAt = line.updatedAt + frame.model().xpWindow();
        if (frame.now() < leavesAt || frame.reducedMotion()) {
            return frame.now() < leavesAt + HudModel.XP_FADE_MILLIS ? 1.0 : 0.0;
        }
        return 1.0 - HudMotion.easeInCubic(HudMotion.progress(frame.now() - leavesAt, HudModel.XP_FADE_MILLIS));
    }

    private static double flourish(Frame frame, long levelUpAt) {
        if (levelUpAt == Long.MIN_VALUE) {
            return 0.0;
        }
        long elapsed = frame.now() - levelUpAt;
        if (elapsed < 0L || elapsed >= 1600L) {
            return 0.0;
        }
        double t = elapsed / 1600.0;
        if (frame.reducedMotion()) {
            return 0.6 * (1.0 - t);
        }
        return (1.0 - t) * (0.55 + 0.45 * (0.5 + 0.5 * Math.cos(t * Math.PI * 4.0)));
    }

    private String name(HudModel model, String skillId) {
        if (namesRevision != model.revision()) {
            names.clear();
            namesRevision = model.revision();
        }
        String name = names.get(skillId);
        if (name == null) {
            name = model.skillName(skillId);
            names.put(skillId, name);
        }
        return name;
    }

    /** Pinned skills first (in the player's order), then active skills by when they started, up to the row cap. */
    private void collect(Frame frame, HudOptions.Skills options) {
        HudModel model = frame.model();
        int limit = frame.fit().skillRows(options.maxRows());
        pinnedShown.clear();
        for (int index = 0; index < options.pinned().size() && pinnedShown.size() < limit; index++) {
            String skillId = options.pinned().get(index);
            if (model.skillState(skillId) != null) {
                pinnedShown.add(skillId);
            }
        }
        active.clear();
        List<HudXpLine> lines = model.xpLines();
        for (int index = 0; index < lines.size(); index++) {
            HudXpLine line = lines.get(index);
            if (!pinnedShown.contains(line.skillId)) {
                active.add(line);
            }
        }
        active.sort(BY_START);
        int room = Math.max(0, limit - pinnedShown.size());
        while (active.size() > room) {
            active.remove(0);
        }
    }
}
