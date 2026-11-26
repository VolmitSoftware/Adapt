package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.MutationEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The head of the mutation spread, drawn across both pages and centred on the fold: the mutated skill's name in large
 * letters over a gilt rule, the level and knowledge it starts with, and everything the mutation gives as medallions on
 * two rows. The first row is the mutation's own adaptations; the second holds the lead's upgrades on the lead's side, the
 * tools in the middle and the partner's upgrades on the partner's side, each group set apart by a gilt dot. Medallions
 * the second row has no room for fold into a count that names them, tools first. Compact, the level and knowledge share
 * the first row's line. Before a partner is picked it shows only {@code title}.
 */
final class SpreadBanner extends UiNode {
    private static final int NAME = 18;
    private static final int RULE_GAP = 2;
    private static final int LINE_GAP = 3;
    private static final int RULE_REACH = 10;
    private static final int SUMMARY_GAP = 6;
    private static final int PLATE_PAD = 6;
    private static final int ROW_GAP = 3;
    private static final int GROUP_GAP = 7;

    private String title = "";
    private String name = "";
    private String summary = "";
    private List<MutationEntry> mutated = List.of();
    private List<MutationEntry> leadUpgrades = List.of();
    private List<MutationEntry> tools = List.of();
    private List<MutationEntry> partnerUpgrades = List.of();
    private final List<SpreadToken> tokens = new ArrayList<>();
    private final List<List<SpreadToken>> groups = new ArrayList<>();
    private final List<Integer> dots = new ArrayList<>();
    private int secondY;
    private boolean compact;
    private int fold;

    SpreadBanner(String id) {
        super(id);
    }

    /** The height the banner takes, compact or not. */
    static int height(boolean compact, int lineHeight) {
        int below = compact ? UiTome.TOKEN : lineHeight + LINE_GAP + UiTome.TOKEN;
        return NAME + RULE_GAP + LINE_GAP + below + ROW_GAP + UiTome.TOKEN;
    }

    /**
     * Shows the pair named {@code newName}: the mutation's own adaptations on the first row, and the lead's upgrades,
     * the tools and the partner's upgrades on the second. An empty name shows {@code newTitle} alone.
     */
    void set(String newTitle, String newName, String newSummary, List<MutationEntry> newMutated, List<MutationEntry> newLeadUpgrades,
             List<MutationEntry> newTools, List<MutationEntry> newPartnerUpgrades) {
        title = newTitle;
        name = newName;
        summary = newSummary;
        mutated = List.copyOf(newMutated);
        leadUpgrades = List.copyOf(newLeadUpgrades);
        tools = List.copyOf(newTools);
        partnerUpgrades = List.copyOf(newPartnerUpgrades);
        tokens.clear();
        clearChildren();
        for (MutationEntry entry : mutated) {
            tokens.add(add(new SpreadToken(id() + ":" + entry.id()).show(entry, SpreadToken.Look.MUTATED)));
        }
    }

    void setCompact(boolean newCompact) {
        compact = newCompact;
    }

    /** Places the banner over {@code box}, centred on {@code foldX}. */
    void place(UiRect box, int foldX, UiDraw metrics) {
        fold = foldX;
        place(box, metrics);
    }

    @Override
    public String kind() {
        return "banner";
    }

    @Override
    public String label() {
        return name.isEmpty() ? title : name;
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("summary", summary);
        state.put("mutated", Integer.toString(mutated.size()));
        state.put("upgrades", Integer.toString(leadUpgrades.size() + partnerUpgrades.size()));
        state.put("tools", Integer.toString(tools.size()));
        state.put("compact", Boolean.toString(compact));
    }

    private int tokensWidth() {
        return tokens.isEmpty() ? 0 : tokens.size() * UiTome.TOKEN + (tokens.size() - 1) * SpreadParts.TOKEN_GAP;
    }

    /** The paper plate the head sits on, across the fold, so the binding never runs through its letters. */
    private void plate(UiDraw draw, int top, int bottom, int room, int contentWidth) {
        int half = Math.min(room / 2, contentWidth / 2 + PLATE_PAD + RULE_REACH);
        int left = fold - half;
        int right = fold + half;
        UiTome.soft(draw, left, top, right, bottom, UiRamp.PAGE[1]);
        UiTome.softEdge(draw, left, top, right - left, bottom - top, UiRamp.alpha(UiTome.GILT, 0x90));
    }

    private int contentWidth(UiDraw draw) {
        int tokens = tokensWidth();
        int first = compact ? draw.textWidth(summary) + SUMMARY_GAP + tokens : Math.max(draw.textWidth(summary), tokens);
        int second = 0;
        for (List<SpreadToken> group : groups) {
            if (!group.isEmpty()) {
                second = Math.max(second, 2 * Math.max(Math.abs(group.get(0).bounds().x() - fold),
                        Math.abs(group.get(group.size() - 1).bounds().right() - fold)));
            }
        }
        return Math.max(first, second);
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiRect box = bounds();
        int y = box.y() + NAME + RULE_GAP + LINE_GAP + (compact ? 0 : metrics.lineHeight() + LINE_GAP);
        int width = tokensWidth();
        int x;
        if (compact && !summary.isEmpty()) {
            int group = metrics.textWidth(summary) + SUMMARY_GAP + width;
            x = Math.max(box.x(), fold - group / 2) + metrics.textWidth(summary) + SUMMARY_GAP;
        } else {
            x = fold - width / 2;
        }
        for (SpreadToken token : tokens) {
            token.place(new UiRect(x, y, UiTome.TOKEN, UiTome.TOKEN), metrics);
            x += UiTome.TOKEN + SpreadParts.TOKEN_GAP;
        }
        secondY = y + UiTome.TOKEN + ROW_GAP;
        second(box.width(), metrics);
    }

    /**
     * Lays out the second row centred on the fold in {@code room} units: the lead's upgrades, the tools, the partner's
     * upgrades. When they do not fit, tools and then upgrades from the partner's end fold into one count medallion in
     * the tools' place.
     */
    private void second(int room, UiDraw metrics) {
        for (List<SpreadToken> group : groups) {
            for (SpreadToken token : group) {
                remove(token);
            }
        }
        groups.clear();
        dots.clear();
        List<MutationEntry> lead = new ArrayList<>(leadUpgrades);
        List<MutationEntry> middle = new ArrayList<>(tools);
        List<MutationEntry> partner = new ArrayList<>(partnerUpgrades);
        List<MutationEntry> folded = new ArrayList<>();
        while (rowWidth(lead.size(), middle.size() + (folded.isEmpty() ? 0 : 1), partner.size()) > room
                && !(middle.isEmpty() && partner.isEmpty() && lead.isEmpty())) {
            List<MutationEntry> from = !middle.isEmpty() ? middle : !partner.isEmpty() ? partner : lead;
            folded.add(0, from.remove(from.size() - 1));
        }
        groups.add(tokens(lead, SpreadToken.Look.GAIN));
        List<SpreadToken> centre = tokens(middle, SpreadToken.Look.TOOL);
        if (!folded.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (MutationEntry entry : folded) {
                names.add(entry.name());
            }
            centre.add(add(new SpreadToken(id() + ":more").more(names)));
        }
        groups.add(centre);
        groups.add(tokens(partner, SpreadToken.Look.GAIN));
        int x = fold - rowWidth(lead.size(), centre.size(), partner.size()) / 2;
        boolean placed = false;
        for (List<SpreadToken> group : groups) {
            if (group.isEmpty()) {
                continue;
            }
            if (placed) {
                dots.add(x - (GROUP_GAP + SpreadParts.TOKEN_GAP) / 2);
            }
            for (SpreadToken token : group) {
                token.place(new UiRect(x, secondY, UiTome.TOKEN, UiTome.TOKEN), metrics);
                x += UiTome.TOKEN + SpreadParts.TOKEN_GAP;
            }
            x += GROUP_GAP;
            placed = true;
        }
    }

    private List<SpreadToken> tokens(List<MutationEntry> entries, SpreadToken.Look look) {
        List<SpreadToken> made = new ArrayList<>();
        for (MutationEntry entry : entries) {
            made.add(add(new SpreadToken(id() + ":" + entry.id()).show(entry, look)));
        }
        return made;
    }

    /** The width of a second row with groups of these sizes, a gap between each pair of groups that are not empty. */
    private static int rowWidth(int... sizes) {
        int width = 0;
        int shown = 0;
        for (int size : sizes) {
            if (size == 0) {
                continue;
            }
            width += size * UiTome.TOKEN + (size - 1) * SpreadParts.TOKEN_GAP;
            shown++;
        }
        return shown == 0 ? 0 : width + (shown - 1) * (GROUP_GAP + SpreadParts.TOKEN_GAP);
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        boolean paired = !name.isEmpty();
        String shown = paired ? name : title;
        int color = paired ? theme.text() : theme.textMuted();
        int scale = draw.textWidth(shown) * 2 + PLATE_PAD * 2 <= box.width() ? 2 : 1;
        int width = draw.textWidth(shown) * scale;
        int nameY = box.y() + (NAME - draw.lineHeight() * scale) / 2 + 1;
        if (!paired) {
            nameY = box.y() + (height(compact, draw.lineHeight()) - draw.lineHeight() * scale) / 2;
        }
        draw.nextLayer();
        if (paired) {
            plate(draw, box.y() - 2, box.bottom() + 2, box.width(), Math.max(width, contentWidth(draw)));
        } else {
            plate(draw, nameY - PLATE_PAD, nameY + draw.lineHeight() * scale + PLATE_PAD - 2, box.width(), width);
        }
        draw.pushPose();
        draw.translate(fold - width / 2.0F, nameY);
        draw.scale(scale, scale);
        draw.text(shown, 0, 0, color, false);
        draw.popPose();
        if (!paired) {
            return;
        }
        int ruleY = box.y() + NAME + RULE_GAP;
        int reach = Math.min(box.width() / 2, width / 2 + RULE_REACH);
        UiTome.rule(draw, fold - reach, fold + reach, ruleY);
        draw.fill(fold - 1, ruleY - 1, fold + 2, ruleY + 2, UiTome.GILT);
        int dotY = secondY + UiTome.TOKEN / 2;
        for (int dot : dots) {
            draw.fill(dot - 1, dotY - 1, dot + 2, dotY + 2, UiTome.GILT);
        }
        if (summary.isEmpty()) {
            return;
        }
        int lineY = ruleY + LINE_GAP;
        if (compact) {
            int group = draw.textWidth(summary) + SUMMARY_GAP + tokensWidth();
            int x = Math.max(box.x(), fold - group / 2);
            draw.text(summary, x, lineY + (UiTome.TOKEN - draw.lineHeight()) / 2 + 1, theme.textMuted(), false);
        } else {
            draw.centeredText(summary, fold, lineY, theme.textMuted(), false);
        }
    }
}
