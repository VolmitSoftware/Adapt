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
import art.arcane.adapt.client.ui.kit.Tag;
import art.arcane.adapt.client.ui.kit.TextBox;
import art.arcane.adapt.client.ui.kit.Tooltip;
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
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.RecipeView;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One adaptation. The left page is the adaptation and what the player can do with it, read top to bottom from one left
 * edge: icon, name and level with the level pips, the description, then at the foot a line that says what blocks an
 * action and the action row; it stays put while the right page turns. The right page says how it is set: the
 * adaptations it requires, the recipes it unlocks and its preferences, each under a heading, running on to further
 * leaves when they do not fit. Beside recipes on a spread the preferences keep to the foot of the right page. The shown level's stat lines, under a "Level" heading, sit on the left page just above the actions when there
 * are settings and the left page has room for them, and lead the right page otherwise.
 *
 * <p>The pips only show state. The actions are two rows in the same columns: a primary button that learns the next
 * level, with what it costs in chips beside it, and under it a quieter button that unlearns the top level, with what
 * it gives back in chips of the same kind marked as gains; a permanent adaptation says so in the unlearn row's place.
 * Neither changes meaning. Pointing at a pip, or moving keyboard focus to it, shows that level's stats and
 * what reaching it would cost or refund without touching the buttons. Clicking a pip offers to bring the adaptation
 * straight to that level: the row turns into one confirm button with the summed cost or refund and Cancel, until the
 * player confirms, presses Escape or clicks elsewhere. The same offer asks before a learn that cannot be undone and
 * before an unlearn that returns nothing; debug mode, where the server skips both, asks nothing. With no control
 * focused Enter learns the next level or confirms the open offer, and Delete unlearns the top level; Backspace goes
 * back as on every page.
 *
 * <p>Every slot of the row keeps the width of the widest thing it can hold, and the stat lines and the blocking line
 * keep the room their tallest level needs, so nothing moves when a level changes. Text wraps instead of being cut. An
 * adaptation the player's skill page does not show says so instead. Every change waits for the server: the buttons
 * and preferences are disabled and the pip in question blinks until the answer arrives.
 */
public final class AdaptationScreen extends AdaptScreen {
    static final String LEARN = "learn";
    static final String UNLEARN = "unlearn";
    static final String CONFIRM = "confirm";
    static final String CANCEL = "cancel";
    static final String PREFERENCE = "preference";
    /** The least lines a long description keeps before its box scrolls. */
    private static final int DESCRIPTION_LINES = 3;
    /** The least lines the stat lines keep before their box scrolls. */
    private static final int STAT_LINES = 4;
    /** The least lines of either on a page short of room. */
    private static final int TIGHT_LINES = 2;

    private final String adaptationId;
    private final Summary summary;
    private final TextBox description;
    private final Tag permanentTag;
    private final Tag debugTag;
    private final Label notice;
    private final ActionRow actionRow;
    private final LevelTrack track;
    private final StatChip costChip;
    private final StatChip powerChip;
    private final StatChip refund;
    private final StatChip powerRefund;
    private final Button learnButton;
    private final Button unlearnButton;
    private final Button confirmButton;
    private final Button cancelButton;
    private final SectionHeading levelHeading;
    private final StatsBox stats;
    private final SectionHeading requirementsHeading;
    private final List<Requirement> requirements = new ArrayList<>();
    private final SectionHeading settingsHeading;
    private final Reason reason;
    private final PreferencePanel preferences;
    private final ToolsSection tools = new ToolsSection();
    private final RecipesSection recipes = new RecipesSection();
    private final SectionHeading lentHeading = new SectionHeading("heading:lent", ScreenText.LENDS.text());
    private final UiGroup lentGroup = new UiGroup("lent", UiLayoutPolicy.flow(2));
    private final Map<String, KeptTile> lentTiles = new HashMap<>();
    private KeptView held;
    private KeptView keptBy;
    private int kept;
    private boolean settings;
    private Offer offer;
    private int peek;
    private int shown;
    private int pendingLevel;

    AdaptationScreen(ScreenRouter router, Route.Adaptation route) {
        super(title(router, route.adaptationId()), route, router);
        adaptationId = route.adaptationId();
        summary = new Summary();
        track = summary.add(new LevelTrack("levels", this::pick));
        description = new TextBox("description", "");
        notice = new Label("notice", "").setWrap(true);
        levelHeading = new SectionHeading("heading:level", "");
        stats = new StatsBox();
        requirementsHeading = new SectionHeading("heading:requirements", ScreenText.REQUIREMENTS.text());
        settingsHeading = new SectionHeading("heading:settings", ScreenText.SETTINGS.text());
        reason = new Reason();
        actionRow = new ActionRow();
        learnButton = actionRow.add(new Button(LEARN, "", Button.Style.PRIMARY, this::levelUp));
        confirmButton = actionRow.add(new Button(CONFIRM, "", Button.Style.PRIMARY, this::confirm));
        costChip = actionRow.add(new StatChip("stat:cost", SkillsScreen.KNOWLEDGE_ICON, "", ""));
        powerChip = actionRow.add(new StatChip("stat:power-cost", SkillsScreen.POWER_ICON, "", ""));
        debugTag = actionRow.add(new Tag("tag:debug", ScreenText.DEBUG_TAG.text(), Palette.LOCK));
        debugTag.setTooltip(new Tooltip(ScreenText.DEBUG.text(), List.of("§7" + ScreenText.DEBUG_TIP.text())));
        refund = actionRow.add(new StatChip("stat:refund", SkillsScreen.KNOWLEDGE_ICON, "", ""));
        refund.setValueColor(Palette.GOOD);
        powerRefund = actionRow.add(new StatChip("stat:power-refund", SkillsScreen.POWER_ICON, "", ""));
        powerRefund.setValueColor(Palette.GOOD);
        unlearnButton = actionRow.add(new Button(UNLEARN, "", Button.Style.NORMAL, this::unlearnTop));
        cancelButton = actionRow.add(new Button(CANCEL, ScreenText.CANCEL.text(), Button.Style.NORMAL, this::cancel));
        permanentTag = actionRow.add(new Tag("tag:permanent", ScreenText.PERMANENT.text(), Palette.LOCK));
        permanentTag.setTooltip(new Tooltip(ScreenText.PERMANENT.text(), List.of("§7" + ScreenText.PERMANENT_TIP.text())));
        preferences = new PreferencePanel("preferences", new PreferencePanel.Actions() {
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
        });
        refresh();
    }

    private static String title(ScreenRouter router, String adaptationId) {
        CatalogIndex catalog = router.index();
        AdaptationView adaptation = catalog == null ? null : catalog.shown(adaptationId);
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

    /**
     * The page wears the theme of the skill the adaptation belongs to, a mutation's blend for a mutated skill's, and for
     * an original held without learning the blend of the mutated skill that holds it.
     */
    @Override
    protected String themeSkill() {
        for (Route step : router().path(route())) {
            if (step instanceof Route.Skill skill) {
                return skill.skillId();
            }
        }
        return null;
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
        state.put("kept", Integer.toString(kept));
    }

    /**
     * Roomy, the stat lines whole above the actions only beside the whole description, and a single page's text whole;
     * then the stat lines whole above the actions with the description at its least; then the stat lines under the
     * description, both sharing the left page and scrolling what they cannot show; then tight preferences; then one flow
     * over both pages; then with the description and the stat lines giving up height down to two lines each.
     */
    @Override
    protected int densities() {
        return 6;
    }

    @Override
    protected void compose(Flow flow, UiDraw metrics, int width) {
        int density = flow.density();
        boolean shared = density >= 4;
        boolean placed = settings && density < 2 && fitsLeft(flow.height(), metrics, width, density > 0);
        boolean statsLeft = stats.any() && (placed || settings && density >= 2 || shared);
        boolean whole = !flow.spread() && density == 0;
        int least = whole ? Integer.MAX_VALUE : metrics.lineHeight() * (density >= 5 ? TIGHT_LINES : DESCRIPTION_LINES);
        flow.add(summary);
        flow.text(description, least);
        flow.add(notice);
        boolean anchored = placed && flow.spread();
        if (statsLeft && !anchored) {
            composeStats(flow, metrics, whole ? -1 : density >= 5 ? TIGHT_LINES : STAT_LINES);
        }
        if (anchored) {
            flow.foot(levelHeading);
            flow.foot(stats);
        }
        flow.foot(reason);
        flow.foot(actionRow);
        if (flow.spread() && !shared) {
            flow.nextPage();
        }
        if (!statsLeft && stats.any()) {
            composeStats(flow, metrics, whole ? -1 : density >= 5 ? TIGHT_LINES : STAT_LINES);
        }
        if (!requirements.isEmpty()) {
            flow.heading(requirementsHeading);
            for (int index = 0; index < requirements.size(); index++) {
                flow.add(requirements.get(index), index == 0 ? PAGE_GAP : 1);
            }
        }
        recipes.compose(flow, metrics, width);
        tools.compose(flow);
        boolean pinned = recipes.any() && flow.spread() && !shared;
        if (pinned && !lentGroup.children().isEmpty()) {
            flow.heading(lentHeading);
            flow.add(lentGroup);
        }
        if (settings) {
            settingsHeading.setAction(preferences.reset());
            Flow part = pinned ? new Flow(true, flow.height(), density) : flow;
            part.heading(settingsHeading);
            preferences.compose(part, metrics, width, density >= 3);
            if (pinned) {
                for (Block block : part.blocks()) {
                    flow.foot(block.node(), block.gap());
                }
            }
        }
        if (!pinned && !lentGroup.children().isEmpty()) {
            flow.heading(lentHeading);
            flow.add(lentGroup);
        }
    }

    /** The level heading and the stat lines, which give up height down to {@code lines} lines; all of it when negative. */
    private void composeStats(Flow flow, UiDraw metrics, int lines) {
        flow.heading(levelHeading);
        flow.text(stats, lines < 0 ? Integer.MAX_VALUE : metrics.lineHeight() * lines);
    }

    /**
     * Whether the stat lines fit whole on the left page above the actions with the whole description or, when
     * {@code shrink}, with the description at its least, leaving the right page to the settings; the description then
     * takes whatever height is left and scrolls the rest.
     */
    private boolean fitsLeft(int height, UiDraw metrics, int width, boolean shrink) {
        int text = description.preferredHeight(width, metrics);
        int used = summary.preferredHeight(width, metrics) + PAGE_GAP
                + (shrink ? Math.min(text, metrics.lineHeight() * DESCRIPTION_LINES) : text)
                + SECTION_GAP + levelHeading.preferredHeight(width, metrics) + PAGE_GAP + stats.preferredHeight(width, metrics);
        for (UiNode foot : List.of(reason, actionRow)) {
            if (foot.visible()) {
                used += PAGE_GAP + foot.preferredHeight(width, metrics);
            }
        }
        return used <= height;
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
        if (plain && (key == UiKey.LEFT || key == UiKey.RIGHT) && focused() == null && leaves() == 1 && track.shown() && shown > 0) {
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
        AdaptationView adaptation = catalog == null ? null : catalog.shown(adaptationId);
        boolean ready = adaptation != null && player != null;
        KeptView keptView = ready ? router().state().keptAdaptation(adaptationId) : null;
        keptBy = keptView != null && catalog.skill(keptView.ownerId()) != null ? keptView : null;
        kept = LearnRules.floor(keptBy);
        held = keptView != null && (kept > 0 || CatalogIndex.level(player, adaptationId) == 0) ? keptView : null;
        boolean open = ready && (held != null || player.access().shows(adaptation.skillId(), adaptationId));
        boolean lent = held != null && kept == 0;
        rebuildLent(catalog);
        settings = open && player.access().preferences() && !adaptation.preferences().isEmpty();
        List<RecipeView> unlocked = open ? router().state().recipes(adaptationId) : List.of();
        recipes.update(unlocked, 1);
        tools.update(catalog, player, open && unlocked.isEmpty() ? router().state().tools(adaptationId) : List.of());
        summary.setVisible(ready);
        description.setVisible(ready && !LegacyText.isBlank(adaptation.description()));
        track.setVisible(open && adaptation.maxLevel() > 0);
        notice.setVisible(!open);
        boolean levels = open && adaptation.maxLevel() > 0;
        actionRow.setVisible(levels && !lent);
        reason.setVisible(levels && !lent);
        if (!levels) {
            stats.clear();
            requirements.clear();
        }
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
        int level = lent ? held.level() : Math.max(kept, CatalogIndex.level(player, adaptationId));
        int max = adaptation.maxLevel();
        if (lent) {
            offer = null;
        }
        if (offer != null && (offer.target() > max || (offer.learn() ? offer.target() <= level : offer.target() >= level))) {
            offer = null;
        }
        int hovered = Math.min(peek, max);
        shown = hovered > 0 ? hovered : offer != null ? Math.max(1, offer.target())
                : lent ? Math.min(max, Math.max(1, level)) : Math.min(max, level + 1);
        updateSummary(player, adaptation, level);
        recipes.update(unlocked, shown);
        if (!levels) {
            return;
        }
        List<LevelTrack.LevelState> states = new ArrayList<>();
        for (int value = 1; value <= max; value++) {
            LevelTrack.Pip pip = value <= kept ? LevelTrack.Pip.KEPT : value <= level ? LevelTrack.Pip.LEARNED : LevelTrack.Pip.EMPTY;
            boolean offered = offer != null && (offer.learn() ? value > level && value <= offer.target()
                    : value <= level && value > offer.target());
            states.add(new LevelTrack.LevelState(pip, offered));
        }
        track.update(states, shown, pending() ? pendingLevel : 0);
        stats.show(adaptation.statLines(), shown);
        levelHeading.setText(ScreenText.LEVEL.text(ScreenText.roman(shown)));
        updateRequirements(catalog, player, adaptation);
        if (!lent) {
            updateReason(catalog, player, adaptation, level, hovered);
            updateActions(catalog, player, adaptation, level);
        }
        if (settings) {
            preferences.update(PreferenceOwner.ADAPTATION, adaptationId, adaptation.preferences(), adaptation.preferenceGroups(), player,
                    level, level < 1 ? ScreenText.LEARN_FIRST.text() : null);
            preferences.setEnabled(!pending());
        }
    }

    /** One line per adaptation this one requires, kept when only whether they are met changes. */
    private void updateRequirements(CatalogIndex catalog, PlayerView player, AdaptationView adaptation) {
        List<RequirementView> needed = adaptation.requirements();
        if (requirements.size() != needed.size()) {
            requirements.clear();
            for (int index = 0; index < needed.size(); index++) {
                requirements.add(new Requirement("requirement:" + index));
            }
        }
        for (int index = 0; index < needed.size(); index++) {
            RequirementView requirement = needed.get(index);
            requirements.get(index).set(catalog.adaptationName(requirement.adaptationId()) + " " + ScreenText.roman(requirement.level()),
                    CatalogIndex.level(player, requirement.adaptationId()) >= requirement.level());
        }
    }

    /** The originals this upgrade lends a level while their own code runs, when the server lists them. */
    private void rebuildLent(CatalogIndex catalog) {
        lentGroup.clearChildren();
        for (KeptView entry : router().state().kept(adaptationId)) {
            KeptTile tile = lentTiles.computeIfAbsent(entry.adaptationId(),
                    (String id) -> new KeptTile("lent:" + id, () -> router().open(new Route.Adaptation(id))));
            tile.update(catalog == null ? null : catalog.adaptation(entry.adaptationId()), entry);
            lentGroup.add(tile);
        }
    }

    /** How the page names the level an original works at without being learned: kept, or lent by an upgrade. */
    private String heldState(CatalogIndex catalog, int level) {
        if (kept > 0) {
            return level > kept ? ScreenText.RAISED_AT.text(ScreenText.roman(kept), ScreenText.roman(level))
                    : ScreenText.KEPT_AT.text(ScreenText.roman(kept));
        }
        return ScreenText.LENT_AT.text(ScreenText.roman(held.level()), catalog.adaptationName(held.ownerId()));
    }

    private void updateSummary(PlayerView player, AdaptationView adaptation, int level) {
        CatalogIndex catalog = router().index();
        String state = held != null && catalog != null ? heldState(catalog, level) : level == 0 ? ScreenText.NOT_LEARNED.text()
                : ScreenText.LEVEL_OF.text(ScreenText.roman(level), ScreenText.roman(adaptation.maxLevel()));
        summary.set(UiIcons.adaptation(adaptation), adaptation.name(), state);
        description.setText(LegacyText.strip(adaptation.description()));
        summary.setTooltip(new Tooltip(adaptation.name(), List.of("§8" + state)));
        long knowledge = LearnRules.knowledge(player, LearnRules.line(adaptation, keptBy));
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
    private Note reach(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int level, int value) {
        if (value < kept) {
            return Note.NONE;
        }
        if (value > level) {
            LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, value, keptBy);
            return check.allowed() ? new Note(LearnText.learnBlock(catalog, check), Tone.INFO) : blocked(catalog, check);
        }
        if (value < level) {
            LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, value, keptBy);
            return check.allowed() ? new Note(LearnText.unlearnOffer(check), Tone.INFO) : blocked(check);
        }
        return Note.NONE;
    }

    /** What the open offer still needs said: what blocks it, or the warning it asks the player to accept. */
    private Note offerNote(CatalogIndex catalog, PlayerView player, AdaptationView adaptation) {
        if (offer.learn()) {
            LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, offer.target(), keptBy);
            if (!check.allowed()) {
                return blocked(catalog, check);
            }
            return adaptation.permanent() && !check.free() ? new Note(ScreenText.PERMANENT_TIP.text(), Tone.LOCK) : Note.NONE;
        }
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, offer.target(), keptBy);
        if (!check.allowed()) {
            return blocked(check);
        }
        return check.refunded() || player.access().debug() ? Note.NONE : new Note(ScreenText.NO_REFUND.text(), Tone.LOCK);
    }

    private void updateReason(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int level, int hovered) {
        int max = adaptation.maxLevel();
        Note rest = level < max ? blocked(catalog, LearnRules.learn(catalog, player, adaptation, level + 1, keptBy)) : Note.NONE;
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
        List<String> freed = new ArrayList<>();
        for (int from = 0; from <= max; from++) {
            for (int to = 0; to <= max; to++) {
                if (to > from) {
                    primary.add(learnLabel(from, to));
                    costs.add(access.debug() ? ScreenText.FREE_COST.text() : ScreenText.number(LearnRules.knowledgeCost(adaptation, from, to)));
                    int power = LearnRules.powerCost(adaptation, from, to);
                    if (power > 0) {
                        powers.add(Integer.toString(power));
                    }
                } else if (to < from) {
                    primary.add(unlearnLabel(from, to));
                    if (refunds) {
                        costs.add(refundText(LearnRules.refund(adaptation, to, from)));
                    }
                    if (to == from - 1) {
                        secondary.add(unlearnLabel(from, to));
                        freed.add(refundText(LearnRules.powerCost(adaptation, to, from)));
                        if (refunds) {
                            refunded.add(refundText(LearnRules.refund(adaptation, to, from)));
                        }
                    }
                }
            }
        }
        actionRow.reserve(primary, costs, powers, refunded, freed, secondary);
    }

    private void updateActions(CatalogIndex catalog, PlayerView player, AdaptationView adaptation, int level) {
        PlayerAccess access = player.access();
        int max = adaptation.maxLevel();
        reserve(adaptation, access);
        boolean offering = offer != null;
        boolean learning = offering ? offer.learn() : level < max;
        boolean permanent = adaptation.permanent() && !access.debug();
        debugTag.setVisible(access.debug());
        learnButton.setVisible(!offering);
        confirmButton.setVisible(offering);
        cancelButton.setVisible(offering);
        cancelButton.setEnabled(!pending());
        permanentTag.setVisible(!offering && permanent);
        LearnRules.UnlearnCheck top = LearnRules.unlearn(catalog, player, adaptation, level - 1, keptBy);
        unlearnButton.setVisible(!offering && !permanent && level > kept);
        unlearnButton.setText(unlearnLabel(level, level - 1));
        unlearnButton.setEnabled(top.allowed() && !pending());
        String topNote = top.allowed() ? LearnText.refund(top) : LearnText.unlearnBlock(top);
        unlearnButton.setTooltip(topNote.isEmpty() ? null : Tooltip.of(topNote));
        refund.setVisible(unlearnButton.visible() && top.allowed() && top.refunded());
        refund.set(SkillsScreen.KNOWLEDGE_ICON, refundText(top.refund()), "");
        refund.setTooltip(Tooltip.of(LearnText.refund(top)));
        int freedPower = level > kept ? LearnRules.powerCost(adaptation, level - 1 - kept, level - kept) : 0;
        powerRefund.setVisible(unlearnButton.visible() && top.allowed() && freedPower > 0);
        powerRefund.set(SkillsScreen.POWER_ICON, refundText(freedPower), "");
        powerRefund.setTooltip(Tooltip.of(ScreenText.POWER_FREED.text(freedPower)));
        costChip.setVisible(learning || offering && LearnRules.unlearn(catalog, player, adaptation, offer.target(), keptBy).refunded());
        powerChip.setVisible(false);
        if (learning) {
            int target = offering ? offer.target() : level + 1;
            LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, target, keptBy);
            powerChip.setVisible(!check.free() && check.powerCost() > 0);
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
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, offer.target(), keptBy);
        costChip.set(SkillsScreen.KNOWLEDGE_ICON, refundText(check.refund()), "").setValueColor(Palette.GOOD);
        costChip.setTooltip(Tooltip.of(LearnText.refund(check)));
        confirmButton.setText(unlearnLabel(level, offer.target()));
        confirmButton.setStyle(Button.Style.DESTRUCTIVE);
        confirmButton.setEnabled(check.allowed() && !pending());
        confirmButton.setTooltip(null);
    }

    private AdaptationView adaptation() {
        CatalogIndex catalog = router().index();
        return catalog == null ? null : catalog.shown(adaptationId);
    }

    /** Offers to bring the adaptation to {@code level}, as clicking that pip does; the level already held closes an open offer. */
    void pick(int level) {
        PlayerView player = router().state().player();
        AdaptationView adaptation = adaptation();
        if (adaptation == null || player == null || pending()) {
            return;
        }
        if (held != null && kept == 0) {
            return;
        }
        int learned = Math.max(kept, CatalogIndex.level(player, adaptationId));
        offer = level == learned || level < Math.max(1, kept) || level > adaptation.maxLevel() ? null : new Offer(level, level > learned);
        refresh();
        requestLayout();
    }

    /** Learns the next level, asking first when that cannot be undone. */
    void levelUp() {
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        AdaptationView adaptation = adaptation();
        if (adaptation == null || player == null || pending() || held != null && kept == 0) {
            return;
        }
        int target = Math.max(kept, CatalogIndex.level(player, adaptationId)) + 1;
        LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation, target, keptBy);
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
        if (adaptation == null || player == null || pending() || held != null && kept == 0) {
            return;
        }
        int level = Math.max(kept, CatalogIndex.level(player, adaptationId));
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation, level - 1, keptBy);
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
            if (LearnRules.learn(catalog, player, adaptation, open.target(), keptBy).allowed()) {
                offer = null;
                learn(open.target());
            }
            return;
        }
        if (LearnRules.unlearn(catalog, player, adaptation, open.target(), keptBy).allowed()) {
            offer = null;
            unlearn(Math.max(kept, CatalogIndex.level(player, adaptationId)), open.target());
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
     * The adaptation's icon and name on one line, its level in words under the name, and the level pips at the right
     * end of the name's line, or of the line under it, or on a line of their own from the page's left edge, wherever
     * they first fit. The name's line is as tall as an icon, so it shares its words' line with the heading that opens
     * the facing page.
     */
    private static final class Summary extends UiGroup {
        private static final int GAP = 4;
        private static final int PIPS_GAP = 8;
        private static final int LINE_GAP = 1;

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

        /** Which line the pips take: 0 the name's, 1 the state's, 2 their own. */
        private int pipsLine(int width, UiDraw metrics) {
            int pips = pipsWidth(metrics);
            int text = UiIcons.SIZE + GAP;
            if (text + metrics.textWidth(name) + PIPS_GAP + pips <= width) {
                return 0;
            }
            return text + metrics.textWidth(state) + PIPS_GAP + pips <= width ? 1 : 2;
        }

        private int stateTop(UiDraw metrics) {
            return UiIcons.SIZE + LINE_GAP;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            int base = stateTop(metrics) + metrics.lineHeight();
            if (pipsWidth(metrics) == 0) {
                return base;
            }
            return switch (pipsLine(width, metrics)) {
                case 0 -> base;
                case 1 -> stateTop(metrics) + Math.max(metrics.lineHeight(), LevelTrack.NODE_HEIGHT);
                default -> base + 3 + track().preferredHeight(width, metrics);
            };
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            LevelTrack track = track();
            if (!track.visible()) {
                return;
            }
            int pips = pipsWidth(metrics);
            switch (pipsLine(box.width(), metrics)) {
                case 0 -> track.place(new UiRect(box.right() - pips, box.y() + (UiIcons.SIZE - LevelTrack.NODE_HEIGHT) / 2, pips,
                        LevelTrack.NODE_HEIGHT), metrics);
                case 1 -> track.place(new UiRect(box.right() - pips, box.y() + stateTop(metrics), pips, LevelTrack.NODE_HEIGHT), metrics);
                default -> {
                    int top = box.y() + stateTop(metrics) + metrics.lineHeight() + 3;
                    track.place(new UiRect(box.x(), top, box.width(), track.preferredHeight(box.width(), metrics)), metrics);
                }
            }
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            UiIcons.paintOn(draw, icon, box.x(), box.y(), UiIcons.SIZE, false, frame.theme().backdrop());
            int left = box.x() + UiIcons.SIZE + GAP;
            draw.text(name, left, box.y() + (UiIcons.SIZE - draw.lineHeight()) / 2 + 1, frame.theme().accent(), false);
            int stateY = box.y() + stateTop(draw) + (pipsLine(box.width(), draw) == 1 ? (LevelTrack.NODE_HEIGHT - draw.lineHeight()) / 2 + 1 : 0);
            draw.text(state, left, stateY, frame.theme().textMuted(), false);
        }
    }

    /**
     * The shown level's stat lines, as tall as the level with the most to say, so pointing at another level never moves
     * the page; a box too short for them scrolls them by itself. A line led by a sign or a note mark ("+ 4 Max Jumps",
     * "* Only while sprinting") keeps the mark in the margin and wraps its text beside it, so a wrapped line reads as one
     * entry; notes are muted.
     */
    private static final class StatsBox extends TextBox {
        private static final String MARKS = "+-*\u2013";
        private static final int INDENT = 9;

        private List<List<String>> levels = List.of();
        private int selected;

        private StatsBox() {
            super("detail.stats", "");
        }

        boolean any() {
            for (List<String> level : levels) {
                if (!level.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        void clear() {
            show(List.of(), 1);
        }

        void show(List<List<String>> lines, int level) {
            List<List<String>> plain = new ArrayList<>();
            for (List<String> each : lines) {
                List<String> stripped = new ArrayList<>();
                for (String line : each) {
                    stripped.add(LegacyText.strip(line).strip());
                }
                plain.add(stripped);
            }
            levels = plain;
            selected = Math.max(0, level - 1);
            List<String> shown = selected < levels.size() ? levels.get(selected) : List.of();
            setText(String.join("\n", shown));
        }

        private static String mark(String line) {
            return line.length() > 2 && line.charAt(1) == ' ' && MARKS.indexOf(line.charAt(0)) >= 0 ? line.substring(0, 1) : "";
        }

        private static String body(String line) {
            return mark(line).isEmpty() ? line : line.substring(2).strip();
        }

        private static int lineHeight(String line, int width, UiDraw metrics) {
            return metrics.wrappedHeight(body(line), Math.max(1, width - (mark(line).isEmpty() ? 0 : INDENT)));
        }

        @Override
        protected int textHeight(int width, UiDraw metrics) {
            int tallest = 0;
            for (List<String> level : levels) {
                int height = 0;
                for (String line : level) {
                    height += lineHeight(line, width, metrics);
                }
                tallest = Math.max(tallest, height);
            }
            return tallest;
        }

        @Override
        protected void paintText(UiFrame frame, int x, int y, int width) {
            if (selected >= levels.size()) {
                return;
            }
            UiDraw draw = frame.draw();
            int top = y;
            for (String line : levels.get(selected)) {
                String mark = mark(line);
                int indent = mark.isEmpty() ? 0 : INDENT;
                int color = mark.equals("*") ? Palette.MUTED : frame.theme().text();
                if (!mark.isEmpty()) {
                    draw.text(mark, x, top, color, false);
                }
                top += draw.wrappedText(body(line), x + indent, top, Math.max(1, width - indent), color, false);
            }
        }
    }

    /** An adaptation this one requires and its level: after a tick once the player has it, behind a padlock until then. */
    private static final class Requirement extends UiNode {
        private static final int LOCK_ROOM = 10;

        private String text = "";
        private boolean met;

        private Requirement(String id) {
            super(id);
        }

        void set(String newText, boolean isMet) {
            text = newText;
            met = isMet;
        }

        @Override
        public String kind() {
            return "label";
        }

        @Override
        public String label() {
            return text;
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("met", Boolean.toString(met));
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return metrics.wrappedHeight(text, Math.max(1, width - LOCK_ROOM));
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            if (met) {
                int ink = frame.theme().text();
                int[][] tick = {{0, 4}, {1, 5}, {2, 6}, {3, 5}, {4, 4}, {5, 3}, {6, 2}};
                for (int[] point : tick) {
                    draw.fill(box.x() + point[0], box.y() + point[1] - 1, box.x() + point[0] + 1, box.y() + point[1] + 1, ink);
                }
            } else {
                UiTome.lock(draw, box.x() + 1, box.y());
            }
            draw.wrappedText(text, box.x() + LOCK_ROOM, box.y(), Math.max(1, box.width() - LOCK_ROOM), met ? frame.theme().text() : Palette.LOCK,
                    false);
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

    /**
     * The actions as two rows in shared columns. The learn row: the primary button, then the knowledge and power it
     * costs, then the debug tag; an offer puts its confirm button there. The unlearn row: the secondary button, then
     * the knowledge and power it gives back; an offer puts Cancel there, a permanent adaptation its tag. Each column
     * is as wide as the widest thing {@link #reserve} says it can hold, so nothing moves when a level or an offer
     * changes. A page wide enough for both rows in its two halves puts them side by side.
     */
    private static final class ActionRow extends UiGroup {
        private static final int GAP = 3;

        private List<String> primary = List.of();
        private List<String> costs = List.of();
        private List<String> powers = List.of();
        private List<String> refunds = List.of();
        private List<String> freed = List.of();
        private List<String> secondary = List.of();

        private ActionRow() {
            super("detail.action");
        }

        void reserve(List<String> primaryLabels, List<String> costValues, List<String> powerValues, List<String> refundValues,
                     List<String> freedValues, List<String> secondaryLabels) {
            primary = List.copyOf(primaryLabels);
            costs = List.copyOf(costValues);
            powers = List.copyOf(powerValues);
            refunds = List.copyOf(refundValues);
            freed = List.copyOf(freedValues);
            secondary = List.copyOf(secondaryLabels);
        }

        private UiNode child(String id) {
            return find(id);
        }

        /** The column widths: button, knowledge chip, power chip, debug tag. */
        private int[] columns(UiDraw metrics) {
            UiTheme theme = services().theme();
            int[] columns = new int[4];
            for (String label : primary) {
                columns[0] = Math.max(columns[0], Button.width(metrics, label, theme));
            }
            for (String label : secondary) {
                columns[0] = Math.max(columns[0], Button.width(metrics, label, theme));
            }
            columns[0] = Math.max(columns[0], child("tag:permanent").preferredWidth(metrics));
            for (String value : costs) {
                columns[1] = Math.max(columns[1], StatChip.width(metrics, value, true));
            }
            for (String value : refunds) {
                columns[1] = Math.max(columns[1], StatChip.width(metrics, value, true));
            }
            for (String value : powers) {
                columns[2] = Math.max(columns[2], StatChip.width(metrics, value, true));
            }
            for (String value : freed) {
                columns[2] = Math.max(columns[2], StatChip.width(metrics, value, true));
            }
            UiNode debug = child("tag:debug");
            columns[3] = debug.visible() ? debug.preferredWidth(metrics) : 0;
            return columns;
        }

        /** Whether a page this wide holds both rows side by side, each in its own half. */
        private boolean sideBySide(int width, UiDraw metrics) {
            int row = 0;
            for (int column : columns(metrics)) {
                row += column == 0 ? 0 : column + GAP;
            }
            return row <= width / 2;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return sideBySide(width, metrics) ? AdaptScreen.BAR : AdaptScreen.BAR * 2 + GAP;
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            int[] columns = columns(metrics);
            int[] lefts = new int[4];
            int x = 0;
            for (int index = 0; index < columns.length; index++) {
                lefts[index] = x;
                x += columns[index] == 0 ? 0 : columns[index] + GAP;
            }
            boolean beside = sideBySide(box.width(), metrics);
            String[][] learn = {{LEARN, CONFIRM}, {"stat:cost"}, {"stat:power-cost"}, {"tag:debug"}};
            String[][] unlearn = {{UNLEARN, CANCEL, "tag:permanent"}, {"stat:refund"}, {"stat:power-refund"}, {}};
            for (int row = 0; row < 2; row++) {
                String[][] holders = row == 0 ? learn : unlearn;
                int top = box.y() + (beside ? 0 : row * (AdaptScreen.BAR + GAP));
                int start = beside && row == 1 ? box.width() / 2 : 0;
                for (int index = 0; index < holders.length; index++) {
                    int width = Math.min(Math.max(0, box.width() - start - lefts[index]), columns[index]);
                    for (String id : holders[index]) {
                        UiNode node = child(id);
                        int height = Math.min(AdaptScreen.BAR, node.preferredHeight(width, metrics));
                        node.place(new UiRect(box.x() + start + lefts[index], top + (AdaptScreen.BAR - height) / 2, width, height), metrics);
                    }
                }
            }
        }
    }
}
