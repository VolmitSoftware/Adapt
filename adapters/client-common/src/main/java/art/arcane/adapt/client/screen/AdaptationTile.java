package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * One adaptation on a skill page: its icon on a medallion, its name and one pip per level, nothing else. Learned
 * levels are solid pips, the next level is an open pip while it can be learned now and a hairline like the later ones
 * while it cannot; a lock on the medallion marks one a requirement or the server blocks, laurels one that is mastered.
 * What the next level costs or what blocks it is on the page's detail strip, and in the tooltip with the description
 * while the strip has no room for it.
 */
final class AdaptationTile extends RowTile implements DetailStrip.Source {
    static final int PIP_HEIGHT = 4;
    private static final int MAX_PIP = 8;
    private static final int MIN_PIP = 3;
    static final int NAME_TOP = 4;
    private static final int PIPS_TOP = 15;

    private AdaptationView adaptation;
    private int level;
    private Status status = Status.NOT_LEARNED;
    private String statusText = "";
    private final BooleanSupplier described;

    /** {@code described} says whether the page already shows the description beside the tiles, so the tooltip is not needed. */
    AdaptationTile(AdaptationView adaptation, Runnable open, BooleanSupplier described) {
        super("adaptation:" + adaptation.id(), open);
        this.adaptation = adaptation;
        this.described = Objects.requireNonNull(described, "described");
    }

    @Override
    public Tooltip tooltip() {
        return described.getAsBoolean() ? null : super.tooltip();
    }

    void update(CatalogIndex catalog, PlayerView player, AdaptationView view) {
        adaptation = view;
        level = CatalogIndex.level(player, view.id());
        if (level >= view.maxLevel()) {
            status = Status.MASTERED;
            statusText = ScreenText.MASTERED.text();
        } else {
            LearnRules.LearnCheck next = LearnRules.learn(catalog, player, view, level + 1);
            status = switch (next.block()) {
                case NONE -> Status.AFFORDABLE;
                case REQUIREMENTS -> Status.LOCKED;
                case DISABLED, DENIED -> Status.DISABLED;
                default -> Status.SHORT;
            };
            statusText = LearnText.learnBlock(catalog, next);
        }
        List<String> lines = new ArrayList<>();
        if (!LegacyText.isBlank(view.description())) {
            lines.add("§7" + view.description());
        }
        lines.add(tone() + statusText);
        setTooltip(new Tooltip(view.name(), lines));
    }

    private String levelText() {
        return level == 0 ? ScreenText.NOT_LEARNED.text()
                : ScreenText.LEVEL_OF.text(ScreenText.roman(level), ScreenText.roman(adaptation.maxLevel()));
    }

    private String tone() {
        return switch (status) {
            case MASTERED, AFFORDABLE -> "§a";
            case LOCKED -> "§6";
            case DISABLED, NOT_LEARNED -> "§8";
            case SHORT -> "§c";
        };
    }

    Status status() {
        return status;
    }

    @Override
    public DetailStrip.Detail detail() {
        String first = "§7" + levelText() + (adaptation.permanent() ? " §8· §6" + ScreenText.PERMANENT.text() : "");
        return new DetailStrip.Detail(adaptation.name(), status == Status.DISABLED ? Palette.MUTED : 0, first, tone() + statusText,
                adaptation.description(), -1.0F);
    }

    @Override
    public String kind() {
        return "adaptation-card";
    }

    @Override
    public String label() {
        return adaptation.name();
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("level", Integer.toString(level));
        state.put("maxLevel", Integer.toString(adaptation.maxLevel()));
        state.put("status", status.name());
        state.put("statusText", LegacyText.strip(statusText));
        state.put("openPip", Integer.toString(openPip()));
    }

    @Override
    IconRef icon() {
        return UiIcons.adaptation(adaptation);
    }

    /**
     * The icon on a medallion tinted with the skill's colour: bronze, gold once learned, gold with laurels when mastered
     * and grey while locked. The adaptation's own sprite, when there is one, sits on the medallion in place of the item.
     */
    @Override
    void paintIcon(UiDraw draw, UiTheme theme, UiRect icon, int backdrop) {
        IconRef art = icon();
        UiTome.Token look = status == Status.MASTERED ? UiTome.Token.MAXED : locked() ? UiTome.Token.LOCKED
                : level > 0 ? UiTome.Token.LEARNED : UiTome.Token.PLAIN;
        int tint = UiEase.mix(theme.accent(), backdrop, 0.72F);
        UiTome.token(draw, icon.x() - UiTome.TOKEN_INSET, icon.y() - UiTome.TOKEN_INSET, tint, look);
        UiIcons.paint(draw, art, icon.x(), icon.y(), UiIcons.SIZE, glint());
    }

    @Override
    int nameColor(UiTheme theme) {
        return status == Status.DISABLED ? theme.textDisabled() : theme.text();
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
        return theme.textMuted();
    }

    @Override
    int nameTop() {
        return NAME_TOP;
    }

    @Override
    int barTop() {
        return PIPS_TOP;
    }

    /** The index of the pip drawn open, the next level while it can be learned now, or -1. */
    int openPip() {
        return status == Status.AFFORDABLE ? level : -1;
    }

    @Override
    int accent(UiTheme theme) {
        return theme.accent();
    }

    @Override
    boolean glint() {
        return status == Status.MASTERED;
    }

    @Override
    void paintBar(UiDraw draw, UiTheme theme, int left, int top, int right) {
        int width = right - left;
        int max = Math.max(1, adaptation.maxLevel());
        int pip = Math.min(MAX_PIP, (width - (max - 1) * ProgressBar.PIP_GAP) / max);
        if (pip < MIN_PIP) {
            ProgressBar.slim(draw, theme, left, top + PIP_HEIGHT - BAR_HEIGHT, right, BAR_HEIGHT, level / (float) max, theme.accent());
            return;
        }
        ProgressBar.pips(draw, theme, left, top, pip, PIP_HEIGHT, max, level, openPip(), theme.accent());
    }

    @Override
    boolean locked() {
        return status == Status.LOCKED || status == Status.DISABLED;
    }

    enum Status {
        NOT_LEARNED,
        AFFORDABLE,
        SHORT,
        LOCKED,
        DISABLED,
        MASTERED
    }
}
