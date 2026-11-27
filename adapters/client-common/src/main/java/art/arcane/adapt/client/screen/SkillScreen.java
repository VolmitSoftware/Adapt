package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiScrollEvent;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.InfoMark;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.Tag;
import art.arcane.adapt.client.ui.kit.TextBox;
import art.arcane.adapt.client.ui.kit.Toggle;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.XpGuideView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * One skill. The left page is the skill: icon, name with its (i) and level over its experience bar, whose tooltip
 * tells the experience; then the adaptation under the pointer or keyboard focus, or until one is pointed at the first
 * the player can learn now: its name, level, what the next level costs or what blocks it, and its description; then a
 * mutation's kept originals as medallions with the mutation tools its adaptations hand out beside them
 * ({@link ToolShelf}); then, when the server lets players change settings, its preferences in a compact block. The
 * skill's description opens the (i)'s popover. The right page is only the adaptations, from its top, named in the right
 * page's header, as {@link AdaptationGrid} cards: a mutation's groups under their skills' names, a grouped skill's
 * under the names the server gives them. Fitting one page comes first: groups that only fit with their headings tight
 * get tight headings, and cards that only fit without headings show as one grid. Cards the page cannot hold turn on to
 * further right pages while the left page stays. On a single page the adaptations fill the second page under a heading
 * and their tooltips say what the left page would. Knowledge and free power sit in the right page's header. Accented
 * with the skill's colour. A skill the player may not open says why instead of showing its adaptations.
 */
public final class SkillScreen extends AdaptScreen {
    /** The id of the Mutate button, shown when the server offers a mutation led by this skill. */
    static final String MUTATE = "action:mutate";
    /** The id of the Unmutate hold control, shown on a mutation's page while the player is in debug mode. */
    static final String UNMUTATE = "action:unmutate";
    /** The columns of adaptation cards on a page, three so a parent's three upgrades make one row. */
    static final int COLUMNS = 3;
    /** The most columns on a single page, which is as wide as both. */
    static final int SINGLE_COLUMNS = 5;
    /** The least lines the pointed adaptation's description keeps before its box scrolls. */
    private static final int DESCRIPTION_LINES = 4;
    /** How an upgrade's id starts, followed by its parent skill's id. */
    private static final String UPGRADE_SEGMENT = "/upgrade/";
    /** Space between the medallions of kept originals. */
    private static final int KEPT_GAP = 2;

    private final String skillId;
    private final Identity identity;
    private final Label notice;
    private final Label hint;
    private final SectionHeading settingsHeading;
    private final SectionHeading adaptationsHeading;
    private final PreferencePanel preferences;
    private final Detail detail;
    private final TextBox detailText;
    private final Map<String, AdaptationTile> tiles = new HashMap<>();
    private final List<AdaptationGrid.Group> groups = new ArrayList<>();
    private final Map<String, GroupHeading> headingNodes = new HashMap<>();
    private final GridScroll.State gridState;
    private final MoreNote more = new MoreNote();
    private final ScrollRail rail = new ScrollRail();
    private GridScroll grid;
    private boolean railHeld;
    private final Toggle switchAll;
    private final MutateRow mutateRow;
    private final HoldToConfirm unmutateGesture = new HoldToConfirm();
    private final UnmutateRow unmutateRow;
    private List<String> parents = List.of();
    private final SectionHeading keptHeading = new SectionHeading("heading:kept", "");
    private final UiGroup keptGroup = new UiGroup("kept", UiLayoutPolicy.flow(KEPT_GAP));
    private final Map<String, KeptTile> keptTiles = new HashMap<>();
    private final ToolShelf tools;
    private String skillDescription = "";
    private UiNode pointed;
    private boolean settings;
    private boolean mutate;
    private boolean unmutate;

    SkillScreen(ScreenRouter router, Route.Skill route) {
        super(title(router, route.skillId()), route, router);
        skillId = route.skillId();
        identity = new Identity(new InfoMark("info:" + route.skillId(), ScreenText.EARNING_XP.text(), this::openGuide));
        notice = new Label("notice", "").setWrap(true);
        hint = new Label("hint", "").setWrap(true).setColor(Palette.MUTED);
        mutateRow = new MutateRow(new Button(MUTATE, ScreenText.MUTATE.text(), Button.Style.PRIMARY, () -> {
            if (router.state().has(ClientProtocol.MUTATE_SPREAD)) {
                router.open(new Route.Mutate(route.skillId()));
            } else {
                router.actions().mutate(route.skillId());
            }
        }));
        unmutateRow = new UnmutateRow(new SpreadSeal.Hold(UNMUTATE, unmutateGesture, () -> motion().now()));
        settingsHeading = new SectionHeading("heading:settings", ScreenText.SETTINGS.text());
        adaptationsHeading = new SectionHeading("heading:adaptations", ScreenText.ADAPTATIONS.text());
        detail = new Detail();
        setPageTitle(ScreenText.ADAPTATIONS.text());
        gridState = new GridScroll.State(router.scroll(route));
        setGutter(rail);
        switchAll = new Toggle("action:switch-all", "", true, (Boolean on) -> send("preference", ScreenText.SAVING.text(),
                () -> router.actions().setPreference(PreferenceOwner.SKILL, skillId, PreferencePanel.PRIMARY,
                        on ? PreferencePanel.ON : PreferencePanel.OFF))).setBoxFirst(true);
        setTitleMark(switchAll);
        detailText = new TextBox("detail.description", "");
        tools = new ToolShelf(keptHeading, keptGroup, (String id) -> router.open(new Route.Adaptation(id)));
        preferences = new PreferencePanel("preferences", new PreferencePanel.Actions() {
            @Override
            public void set(String preferenceId, String value) {
                send("preference", ScreenText.SAVING.text(),
                        () -> router.actions().setPreference(PreferenceOwner.SKILL, skillId, preferenceId, value));
            }

            @Override
            public void reset() {
                send("preference", ScreenText.SAVING.text(), () -> router.actions().resetPreference(PreferenceOwner.SKILL, skillId));
            }
        });
        refresh();
    }

    private static String title(ScreenRouter router, String skillId) {
        CatalogIndex catalog = router.index();
        SkillView skill = catalog == null ? null : catalog.skill(skillId);
        return skill == null ? skillId : skill.name();
    }

    AdaptationTile tile(String adaptationId) {
        return tiles.get(adaptationId);
    }

    @Override
    protected String themeSkill() {
        return skillId;
    }

    @Override
    protected String probeKind() {
        return "adapt-skill";
    }

    @Override
    protected void describe(Map<String, String> state) {
        super.describe(state);
        state.put("skill", skillId);
        state.put("groups", Integer.toString(groups.size()));
    }

    @Override
    protected void compose(Flow flow, UiDraw metrics, int width) {
        boolean spread = flow.spread();
        flow.pinFirstPage();
        flow.add(identity);
        flow.add(notice);
        flow.add(hint);
        if (mutate) {
            flow.add(mutateRow);
        }
        if (unmutate) {
            flow.add(unmutateRow);
        }
        detail.setVisible(spread && pointed != null);
        detailText.setVisible(spread && pointed != null && !LegacyText.isBlank(detailText.text()));
        if (spread) {
            flow.heading(detail);
            flow.text(detailText, metrics.lineHeight() * DESCRIPTION_LINES);
        }
        if (settings) {
            Flow part = new Flow(spread, flow.height(), flow.density());
            settingsHeading.setAction(preferences.reset());
            part.heading(settingsHeading);
            preferences.compose(part, metrics, width, true);
            for (Block block : part.blocks()) {
                flow.foot(block.node());
            }
        }
        tools.compose(flow);
        more.follow(null);
        rail.follow(null);
        grid = null;
        if (groups.isEmpty()) {
            return;
        }
        flow.nextPage();
        int first = flow.height();
        if (!spread) {
            flow.heading(adaptationsHeading);
            first -= adaptationsHeading.preferredHeight(width, metrics) + PAGE_GAP;
        }
        int columns = AdaptationGrid.columns(width, spread ? COLUMNS : SINGLE_COLUMNS);
        AdaptationGrid.Arrangement arrangement = AdaptationGrid.arrange(groups, width, columns, first, metrics);
        AdaptationGrid.compose(flow, arrangement, width, columns, PAGE_GAP, first, gridState, more, metrics);
        grid = more.scroll();
        rail.follow(grid);
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        boolean free = grid != null && grid.maxScroll() > 0 && grid.shown() && event.modifiers().equals(UiModifiers.NONE)
                && overlays().isEmpty() && !editingText();
        if (free && (event.key() == UiKey.PAGE_UP || event.key() == UiKey.PAGE_DOWN)) {
            grid.page(event.key() == UiKey.PAGE_DOWN ? 1 : -1);
            return true;
        }
        if (free && focused() == null && (event.key() == UiKey.UP || event.key() == UiKey.DOWN)) {
            grid.rows(event.key() == UiKey.DOWN ? 1 : -1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(UiMouseEvent event) {
        if (overlays().isEmpty() && rail.press(event)) {
            railHeld = true;
            return true;
        }
        return super.mouseClicked(event);
    }

    @Override
    public boolean mouseDragged(UiMouseEvent event, double dragX, double dragY) {
        return railHeld ? rail.drag(event) : super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(UiMouseEvent event) {
        if (railHeld) {
            railHeld = false;
            return rail.release();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(UiScrollEvent event) {
        if (grid != null && grid.maxScroll() > 0 && grid.shown() && overlays().isEmpty()) {
            return grid.mouseScrolled(event);
        }
        return super.mouseScrolled(event);
    }

    @Override
    public void removed() {
        router().rememberScroll(route(), gridState.rest());
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        advanceUnmutate();
        CatalogIndex catalog = router().index();
        if (!parents.isEmpty() && catalog != null && catalog.skill(skillId) == null) {
            router().open(new Route.Skill(parents.get(0)));
        }
    }

    /** Asks the server to unmutate once the hold has lasted, and drops a mouse hold the pointer left. */
    private void advanceUnmutate() {
        long now = motion().now();
        if (unmutateGesture.holding() && unmutateRow.hold().byMouse() && hovered() != unmutateRow.hold()) {
            unmutateGesture.release(now);
        }
        if (unmutateGesture.poll(now) && !send("unmutate", ScreenText.UNMUTATE.text(), () -> router().actions().unmutate(skillId))) {
            unmutateGesture.reset();
        }
    }

    @Override
    protected void answered(String action, FeedbackKind kind) {
        if (kind != FeedbackKind.SUCCESS) {
            unmutateGesture.reset();
        }
    }

    @Override
    public boolean keyReleased(UiKeyEvent event) {
        if ((event.key() == UiKey.ENTER || event.key() == UiKey.SPACE) && unmutateGesture.holding() && !unmutateRow.hold().byMouse()) {
            unmutateGesture.release(motion().now());
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public void render(UiDraw draw, int mouseX, int mouseY, float delta) {
        advanceUnmutate();
        UiNode under = tileOf(hit(mouseX, mouseY));
        if (under == null) {
            under = tileOf(focused());
        }
        if (under != null && under != pointed) {
            point(under);
        }
        super.render(draw, mouseX, mouseY, delta);
    }

    /** The adaptation card or kept medallion that holds {@code node}, or null. */
    private static UiNode tileOf(UiNode node) {
        for (UiNode current = node; current != null; current = current.parent()) {
            if (current instanceof AdaptationTile || current instanceof KeptTile) {
                return current;
            }
        }
        return null;
    }

    /** Shows an adaptation card or a kept medallion on the left page, or nothing for null. */
    private void point(UiNode tile) {
        pointed = tile;
        Shown shown = shown(tile);
        detail.show(shown);
        detailText.setText(shown == null ? "" : LegacyText.strip(shown.adaptation().description()));
        requestLayout();
    }

    /** What the left page says about a card or a kept medallion. */
    private Shown shown(UiNode tile) {
        if (tile instanceof AdaptationTile card) {
            int level = card.level();
            String words = level == 0 ? ScreenText.NOT_LEARNED.text()
                    : ScreenText.LEVEL_OF.text(ScreenText.roman(level), ScreenText.roman(card.adaptation().maxLevel()));
            return new Shown(card.adaptation(), words, card.statusText(), card::statusColor);
        }
        if (tile instanceof KeptTile kept && kept.adaptation() != null) {
            return new Shown(kept.adaptation(), kept.state(), kept.next(), (UiTheme theme) -> kept.affordable() ? Palette.GOOD : theme.textMuted());
        }
        return null;
    }

    /** The adaptation the left page shows before one is pointed at: the first the player can learn now, else the first not mastered. */
    private UiNode firstToLearn() {
        AdaptationTile fallback = null;
        for (AdaptationGrid.Group group : groups) {
            for (AdaptationTile tile : group.tiles()) {
                if (tile.status() == AdaptationTile.Status.AFFORDABLE) {
                    return tile;
                }
                if (fallback == null && tile.status() != AdaptationTile.Status.MASTERED) {
                    fallback = tile;
                }
            }
        }
        return fallback;
    }

    @Override
    protected void refresh() {
        updatePath();
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        SkillView skill = catalog == null ? null : catalog.skill(skillId);
        boolean ready = skill != null && player != null;
        boolean open = ready && player.access().canOpen(skillId);
        PreferenceView switchAll = null;
        List<PreferenceView> others = new ArrayList<>();
        for (PreferenceView preference : skill == null ? List.<PreferenceView>of() : skill.preferences()) {
            if (PreferencePanel.PRIMARY.equals(preference.id())) {
                switchAll = preference;
            } else {
                others.add(preference);
            }
        }
        settings = open && player.access().preferences() && !others.isEmpty();
        updateSwitch(open ? switchAll : null, player);
        mutate = open && router().state().has(ClientProtocol.MUTATE) && router().state().mutateOffers().contains(skillId);
        XpGuideView guide = router().state().xpGuide(skillId);
        if (guide != null && guide.mutated() && guide.parents().size() == 2) {
            parents = guide.parents();
        }
        unmutate = open && !parents.isEmpty() && player.access().debug() && router().state().has(ClientProtocol.UNMUTATE);
        identity.setVisible(ready);
        notice.setVisible(!open);
        hint.setVisible(ready && !open);
        chip("stat:knowledge").setVisible(ready);
        chip("stat:power").setVisible(ready);
        if (!open) {
            groups.clear();
            point(null);
        }
        if (!ready) {
            notice.setText(catalog == null || player == null ? ScreenText.WAITING.text() : ScreenText.UNAVAILABLE.text());
            notice.setColor(0);
            return;
        }
        int accent = Palette.accent(skill.color());
        setTheme(router().theme().withAccent(accent));
        SkillLineView line = CatalogIndex.line(player, skillId);
        int level = line == null ? 0 : Math.max(0, line.level());
        float progress = line == null ? 0.0F : UiEase.clamp((float) line.progress());
        long toNext = line == null ? 0L : Math.round(Math.max(0.0D, line.xpToNext()));
        long xp = line == null ? 0L : Math.round(Math.max(0.0D, line.xp()));
        identity.set(UiIcons.skill(skill), skill.name(), ScreenText.LEVEL.text(level), progress);
        identity.bar().setTooltip(new Tooltip(ScreenText.LEVEL.text(level), List.of(
                "§7" + ScreenText.XP_TO_LEVEL.text(ScreenText.number(toNext), level + 1),
                "§8" + ScreenText.XP_PROGRESS.text(ScreenText.number(xp), Math.round(progress * 100.0F)))));
        skillDescription = LegacyText.strip(skill.description());
        identity.info().setVisible(router().state().xpGuide(skillId) != null || !LegacyText.isBlank(skillDescription));
        notice.setText(skill.enabled() ? ScreenText.NO_ACCESS.text() : ScreenText.DISABLED.text());
        notice.setColor(Palette.LOCK);
        hint.setText(skill.enabled() ? ScreenText.NO_ACCESS_HINT.text() : "");
        hint.setVisible(!open && skill.enabled());
        long knowledge = LearnRules.knowledge(player, skillId);
        chip("stat:knowledge").set(SkillsScreen.KNOWLEDGE_ICON, ScreenText.number(knowledge), "")
                .setTooltip(new Tooltip(ScreenText.KNOWLEDGE_TITLE.text(), List.of("§7" + ScreenText.KNOWLEDGE_SKILL_TIP.text())));
        SkillsScreen.powerChip(chip("stat:power"), player);
        if (!open) {
            return;
        }
        if (unmutate) {
            unmutateRow.hold().set(ScreenText.UNMUTATE.text(), parentAccent(catalog, parents.get(0)), parentAccent(catalog, parents.get(1)));
            unmutateRow.hold().setEnabled(unmutateGesture.fired() || !pending());
        }
        rebuildKept(catalog, player);
        rebuildGroups(catalog, player);
        tools.update(router().state(), catalog, player, catalog.groups(player, skillId));
        if (settings) {
            preferences.update(PreferenceOwner.SKILL, skillId, others, List.of(), player, Integer.MAX_VALUE, null);
            preferences.setEnabled(!pending());
        }
    }

    /**
     * The checkbox after the Adaptations title that switches all of the skill's adaptations on or off, read-only where
     * the server keeps the setting; hidden while the skill has no such setting or cannot be opened.
     */
    private void updateSwitch(PreferenceView preference, PlayerView player) {
        switchAll.setVisible(preference != null);
        if (preference == null) {
            return;
        }
        boolean on = PreferencePanel.ON.equals(CatalogIndex.preference(player, PreferenceOwner.SKILL, skillId, preference));
        switchAll.setOn(on);
        boolean editable = player.access().preferences() && preference.playerEditable() && preference.choices().size() > 1;
        switchAll.setEnabled(editable && !pending());
        List<String> lines = new ArrayList<>(List.of("§7" + (on ? ScreenText.SWITCH_ON.text() : ScreenText.SWITCH_OFF.text())));
        if (!editable) {
            lines.add("§6" + ScreenText.SERVER_CONTROLLED.text());
        }
        switchAll.setTooltip(new Tooltip(ScreenText.SWITCH_ALL.text(), lines));
    }

    private static int parentAccent(CatalogIndex catalog, String parentId) {
        SkillView parent = catalog.skill(parentId);
        return parent == null ? Palette.MUTED : Palette.accent(parent.color());
    }

    /** Opens the skill's description and how it earns XP under the (i) beside its name. */
    private void openGuide() {
        XpGuideView guide = router().state().xpGuide(skillId);
        if (guide != null || !LegacyText.isBlank(skillDescription)) {
            openOverlay(XpGuidePopover.of(identity.label(), router().state().xpGuideTitle(), skillDescription, guide, router().index(),
                    identity.info().bounds()));
        }
    }

    /** The originals this skill keeps without learning, when it is a mutation and the server lists them. */
    private void rebuildKept(CatalogIndex catalog, PlayerView player) {
        keptGroup.clearChildren();
        List<KeptView> kept = router().state().kept(skillId);
        keptHeading.setText(router().state().keptTitle());
        for (KeptView entry : kept) {
            KeptTile tile = keptTiles.computeIfAbsent(entry.adaptationId(),
                    (String id) -> new KeptTile("kept:" + id, () -> router().open(new Route.Adaptation(id))));
            tile.update(catalog, player, entry);
            keptGroup.add(tile);
        }
    }

    private void rebuildGroups(CatalogIndex catalog, PlayerView player) {
        groups.clear();
        List<List<AdaptationView>> arrangement = catalog.groups(player, skillId);
        SkillView skill = catalog.skill(skillId);
        boolean mutated = !parents.isEmpty();
        for (List<AdaptationView> arranged : arrangement) {
            List<AdaptationTile> group = new ArrayList<>();
            for (AdaptationView adaptation : arranged) {
                AdaptationTile tile = tiles.computeIfAbsent(adaptation.id(),
                        (String id) -> {
                            AdaptationTile created = new AdaptationTile(adaptation, () -> router().open(new Route.Adaptation(id)),
                                    this::spread);
                            created.theme(tileTheming(), skillId);
                            return created;
                        });
                tile.update(catalog, player, adaptation);
                group.add(tile);
            }
            int index = groups.size();
            String label = mutated ? ownerName(catalog, arranged.get(0).id()) : label(skill, arranged.get(0).id());
            int color = mutated ? ownerAccent(catalog, arranged.get(0).id()) : Palette.accent(skill.color());
            GroupHeading heading = label.isEmpty() ? null
                    : headingNodes.computeIfAbsent("heading:group:" + index, GroupHeading::new).set(label, color);
            groups.add(new AdaptationGrid.Group(heading, group));
        }
        if (groups.isEmpty()) {
            notice.setText(ScreenText.NO_ADAPTATIONS.text());
            notice.setColor(Palette.MUTED);
            notice.setVisible(true);
        }
        boolean listed = pointed instanceof KeptTile && keptGroup.children().contains(pointed);
        for (AdaptationGrid.Group group : groups) {
            listed |= group.tiles().contains(pointed);
        }
        point(listed ? pointed : firstToLearn());
    }

    /** What the left page says about the pointed adaptation: its level in words and its status line in the status's colour. */
    private record Shown(AdaptationView adaptation, String words, String status, ToIntFunction<UiTheme> color) {
    }

    /** The name the server gives the declared group of {@code skill} that holds {@code adaptationId}, or empty. */
    private String label(SkillView skill, String adaptationId) {
        List<String> labels = router().state().groupLabels(skillId);
        List<List<String>> declared = skill.rowGroups();
        for (int index = 0; index < declared.size() && index < labels.size(); index++) {
            if (declared.get(index).contains(adaptationId)) {
                return labels.get(index);
            }
        }
        return "";
    }

    private String ownerName(CatalogIndex catalog, String adaptationId) {
        SkillView owner = catalog.skill(owner(adaptationId));
        return owner == null ? "" : owner.name();
    }

    private int ownerAccent(CatalogIndex catalog, String adaptationId) {
        SkillView owner = catalog.skill(owner(adaptationId));
        return owner == null ? Palette.MUTED : Palette.accent(owner.color());
    }

    /** The skill a mutation's adaptation comes from: the parent an upgrade's id names, else the mutated skill. */
    private String owner(String adaptationId) {
        for (String parent : parents) {
            if (adaptationId.startsWith(parent + UPGRADE_SEGMENT)) {
                return parent;
            }
        }
        return skillId;
    }

    /**
     * The pointed adaptation on the left page: its icon and name, its level in words under the name, and what the next
     * level costs or what blocks it, in the colour of its state. It keeps the height of the longest state line of any
     * adaptation or kept original on the page, so pointing at another one never moves the page.
     */
    private final class Detail extends UiNode {
        private static final int GAP = 4;
        private static final int LINE_GAP = 2;

        private Shown shown;

        private Detail() {
            super("detail");
        }

        void show(Shown next) {
            shown = next;
        }

        @Override
        public String kind() {
            return "detail";
        }

        @Override
        public String label() {
            return shown == null ? "" : LegacyText.strip(shown.adaptation().name());
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("status", shown == null ? "" : LegacyText.strip(shown.status()));
            state.put("words", shown == null ? "" : LegacyText.strip(shown.words()));
        }

        private int statusHeight(int width, UiDraw metrics) {
            int tallest = metrics.lineHeight();
            for (AdaptationGrid.Group group : groups) {
                for (AdaptationTile each : group.tiles()) {
                    tallest = Math.max(tallest, metrics.wrappedHeight(LegacyText.strip(each.statusText()), Math.max(1, width)));
                }
            }
            for (UiNode each : keptGroup.children()) {
                if (each instanceof KeptTile kept) {
                    tallest = Math.max(tallest, metrics.wrappedHeight(LegacyText.strip(kept.next()), Math.max(1, width)));
                }
            }
            return tallest;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return UiIcons.SIZE + LINE_GAP + metrics.lineHeight() + LINE_GAP + statusHeight(width, metrics);
        }

        @Override
        protected void paint(UiFrame frame) {
            if (shown == null) {
                return;
            }
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            AdaptationView adaptation = shown.adaptation();
            UiIcons.paintOn(draw, UiIcons.adaptation(adaptation), box.x(), box.y(), UiIcons.SIZE, false, theme.backdrop());
            draw.text(adaptation.name(), box.x() + UiIcons.SIZE + GAP, box.y() + (UiIcons.SIZE - draw.lineHeight()) / 2 + 1, theme.accent(),
                    false);
            int line = box.y() + UiIcons.SIZE + LINE_GAP;
            draw.text(shown.words(), box.x() + UiIcons.SIZE + GAP, line, theme.textMuted(), false);
            draw.wrappedText(LegacyText.strip(shown.status()), box.x(), line + draw.lineHeight() + LINE_GAP, Math.max(1, box.width()),
                    shown.color().applyAsInt(theme), false);
        }
    }

    /** The Mutate button at its own width on the left of the page, like the adaptation page's Learn button. */
    private static final class MutateRow extends UiGroup {
        private final Button button;

        private MutateRow(Button button) {
            super("mutate");
            this.button = add(button);
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return button.preferredHeight(width, metrics);
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            int height = button.preferredHeight(box.width(), metrics);
            button.place(new UiRect(box.x(), box.y(), Math.min(box.width(), button.preferredWidth(metrics)), height), metrics);
        }
    }

    /**
     * The Debug tag and, beside it, the control that undoes the mutation when held: its fill grows from both ends in the
     * two parents' colours.
     */
    private static final class UnmutateRow extends UiGroup {
        private static final int GAP = 4;
        private static final int HOLD_WIDTH = 96;

        private final Tag tag;
        private final SpreadSeal.Hold hold;

        private UnmutateRow(SpreadSeal.Hold hold) {
            super("unmutate");
            tag = add(new Tag("tag:debug", ScreenText.DEBUG_TAG.text(), Palette.LOCK));
            tag.setTooltip(new Tooltip(ScreenText.DEBUG.text(), List.of("§7" + ScreenText.DEBUG_TIP.text())));
            this.hold = add(hold);
        }

        SpreadSeal.Hold hold() {
            return hold;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return Math.max(tag.preferredHeight(width, metrics), services().theme().controlHeight());
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            int tagWidth = tag.preferredWidth(metrics);
            tag.place(new UiRect(box.x(), box.y(), tagWidth, box.height()), metrics);
            int holdX = box.x() + tagWidth + GAP;
            hold.place(new UiRect(holdX, box.y(), Math.max(0, Math.min(HOLD_WIDTH, box.right() - holdX)), box.height()), metrics);
        }
    }

    /**
     * The skill's icon and name in its colour, with an (i) after the name that opens its description and how it earns
     * XP, and its level against the right edge; the experience bar under them, whose tooltip and keyboard focus tell the
     * experience. A name too long to share its line with the level leaves the level to the bar's right end.
     */
    private static final class Identity extends UiNode {
        private static final int NAME_GAP = 4;
        private static final int BAR_TOP = 19;
        private static final int BAR_HEIGHT = 5;

        private final InfoMark info;
        private final ExperienceBar bar;
        private IconRef icon;
        private String name = "";
        private String level = "";

        private Identity(InfoMark mark) {
            super("identity");
            info = add(mark);
            info.setVisible(false);
            bar = add(new ExperienceBar());
        }

        InfoMark info() {
            return info;
        }

        ExperienceBar bar() {
            return bar;
        }

        void set(IconRef newIcon, String newName, String newLevel, float newProgress) {
            icon = newIcon;
            name = newName;
            level = newLevel;
            bar.progress = newProgress;
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
        public void describe(Map<String, String> state) {
            state.put("level", level);
        }

        private int nameEnd(UiDraw metrics) {
            int end = UiIcons.SIZE + NAME_GAP + metrics.textWidth(name);
            return info.visible() ? end + NAME_GAP + InfoMark.SIZE : end;
        }

        private boolean shared(int width, UiDraw metrics) {
            return nameEnd(metrics) + NAME_GAP * 2 + metrics.textWidth(level) <= width;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return BAR_TOP + BAR_HEIGHT + ExperienceBar.REACH;
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            int x = box.x() + UiIcons.SIZE + NAME_GAP + metrics.textWidth(name) + NAME_GAP;
            info.place(new UiRect(x, box.y() + (UiIcons.SIZE - InfoMark.SIZE) / 2, InfoMark.SIZE, InfoMark.SIZE), metrics);
            int barRight = shared(box.width(), metrics) ? box.right() : box.right() - metrics.textWidth(level) - NAME_GAP;
            bar.place(UiRect.of(box.x(), box.y() + BAR_TOP - ExperienceBar.REACH, barRight,
                    box.y() + BAR_TOP + BAR_HEIGHT + ExperienceBar.REACH), metrics);
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            int accent = frame.theme().accent();
            UiIcons.paintOn(draw, icon, box.x(), box.y(), UiIcons.SIZE, false, frame.theme().backdrop());
            int textY = box.y() + (UiIcons.SIZE - draw.lineHeight()) / 2 + 1;
            draw.text(name, box.x() + UiIcons.SIZE + NAME_GAP, textY, accent, false);
            int levelWidth = draw.textWidth(level);
            int levelY = shared(box.width(), draw) ? textY : box.y() + BAR_TOP + (BAR_HEIGHT - draw.lineHeight()) / 2 + 1;
            draw.text(level, box.right() - levelWidth, levelY, frame.theme().text(), false);
        }
    }

    /**
     * The skill's experience bar: its tooltip, under the pointer or keyboard focus, tells the experience to the next
     * level. It answers the pointer {@link #REACH} above and below the drawn bar.
     */
    private static final class ExperienceBar extends UiNode {
        private static final int REACH = 2;

        private float progress;

        private ExperienceBar() {
            super("experience");
        }

        @Override
        public String kind() {
            return "experience";
        }

        @Override
        public String label() {
            return tooltip() == null ? "" : tooltip().title();
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("progress", Float.toString(progress));
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
        protected void paint(UiFrame frame) {
            UiRect box = bounds();
            ProgressBar.framed(frame.draw(), frame.theme(), box.x(), box.y() + REACH, box.right(), box.height() - 2 * REACH, progress,
                    frame.theme().accent());
            if (frame.showsFocus(this)) {
                UiControl.focusRing(frame.draw(), box, frame.theme());
            }
        }
    }
}
