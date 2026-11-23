package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.TextBox;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One skill. The left page is only the skill: icon, name and level over its experience bar, its description and, when
 * the server lets players change settings, its preferences under a heading; under them the adaptation under the pointer
 * or keyboard focus, or until one is pointed at the first the player can learn now: its name, level, what the next
 * level costs or what blocks it, and its description. The right page is only the adaptations, from its top,
 * named in the right page's header, as a uniform grid of cells ({@link AdaptationGrid}) in the chest page's
 * groups. On a single page the adaptations fill the second page under a heading and their tooltips say what the left
 * page would. Knowledge and free power sit in the right
 * page's header. Accented with the skill's colour. A skill the player may not open says why instead of showing its
 * adaptations.
 */
public final class SkillScreen extends AdaptScreen {
    /** The columns of adaptation cells on a page at its roomiest. */
    static final int COLUMNS = 2;
    /** The columns on a single page, which is as wide as both. */
    static final int SINGLE_COLUMNS = 3;
    /** The least lines a long description keeps before its box scrolls. */
    private static final int DESCRIPTION_LINES = 3;

    private final String skillId;
    private final Identity identity;
    private final TextBox description;
    private final Label notice;
    private final Label hint;
    private final SectionHeading settingsHeading;
    private final SectionHeading adaptationsHeading;
    private final PreferencePanel preferences;
    private final Detail detail;
    private final TextBox detailText;
    private final Map<String, AdaptationTile> tiles = new HashMap<>();
    private final List<List<AdaptationTile>> groups = new ArrayList<>();
    private AdaptationTile pointed;
    private boolean settings;

    SkillScreen(ScreenRouter router, Route.Skill route) {
        super(title(router, route.skillId()), route, router);
        skillId = route.skillId();
        identity = new Identity();
        description = new TextBox("description", "");
        notice = new Label("notice", "").setWrap(true);
        hint = new Label("hint", "").setWrap(true).setColor(Palette.MUTED);
        settingsHeading = new SectionHeading("heading:settings", ScreenText.SETTINGS.text());
        adaptationsHeading = new SectionHeading("heading:adaptations", ScreenText.ADAPTATIONS.text());
        detail = new Detail();
        setPageTitle(ScreenText.ADAPTATIONS.text());
        detailText = new TextBox("detail.description", "");
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
        flow.text(description, metrics.lineHeight() * DESCRIPTION_LINES);
        flow.add(notice);
        flow.add(hint);
        if (settings) {
            settingsHeading.setAction(preferences.reset());
            flow.heading(settingsHeading);
            preferences.compose(flow, metrics, width, false);
        }
        detail.setVisible(spread && pointed != null);
        detailText.setVisible(spread && pointed != null && !LegacyText.isBlank(detailText.text()));
        if (spread) {
            flow.heading(detail);
            flow.text(detailText, metrics.lineHeight() * DESCRIPTION_LINES);
        }
        if (groups.isEmpty()) {
            return;
        }
        flow.nextPage();
        int room = flow.height();
        if (!spread) {
            flow.heading(adaptationsHeading);
            room -= adaptationsHeading.preferredHeight(width, metrics) + PAGE_GAP;
        }
        AdaptationGrid.Layout layout = AdaptationGrid.choose(groups, width, room, spread ? COLUMNS : SINGLE_COLUMNS, metrics);
        AdaptationGrid.compose(flow, layout, groups, PAGE_GAP, room);
    }

    @Override
    public void render(UiDraw draw, int mouseX, int mouseY, float delta) {
        AdaptationTile under = tileOf(hit(mouseX, mouseY));
        if (under == null) {
            under = tileOf(focused());
        }
        if (under != null && under != pointed) {
            point(under);
        }
        super.render(draw, mouseX, mouseY, delta);
    }

    private static AdaptationTile tileOf(UiNode node) {
        for (UiNode current = node; current != null; current = current.parent()) {
            if (current instanceof AdaptationTile tile) {
                return tile;
            }
        }
        return null;
    }

    /** Shows {@code tile} on the left page, or nothing for null. */
    private void point(AdaptationTile tile) {
        pointed = tile;
        detail.show(tile);
        detailText.setText(tile == null ? "" : LegacyText.strip(tile.adaptation().description()));
        requestLayout();
    }

    /** The adaptation the left page shows before one is pointed at: the first the player can learn now, else the first not mastered. */
    private AdaptationTile firstToLearn() {
        AdaptationTile fallback = null;
        for (List<AdaptationTile> group : groups) {
            for (AdaptationTile tile : group) {
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
        settings = open && player.access().preferences() && !skill.preferences().isEmpty();
        identity.setVisible(ready);
        description.setVisible(ready && !LegacyText.isBlank(skill.description()));
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
        identity.set(UiIcons.skill(skill), skill.name(), ScreenText.LEVEL.text(level), progress,
                ScreenText.XP_TO_LEVEL.text(ScreenText.number(toNext), level + 1));
        description.setText(LegacyText.strip(skill.description()));
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
        rebuildGroups(catalog, player);
        if (settings) {
            preferences.update(PreferenceOwner.SKILL, skillId, skill.preferences(), List.of(), player, Integer.MAX_VALUE, null);
            preferences.setEnabled(!pending());
        }
    }

    private void rebuildGroups(CatalogIndex catalog, PlayerView player) {
        groups.clear();
        for (List<AdaptationView> arranged : catalog.groups(player, skillId)) {
            List<AdaptationTile> group = new ArrayList<>();
            for (AdaptationView adaptation : arranged) {
                AdaptationTile tile = tiles.computeIfAbsent(adaptation.id(),
                        (String id) -> new AdaptationTile(adaptation, () -> router().open(new Route.Adaptation(id)), this::spread));
                tile.update(catalog, player, adaptation);
                group.add(tile);
            }
            groups.add(group);
        }
        if (groups.isEmpty()) {
            notice.setText(ScreenText.NO_ADAPTATIONS.text());
            notice.setColor(Palette.MUTED);
            notice.setVisible(true);
        }
        boolean listed = false;
        for (List<AdaptationTile> group : groups) {
            listed |= group.contains(pointed);
        }
        point(listed ? pointed : firstToLearn());
    }

    /**
     * The pointed adaptation on the left page: its icon and name, its level in words under the name, and what the next
     * level costs or what blocks it, in the colour of its state. It keeps the height of the longest state line of any
     * adaptation on the page, so pointing at another one never moves the page.
     */
    private final class Detail extends UiNode {
        private static final int GAP = 4;
        private static final int LINE_GAP = 2;

        private AdaptationTile tile;

        private Detail() {
            super("detail");
        }

        void show(AdaptationTile shown) {
            tile = shown;
        }

        @Override
        public String kind() {
            return "detail";
        }

        @Override
        public String label() {
            return tile == null ? "" : LegacyText.strip(tile.adaptation().name());
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("status", tile == null ? "" : LegacyText.strip(tile.statusText()));
        }

        private int statusHeight(int width, UiDraw metrics) {
            int tallest = metrics.lineHeight();
            for (List<AdaptationTile> group : groups) {
                for (AdaptationTile each : group) {
                    tallest = Math.max(tallest, metrics.wrappedHeight(LegacyText.strip(each.statusText()), Math.max(1, width)));
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
            if (tile == null) {
                return;
            }
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            AdaptationView adaptation = tile.adaptation();
            UiIcons.paintOn(draw, UiIcons.adaptation(adaptation), box.x(), box.y(), UiIcons.SIZE, false, theme.backdrop());
            draw.text(adaptation.name(), box.x() + UiIcons.SIZE + GAP, box.y() + (UiIcons.SIZE - draw.lineHeight()) / 2 + 1, theme.accent(),
                    false);
            int line = box.y() + UiIcons.SIZE + LINE_GAP;
            int level = tile.level();
            String words = level == 0 ? ScreenText.NOT_LEARNED.text()
                    : ScreenText.LEVEL_OF.text(ScreenText.roman(level), ScreenText.roman(adaptation.maxLevel()));
            draw.text(words, box.x() + UiIcons.SIZE + GAP, line, theme.textMuted(), false);
            draw.wrappedText(LegacyText.strip(tile.statusText()), box.x(), line + draw.lineHeight() + LINE_GAP, Math.max(1, box.width()),
                    tile.statusColor(theme), false);
        }
    }

    /**
     * The skill's icon and name in its colour with its level against the right edge, the experience bar under them and
     * the experience still to earn under the bar. A name too long to share its line with the level leaves the level to
     * the line under the bar.
     */
    private static final class Identity extends UiNode {
        private static final int NAME_GAP = 4;
        private static final int BAR_TOP = 19;
        private static final int BAR_HEIGHT = 5;
        private static final int TEXT_TOP = 27;

        private IconRef icon;
        private String name = "";
        private String level = "";
        private String experience = "";
        private float progress;

        private Identity() {
            super("identity");
        }

        void set(IconRef newIcon, String newName, String newLevel, float newProgress, String newExperience) {
            icon = newIcon;
            name = newName;
            level = newLevel;
            progress = newProgress;
            experience = newExperience;
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
            state.put("experience", experience);
        }

        private boolean shared(int width, UiDraw metrics) {
            return UiIcons.SIZE + NAME_GAP + metrics.textWidth(name) + NAME_GAP * 2 + metrics.textWidth(level) <= width;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return TEXT_TOP + metrics.lineHeight();
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            int accent = frame.theme().accent();
            int muted = frame.theme().textMuted();
            UiIcons.paintOn(draw, icon, box.x(), box.y(), UiIcons.SIZE, false, frame.theme().backdrop());
            int textY = box.y() + (UiIcons.SIZE - draw.lineHeight()) / 2 + 1;
            draw.text(name, box.x() + UiIcons.SIZE + NAME_GAP, textY, accent, false);
            ProgressBar.framed(draw, frame.theme(), box.x(), box.y() + BAR_TOP, box.right(), BAR_HEIGHT, progress, accent);
            int lineY = box.y() + TEXT_TOP;
            draw.text(experience, box.x(), lineY, muted, false);
            int levelWidth = draw.textWidth(level);
            draw.text(level, box.right() - levelWidth, shared(box.width(), draw) ? textY : lineY, frame.theme().text(), false);
        }
    }
}
