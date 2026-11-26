package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiRamp;
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
 * One adaptation as a card of a skill page's grid, every card of a page the same cell ({@link Look}): a faint plate,
 * the icon at the top left with its level pips beside the icon's top and what the next level costs, or that it is
 * mastered or locked, beside its bottom, and under them a name block of a fixed number of lines, the name at its top
 * ({@link CardName}). A pip fills only for a level the player holds, in the skill's colour, gold once mastered; every
 * other pip is a hollow neutral box. The cost reads green while affordable and red while short; a padlock on the icon
 * marks one a requirement or the server blocks. A signature wears a gold frame. The tooltip carries the description
 * and what the next level costs or what blocks it while the page has no room to say so.
 */
final class AdaptationTile extends RowTile {
    /** Space inside each edge of a card, as on the skills page's seats. */
    static final int PAD = Card.PAD;
    /** Space between the icon's row and the name block. */
    static final int NAME_GAP = 2;
    /** How strongly the plate under a card shows through the page. */
    static final float PLATE = 0.06F;
    private static final int PIP = 3;
    private static final int PIP_PITCH = PIP + 1;
    private static final int GLYPH_GAP = 2;
    private static final float UNLEARNED_WASH = 0.35F;

    private final BooleanSupplier described;
    private AdaptationView adaptation;
    private int level;
    private Status status = Status.NOT_LEARNED;
    private String statusText = "";
    private int cost;
    private Look look = Look.FULL;
    private List<String> fit;
    private String fitName = "";
    private int fitWidth = -1;
    private Look fitLook;

    /** {@code described} says whether the page shows the pointed adaptation's details beside the grid. */
    AdaptationTile(AdaptationView adaptation, Runnable open, BooleanSupplier described) {
        super("adaptation:" + adaptation.id(), open);
        this.adaptation = adaptation;
        this.described = Objects.requireNonNull(described, "described");
    }

    /**
     * The cell every card of a page shares: the icon's size and how many lines the name block holds. The grid gives up
     * name lines, then icon size, before it scrolls; a name of words too long for three lines takes up to five.
     */
    record Look(int icon, int lines) {
        static final Look FULL = new Look(UiIcons.SIZE, 3);
        static final Look TWO_LINES = new Look(UiIcons.SIZE, 2);
        static final Look SMALL_ICON = new Look(12, 3);
        static final Look SMALL = new Look(12, 2);
        static final Look TALL = new Look(UiIcons.SIZE, 5);
        static final List<Look> ORDER = List.of(FULL, TWO_LINES, SMALL_ICON, SMALL, TALL);

        int height() {
            return PAD + icon + NAME_GAP + CardName.blockHeight(lines) + PAD;
        }
    }

    void setLook(Look newLook) {
        look = Objects.requireNonNull(newLook, "look");
    }

    Look look() {
        return look;
    }

    /** How the name sits in the name block of a card {@code width} wide drawn as {@code at}, or null when it cannot. */
    List<String> nameFit(int width, Look at, UiDraw metrics) {
        if (width != fitWidth || !adaptation.name().equals(fitName) || at != fitLook) {
            fit = CardName.fit(metrics, adaptation.name(), nameRoom(width), at.lines());
            fitWidth = width;
            fitName = adaptation.name();
            fitLook = at;
        }
        return fit;
    }

    /** The room a name has in a card {@code width} wide, inside its padding on both sides. */
    static int nameRoom(int width) {
        return Math.max(1, width - 2 * PAD);
    }

    AdaptationView adaptation() {
        return adaptation;
    }

    int level() {
        return level;
    }

    String statusText() {
        return statusText;
    }

    /** Whether the adaptation is a signature, which takes the place of the recipe it replaces. */
    boolean signature() {
        String sprite = adaptation.icon().spriteId();
        return sprite != null && sprite.contains("signature/");
    }

    /** The colour the status line reads in. */
    int statusColor(UiTheme theme) {
        return switch (status) {
            case MASTERED, AFFORDABLE -> Palette.GOOD;
            case LOCKED -> Palette.LOCK;
            case DISABLED, NOT_LEARNED -> theme.textMuted();
            case SHORT -> Palette.BAD;
        };
    }

    @Override
    public Tooltip tooltip() {
        return described.getAsBoolean() ? null : super.tooltip();
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return look.height();
    }

    @Override
    UiRect iconBounds() {
        UiRect box = bounds();
        return new UiRect(box.x() + PAD, box.y() + PAD, look.icon(), look.icon());
    }

    @Override
    void paintCell(UiDraw draw, UiTheme theme, int backdrop) {
        UiRect box = bounds();
        UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), UiEase.mix(backdrop, theme.text(), PLATE));
        UiRect icon = iconBounds();
        UiIcons.paintOn(draw, icon(), icon.x(), icon.y(), look.icon(), glint(), backdrop);
        if (level == 0 && !locked()) {
            draw.fill(icon.x(), icon.y(), icon.right(), icon.bottom(), UiEase.fade(backdrop, UNLEARNED_WASH));
        }
        if (signature()) {
            UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), UiRamp.GOLD[2]);
        }
        int textX = icon.right() + PAD;
        int upper = box.y() + PAD;
        int lower = icon.bottom() - UiControl.CAP_HEIGHT;
        paintLevel(draw, theme, backdrop, textX, upper, Math.max(1, box.right() - PAD - textX));
        paintNext(draw, theme, textX, lower, box.right() - PAD);
        List<String> name = nameFit(box.width(), look, draw);
        if (name == null) {
            name = NameLines.fit(draw, adaptation.name(), nameRoom(box.width()) + 1, look.lines());
        }
        CardName.paint(draw, name, box.x() + PAD, icon.bottom() + NAME_GAP, nameColor(theme));
    }

    /**
     * The level as pips from {@code x} on the line at {@code top}, a level not held an outline hollow to the
     * {@code backdrop}, or as "Lv n/max" where the pips would not fit in {@code room}. Returns where it ends.
     */
    private int paintLevel(UiDraw draw, UiTheme theme, int backdrop, int x, int top, int room) {
        int max = adaptation.maxLevel();
        if (max <= 0) {
            return x;
        }
        if (max * PIP_PITCH - 1 > room) {
            String text = ScreenText.LEVEL_SHORT.text(level + "/" + max);
            draw.text(text, x, top, theme.text(), false);
            return x + draw.textWidth(text);
        }
        int filled = status == Status.MASTERED ? UiRamp.GOLD[2] : UiContrast.darkenFor(theme.accent(), theme.textInverse(), UiContrast.TEXT);
        int pipTop = top + (8 - PIP) / 2;
        for (int value = 1; value <= max; value++) {
            int left = x + (value - 1) * PIP_PITCH;
            if (value <= level) {
                draw.fill(left, pipTop, left + PIP, pipTop + PIP, filled);
            } else {
                draw.fill(left, pipTop, left + PIP, pipTop + PIP, theme.surfaceBorder());
                draw.fill(left + 1, pipTop + 1, left + PIP - 1, pipTop + PIP - 1, backdrop);
            }
        }
        return x + max * PIP_PITCH - 1;
    }

    /**
     * What the next level costs, or that the adaptation is mastered or locked, from {@code x} on the line at {@code top}:
     * a mastered one leads with a check, and a word too wide for the card is left out rather than cut short.
     */
    private void paintNext(UiDraw draw, UiTheme theme, int x, int top, int right) {
        int color = statusColor(theme);
        String text = switch (status) {
            case MASTERED -> ScreenText.MASTERED.text();
            case LOCKED, DISABLED -> ScreenText.LOCKED.text();
            case NOT_LEARNED, AFFORDABLE, SHORT -> ScreenText.number(cost);
        };
        int left = x;
        UiTome.Glyph glyph = switch (status) {
            case AFFORDABLE -> UiTome.Glyph.BOOK;
            case SHORT, NOT_LEARNED -> UiTome.Glyph.BOOK_CLOSED;
            case MASTERED -> UiTome.Glyph.CHECK;
            case LOCKED, DISABLED -> null;
        };
        if (glyph != null) {
            UiTome.glyph(draw, glyph, left, top, color);
            left += glyph.width() + GLYPH_GAP;
        }
        if (draw.textWidth(text) <= right - left) {
            draw.text(text, left, top, color, false);
        }
    }

    void update(CatalogIndex catalog, PlayerView player, AdaptationView view) {
        adaptation = view;
        level = CatalogIndex.level(player, view.id());
        cost = 0;
        if (level >= view.maxLevel()) {
            status = Status.MASTERED;
            statusText = ScreenText.MASTERED.text();
        } else {
            LearnRules.LearnCheck next = LearnRules.learn(catalog, player, view, level + 1);
            cost = next.knowledgeCost();
            status = switch (next.block()) {
                case NONE -> Status.AFFORDABLE;
                case REQUIREMENTS -> Status.LOCKED;
                case DISABLED, DENIED -> Status.DISABLED;
                default -> Status.SHORT;
            };
            statusText = LearnText.learnBlock(catalog, next);
        }
        List<String> lines = new ArrayList<>();
        lines.add("§8" + levelText() + (view.permanent() ? " · " + ScreenText.PERMANENT.text() : ""));
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
        state.put("signature", Boolean.toString(signature()));
    }

    @Override
    IconRef icon() {
        return UiIcons.adaptation(adaptation);
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
    boolean glint() {
        return status == Status.MASTERED;
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
