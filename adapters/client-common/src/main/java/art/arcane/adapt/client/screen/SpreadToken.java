package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.MutationEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One medallion on the mutation spread, named in its tooltip: an adaptation the mutation brings, an original it keeps,
 * a tool, an original that goes away, or the count of those that did not fit their row.
 */
final class SpreadToken extends UiNode {
    /** How the medallion looks: what it stands for decides its rim. */
    enum Look {
        MUTATED(UiTome.Token.MAXED),
        GAIN(UiTome.Token.LEARNED),
        KEPT(UiTome.Token.LEARNED),
        TOOL(UiTome.Token.PLAIN),
        LOST(UiTome.Token.LOCKED),
        MORE(UiTome.Token.PLAIN);

        private final UiTome.Token token;

        Look(UiTome.Token token) {
            this.token = token;
        }
    }

    private static final int DIM = 0x8CE8DCC0;
    private static final float MARK_DEPTH = 210.0F;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
    private Look look = Look.GAIN;
    private IconRef icon;
    private int level;
    private String count = "";

    SpreadToken(String id) {
        super(id);
    }

    /** Shows {@code entry}; its detail becomes the tooltip's lines, one per line break. */
    SpreadToken show(MutationEntry entry, Look shown) {
        look = shown;
        icon = entry.icon();
        level = entry.level();
        count = "";
        List<String> lines = new ArrayList<>();
        for (String line : entry.detail().split("\n")) {
            if (!line.isBlank()) {
                lines.add("§7" + line);
            }
        }
        setTooltip(new Tooltip(entry.name(), lines));
        return this;
    }

    /** Stands for {@code names}, the entries its row had no room for. */
    SpreadToken more(List<String> names) {
        look = Look.MORE;
        icon = null;
        level = 0;
        count = "+" + names.size();
        List<String> lines = new ArrayList<>();
        for (String name : names) {
            lines.add("§7" + name);
        }
        setTooltip(new Tooltip(count, lines));
        return this;
    }

    @Override
    public String kind() {
        return "token";
    }

    @Override
    public String label() {
        return tooltip() == null ? "" : LegacyText.strip(tooltip().title());
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("look", look.name());
        state.put("level", Integer.toString(level));
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean hittable() {
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
        float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
        UiTome.token(draw, box.x(), box.y(), theme.tile(), look.token);
        if (icon != null) {
            UiIcons.paint(draw, icon, box.x() + UiTome.TOKEN_INSET, box.y() + UiTome.TOKEN_INSET, UiIcons.SIZE, false);
        }
        if (look == Look.LOST) {
            draw.fill(box.x() + 3, box.y() + 3, box.right() - 3, box.bottom() - 3, DIM);
        }
        if (look == Look.MORE) {
            draw.pushPose();
            draw.depth(MARK_DEPTH);
            draw.centeredText(count, box.centerX(), box.y() + (box.height() - draw.lineHeight()) / 2 + 1, theme.text(), false);
            draw.popPose();
        } else if (level > 0 && level < ROMAN.length && look != Look.GAIN) {
            String mark = ROMAN[level];
            int x = box.right() - draw.textWidth(mark);
            draw.pushPose();
            draw.depth(MARK_DEPTH);
            draw.text(mark, x, box.bottom() - draw.lineHeight() + 1, look == Look.LOST ? Palette.LOCK : theme.text(), true);
            draw.popPose();
        }
        if (lift > 0.0F) {
            UiTome.filigree(draw, box, lift);
        }
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, box, theme);
        }
    }
}
