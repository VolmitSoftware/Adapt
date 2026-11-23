package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFrame;
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
 * One adaptation as a cell of a skill page's grid: its icon on a medallion, and its name beside it, on one line or, for
 * a long name, two, broken at spaces and after hyphens and centred on the medallion either way. Every cell of a grid
 * is the same size ({@link Look}). The level sits on the medallion's corner: a pill in the skill's colour with the
 * learned level in roman numerals, gold once mastered (the medallion then wears its laurels), and an empty ring while
 * nothing is learned, in the skill's colour when the first level can be learned now; a padlock on the icon marks one a
 * requirement or the server blocks. The tooltip carries the description and what the next level costs or what blocks
 * it while the page has no room to say so.
 */
final class AdaptationTile extends RowTile {
    private static final int LINE = 9;
    private static final int TEXT_GAP = 3;
    private static final int BADGE_HEIGHT = 9;
    private static final int RING = 5;
    private static final float BADGE_DEPTH = 210.0F;

    private final BooleanSupplier described;
    private AdaptationView adaptation;
    private int level;
    private Status status = Status.NOT_LEARNED;
    private String statusText = "";
    private Look look = new Look(1, ROOMY);
    private int shownLines = 1;

    /** {@code described} says whether the page shows the pointed adaptation's details beside the grid. */
    AdaptationTile(AdaptationView adaptation, Runnable open, BooleanSupplier described) {
        super("adaptation:" + adaptation.id(), open);
        this.adaptation = adaptation;
        this.described = Objects.requireNonNull(described, "described");
    }

    /** A cell's height with room round the medallion. */
    static final int ROOMY = UiTome.TOKEN + 2;
    /** A cell's height in a grid short of room: the medallion and one unit. */
    static final int TIGHT = UiTome.TOKEN + 1;

    /** How the cells of one grid are drawn: how many lines a name may take and the cells' height. */
    record Look(int lines, int height) {
        /** Where the name starts inside the cell: after the medallion and a gap. */
        static int textLeft() {
            return UiTome.TOKEN + TEXT_GAP;
        }

        /** The room the name has in a cell {@code width} wide. */
        static int textWidth(int width) {
            return Math.max(1, width - textLeft() - 1);
        }
    }

    void setLook(Look newLook) {
        look = Objects.requireNonNull(newLook, "look");
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

    /**
     * {@code name} broken into lines no wider than {@code width}: at spaces, and after a hyphen; a word wider than a
     * whole line breaks where it must.
     */
    static List<String> nameLines(UiDraw metrics, String name, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        StringBuilder word = new StringBuilder();
        String plain = LegacyText.strip(name);
        for (int index = 0; index <= plain.length(); index++) {
            char next = index < plain.length() ? plain.charAt(index) : ' ';
            if (next != ' ') {
                word.append(next);
            }
            if (next != ' ' && next != '-' && index < plain.length()) {
                continue;
            }
            String piece = word.toString();
            word.setLength(0);
            if (piece.isEmpty()) {
                continue;
            }
            String joined = line.length() == 0 || line.charAt(line.length() - 1) == '-' ? line + piece : line + " " + piece;
            if (line.length() > 0 && metrics.textWidth(joined) > width) {
                lines.add(line.toString());
                line.setLength(0);
                joined = piece;
            }
            line.setLength(0);
            line.append(joined);
            while (metrics.textWidth(line.toString()) > width && line.length() > 1) {
                int cut = line.length() - 1;
                while (cut > 1 && metrics.textWidth(line.substring(0, cut)) > width) {
                    cut--;
                }
                lines.add(line.substring(0, cut));
                String rest = line.substring(cut);
                line.setLength(0);
                line.append(rest);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return look.height();
    }

    @Override
    UiRect iconBounds() {
        UiRect box = bounds();
        return new UiRect(box.x() + UiTome.TOKEN_INSET, box.y() + (box.height() - UiIcons.SIZE) / 2, UiIcons.SIZE, UiIcons.SIZE);
    }

    Look look() {
        return look;
    }

    @Override
    UiRect textBounds() {
        UiRect box = bounds();
        return new UiRect(box.x() + Look.textLeft(), box.y(), Look.textWidth(box.width()), box.height());
    }

    /** Where the name's first line starts: its lines centred on the medallion. */
    @Override
    int nameTop() {
        return (bounds().height() - LINE * shownLines) / 2 + 1;
    }

    @Override
    protected void layout(UiDraw metrics) {
        shownLines = Math.max(1, nameLines(metrics, adaptation.name(), textBounds().width()).size());
    }

    /** The medallion and the name's lines beside it. */
    @Override
    void paintCell(UiDraw draw, UiTheme theme, int backdrop) {
        UiRect box = bounds();
        paintIcon(draw, theme, iconBounds(), backdrop);
        UiRect text = textBounds();
        int y = box.y() + nameTop();
        for (String line : nameLines(draw, adaptation.name(), text.width())) {
            draw.text(line, text.x(), y, nameColor(theme), false);
            y += LINE;
        }
    }

    /** The padlock, then the level on the medallion's lower right corner, both above the item. */
    @Override
    protected void paintOver(UiFrame frame) {
        super.paintOver(frame);
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect icon = iconBounds();
        int right = icon.right() + UiTome.TOKEN_INSET;
        int bottom = icon.bottom() + UiTome.TOKEN_INSET;
        draw.pushPose();
        draw.depth(BADGE_DEPTH);
        if (level <= 0) {
            int ring = status == Status.AFFORDABLE ? theme.accent() : theme.textMuted();
            UiTome.soft(draw, right - RING, bottom - RING, right, bottom, theme.backdrop());
            UiTome.softEdge(draw, right - RING, bottom - RING, RING, RING, ring);
        } else {
            String numeral = ScreenText.roman(level);
            int width = draw.textWidth(numeral) + 2;
            boolean mastered = status == Status.MASTERED;
            int fill = mastered ? UiRamp.GOLD[3] : UiContrast.darkenFor(theme.accent(), theme.textInverse(), UiContrast.TEXT);
            int ink = mastered ? UiRamp.INK[0] : theme.textInverse();
            int left = right - width;
            int top = bottom - BADGE_HEIGHT;
            UiTome.soft(draw, left, top, right, bottom, fill);
            UiTome.softEdge(draw, left, top, width, BADGE_HEIGHT, mastered ? UiRamp.GOLD[0] : UiEase.mix(fill, 0xFF000000, 0.35F));
            draw.text(numeral, left + 1, top + 1, ink, false);
        }
        draw.popPose();
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
    }

    @Override
    IconRef icon() {
        return UiIcons.adaptation(adaptation);
    }

    /**
     * The icon on a medallion tinted with the skill's colour: bronze, gold once learned, gold with laurels when mastered
     * and grey while locked. The adaptation's own sprite, when there is one, sits on the
     * medallion in place of the item.
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
