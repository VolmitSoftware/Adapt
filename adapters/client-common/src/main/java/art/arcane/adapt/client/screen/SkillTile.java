package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.InfoMark;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.XpGuideView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One seat of the skills page as a {@link Card}: the icon at the top left with the level beside its top and the
 * knowledge beside its bottom, the experience bar across the card, and the name under it on up to two lines. The knowledge book is closed in muted ink,
 * or open in the skill's colour while that knowledge can buy an adaptation level. An (i) at the top right opens how the
 * skill earns XP. A skill the player has not started is washed out with an empty bar. A skill the player may not open
 * shows locked. Which parts show is the page's {@link SkillsLayout} choice.
 */
final class SkillTile extends RowTile {
    /** The card's height: the icon's row, the bar and two lines of name, {@link Card#PAD} apart. */
    static final int HEIGHT = Card.height(BAR_HEIGHT + Card.PAD);
    private static final int BAR_TOP = Card.ROW_BOTTOM + Card.PAD;
    private static final int NAME_TOP = BAR_TOP + BAR_HEIGHT + Card.PAD;
    private static final int GLYPH_GAP = 3;
    private static final String KNOWLEDGE_MARK = IconSprites.mark("knowledge");
    private static final int NOTHING_LEARNABLE_TINT = 0x8CFFFFFF;
    private static final float UNSTARTED_WASH = 0.45F;

    /** How the seat stands for the player. */
    enum State {
        ACTIVE,
        UNSTARTED
    }

    private final SkillsLayout layout;
    private final InfoMark info;
    private final Consumer<String> open;
    private SkillView skill;
    private SkillLineView line;
    private State state = State.ACTIVE;
    private int learnable;
    private int powerUsed;
    private boolean openable;
    private boolean mutated;
    private int accent;

    SkillTile(SkillView skill, SkillsLayout layout, Consumer<String> open, Consumer<SkillTile> guide) {
        super("skill:" + skill.id(), () -> {
        });
        this.skill = skill;
        this.layout = layout;
        this.open = open;
        info = add(new InfoMark("info:" + skill.id(), ScreenText.EARNING_XP.text(), () -> guide.accept(this)));
        info.setVisible(false);
    }

    /** How the skill earns XP as the server describes it, or null: its (i) shows only with one. */
    void guide(XpGuideView guide) {
        mutated = guide != null && guide.mutated();
        info.setVisible(guide != null && layout.shows(SkillsLayout.Part.INFO));
    }

    /** Whether the skill is a mutation. */
    boolean mutated() {
        return mutated;
    }

    InfoMark info() {
        return info;
    }

    SkillView skill() {
        return skill;
    }

    State state() {
        return state;
    }

    int learnable() {
        return learnable;
    }

    /** Shows {@code view} as a skill the player has started or not, with its line and what it can learn now. */
    void update(SkillView view, SkillLineView skillLine, int canLearn, int power, boolean canOpen, boolean started) {
        skill = view;
        line = skillLine;
        state = started ? State.ACTIVE : State.UNSTARTED;
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
        if (state == State.UNSTARTED) {
            lines.add("§8" + ScreenText.NOT_STARTED.text());
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
        state.put("seat", this.state.name());
        state.put("level", Integer.toString(level()));
        state.put("knowledge", Long.toString(knowledge()));
        state.put("progress", Float.toString(progress()));
        state.put("affordable", Boolean.toString(learnable > 0));
        state.put("learnable", Integer.toString(learnable));
        state.put("power", Integer.toString(powerUsed));
        state.put("access", Boolean.toString(openable));
        state.put("opens", skill.id());
    }

    @Override
    public boolean activate() {
        services().playClick();
        open.accept(skill.id());
        return true;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return layout.tileHeight();
    }

    @Override
    IconRef icon() {
        return UiIcons.skill(skill);
    }

    @Override
    UiRect iconBounds() {
        UiRect box = bounds();
        return Card.icon(box);
    }

    @Override
    int nameColor(UiTheme theme) {
        return switch (state) {
            case ACTIVE -> openable ? accent : theme.textMuted();
            case UNSTARTED -> theme.textMuted();
        };
    }

    @Override
    String number() {
        return "";
    }

    @Override
    int numberColor(UiTheme theme) {
        return theme.text();
    }

    @Override
    String second() {
        return "";
    }

    @Override
    int secondColor(UiTheme theme) {
        return learnable > 0 ? accent : theme.textMuted();
    }

    @Override
    boolean locked() {
        return !openable;
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiRect box = bounds();
        info.place(new UiRect(box.right() - Card.PAD - InfoMark.SIZE, box.y() + Card.PAD, InfoMark.SIZE, InfoMark.SIZE), metrics);
    }

    /**
     * The knowledge mark before the amount on the text line at {@code top}, faded when nothing is learnable now; an
     * inked open or closed book when no resource pack has the mark. Returns its width.
     */
    private int knowledgeMark(UiDraw draw, int x, int top, int color) {
        UiTexture mark = draw.sprites().texture(KNOWLEDGE_MARK);
        if (mark == null) {
            UiTome.Glyph glyph = learnable > 0 ? UiTome.Glyph.BOOK : UiTome.Glyph.BOOK_CLOSED;
            UiTome.glyph(draw, glyph, x, top, color);
            return glyph.width();
        }
        draw.blit(mark, x, top - 1, Button.MARK, Button.MARK, 0, 0, mark.width(), mark.height(),
                learnable > 0 ? 0xFFFFFFFF : NOTHING_LEARNABLE_TINT);
        return Button.MARK;
    }

    @Override
    void paintCell(UiDraw draw, UiTheme theme, int backdrop) {
        UiRect box = bounds();
        UiRect icon = iconBounds();
        UiIcons.paintOn(draw, icon(), icon.x(), icon.y(), UiIcons.SIZE, false, backdrop);
        if (state != State.ACTIVE) {
            draw.fill(icon.x(), icon.y(), icon.right(), icon.bottom(), UiEase.fade(backdrop, UNSTARTED_WASH));
        }
        int textX = box.x() + Card.TEXT_LEFT;
        if (layout.shows(SkillsLayout.Part.LEVEL)) {
            draw.text(ScreenText.LEVEL_SHORT.text(level()), textX, box.y() + Card.UPPER_LINE,
                    state == State.ACTIVE ? theme.text() : theme.textDisabled(), false);
        }
        if (state == State.ACTIVE && openable && layout.shows(SkillsLayout.Part.KNOWLEDGE) && knowledge() > 0L) {
            int color = secondColor(theme);
            int markWidth = knowledgeMark(draw, textX, box.y() + Card.LOWER_LINE, color);
            String amount = ScreenText.number(knowledge());
            int room = box.right() - Card.PAD - (textX + markWidth + GLYPH_GAP);
            if (draw.textWidth(amount) > room) {
                amount = ScreenText.compact(knowledge());
            }
            draw.text(amount, textX + markWidth + GLYPH_GAP, box.y() + Card.LOWER_LINE, color, false);
        }
        if (openable && layout.shows(SkillsLayout.Part.PROGRESS)) {
            ProgressBar.framed(draw, theme, box.x() + Card.PAD, box.y() + BAR_TOP, box.right() - Card.PAD, BAR_HEIGHT,
                    state == State.ACTIVE ? progress() : 0.0F, accent);
        }
        Card.name(draw, box, skill.name(), box.y() + NAME_TOP, nameColor(theme));
    }
}
