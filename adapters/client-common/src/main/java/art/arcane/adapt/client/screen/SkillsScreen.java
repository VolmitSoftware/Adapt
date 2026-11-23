package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.StatChip;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The skills list: skill tiles in the chest menu's order, row by row down the left page and on down the right one, as
 * many columns to a page as its width holds up to {@link #COLUMNS}, or one column on each page when the whole list fits
 * one spread that way, with total knowledge and power used as chips in the right page's header beside the search field
 * that filters them. The columns follow the whole list, so a search never reshapes the tiles. A last spread with room to
 * spare shares its rows evenly between its two pages. A new search starts again from the first leaf.
 */
public final class SkillsScreen extends AdaptScreen {
    static final IconRef POWER_ICON = IconSprites.withSprite(IconRef.item("minecraft:iron_sword"), "adapt:textures/gui/mark/power.png");
    static final IconRef KNOWLEDGE_ICON = IconRef.item("minecraft:knowledge_book");
    /** The most columns of tiles on one page. */
    static final int COLUMNS = 2;
    /** The most columns when the book shows one page as wide as both. */
    static final int SINGLE_COLUMNS = 4;
    private static final int SEARCH_WIDTH = 84;

    private final TextField search;
    private final Label empty;
    private final Label emptyHint;
    private final Map<String, SkillTile> tiles = new HashMap<>();
    private final List<SkillTile> matching = new ArrayList<>();
    private int total;

    SkillsScreen(ScreenRouter router) {
        super(ScreenText.SKILLS.text(), new Route.Skills(), router);
        search = new TextField("search", 48);
        search.setPlaceholder(ScreenText.SEARCH.text());
        search.setOnChange((String text) -> {
            refresh();
            restart();
        });
        setTool(search, SEARCH_WIDTH);
        empty = new Label("empty", "").setWrap(true);
        emptyHint = new Label("empty.hint", "").setWrap(true).setColor(Palette.MUTED);
        refresh();
    }

    public TextField search() {
        return search;
    }

    SkillTile tile(String skillId) {
        return tiles.get(skillId);
    }

    @Override
    protected String probeKind() {
        return "adapt-skills";
    }

    @Override
    protected void describe(Map<String, String> state) {
        super.describe(state);
        state.put("query", search.text());
        state.put("shown", Integer.toString(matching.size()));
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        if (event.key() == UiKey.ESCAPE && search.isFocused() && !search.text().isEmpty()) {
            search.setText("");
            refresh();
            restart();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void compose(Flow flow, UiDraw metrics, int width) {
        flow.add(empty);
        flow.add(emptyHint);
        int need = RowTile.need(new ArrayList<>(tiles.values()), metrics);
        TileGrid.Fit fit = TileGrid.fit(width, need, flow.spread() ? COLUMNS : SINGLE_COLUMNS);
        int rows = Math.max(1, (flow.height() + TileGrid.ROW_GAP) / TileGrid.PITCH);
        if (flow.spread() && fit.columns() > 1 && (total + 1) / 2 <= rows) {
            fit = TileGrid.fit(width, need, 1);
        }
        for (UiGroup row : TileGrid.rows("skills", matching, fit)) {
            flow.row(row, TileGrid.ROW_GAP);
        }
        flow.balance();
    }

    @Override
    protected void refresh() {
        updatePath();
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        if (catalog == null || player == null) {
            chip("stat:knowledge").setVisible(false);
            chip("stat:power").setVisible(false);
            showEmpty(ScreenText.WAITING.text(), "");
            matching.clear();
            total = 0;
            return;
        }
        long knowledge = 0L;
        for (SkillLineView line : player.skills()) {
            knowledge += Math.max(0L, line.knowledge());
        }
        chip("stat:knowledge").set(KNOWLEDGE_ICON, ScreenText.number(knowledge), "")
                .setTooltip(new Tooltip(ScreenText.KNOWLEDGE_TITLE.text(), List.of("§7" + ScreenText.KNOWLEDGE_TOTAL_TIP.text())));
        chip("stat:knowledge").setVisible(true);
        powerChip(chip("stat:power"), player).setVisible(true);
        List<SkillView> visible = catalog.visibleSkills(player);
        total = visible.size();
        String query = search.text().trim().toLowerCase(Locale.ROOT);
        matching.clear();
        for (SkillView skill : visible) {
            if (!query.isEmpty() && !matches(skill, query)) {
                continue;
            }
            SkillTile tile = tiles.computeIfAbsent(skill.id(),
                    (String id) -> new SkillTile(skill, () -> router().open(new Route.Skill(id))));
            int affordable = LearnRules.affordable(catalog, player, skill);
            tile.update(skill, CatalogIndex.line(player, skill.id()), affordable, CatalogIndex.powerUsed(player, skill),
                    player.access().canOpen(skill.id()));
            matching.add(tile);
        }
        if (visible.isEmpty()) {
            showEmpty(ScreenText.NO_SKILLS.text(), ScreenText.NO_SKILLS_HINT.text());
        } else if (matching.isEmpty()) {
            showEmpty(ScreenText.NO_MATCH.text(search.text().trim()), "");
        } else {
            showEmpty("", "");
        }
    }

    /** The power chip: a sword and power used of the player's maximum. */
    static StatChip powerChip(StatChip chip, PlayerView player) {
        int max = player.header().powerMax();
        int free = LearnRules.freePower(player);
        int used = Math.max(0, max - free);
        chip.set(POWER_ICON, used + "/" + max, "");
        chip.setTooltip(new Tooltip(ScreenText.POWER_TITLE.text(), List.of("§7" + ScreenText.POWER_TIP.text(free, max))));
        return chip;
    }

    private static boolean matches(SkillView skill, String query) {
        return skill.name().toLowerCase(Locale.ROOT).contains(query)
                || skill.id().toLowerCase(Locale.ROOT).contains(query)
                || LegacyText.strip(skill.description()).toLowerCase(Locale.ROOT).contains(query);
    }

    private void showEmpty(String text, String hint) {
        empty.setText(text);
        empty.setVisible(!text.isEmpty());
        emptyHint.setText(hint);
        emptyHint.setVisible(!hint.isEmpty());
    }
}
