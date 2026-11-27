package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.LearnRules;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One original a mutated skill keeps without learning, or an upgrade lends a level: the adaptation's icon on a learned
 * medallion, its level as a numeral on the corner and named with that level in its tooltip. A kept original the player
 * raised with knowledge has its numeral in green. A click, Enter or Space opens the adaptation's page, where its settings and the levels
 * above the kept one can be changed.
 */
final class KeptTile extends UiNode {
    static final int BADGE_HEIGHT = 9;
    private static final float BADGE_DEPTH = 210.0F;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private final Runnable open;
    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
    private AdaptationView adaptation;
    private int level;
    private boolean raised;
    private String state = "";
    private String next = "";
    private boolean affordable;

    KeptTile(String id, Runnable open) {
        super(id);
        this.open = Objects.requireNonNull(open, "open");
    }

    /** An original an upgrade lends {@code lent}'s level. */
    void update(AdaptationView view, KeptView lent) {
        adaptation = view;
        level = lent.level();
        raised = false;
        setTooltip(new Tooltip((view == null ? lent.adaptationId() : view.name()) + " " + roman(level), List.of()));
    }

    /** An original a mutated skill keeps at {@code kept}'s level, or the higher level the player raised it to. */
    void update(CatalogIndex catalog, PlayerView player, KeptView kept) {
        AdaptationView view = catalog.adaptation(kept.adaptationId());
        adaptation = view;
        level = Math.max(kept.level(), CatalogIndex.level(player, kept.adaptationId()));
        raised = level > kept.level();
        state = raised ? ScreenText.RAISED_AT.text(roman(kept.level()), roman(level)) : ScreenText.KEPT_AT.text(roman(level));
        affordable = false;
        next = "";
        List<String> lines = new ArrayList<>();
        lines.add("§8" + state);
        if (view != null && level < view.maxLevel()) {
            LearnRules.LearnCheck check = LearnRules.learn(catalog, player, view, level + 1, kept);
            affordable = check.allowed();
            next = LearnText.learnBlock(catalog, check);
            lines.add((affordable ? "§a" : "§7") + next);
        } else if (view != null) {
            next = ScreenText.MASTERED.text();
            lines.add("§a" + next);
        }
        setTooltip(new Tooltip((view == null ? kept.adaptationId() : view.name()) + " " + roman(level), lines));
    }

    AdaptationView adaptation() {
        return adaptation;
    }

    /** The level it works at in words: kept, or kept and raised. */
    String state() {
        return state;
    }

    /** What its next level costs or what blocks it; empty once mastered or while unknown. */
    String next() {
        return next;
    }

    boolean affordable() {
        return affordable;
    }

    boolean raised() {
        return raised;
    }

    private static String roman(int value) {
        return value >= 0 && value < ROMAN.length ? ROMAN[value] : Integer.toString(value);
    }

    @Override
    public String kind() {
        return "kept";
    }

    @Override
    public String label() {
        return tooltip() == null ? "" : tooltip().title();
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("level", Integer.toString(level));
        state.put("raised", Boolean.toString(raised));
    }

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        if (event.button() != UiMouseButton.LEFT) {
            return false;
        }
        return activate();
    }

    @Override
    public boolean activate() {
        services().playClick();
        open.run();
        return true;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        return UiTome.TOKEN;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return UiTome.TOKEN;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        UiTome.token(draw, box.x(), box.y(), theme.tile(), UiTome.Token.LEARNED);
        if (adaptation != null) {
            UiIcons.paint(draw, UiIcons.adaptation(adaptation), box.x() + UiTome.TOKEN_INSET, box.y() + UiTome.TOKEN_INSET, UiIcons.SIZE, false);
        }
        float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
        if (lift > 0.0F) {
            UiTome.filigree(draw, box, frame.showsFocus(this) ? 1.0F : lift);
        }
    }

    /** The level as a numeral on the medallion's lower right corner, above the item: green once raised, paper while kept. */
    @Override
    protected void paintOver(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        String numeral = roman(level);
        int fill = raised ? UiContrast.darkenFor(Palette.GOOD, theme.textInverse(), UiContrast.TEXT) : theme.tile();
        badge(draw, box.right() - badgeWidth(draw, numeral), box.bottom() - BADGE_HEIGHT, numeral, fill,
                raised ? UiEase.mix(fill, 0xFF000000, 0.35F) : theme.surfaceBorder(), raised ? theme.textInverse() : theme.text());
    }

    static int badgeWidth(UiDraw draw, String numeral) {
        return draw.textWidth(numeral) + 2;
    }

    /** A level numeral on a small soft plate with its top left at (left, top), above the tile's item. */
    static void badge(UiDraw draw, int left, int top, String numeral, int fill, int edge, int ink) {
        int width = badgeWidth(draw, numeral);
        draw.pushPose();
        draw.depth(BADGE_DEPTH);
        UiTome.soft(draw, left, top, left + width, top + BADGE_HEIGHT, fill);
        UiTome.softEdge(draw, left, top, width, BADGE_HEIGHT, edge);
        draw.text(numeral, left + 1, top + 1, ink, false);
        draw.popPose();
    }
}
