package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One skill: its adaptations as row tiles in the chest page's groups on the left page, and on the facing page a detail
 * strip naming the hovered adaptation (the skill's level, experience and description until one is hovered) with the
 * skill's own preferences under it when the server lets players change settings. Knowledge and free power sit in the top bar. Accented with the skill's
 * colour. A skill the player may not open says why instead of showing its adaptations.
 */
public final class SkillScreen extends AdaptScreen {
    static final int MAX_COLUMNS = 6;
    private static final int STRIP_WIDTH = 160;

    private final String skillId;
    private final Label notice;
    private final Label hint;
    private final UiGroup sections;
    private final DetailStrip strip;
    private final PreferencePanel preferences;
    private final Map<String, AdaptationTile> tiles = new HashMap<>();
    private final List<UiGroup> groups = new ArrayList<>();
    private TileGrid.Fit fit = TileGrid.Fit.NONE;

    SkillScreen(ScreenRouter router, Route.Skill route) {
        super(title(router, route.skillId()), route, router);
        skillId = route.skillId();
        notice = page().add(new Label("notice", "").setWrap(true));
        hint = page().add(new Label("hint", "").setWrap(true).setColor(Palette.MUTED));
        sections = page().add(new UiGroup("sections", UiLayoutPolicy.column(TileGrid.GROUP_GAP)));
        strip = facing().add(new DetailStrip("strip", this::shownAdaptations));
        preferences = facing().add(new PreferencePanel("preferences", new PreferencePanel.Actions() {
            @Override
            public void set(String preferenceId, String value) {
                send("preference", ScreenText.SAVING.text(),
                        () -> router.actions().setPreference(PreferenceOwner.SKILL, skillId, preferenceId, value));
            }

            @Override
            public void reset() {
                send("preference", ScreenText.SAVING.text(), () -> router.actions().resetPreference(PreferenceOwner.SKILL, skillId));
            }
        }));
        refresh();
    }

    private static String title(ScreenRouter router, String skillId) {
        CatalogIndex catalog = router.index();
        SkillView skill = catalog == null ? null : catalog.skill(skillId);
        return skill == null ? skillId : skill.name();
    }

    private List<RowTile> shownTiles() {
        List<RowTile> shown = new ArrayList<>();
        for (UiGroup group : groups) {
            for (UiNode node : group.children()) {
                if (node instanceof RowTile tile) {
                    shown.add(tile);
                }
            }
        }
        return shown;
    }

    private List<AdaptationTile> shownAdaptations() {
        List<AdaptationTile> shown = new ArrayList<>();
        for (RowTile tile : shownTiles()) {
            shown.add((AdaptationTile) tile);
        }
        return shown;
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
    protected int pageWidth(UiDraw metrics, int available, int room) {
        int rest = 0;
        for (UiNode node : List.of(notice, hint)) {
            if (node.visible()) {
                rest += node.preferredHeight(available, metrics) + PAGE_GAP;
            }
        }
        List<Integer> counts = new ArrayList<>();
        for (UiGroup group : groups) {
            counts.add(group.children().size());
        }
        fit = TileGrid.fit(counts, available, room - rest, MAX_COLUMNS, RowTile.need(shownTiles(), metrics));
        return groups.isEmpty() ? 0 : fit.span();
    }

    @Override
    protected int facingWidth(UiDraw metrics) {
        return Math.max(STRIP_WIDTH, preferences.visible() ? preferences.naturalWidth(metrics) : 0);
    }

    @Override
    protected void refresh() {
        updatePath();
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        SkillView skill = catalog == null ? null : catalog.skill(skillId);
        boolean ready = skill != null && player != null;
        boolean open = ready && player.access().canOpen(skillId);
        boolean settings = open && player.access().preferences() && !skill.preferences().isEmpty();
        notice.setVisible(!open);
        hint.setVisible(ready && !open);
        strip.setVisible(open);
        preferences.setVisible(settings);
        chip("stat:knowledge").setVisible(ready);
        chip("stat:power").setVisible(ready);
        if (!open) {
            sections.clearChildren();
            groups.clear();
        }
        if (!ready) {
            notice.setText(catalog == null || player == null ? ScreenText.WAITING.text() : ScreenText.UNAVAILABLE.text());
            notice.setColor(0);
            return;
        }
        int accent = Palette.accent(skill.color());
        setTheme(router().theme().withAccent(accent));
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
        SkillLineView line = CatalogIndex.line(player, skillId);
        int level = line == null ? 0 : Math.max(0, line.level());
        float progress = line == null ? 0.0F : UiEase.clamp((float) line.progress());
        strip.setFallback(new DetailStrip.Detail(skill.name(), accent, ScreenText.LEVEL_SHORT.text(level), skill.description(), progress));
        rebuildSections(catalog, player);
        if (settings) {
            preferences.update(PreferenceOwner.SKILL, skillId, skill.preferences(), List.of(), player, Integer.MAX_VALUE, null);
            preferences.setEnabled(!pending());
        }
    }

    private void rebuildSections(CatalogIndex catalog, PlayerView player) {
        List<List<AdaptationView>> arranged = catalog.groups(player, skillId);
        while (groups.size() > arranged.size()) {
            sections.remove(groups.remove(groups.size() - 1));
        }
        while (groups.size() < arranged.size()) {
            groups.add(sections.add(new UiGroup("group:" + groups.size(),
                    TileGrid.rows(() -> fit))));
        }
        for (int index = 0; index < arranged.size(); index++) {
            UiGroup group = groups.get(index);
            List<UiNode> next = new ArrayList<>();
            for (AdaptationView adaptation : arranged.get(index)) {
                AdaptationTile tile = tiles.computeIfAbsent(adaptation.id(),
                        (String id) -> new AdaptationTile(adaptation, () -> router().open(new Route.Adaptation(id))));
                tile.update(catalog, player, adaptation);
                next.add(tile);
            }
            if (!group.children().equals(next)) {
                group.clearChildren();
                for (UiNode tile : next) {
                    group.add(tile);
                }
            }
        }
        if (arranged.isEmpty()) {
            notice.setText(ScreenText.NO_ADAPTATIONS.text());
            notice.setColor(Palette.MUTED);
            notice.setVisible(true);
        }
    }
}
