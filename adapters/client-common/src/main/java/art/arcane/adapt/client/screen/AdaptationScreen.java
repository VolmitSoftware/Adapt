package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.StatChip;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One adaptation. The left page reads top to bottom from one left edge: icon, name and level pips on one line, the
 * description and one level's stat lines, then at the foot a line that says what blocks an action and the action row.
 *
 * <p>The pips only show state. The row always holds the same two actions in the same places: a primary button that
 * learns the next level, with what it costs beside it, and a quiet one that unlearns the top level, with what it
 * gives back. Neither changes meaning. Pointing at a pip, or moving keyboard focus to it, shows that level's stats and
 * what reaching it would cost or refund without touching the buttons. Clicking a pip offers to bring the adaptation
 * straight to that level: the row turns into one confirm button with the summed cost or refund and Cancel, until the
 * player confirms, presses Escape or clicks elsewhere. The same offer asks before a learn that cannot be undone and
 * before an unlearn that returns nothing; debug mode, where the server skips both, asks nothing. With no control
 * focused Enter learns the next level or confirms the open offer, and Delete unlearns the top level; Backspace goes
 * back as on every page.
 *
 * <p>Every slot of the row keeps the width of the widest thing it can hold, and the stat lines and the blocking line
 * keep the room their tallest level needs, so nothing moves when a level changes. Preferences are a grid on the facing
 * page with reset at the foot. Text wraps instead of being cut. An adaptation the player's skill page does not show
 * says so instead. Every change waits for the server: the buttons and preferences are disabled and the pip in question
 * blinks until the answer arrives.
 */
public final class AdaptationScreen extends AdaptScreen {
    static final String LEARN = "learn";
    static final String UNLEARN = "unlearn";
    static final String CONFIRM = "confirm";
    static final String CANCEL = "cancel";
    static final String PREFERENCE = "preference";
    /** Drops the facing page's first row so its text sits on the line of the name across the spine. */
    private static final int FACING_DROP = (UiIcons.SIZE - PreferencePanel.CELL_HEIGHT) / 2;

    private final String adaptationId;
    private final Summary summary;
    private final Label description;
    private final StatChip permanentChip;
    private final StatChip debugChip;
    private final Label notice;
    private final ActionRow actionRow;
    private final LevelTrack track;
    private final StatChip costChip;
    private final StatChip powerChip;
    private final InkValue refund;
    private final Button learnButton;
    private final Button unlearnButton;
    private final Button confirmButton;
    private final Button cancelButton;
    private final Stats stats;
    private final Reason reason;
    private final PreferencePanel preferences;
    private Offer offer;
    private int peek;
    private int shown;
    private int pendingLevel;

    AdaptationScreen(ScreenRouter router, Route.Adaptation route) {
        super(title(router, route.adaptationId()), route, router);
        adaptationId = route.adaptationId();
        summary = page().add(new Summary());
        track = summary.add(new LevelTrack("levels", this::pick));
        description = page().add(new Label("description", "").setWrap(true).setColor(Palette.MUTED));
        notice = page().add(new Label("notice", "").setWrap(true));
        stats = page().add(new Stats());
        reason = page().add(new Reason());
        actionRow = page().add(new ActionRow());
        page().setLayout(UiLayoutPolicy.footed(PAGE_GAP, reason, actionRow));
        facing().setLayout(new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                preferences.place(area.belowTop(FACING_DROP), metrics);
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                int height = preferences.visible() ? preferences.preferredHeight(width, metrics) : 0;
                return height == 0 ? 0 : height + FACING_DROP;
            }
        });
        learnButton = actionRow.add(new Button(LEARN, "", Button.Style.PRIMARY, this::levelUp));
        confirmButton = actionRow.add(new Button(CONFIRM, "", Button.Style.PRIMARY, this::confirm));
        costChip = actionRow.add(new StatChip("stat:cost", SkillsScreen.KNOWLEDGE_ICON, "", ""));
        powerChip = actionRow.add(new StatChip("stat:power-cost", SkillsScreen.POWER_ICON, "", ""));
        debugChip = actionRow.add(new StatChip("stat:debug", null, ScreenText.DEBUG.text(), ""));
        debugChip.setValueColor(Palette.LOCK).setTooltip(new Tooltip(ScreenText.DEBUG.text(), List.of("§7" + ScreenText.DEBUG_TIP.text())));
        refund = actionRow.add(new InkValue("stat:refund"));
        unlearnButton = actionRow.add(new Button(UNLEARN, "", Button.Style.QUIET, this::unlearnTop));
        cancelButton = actionRow.add(new Button(CANCEL, ScreenText.CANCEL.text(), Button.Style.QUIET, this::cancel));
        permanentChip = actionRow.add(new StatChip("stat:permanent", null, ScreenText.PERMANENT.text(), ""));
        permanentChip.setValueColor(Palette.LOCK).setTooltip(new Tooltip(ScreenText.PERMANENT.text(),
                List.of("§7" + ScreenText.PERMANENT_TIP.text())));
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
        }, this::spreadOpen));
        refresh();
    }

    private static String title(ScreenRouter router, String adaptationId) {
        CatalogIndex catalog = router.index();
        AdaptationView adaptation = catalog == null ? null : catalog.adaptation(adaptationId);
        return adaptation == null ? adaptationId : adaptation.name();
    }

    /** A level the row offers to bring the adaptation to, by learning up to it or unlearning down to it. */
    public record Offer(int target, boolean learn) {
    }

    /** The level whose stats show; 0 when the adaptation has no levels. */
    public int shownLevel() {
        return shown;
    }

    /** The offer waiting for the player's confirmation, or null. */
    public Offer offer() {
        return offer;
    }

    public Button learnButton() {
        return learnButton;
    }

    public LevelTrack.Node levelNode(int level) {
        return track.node(level);
    }

    @Override
    protected String probeKind() {
        return "adapt-adaptation";
    }

    @Override
    protected void describe(Map<String, String> state) {
        super.describe(state);
        state.put("adaptation", adaptationId);
        state.put("shown", Integer.toString(shown));
        state.put("offer", offer == null ? "" : (offer.learn() ? "learn:" : "unlearn:") + offer.target());
        PlayerView player = router().state().player();
        state.put("level", Integer.toString(CatalogIndex.level(player, adaptationId)));
    }

    @Override
    protected boolean footed() {
        return true;
    }

    @Override
    public void render(UiDraw draw, int mouseX, int mouseY, float delta) {
        int level = track.levelOf(hit(mouseX, mouseY));
        if (level == 0 && track.shown() && track.bounds().contains(mouseX, mouseY)) {
            level = peek;
        }
        if (level == 0) {
            level = track.levelOf(focused());
        }
        if (level != peek) {
            peek = level;
            refresh();
            requestLayout();
        }
        super.render(draw, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(UiMouseEvent event) {
        if (offer != null) {
            UiNode target = hit(event.x(), event.y());
            if (target == null || !actionRow.isAncestorOf(target) && !track.isAncestorOf(target)) {
                cancel();
            }
        }
        return super.mouseClicked(event);
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        boolean plain = event.modifiers().equals(UiModifiers.NONE) && overlays().isEmpty() && !editingText();
        UiKey key = event.key();
        if (plain && offer != null && key == UiKey.ESCAPE) {
            cancel();
            return true;
        }
        if (plain && key == UiKey.ENTER && offer != null && (focused() == null || track.levelOf(focused()) == offer.target())) {
            confirm();
            return true;
        }
        if (plain && key == UiKey.ENTER && offer == null && focused() == null) {
            levelUp();
            return true;
        }
        if (plain && key == UiKey.DELETE && offer == null) {
            unlearnTop();
            return true;
        }
        if (plain && (key == UiKey.LEFT || key == UiKey.RIGHT) && focused() == null && track.shown() && shown > 0) {
            super.keyPressed(event);
            focus(track.node(shown));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void answered(String done, FeedbackKind kind) {
        pendingLevel = 0;
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
        boolean levels = open && adaptation.maxLevel() > 0;
        actionRow.setVisible(levels);
        stats.setVisible(levels);
        reason.setVisible(levels);
        preferences.setVisible(settings);
        chip("stat:knowledge").setVisible(ready);
        chip("stat:power").setVisible(ready);
        if (!open) {
            notice.setText(!ready ? (catalog == null || player == null ? ScreenText.WAITING.text() : ScreenText.UNAVAILABLE.text())
                    : adaptation.enabled() ? ScreenText.DENIED.text() : ScreenText.DISABLED.text());
            notice.setColor(ready ? Palette.LOCK : 0);
            offer = null;
            shown = 0;
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
        if (offer != null && (offer.target() > max || (offer.learn() ? offer.target() <= level : offer.target() >= level))) {
            offer = null;
        }
        int hovered = Math.min(peek, max);
        shown = hovered > 0 ? hovered : offer != null ? Math.max(1, offer.target()) : Math.min(max, level + 1);
        updateSummary(player, adaptation, level);
        if (!levels) {
            return;
        }
        boolean nextOpen = level < max && (offer == null || offer.learn()) && LearnRules.learn(catalog, player, adaptation, level + 1).allowed();
        List<LevelTrack.LevelState> states = new ArrayList<>();
        for (int value = 1; value <= max; value++) {
            LevelTrack.Pip pip = value <= level ? LevelTrack.Pip.LEARNED
                    : value == level + 1 && nextOpen ? LevelTrack.Pip.OPEN : LevelTrack.Pip.EMPTY;
            boolean offered = offer != null && (offer.learn() ? value > level && value <= offer.target()
                    : value <= level && value > offer.target());
            states.add(new LevelTrack.LevelState(pip, offered));
        }
        track.update(states, shown, pending() ? pendingLevel : 0);
        stats.show(adaptation.statLines(), shown);
        updateReason(catalog, player, adaptation, level, hovered);
        updateActions(catalog, player, adaptation, level);
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
        long knowledge = LearnRules.knowledge(player, adaptation.skillId());
        chip("stat:knowledge").set(SkillsScreen.KNOWLEDGE_ICON, ScreenText.number(knowledge), "")
                .setTooltip(new Tooltip(ScreenText.KNOWLEDGE_TITLE.text(), List.of("§7" + ScreenText.KNOWLEDGE_SKILL_TIP.text())));
        SkillsScreen.powerChip(chip("stat:power"), player);
    }

    /** How a line under the stats reads: plain information, a resource the player is short of, or a rule that locks the action. */
    enum Tone {
        INFO,
        SHORT,
        LOCK
    }

    /** One line for the foot of the page and how it reads; empty text shows nothing. */
    record Note(String text, Tone tone) {
        static final Note NONE = new Note("", Tone.INFO);
    }

    private static Note blocked(CatalogIndex catalog, LearnRules.LearnCheck check) {
        return switch (check.block()) {
            case NONE, LEARNED -> Note.NONE;
            case POWER, KNOWLEDGE -> new Note(LearnText.learnBlock(catalog, check), Tone.SHORT);
            case REQUIREMENTS, DISABLED, DENIED -> new Note(LearnText.learnBlock(catalog, check), Tone.LOCK);
        };
    }

    private static Note blocked(LearnRules.UnlearnCheck check) {
        String text = LearnText.unlearnBlock(check);
        return text.isEmpty() ? Note.NONE : new Note(text, Tone.LOCK);
    }

    /** What reaching {@code value} from {@code level} would cost or give back, or what stops it; nothing for the level held. */
    private static Note reach(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int level, int value) {
        if (value > level) {
            LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, value);
            return check.allowed() ? new Note(LearnText.learnBlock(catalog, check), Tone.INFO) : blocked(catalog, check);
        }
        if (value < level) {
            LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, value);
            return check.allowed() ? new Note(LearnText.unlearnOffer(check), Tone.INFO) : blocked(check);
        }
        return Note.NONE;
    }

    /** What the open offer still needs said: what blocks it, or the warning it asks the player to accept. */
    private Note offerNote(CatalogIndex catalog, PlayerView player, AdaptationView adaptation) {
        if (offer.learn()) {
            LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, offer.target());
            if (!check.allowed()) {
                return blocked(catalog, check);
            }
            return adaptation.permanent() && !check.free() ? new Note(ScreenText.PERMANENT_TIP.text(), Tone.LOCK) : Note.NONE;
        }
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, offer.target());
        if (!check.allowed()) {
            return blocked(check);
        }
        return check.refunded() || player.access().debug() ? Note.NONE : new Note(ScreenText.NO_REFUND.text(), Tone.LOCK);
    }

    private void updateReason(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int level, int hovered) {
        int max = adaptation.maxLevel();
        Note rest = level < max ? blocked(catalog, LearnRules.learn(catalog, player, adaptation, level + 1)) : Note.NONE;
        List<String> every = new ArrayList<>();
        every.add(rest.text());
        for (int value = 1; value <= max; value++) {
            every.add(reach(catalog, player, adaptation, level, value).text());
        }
        PlayerAccess access = player.access();
        if (!access.debug()) {
            every.add(adaptation.permanent() ? ScreenText.PERMANENT_TIP.text() : "");
            every.add(access.refunds() ? "" : ScreenText.NO_REFUND.text());
        }
        Note note = offer != null ? offerNote(catalog, player, adaptation)
                : hovered > 0 && hovered != level ? reach(catalog, player, adaptation, level, hovered) : rest;
        reason.show(note, every);
    }

    private static String learnLabel(int from, int to) {
        if (to > from + 1) {
            return ScreenText.LEARN_TO.text(ScreenText.roman(to));
        }
        return from == 0 ? ScreenText.LEARN.text(ScreenText.roman(to)) : ScreenText.LEVEL_UP.text(ScreenText.roman(to));
    }

    private static String unlearnLabel(int from, int to) {
        return to < from - 1 ? ScreenText.UNLEARN_TO.text(ScreenText.roman(to)) : ScreenText.UNLEARN.text(ScreenText.roman(from));
    }

    private static String costText(LearnRules.LearnCheck check) {
        return check.free() ? ScreenText.FREE_COST.text() : ScreenText.number(check.knowledgeCost());
    }

    private static String refundText(int refund) {
        return "+" + ScreenText.number(refund);
    }

    /** Tells the row the widest thing each slot can hold at any level, in any offer. */
    private void reserve(AdaptationView adaptation, PlayerAccess access) {
        int max = adaptation.maxLevel();
        boolean refunds = access.refunds() && !access.debug();
        List<String> primary = new ArrayList<>(List.of(ScreenText.MASTERED.text()));
        List<String> secondary = new ArrayList<>(List.of(ScreenText.CANCEL.text()));
        List<String> costs = new ArrayList<>();
        List<String> powers = new ArrayList<>();
        List<String> refunded = new ArrayList<>();
        for (int from = 0; from <= max; from++) {
            for (int to = 0; to <= max; to++) {
                if (to > from) {
                    primary.add(learnLabel(from, to));
                    costs.add(access.debug() ? ScreenText.FREE_COST.text() : ScreenText.number(LearnRules.knowledgeCost(adaptation, from, to)));
                    powers.add(Integer.toString(LearnRules.powerCost(adaptation, from, to)));
                } else if (to < from) {
                    primary.add(unlearnLabel(from, to));
                    if (refunds) {
                        costs.add(refundText(LearnRules.refund(adaptation, to, from)));
                    }
                    if (to == from - 1) {
                        secondary.add(unlearnLabel(from, to));
                        if (refunds) {
                            refunded.add(refundText(LearnRules.refund(adaptation, to, from)));
                        }
                    }
                }
            }
        }
        actionRow.reserve(primary, costs, powers, refunded, secondary);
    }

    private void updateActions(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int level) {
        PlayerAccess access = player.access();
        int max = adaptation.maxLevel();
        reserve(adaptation, access);
        boolean offering = offer != null;
        boolean learning = offering ? offer.learn() : level < max;
        boolean permanent = adaptation.permanent() && !access.debug();
        debugChip.setVisible(access.debug());
        learnButton.setVisible(!offering);
        confirmButton.setVisible(offering);
        cancelButton.setVisible(offering);
        cancelButton.setEnabled(!pending());
        permanentChip.setVisible(!offering && permanent);
        LearnRules.UnlearnCheck top = LearnRules.unlearn(catalog, player, adaptation, level - 1);
        unlearnButton.setVisible(!offering && !permanent && level > 0);
        unlearnButton.setText(unlearnLabel(level, level - 1));
        unlearnButton.setEnabled(top.allowed() && !pending());
        String topNote = top.allowed() ? LearnText.refund(top) : LearnText.unlearnBlock(top);
        unlearnButton.setTooltip(topNote.isEmpty() ? null : Tooltip.of(topNote));
        refund.setVisible(unlearnButton.visible() && top.allowed() && top.refunded());
        refund.set(refundText(top.refund()));
        refund.setTooltip(Tooltip.of(LearnText.refund(top)));
        costChip.setVisible(learning || offering && LearnRules.unlearn(catalog, player, adaptation, offer.target()).refunded());
        powerChip.setVisible(learning);
        if (learning) {
            int target = offering ? offer.target() : level + 1;
            LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, target);
            costChip.set(SkillsScreen.KNOWLEDGE_ICON, costText(check), "")
                    .setValueColor(check.free() ? Palette.GOOD : check.knowledge() < check.knowledgeCost() ? Palette.BAD : 0);
            costChip.setTooltip(new Tooltip(ScreenText.KNOWLEDGE_COST.text(ScreenText.number(check.knowledgeCost())),
                    List.of("§7" + ScreenText.KNOWLEDGE_SKILL_TIP.text(), "§b" + ScreenText.number(check.knowledge()) + " §7"
                            + ScreenText.KNOWLEDGE.text())));
            powerChip.set(SkillsScreen.POWER_ICON, Integer.toString(check.powerCost()), "")
                    .setValueColor(!check.free() && check.freePower() < check.powerCost() ? Palette.BAD : 0);
            powerChip.setTooltip(new Tooltip(ScreenText.POWER_COST.text(check.powerCost()),
                    List.of("§7" + ScreenText.POWER_TIP.text(check.freePower(), player.header().powerMax()))));
            List<String> requirements = new ArrayList<>();
            for (RequirementView requirement : adaptation.requirements()) {
                boolean met = CatalogIndex.level(player, requirement.adaptationId()) >= requirement.level();
                requirements.add((met ? "§a" : "§c") + LearnText.requirement(catalog, requirement));
            }
            Button button = offering ? confirmButton : learnButton;
            button.setText(learnLabel(level, target));
            button.setStyle(Button.Style.PRIMARY);
            button.setEnabled(check.allowed() && !pending());
            button.setTooltip(requirements.isEmpty() ? null : new Tooltip(ScreenText.LEVEL.text(ScreenText.roman(target)), requirements));
            return;
        }
        if (!offering) {
            learnButton.setText(ScreenText.MASTERED.text());
            learnButton.setEnabled(false);
            learnButton.setTooltip(null);
            return;
        }
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, offer.target());
        costChip.set(SkillsScreen.KNOWLEDGE_ICON, refundText(check.refund()), "").setValueColor(Palette.GOOD);
        costChip.setTooltip(Tooltip.of(LearnText.refund(check)));
        confirmButton.setText(unlearnLabel(level, offer.target()));
        confirmButton.setStyle(Button.Style.DESTRUCTIVE);
        confirmButton.setEnabled(check.allowed() && !pending());
        confirmButton.setTooltip(null);
    }

    private AdaptationView adaptation() {
        CatalogIndex catalog = router().index();
        return catalog == null ? null : catalog.adaptation(adaptationId);
    }

    /** Offers to bring the adaptation to {@code level}, as clicking that pip does; the level already held closes an open offer. */
    void pick(int level) {
        PlayerView player = router().state().player();
        AdaptationView adaptation = adaptation();
        if (adaptation == null || player == null || pending()) {
            return;
        }
        int held = CatalogIndex.level(player, adaptationId);
        offer = level == held || level < 1 || level > adaptation.maxLevel() ? null : new Offer(level, level > held);
        refresh();
        requestLayout();
    }

    /** Learns the next level, asking first when that cannot be undone. */
    void levelUp() {
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        AdaptationView adaptation = adaptation();
        if (adaptation == null || player == null || pending()) {
            return;
        }
        int target = CatalogIndex.level(player, adaptationId) + 1;
        LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, target);
        if (!check.allowed()) {
            return;
        }
        if (adaptation.permanent() && !check.free()) {
            pick(target);
            return;
        }
        learn(target);
    }

    /** Unlearns the top level, asking first when the server returns nothing for it. */
    void unlearnTop() {
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        AdaptationView adaptation = adaptation();
        if (adaptation == null || player == null || pending()) {
            return;
        }
        int level = CatalogIndex.level(player, adaptationId);
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, level - 1);
        if (!check.allowed()) {
            return;
        }
        if (!check.refunded() && !player.access().debug()) {
            offer = new Offer(level - 1, false);
            refresh();
            requestLayout();
            return;
        }
        unlearn(level, level - 1);
    }

    /** Sends the open offer, if the server would accept it. */
    void confirm() {
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        AdaptationView adaptation = adaptation();
        Offer open = offer;
        if (open == null || adaptation == null || player == null || pending()) {
            return;
        }
        if (open.learn()) {
            if (LearnRules.learn(catalog, player, adaptation, open.target()).allowed()) {
                offer = null;
                learn(open.target());
            }
            return;
        }
        if (LearnRules.unlearn(catalog, player, adaptation, open.target()).allowed()) {
            offer = null;
            unlearn(CatalogIndex.level(player, adaptationId), open.target());
        }
    }

    void cancel() {
        if (offer != null) {
            offer = null;
            refresh();
            requestLayout();
        }
    }

    private void learn(int target) {
        pendingLevel = target;
        send(LEARN, ScreenText.LEARNING.text(), () -> router().actions().learn(adaptationId, target));
    }

    private void unlearn(int from, int target) {
        pendingLevel = from;
        send(UNLEARN, ScreenText.UNLEARNING.text(), () -> router().actions().unlearn(adaptationId, target));
    }

    /**
     * The adaptation's icon and name with the level pips at the right end of the same line, or under the name from the
     * page's left edge when they do not share it.
     */
    private static final class Summary extends UiGroup {
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

    /** The selected level's stat lines, as tall as the level with the most to say. */
    private static final class Stats extends UiGroup {
        private final StatColumns columns;
        private List<List<UiNode>> levels = List.of();

        private Stats() {
            this(new StatColumns());
        }

        private Stats(StatColumns columns) {
            super("detail.stats", columns);
            this.columns = columns;
        }

        void show(List<List<String>> lines, int selected) {
            List<List<UiNode>> next = new ArrayList<>();
            for (List<String> level : lines) {
                List<UiNode> labels = new ArrayList<>();
                for (String stat : level) {
                    labels.add(new StatLine("stat-line:" + labels.size(), LegacyText.strip(stat).strip()));
                }
                next.add(labels);
            }
            levels = next;
            clearChildren();
            boolean any = false;
            for (List<UiNode> level : next) {
                any |= !level.isEmpty();
            }
            for (UiNode label : next.get(selected - 1)) {
                add(label);
            }
            setVisible(any);
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            int tallest = 0;
            for (List<UiNode> level : levels) {
                tallest = Math.max(tallest, columns.preferredHeight(level, width, metrics));
            }
            return tallest;
        }
    }

    /**
     * One stat line. A line led by a sign or a note mark ("+ 4 Max Jumps", "* Only while sprinting") keeps the mark in
     * the margin and wraps its text beside it, so a wrapped line reads as one entry; notes are muted.
     */
    private static final class StatLine extends UiNode {
        private static final String MARKS = "+-*\u2013";
        private static final int INDENT = 9;

        private final String mark;
        private final String text;

        private StatLine(String id, String line) {
            super(id);
            boolean marked = line.length() > 2 && line.charAt(1) == ' ' && MARKS.indexOf(line.charAt(0)) >= 0;
            mark = marked ? line.substring(0, 1) : "";
            text = marked ? line.substring(2).strip() : line;
        }

        @Override
        public String kind() {
            return "label";
        }

        @Override
        public String label() {
            return mark.isEmpty() ? text : mark + " " + text;
        }

        private int indent() {
            return mark.isEmpty() ? 0 : INDENT;
        }

        @Override
        public int preferredWidth(UiDraw metrics) {
            return indent() + metrics.textWidth(text);
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return metrics.wrappedHeight(text, Math.max(1, width - indent()));
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            int color = mark.equals("*") ? Palette.MUTED : frame.theme().text();
            if (!mark.isEmpty()) {
                draw.text(mark, box.x(), box.y(), color, false);
            }
            draw.wrappedText(text, box.x() + indent(), box.y(), Math.max(1, box.width() - indent()), color, false);
        }
    }

    /**
     * The line above the actions: what blocks one, or what the level under the pointer would cost. A rule that locks an
     * action is gold behind a padlock, a shortage is wine, information is muted. It keeps the height of the longest
     * line it can show and never less than two lines, so a note that wraps at one level does not move the text above.
     */
    private static final class Reason extends UiNode {
        private static final int LOCK_ROOM = 10;

        private List<String> all = List.of();
        private Note note = Note.NONE;

        private Reason() {
            super("reason");
        }

        void show(Note shown, List<String> every) {
            all = List.copyOf(every);
            note = shown;
            boolean any = false;
            for (String text : all) {
                any |= !text.isEmpty();
            }
            setVisible(any);
        }

        @Override
        public String kind() {
            return "label";
        }

        @Override
        public String label() {
            return note.text();
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("tone", note.tone().name());
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            int tallest = 0;
            for (String text : all) {
                if (!text.isEmpty()) {
                    tallest = Math.max(tallest, metrics.wrappedHeight(text, Math.max(1, width - LOCK_ROOM)));
                }
            }
            return tallest == 0 ? 0 : Math.max(tallest, metrics.lineHeight() * 2);
        }

        @Override
        protected void paint(UiFrame frame) {
            if (note.text().isEmpty()) {
                return;
            }
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            int color = switch (note.tone()) {
                case INFO -> frame.theme().textMuted();
                case SHORT -> Palette.BAD;
                case LOCK -> Palette.LOCK;
            };
            int indent = note.tone() == Tone.LOCK ? LOCK_ROOM : 0;
            if (indent > 0) {
                UiTome.lock(draw, box.x() + 1, box.y());
            }
            draw.wrappedText(note.text(), box.x() + indent, box.y(), Math.max(1, box.width() - indent), color, false);
        }
    }

    /** A number in muted ink behind the ink book, such as what unlearning gives back: quieter than a chip. */
    private static final class InkValue extends UiNode {
        private static final int GAP = 3;

        private String value = "";

        private InkValue(String id) {
            super(id);
        }

        void set(String text) {
            value = text;
        }

        String value() {
            return value;
        }

        @Override
        public String kind() {
            return "stat";
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("value", value);
        }

        @Override
        public boolean hittable() {
            return true;
        }

        static int width(UiDraw metrics, String text) {
            return UiTome.Glyph.BOOK.width() + GAP + metrics.textWidth(text);
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return services().theme().controlHeight();
        }

        @Override
        public int preferredWidth(UiDraw metrics) {
            return width(metrics, value);
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            int ink = frame.theme().textMuted();
            int textY = UiControl.textY(box);
            int left = box.right() - width(draw, value);
            UiTome.glyph(draw, UiTome.Glyph.BOOK, left, textY + UiControl.CAP_HEIGHT - UiTome.Glyph.BOOK.height(), ink);
            draw.text(value, left + UiTome.Glyph.BOOK.width() + GAP, textY, ink, false);
        }
    }

    /**
     * The actions in fixed slots: the primary button with the knowledge and power it costs after it, then against the
     * right edge what unlearning gives back and the quiet button. An offer puts its confirm button in the primary slot
     * and Cancel in the quiet one. Each slot is as wide as the widest thing {@link #reserve} says it can hold, so
     * nothing in the row moves when a level or an offer changes. The quiet slot drops to a line of its own, still
     * against the right edge, when the page is too narrow for both.
     */
    private static final class ActionRow extends UiGroup {
        private static final int GAP = 3;
        private static final int APART = 8;

        private List<String> primary = List.of();
        private List<String> costs = List.of();
        private List<String> powers = List.of();
        private List<String> refunds = List.of();
        private List<String> secondary = List.of();

        private ActionRow() {
            super("detail.action");
        }

        void reserve(List<String> primaryLabels, List<String> costValues, List<String> powerValues, List<String> refundValues,
                     List<String> secondaryLabels) {
            primary = List.copyOf(primaryLabels);
            costs = List.copyOf(costValues);
            powers = List.copyOf(powerValues);
            refunds = List.copyOf(refundValues);
            secondary = List.copyOf(secondaryLabels);
        }

        private UiNode child(String id) {
            return find(id);
        }

        /** The slot widths: primary button, knowledge chip, power chip, debug chip, refund, quiet button. */
        private int[] slots(UiDraw metrics) {
            UiTheme theme = services().theme();
            int[] slots = new int[6];
            for (String label : primary) {
                slots[0] = Math.max(slots[0], Button.width(metrics, label, theme));
            }
            for (String value : costs) {
                slots[1] = Math.max(slots[1], StatChip.width(metrics, value, true));
            }
            for (String value : powers) {
                slots[2] = Math.max(slots[2], StatChip.width(metrics, value, true));
            }
            UiNode debug = child("stat:debug");
            slots[3] = debug.visible() ? debug.preferredWidth(metrics) : 0;
            for (String value : refunds) {
                slots[4] = Math.max(slots[4], InkValue.width(metrics, value));
            }
            for (String label : secondary) {
                slots[5] = Math.max(slots[5], Button.width(metrics, label, theme));
            }
            slots[5] = Math.max(slots[5], child("stat:permanent").preferredWidth(metrics));
            return slots;
        }

        private static int lead(int[] slots) {
            int width = 0;
            for (int index = 0; index < 4; index++) {
                width += slots[index] == 0 ? 0 : (width == 0 ? 0 : GAP) + slots[index];
            }
            return width;
        }

        private static int trail(int[] slots) {
            return (slots[4] == 0 ? 0 : slots[4] + GAP) + slots[5];
        }

        /** Where each slot goes in {@code width}: x and line for the six slots, then the number of lines. */
        private int[][] arrange(int[] slots, int width) {
            int[][] placed = new int[7][2];
            int x = 0;
            int line = 0;
            for (int index = 0; index < 4; index++) {
                if (slots[index] == 0) {
                    continue;
                }
                if (index == 1 && lead(slots) > width) {
                    x = 0;
                    line++;
                }
                placed[index] = new int[]{x, line};
                x += slots[index] + GAP;
            }
            int trail = trail(slots);
            int from = width - trail;
            if (x > 0 && x - GAP + APART > from) {
                line++;
                from = Math.max(0, from);
            }
            placed[4] = new int[]{from, line};
            placed[5] = new int[]{from + trail - slots[5], line};
            placed[6] = new int[]{line + 1, 0};
            return placed;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            int lines = arrange(slots(metrics), width)[6][0];
            return lines * AdaptScreen.BAR + (lines - 1) * GAP;
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            int[] slots = slots(metrics);
            int[][] placed = arrange(slots, box.width());
            String[][] holders = {{LEARN, CONFIRM}, {"stat:cost"}, {"stat:power-cost"}, {"stat:debug"}, {"stat:refund"},
                    {UNLEARN, CANCEL, "stat:permanent"}};
            for (int index = 0; index < holders.length; index++) {
                int width = Math.min(box.width(), slots[index]);
                int top = box.y() + placed[index][1] * (AdaptScreen.BAR + GAP);
                for (String id : holders[index]) {
                    UiNode node = child(id);
                    int height = Math.min(AdaptScreen.BAR, node.preferredHeight(width, metrics));
                    node.place(new UiRect(box.x() + placed[index][0], top + (AdaptScreen.BAR - height) / 2, width, height), metrics);
                }
            }
        }
    }

    /**
     * Stat lines one under another, wrapping when long, or in two columns (first half left, second half right) when there
     * are many and every line fits its column on one line.
     */
    private static final class StatColumns implements UiLayoutPolicy {
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
