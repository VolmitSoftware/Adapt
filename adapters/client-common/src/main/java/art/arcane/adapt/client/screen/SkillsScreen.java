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
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
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
 * The skills list: skill tiles in the chest menu's order over two facing pages when there is room, at most
 * {@link #MAX_COLUMNS} columns in all, with total knowledge and power used as chips in the top bar, filtered by the
 * search field there. The grid is fitted to the whole skill list and keeps its height, so typing a search never
 * resizes the book; its rows spread out to fill the page the book's proportions give it.
 */
public final class SkillsScreen extends AdaptScreen {
    static final IconRef POWER_ICON = IconSprites.withSprite(IconRef.item("minecraft:iron_sword"), "adapt:textures/gui/mark/power.png");
    static final IconRef KNOWLEDGE_ICON = IconRef.item("minecraft:knowledge_book");
    static final int MAX_COLUMNS = 4;
    static final TileGrid.Shape SHAPE = new TileGrid.Shape(MAX_COLUMNS, MAX_COLUMNS, 112, true);
    private static final int SEARCH_WIDTH = 84;

    private final TextField search;
    private final Label empty;
    private final Label emptyHint;
    private final UiGroup grid;
    private final Map<String, SkillTile> tiles = new HashMap<>();
    private int shown;
    private int total;
    private int need;
    private TileGrid.Fit fit = TileGrid.Fit.NONE;

    SkillsScreen(ScreenRouter router) {
        super(ScreenText.SKILLS.text(), new Route.Skills(), router);
        search = new TextField("search", 48);
        search.setPlaceholder(ScreenText.SEARCH.text());
        search.setOnChange((String text) -> refresh());
        setTool(search, SEARCH_WIDTH);
        empty = page().add(new Label("empty", "").setAlign(Label.Align.CENTER));
        emptyHint = page().add(new Label("empty.hint", "").setAlign(Label.Align.CENTER).setColor(Palette.MUTED));
        grid = page().add(new UiGroup("skills", TileGrid.rows(() -> fit, () -> total)));
        UiLayoutPolicy notes = UiLayoutPolicy.column(PAGE_GAP);
        page().setLayout(new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                notes.layout(notes(children), area, metrics);
                grid.place(area, metrics);
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                return Math.max(grid.preferredHeight(width, metrics), notes.preferredHeight(notes(children), width, metrics));
            }
        });
        refresh();
    }

    public TextField search() {
        return search;
    }

    /** The messages that show over the grid's place while it has no tiles. */
    private List<UiNode> notes(List<UiNode> shown) {
        List<UiNode> notes = new ArrayList<>(shown);
        notes.remove(grid);
        return notes;
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
        state.put("shown", Integer.toString(shown));
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        if (event.key() == UiKey.ESCAPE && search.isFocused() && !search.text().isEmpty()) {
            search.setText("");
            refresh();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected Span pageSpan(UiDraw metrics) {
        need = RowTile.need(new ArrayList<>(tiles.values()), metrics);
        return new Span(TileGrid.narrowest(need), SHAPE.span(SHAPE.columns(), need));
    }

    private List<Integer> counts() {
        return List.of(Math.max(1, total));
    }

    @Override
    protected int arrange(UiDraw metrics, int width, int room, boolean split) {
        fit = TileGrid.fit(counts(), Math.max(width, floor()), room, need, split ? SHAPE : SHAPE.flat()).filling(floor());
        return fit.span();
    }

    @Override
    protected boolean split() {
        return fit.pages() > 1;
    }

    @Override
    protected int slack(int maxPitch) {
        return fit.slack(counts(), maxPitch);
    }

    @Override
    protected void stretch(int extra, int maxPitch) {
        fit = fit.stretched(counts(), fit.height(counts()) + extra, maxPitch);
    }

    @Override
    protected int gutter() {
        return TileGrid.spine(fit, grid.bounds());
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
            replaceTiles(List.of());
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
        List<SkillTile> matching = new ArrayList<>();
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
        replaceTiles(matching);
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

    private void replaceTiles(List<SkillTile> next) {
        shown = next.size();
        if (grid.children().equals(next)) {
            return;
        }
        grid.clearChildren();
        for (SkillTile tile : next) {
            grid.add(tile);
        }
    }

    private void showEmpty(String text, String hint) {
        empty.setText(text);
        empty.setVisible(!text.isEmpty());
        emptyHint.setText(hint);
        emptyHint.setVisible(!hint.isEmpty());
    }
}
