package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One skill in the skills grid: icon, name in the skill's colour, level, the experience bar in the skill's colour, its
 * knowledge as a book mark and number when it has any, and a green dot when an adaptation can be learned now. A skill
 * the player may not open shows locked. The tooltip carries the exact numbers.
 */
final class SkillTile extends RowTile {

    private SkillView skill;
    private SkillLineView line;
    private int learnable;
    private int powerUsed;
    private boolean openable;
    private int accent;

    SkillTile(SkillView skill, Runnable open) {
        super("skill:" + skill.id(), open);
        this.skill = skill;
    }

    void update(SkillView view, SkillLineView skillLine, int canLearn, int power, boolean canOpen) {
        skill = view;
        line = skillLine;
        learnable = canLearn;
        powerUsed = power;
        openable = canOpen;
        accent = Palette.accent(view.color());
        setTooltip(new Tooltip(view.name(), tooltipLines(view)));
    }

    private List<String> tooltipLines(SkillView view) {
        List<String> lines = new ArrayList<>();
        if (!LegacyText.isBlank(view.description())) {
            lines.add("§7" + view.description());
        }
        if (!openable) {
            lines.add("§6" + ScreenText.NO_ACCESS.text());
            return lines;
        }
        lines.add("§8" + ScreenText.LEVEL_SHORT.text(level()) + " · " + ScreenText.XP_TO_LEVEL.text(ScreenText.number(xpToNext()), level() + 1));
        lines.add("§8" + ScreenText.number(knowledge()) + " " + ScreenText.KNOWLEDGE.text());
        if (learnable > 0) {
            lines.add("§8" + ScreenText.TO_LEARN.text(learnable));
        }
        if (powerUsed > 0) {
            lines.add("§8" + ScreenText.POWER_USED.text(powerUsed));
        }
        return lines;
    }

    SkillView skill() {
        return skill;
    }

    int learnable() {
        return learnable;
    }

    private int level() {
        return line == null ? 0 : Math.max(0, line.level());
    }

    private long knowledge() {
        return line == null ? 0L : line.knowledge();
    }

    private long xpToNext() {
        return line == null ? 0L : Math.round(Math.max(0.0D, line.xpToNext()));
    }

    private float progress() {
        return line == null ? 0.0F : UiEase.clamp((float) line.progress());
    }

    @Override
    public String kind() {
        return "skill-card";
    }

    @Override
    public String label() {
        return skill.name();
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("level", Integer.toString(level()));
        state.put("knowledge", Long.toString(knowledge()));
        state.put("progress", Float.toString(progress()));
        state.put("affordable", Boolean.toString(learnable > 0));
        state.put("learnable", Integer.toString(learnable));
        state.put("power", Integer.toString(powerUsed));
        state.put("access", Boolean.toString(openable));
    }

    @Override
    IconRef icon() {
        return UiIcons.skill(skill);
    }

    @Override
    int nameColor(UiTheme theme) {
        return openable ? accent : theme.textMuted();
    }

    @Override
    String number() {
        return Integer.toString(level());
    }

    @Override
    int numberColor(UiTheme theme) {
        return openable ? theme.text() : theme.textDisabled();
    }

    @Override
    String second() {
        if (!openable) {
            return ScreenText.DENIED.text();
        }
        return knowledge() > 0L ? ScreenText.number(knowledge()) : "";
    }

    @Override
    UiTome.Mark secondMark() {
        return openable ? UiTome.Mark.KNOWLEDGE : null;
    }

    @Override
    int secondColor(UiTheme theme) {
        return openable ? theme.text() : Palette.LOCK;
    }

    @Override
    int accent(UiTheme theme) {
        return openable ? accent : theme.textMuted();
    }

    @Override
    void paintBar(UiDraw draw, UiTheme theme, int left, int top, int right) {
        draw.fill(left, top, right, top + BAR_HEIGHT, Palette.TRACK);
        int filled = Math.round((right - left) * progress());
        if (openable && filled > 0) {
            draw.fill(left, top, left + filled, top + BAR_HEIGHT, accent);
        }
    }

    @Override
    int dot() {
        return openable && learnable > 0 ? Palette.GOOD : 0;
    }

    @Override
    boolean locked() {
        return !openable;
    }
}
