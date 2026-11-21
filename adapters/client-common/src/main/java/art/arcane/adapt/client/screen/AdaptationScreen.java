package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.ConfirmOverlay;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.StatChip;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiOverlay;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One adaptation. The left page reads top to bottom from one left edge: icon, name and level pips on one line, the
 * description, the selected level's stat lines, what blocks it, then its cost or refund with the learn or unlearn
 * button (every requirement is in the button's tooltip). Its preferences are a grid on the facing page. Text wraps
 * instead of being cut, and the panel widens while that would otherwise make the page scroll. Learning a permanent adaptation asks for confirmation first,
 * except in debug mode where the server skips it. An adaptation the player's skill page does not show says so instead.
 * Every change waits for the server: the button and preferences are disabled and the target level blinks until the
 * answer arrives.
 */
public final class AdaptationScreen extends AdaptScreen {
    static final String LEARN = "learn";
    static final String UNLEARN = "unlearn";
    static final String PREFERENCE = "preference";
    private static final int MAX_WIDTH = 420;
    private static final int WIDEN_STEP = 32;

    private final String adaptationId;
    private final Summary summary;
    private final Label description;
    private final StatChip permanentChip;
    private final StatChip debugChip;
    private final Label notice;
    private final UiGroup detail;
    private final ActionRow actionRow;
    private final LevelTrack track;
    private final StatChip costChip;
    private final StatChip powerChip;
    private final StatChip refundChip;
    private final Button learnButton;
    private final Button unlearnButton;
    private final UiGroup stats;
    private final Label reason;
    private final PreferencePanel preferences;
    private int selected;
    private boolean chosen;
    private int pendingLevel;
    private ConfirmOverlay confirm;

    AdaptationScreen(ScreenRouter router, Route.Adaptation route) {
        super(title(router, route.adaptationId()), route, router);
        adaptationId = route.adaptationId();
        summary = page().add(new Summary());
        track = summary.add(new LevelTrack("levels", this::select));
        description = page().add(new Label("description", "").setWrap(true).setColor(Palette.MUTED));
        notice = page().add(new Label("notice", "").setWrap(true));
        detail = page().add(new UiGroup("detail", UiLayoutPolicy.column(3)));
        stats = detail.add(new UiGroup("detail.stats", new StatColumns()));
        reason = detail.add(new Label("reason", "").setWrap(true));
        actionRow = detail.add(new ActionRow());
        costChip = actionRow.add(new StatChip("stat:cost", SkillsScreen.KNOWLEDGE_ICON, "", ""));
        powerChip = actionRow.add(new StatChip("stat:power-cost", SkillsScreen.POWER_ICON, "", ""));
        refundChip = actionRow.add(new StatChip("stat:refund", SkillsScreen.KNOWLEDGE_ICON, "", ""));
        learnButton = actionRow.add(new Button(LEARN, "", Button.Style.PRIMARY, this::act));
        unlearnButton = actionRow.add(new Button(UNLEARN, "", Button.Style.DESTRUCTIVE, this::act));
        permanentChip = actionRow.add(new StatChip("stat:permanent", null, ScreenText.PERMANENT.text(), ""));
        permanentChip.setValueColor(Palette.LOCK).setTooltip(new Tooltip(ScreenText.PERMANENT.text(),
                List.of("§7" + ScreenText.PERMANENT_TIP.text())));
        debugChip = actionRow.add(new StatChip("stat:debug", null, ScreenText.DEBUG.text(), ""));
        debugChip.setValueColor(Palette.LOCK).setTooltip(new Tooltip(ScreenText.DEBUG.text(), List.of("§7" + ScreenText.DEBUG_TIP.text())));
        preferences = facing().add(new PreferencePanel("preferences", new PreferencePanel.Actions() {
            @Override
            public void set(String preferenceId, String value) {
                send(PREFERENCE, ScreenText.SAVING.text(),
                        () -> router.actions().setPreference(PreferenceOwner.ADAPTATION, adaptationId, preferenceId, value));
            }

            @Override
            public void reset() {
                send(PREFERENCE, ScreenText.SAVING.text(),
                        () -> router.actions().resetPreference(PreferenceOwner.ADAPTATION, adaptationId));
            }
        }));
        refresh();
    }

    private static String title(ScreenRouter router, String adaptationId) {
        CatalogIndex catalog = router.index();
        AdaptationView adaptation = catalog == null ? null : catalog.adaptation(adaptationId);
        return adaptation == null ? adaptationId : adaptation.name();
    }

    /** The level whose details show; 0 when the adaptation has no levels. */
    public int selectedLevel() {
        return selected;
    }

    /** The learn or unlearn button for the selected level, whichever shows. */
    public Button actionButton() {
        return unlearnButton.visible() ? unlearnButton : learnButton;
    }

    public LevelTrack.Node levelNode(int level) {
        return track.node(level);
    }

    /** The open permanent-learn confirmation, or null. */
    public ConfirmOverlay confirmation() {
        return confirm;
    }

    @Override
    protected String probeKind() {
        return "adapt-adaptation";
    }

    @Override
    protected void describe(Map<String, String> state) {
        super.describe(state);
        state.put("adaptation", adaptationId);
        state.put("selected", Integer.toString(selected));
        PlayerView player = router().state().player();
        state.put("level", Integer.toString(CatalogIndex.level(player, adaptationId)));
    }

    @Override
    protected int pageWidth(UiDraw metrics, int available, int room) {
        int width = Math.max(summary.naturalWidth(metrics), actionRow.naturalWidth(metrics));
        width = Math.max(width, Math.min(Summary.DESCRIPTION, description.preferredWidth(metrics)));
        for (UiNode line : stats.children()) {
            width = Math.max(width, Math.min(StatColumns.NATURAL, line.preferredWidth(metrics)));
        }
        width = Math.max(width, reason.visible() ? reason.preferredWidth(metrics) : 0);
        width = Math.min(Math.min(available, MAX_WIDTH), width);
        while (width < available && page().preferredHeight(width, metrics) > room) {
            width = Math.min(available, width + WIDEN_STEP);
        }
        return width;
    }

    @Override
    protected int facingWidth(UiDraw metrics) {
        return preferences.visible() ? preferences.naturalWidth(metrics) : 0;
    }

    void select(int level) {
        selected = level;
        chosen = true;
        refresh();
        requestLayout();
    }

    @Override
    protected void answered(String done, FeedbackKind kind) {
        pendingLevel = 0;
        if (kind == FeedbackKind.SUCCESS && (LEARN.equals(done) || UNLEARN.equals(done))) {
            chosen = false;
        }
    }

    @Override
    protected void refresh() {
        updatePath();
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        AdaptationView adaptation = catalog == null ? null : catalog.adaptation(adaptationId);
        boolean ready = adaptation != null && player != null;
        boolean open = ready && player.access().shows(adaptation.skillId(), adaptationId);
        boolean settings = open && player.access().preferences() && !adaptation.preferences().isEmpty();
        summary.setVisible(ready);
        description.setVisible(ready && !LegacyText.isBlank(adaptation.description()));
        track.setVisible(open && adaptation.maxLevel() > 0);
        notice.setVisible(!open);
        detail.setVisible(open && adaptation.maxLevel() > 0);
        preferences.setVisible(settings);
        chip("stat:knowledge").setVisible(ready);
        chip("stat:power").setVisible(ready);
        if (!open) {
            notice.setText(!ready ? (catalog == null || player == null ? ScreenText.WAITING.text() : ScreenText.UNAVAILABLE.text())
                    : adaptation.enabled() ? ScreenText.DENIED.text() : ScreenText.DISABLED.text());
            notice.setColor(ready ? Palette.LOCK : 0);
            closeConfirm();
            if (ready) {
                updateSummary(player, adaptation, CatalogIndex.level(player, adaptationId));
            }
            return;
        }
        SkillView skill = catalog.skill(adaptation.skillId());
        if (skill != null) {
            setTheme(router().theme().withAccent(Palette.accent(skill.color())));
        }
        int level = CatalogIndex.level(player, adaptationId);
        int max = adaptation.maxLevel();
        if (!chosen || selected > max) {
            selected = Math.min(max, level + 1);
        }
        updateSummary(player, adaptation, level);
        List<LevelTrack.LevelState> states = new ArrayList<>();
        for (int value = 1; value <= max; value++) {
            states.add(levelState(catalog, player, adaptation, level, value));
        }
        track.update(states, selected, pending() ? pendingLevel : 0);
        updateDetail(catalog, player, adaptation, level);
        if (settings) {
            preferences.update(PreferenceOwner.ADAPTATION, adaptationId, adaptation.preferences(), adaptation.preferenceGroups(), player,
                    level, level < 1 ? ScreenText.LEARN_FIRST.text() : null);
            preferences.setEnabled(!pending());
        }
    }

    private void updateSummary(PlayerView player, AdaptationView adaptation, int level) {
        String state = level == 0 ? ScreenText.NOT_LEARNED.text()
                : ScreenText.LEVEL_OF.text(ScreenText.roman(level), ScreenText.roman(adaptation.maxLevel()));
        summary.set(UiIcons.adaptation(adaptation), adaptation.name(), state);
        description.setText(LegacyText.strip(adaptation.description()));
        summary.setTooltip(new Tooltip(adaptation.name(), List.of("§8" + state)));
        permanentChip.setVisible(adaptation.permanent());
        debugChip.setVisible(player.access().debug());
        long knowledge = LearnRules.knowledge(player, adaptation.skillId());
        chip("stat:knowledge").set(SkillsScreen.KNOWLEDGE_ICON, ScreenText.number(knowledge), "")
                .setTooltip(new Tooltip(ScreenText.KNOWLEDGE_TITLE.text(), List.of("§7" + ScreenText.KNOWLEDGE_SKILL_TIP.text())));
        SkillsScreen.powerChip(chip("stat:power"), player);
    }

    private LevelTrack.LevelState levelState(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int level, int value) {
        boolean learned = value <= level;
        boolean next = value == level + 1;
        List<String> lines = new ArrayList<>();
        boolean available;
        if (learned) {
            LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, value - 1);
            available = check.allowed();
            lines.add("§a" + ScreenText.LEARNED.text());
            if (check.allowed()) {
                lines.add("§7" + LearnText.refund(check));
            } else {
                lines.add("§6" + LearnText.unlearnBlock(check));
            }
        } else {
            LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, value);
            available = check.allowed();
            lines.add((available ? "§a" : "§c") + LearnText.learnBlock(catalog, check));
            lines.add("§7" + (check.free() ? ScreenText.FREE_COST.text()
                    : ScreenText.KNOWLEDGE_COST.text(ScreenText.number(check.knowledgeCost()))));
            lines.add("§7" + ScreenText.POWER_COST.text(check.powerCost()));
        }
        List<String> statLines = adaptation.statLines().get(value - 1);
        if (!statLines.isEmpty()) {
            lines.add("");
            lines.addAll(statLines);
        }
        return new LevelTrack.LevelState(learned, next, available, adaptation.name() + " §f" + ScreenText.roman(value), lines);
    }

    private void updateDetail(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int level) {
        if (selected < 1) {
            return;
        }
        boolean learned = selected <= level;
        stats.clearChildren();
        int line = 0;
        for (String stat : adaptation.statLines().get(selected - 1)) {
            String plain = LegacyText.strip(stat).strip();
            stats.add(new Label("stat-line:" + line++, plain).setWrap(true).setColor(plain.startsWith("*") ? Palette.MUTED : 0));
        }
        if (learned) {
            LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, selected - 1);
            costChip.setVisible(false);
            powerChip.setVisible(false);
            refundChip.setVisible(check.refunded() && check.allowed());
            refundChip.set(SkillsScreen.KNOWLEDGE_ICON, ScreenText.number(check.refund()), "");
            refundChip.setTooltip(Tooltip.of(LearnText.refund(check)));
            String block = check.allowed() && !check.refunded() ? LearnText.refund(check) : LearnText.unlearnBlock(check);
            reason.setText(block);
            reason.setColor(Palette.LOCK);
            reason.setVisible(!block.isEmpty());
            learnButton.setVisible(false);
            unlearnButton.setVisible(true);
            unlearnButton.setText(ScreenText.UNLEARN.text(ScreenText.roman(selected)));
            unlearnButton.setEnabled(check.allowed() && !pending());
            unlearnButton.setTooltip(Tooltip.of(LearnText.refund(check)));
        } else {
            LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, selected);
            costChip.setVisible(true);
            powerChip.setVisible(true);
            refundChip.setVisible(false);
            if (check.free()) {
                costChip.set(SkillsScreen.KNOWLEDGE_ICON, ScreenText.FREE_COST.text(), "").setValueColor(Palette.GOOD);
            } else {
                costChip.set(SkillsScreen.KNOWLEDGE_ICON, ScreenText.number(check.knowledgeCost()), "")
                        .setValueColor(check.knowledge() < check.knowledgeCost() ? Palette.BAD : 0);
            }
            costChip.setTooltip(new Tooltip(ScreenText.KNOWLEDGE_COST.text(ScreenText.number(check.knowledgeCost())),
                    List.of("§7" + ScreenText.KNOWLEDGE_SKILL_TIP.text(), "§b" + ScreenText.number(check.knowledge()) + " §7"
                            + ScreenText.KNOWLEDGE.text())));
            powerChip.set(SkillsScreen.POWER_ICON, Integer.toString(check.powerCost()), "")
                    .setValueColor(check.freePower() < check.powerCost() ? Palette.BAD : 0);
            powerChip.setTooltip(new Tooltip(ScreenText.POWER_COST.text(check.powerCost()),
                    List.of("§7" + ScreenText.POWER_TIP.text(check.freePower(), player.header().powerMax()))));
            boolean blocked = !check.allowed();
            reason.setText(blocked ? LearnText.learnBlock(catalog, check) : "");
            reason.setColor(check.block() == LearnRules.LearnBlock.REQUIREMENTS ? Palette.LOCK : Palette.BAD);
            reason.setVisible(blocked);
            unlearnButton.setVisible(false);
            learnButton.setVisible(true);
            learnButton.setText(ScreenText.LEARN.text(ScreenText.roman(selected)));
            learnButton.setEnabled(check.allowed() && !pending());
            List<String> learnLines = new ArrayList<>();
            learnLines.add("§7" + (selected == level + 1 ? ScreenText.NEXT.text() : ScreenText.NOT_LEARNED.text()));
            for (RequirementView requirement : adaptation.requirements()) {
                boolean met = CatalogIndex.level(player, requirement.adaptationId()) >= requirement.level();
                learnLines.add((met ? "§a" : "§c") + LearnText.requirement(catalog, requirement));
            }
            learnButton.setTooltip(new Tooltip(ScreenText.LEVEL.text(ScreenText.roman(selected)), learnLines));
        }
        stats.setVisible(!stats.children().isEmpty());
    }

    private void act() {
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        AdaptationView adaptation = catalog == null ? null : catalog.adaptation(adaptationId);
        if (adaptation == null || player == null || pending() || selected < 1) {
            return;
        }
        int level = CatalogIndex.level(player, adaptationId);
        int target = selected;
        if (target <= level) {
            if (LearnRules.unlearn(catalog, player, adaptation, target - 1).allowed()) {
                pendingLevel = target;
                send(UNLEARN, ScreenText.UNLEARNING.text(), () -> router().actions().unlearn(adaptationId, target - 1));
            }
            return;
        }
        LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, target);
        if (!check.allowed()) {
            return;
        }
        if (adaptation.permanent() && !check.free()) {
            confirm = new ConfirmOverlay("confirm", ScreenText.CONFIRM_TITLE.text(),
                    ScreenText.CONFIRM_TEXT.text(adaptation.name(), ScreenText.number(check.knowledgeCost())),
                    ScreenText.LEARN.text(ScreenText.roman(target)), ScreenText.CANCEL.text(), Button.Style.PRIMARY,
                    () -> {
                        confirm = null;
                        learn(target);
                    },
                    () -> confirm = null);
            openOverlay(confirm);
            return;
        }
        learn(target);
    }

    private void learn(int target) {
        pendingLevel = target;
        send(LEARN, ScreenText.LEARNING.text(), () -> router().actions().learn(adaptationId, target));
    }

    private void closeConfirm() {
        UiOverlay open = confirm;
        if (open != null) {
            confirm = null;
            closeOverlay(open);
        }
    }

    /**
     * The adaptation's icon and name with the level pips at the right end of the same line, or under the name from the
     * page's left edge when they do not share it.
     */
    private static final class Summary extends UiGroup {
        static final int DESCRIPTION = 240;
        private static final int GAP = 4;
        private static final int PIPS_GAP = 8;

        private IconRef icon;
        private String name = "";
        private String state = "";

        private Summary() {
            super("summary");
        }

        void set(IconRef newIcon, String newName, String newState) {
            icon = newIcon;
            name = newName;
            state = newState;
        }

        @Override
        public String kind() {
            return "summary";
        }

        @Override
        public String label() {
            return LegacyText.strip(name);
        }

        @Override
        public void describe(Map<String, String> values) {
            values.put("state", LegacyText.strip(state));
        }

        @Override
        public boolean hittable() {
            return true;
        }

        private LevelTrack track() {
            return (LevelTrack) children().get(0);
        }

        private int pipsWidth(UiDraw metrics) {
            return track().visible() ? track().preferredWidth(metrics) : 0;
        }

        private int nameWidth(UiDraw metrics) {
            return UiIcons.SIZE + GAP + metrics.textWidth(name);
        }

        int naturalWidth(UiDraw metrics) {
            int pips = pipsWidth(metrics);
            return nameWidth(metrics) + (pips > 0 ? PIPS_GAP + pips : 0);
        }

        private boolean shared(int width, UiDraw metrics) {
            return naturalWidth(metrics) <= width;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            int line = Math.max(UiIcons.SIZE, metrics.lineHeight());
            if (pipsWidth(metrics) == 0 || shared(width, metrics)) {
                return line;
            }
            return line + 3 + track().preferredHeight(width, metrics);
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            LevelTrack track = track();
            if (!track.visible()) {
                return;
            }
            int pips = pipsWidth(metrics);
            if (shared(box.width(), metrics)) {
                track.place(new UiRect(box.right() - pips, box.y() + (UiIcons.SIZE - LevelTrack.NODE_HEIGHT) / 2, pips,
                        LevelTrack.NODE_HEIGHT), metrics);
                return;
            }
            int top = box.y() + Math.max(UiIcons.SIZE, metrics.lineHeight()) + 3;
            track.place(new UiRect(box.x(), top, box.width(), track.preferredHeight(box.width(), metrics)), metrics);
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            UiIcons.paintOn(draw, icon, box.x(), box.y(), UiIcons.SIZE, false, frame.theme().backdrop());
            int left = box.x() + UiIcons.SIZE + GAP;
            int room = Math.max(1, box.right() - left);
            String shown = LegacyText.truncate(draw, name, room);
            draw.text(shown, left, box.y() + (UiIcons.SIZE - draw.lineHeight()) / 2 + 1, frame.theme().accent(), false);
        }
    }

    /** The selected level's cost or refund chips and its button from the left edge, wrapping onto a second line when narrow. */
    private static final class ActionRow extends UiGroup {
        private static final int GAP = 3;

        private ActionRow() {
            super("detail.action");
        }

        int naturalWidth(UiDraw metrics) {
            int width = 0;
            for (UiNode node : visibleChildren()) {
                width += (width == 0 ? 0 : GAP) + node.preferredWidth(metrics);
            }
            return width;
        }

        private int lines(int width, UiDraw metrics) {
            int lines = 1;
            int x = 0;
            for (UiNode node : visibleChildren()) {
                int nodeWidth = node.preferredWidth(metrics);
                if (x > 0 && x + nodeWidth > width) {
                    lines++;
                    x = 0;
                }
                x += nodeWidth + GAP;
            }
            return lines;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            int lines = lines(width, metrics);
            return lines * AdaptScreen.BAR + (lines - 1) * GAP;
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            int x = 0;
            int y = box.y();
            for (UiNode node : visibleChildren()) {
                int width = Math.min(box.width(), node.preferredWidth(metrics));
                if (x > 0 && x + width > box.width()) {
                    x = 0;
                    y += AdaptScreen.BAR + GAP;
                }
                int height = Math.min(AdaptScreen.BAR, node.preferredHeight(width, metrics));
                node.place(new UiRect(box.x() + x, y + (AdaptScreen.BAR - height) / 2, width, height), metrics);
                x += width + GAP;
            }
        }
    }

    /**
     * Stat lines one under another, wrapping when long, or in two columns (first half left, second half right) when there
     * are many and every line fits its column on one line.
     */
    private static final class StatColumns implements UiLayoutPolicy {
        static final int NATURAL = 320;
        private static final int MIN_COLUMN = 170;
        private static final int SPLIT_AT = 5;
        private static final int GAP = 8;

        private static int columns(List<UiNode> lines, int width, UiDraw metrics) {
            int column = (width - GAP) / 2;
            if (lines.size() < SPLIT_AT || column < MIN_COLUMN) {
                return 1;
            }
            for (UiNode line : lines) {
                if (line.preferredWidth(metrics) > column) {
                    return 1;
                }
            }
            return 2;
        }

        @Override
        public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
            int columns = columns(children, area.width(), metrics);
            int perColumn = (children.size() + columns - 1) / columns;
            int width = (area.width() - (columns - 1) * GAP) / columns;
            int y = area.y();
            for (int index = 0; index < children.size(); index++) {
                int column = index / Math.max(1, perColumn);
                if (index % Math.max(1, perColumn) == 0) {
                    y = area.y();
                }
                UiNode line = children.get(index);
                int height = line.preferredHeight(width, metrics);
                line.place(new UiRect(area.x() + column * (width + GAP), y, width, height), metrics);
                y += height;
            }
        }

        @Override
        public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
            int columns = columns(children, width, metrics);
            int perColumn = (children.size() + columns - 1) / columns;
            int columnWidth = (width - (columns - 1) * GAP) / columns;
            int tallest = 0;
            int height = 0;
            for (int index = 0; index < children.size(); index++) {
                if (index % Math.max(1, perColumn) == 0) {
                    height = 0;
                }
                height += children.get(index).preferredHeight(columnWidth, metrics);
                tallest = Math.max(tallest, height);
            }
            return tallest;
        }
    }
}
