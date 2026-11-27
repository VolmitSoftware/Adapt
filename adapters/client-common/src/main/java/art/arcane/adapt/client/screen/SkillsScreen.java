package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.Popover;
import art.arcane.adapt.client.ui.kit.StatChip;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.Toggle;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiOverlay;
import art.arcane.adapt.client.view.LearnRules;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.XpGuideView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The skills page: one seat for every skill on the server, in roster order, as cards laid out by {@link #LAYOUT}. A
 * mutation sits in its lead's seat and its partner's seat is left out; a skill the player has not started shows greyed
 * out. When inactive skills are hidden (by the server, or by the player where the server allows it) greyed seats are
 * left out too; the remaining cards always close up in the same order, as the cards a search matches do. The grid
 * never depends on how many skills the player holds. Knowledge and power sit as chips in the right page's header beside the search field that filters the cards; each chip opens what it
 * is spent on. A new search starts again from the first leaf.
 */
public final class SkillsScreen extends AdaptScreen {
    static final IconRef POWER_ICON = IconSprites.withSprite(IconRef.item("minecraft:iron_sword"), IconSprites.mark("power"));
    static final IconRef KNOWLEDGE_ICON = IconSprites.withSprite(IconRef.item("minecraft:knowledge_book"), IconSprites.mark("knowledge"));
    /** How the page lays out its seats. */
    static final SkillsLayout LAYOUT = SkillsLayout.SEATS;
    /** The widest values the header chips keep room for, so a larger total never moves the search field. */
    static final String WIDEST_KNOWLEDGE = "99,999";
    static final String WIDEST_POWER = "99/99";
    private static final int SEARCH_WIDTH = 84;
    private static final int BOOK_OPTIONS_WIDTH = 120;

    private final TextField search;
    private final Label empty;
    private final Label emptyHint;
    private final Button book;
    private final Map<String, SkillTile> tiles = new HashMap<>();
    private final List<UiNode> seated = new ArrayList<>();
    private final List<UiNode> matching = new ArrayList<>();
    private final SpendingPopover.Order order = new SpendingPopover.Order();

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
        book = addToHeader(Button.mark("action:book", ScreenText.BOOK.text(), IconSprites.mark("book"), this::openBookOptions));
        chip("stat:knowledge").setAction(this::openKnowledge);
        chip("stat:power").setAction(this::openPower);
        refresh();
    }

    /** The book's own options: themed pages, reduced motion and inactive skills, the last locked when the server hides them. */
    private void openBookOptions() {
        UiGroup options = new UiGroup("book.options", UiLayoutPolicy.column(2));
        options.add(new Toggle("option:themed-pages", ScreenText.THEMED_PAGES.text(), router().themes().enabled(), (Boolean on) -> {
            router().themes().setEnabled(on);
            requestLayout();
        }).setBoxFirst(true));
        options.add(new Toggle("option:reduced-motion", ScreenText.REDUCED_MOTION.text(), router().themes().reducedMotion(),
                router().themes()::setReducedMotion).setBoxFirst(true));
        boolean allowed = router().state().inactiveSkillsAllowed();
        Toggle inactive = options.add(new Toggle("option:inactive-skills", ScreenText.INACTIVE_SKILLS.text(),
                allowed && router().themes().inactiveSkills(), (Boolean on) -> {
            router().themes().setInactiveSkills(on);
            refresh();
            requestLayout();
        }).setBoxFirst(true));
        inactive.setEnabled(allowed);
        if (!allowed) {
            inactive.setTooltip(Tooltip.of(ScreenText.INACTIVE_HIDDEN.text()));
        }
        openOverlay(new Popover("book.popover", ScreenText.BOOK.text(), book.bounds(), BOOK_OPTIONS_WIDTH, options));
    }

    /** Whether greyed-out seats show: the server allows them and the player has not turned them off. */
    boolean showsInactive() {
        return router().state().inactiveSkillsAllowed() && router().themes().inactiveSkills();
    }

    private void openKnowledge() {
        closeOverlays(SpendingPopover.KNOWLEDGE_ID);
        openOverlay(SpendingPopover.knowledge(router(), seatedSkills(), order, chip("stat:knowledge").bounds(), this::openKnowledge));
    }

    private void openPower() {
        closeOverlays(SpendingPopover.POWER_ID);
        openOverlay(SpendingPopover.power(router(), seatedSkills(), chip("stat:power").bounds()));
    }

    /** Closes the open overlay {@code overlayId}, so reopening it rebuilds it in place. */
    private void closeOverlays(String overlayId) {
        for (UiOverlay overlay : List.copyOf(overlays())) {
            if (overlay.id().equals(overlayId)) {
                closeOverlay(overlay);
            }
        }
    }

    /** The skills of the cards in seat order. */
    private List<SkillView> seatedSkills() {
        List<SkillView> skills = new ArrayList<>();
        for (UiNode node : seated) {
            if (node instanceof SkillTile tile) {
                skills.add(tile.skill());
            }
        }
        return skills;
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
        state.put("inactive", Boolean.toString(showsInactive()));
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
        chip("stat:knowledge").setMinWidth(StatChip.width(metrics, WIDEST_KNOWLEDGE, true));
        chip("stat:power").setMinWidth(StatChip.width(metrics, WIDEST_POWER, true));
        flow.holdPitch();
        flow.add(empty);
        flow.add(emptyHint);
        SkillsLayout.Grid grid = LAYOUT.grid(flow.spread(), width, flow.height());
        List<UiGroup> rows = TileGrid.seats("skills", matching, grid);
        for (int index = 0; index < rows.size(); index++) {
            if (index > 0 && index % grid.rows() == 0) {
                flow.nextPage();
            }
            flow.row(rows.get(index), index % grid.rows() == 0 ? 0 : grid.gap());
        }
    }

    @Override
    protected void refresh() {
        updatePath();
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        seated.clear();
        matching.clear();
        if (catalog == null || player == null) {
            chip("stat:knowledge").setVisible(false);
            chip("stat:power").setVisible(false);
            showEmpty(ScreenText.WAITING.text(), "");
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
        boolean inactive = showsInactive();
        for (Seats.Seat seat : Seats.of(catalog, player, router().state())) {
            SkillTile tile = tiles.computeIfAbsent(seat.seatId(), (String id) -> {
                SkillTile created = new SkillTile(seat.skill(), LAYOUT, (String target) -> router().open(new Route.Skill(target)), this::openGuide);
                created.theme(tileTheming(), seat.shown().id());
                return created;
            });
            tile.theme(tileTheming(), seat.shown().id());
            SkillView shown = seat.shown();
            tile.update(shown, CatalogIndex.line(player, shown.id()), LearnRules.affordable(catalog, player, shown),
                    CatalogIndex.powerUsed(player, shown), player.access().canOpen(shown.id()), seat.started());
            tile.guide(router().state().xpGuide(shown.id()));
            if (tile.state() != SkillTile.State.ACTIVE && !inactive) {
                continue;
            }
            seated.add(tile);
        }
        String query = search.text().trim().toLowerCase(Locale.ROOT);
        for (UiNode node : seated) {
            if (query.isEmpty() || node instanceof SkillTile tile && matches(tile.skill(), query)) {
                matching.add(node);
            }
        }
        if (seatedSkills().isEmpty()) {
            showEmpty(ScreenText.NO_SKILLS.text(), ScreenText.NO_SKILLS_HINT.text());
        } else if (matching.stream().noneMatch(SkillTile.class::isInstance)) {
            showEmpty(ScreenText.NO_MATCH.text(search.text().trim()), "");
        } else {
            showEmpty("", "");
        }
    }

    /** Opens how {@code tile}'s skill earns XP under its (i). */
    private void openGuide(SkillTile tile) {
        XpGuideView guide = router().state().xpGuide(tile.skill().id());
        if (guide != null) {
            openOverlay(XpGuidePopover.of(tile.skill().name(), router().state().xpGuideTitle(), "", guide, router().index(),
                    tile.info().bounds()));
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
