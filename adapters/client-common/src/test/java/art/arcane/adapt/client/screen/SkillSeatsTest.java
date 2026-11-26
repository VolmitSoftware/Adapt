package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.kit.InfoMark;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.StatChip;
import art.arcane.adapt.client.ui.kit.Toggle;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.client.view.XpGuideView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skills page's seats: every skill on the server in name order, a mutation in its lead's place and its partner left
 * out with the rest closed up in the same grid, skills not started washed out, and hiding inactive skills closing the
 * rest up the same way. The header never moves, and the
 * knowledge and power chips open where they are spent.
 */
class SkillSeatsTest {
    private static final int[][] SIZES = {{960, 540, 2}, {640, 360, 3}, {480, 270, 4}, {640, 352, 4}, {427, 240, 3}, {1920, 1080, 1}};
    private static final List<String> HEADER = List.of("search", "action:book", "stat:knowledge", "stat:power", "back");

    /** The typical roster's ids in name order, the order its seats keep. */
    private static List<String> roster() {
        List<SkillView> skills = new ArrayList<>(TypicalCatalog.catalog().skills());
        skills.sort((SkillView left, SkillView right) -> left.name().toLowerCase(Locale.ROOT).compareTo(right.name().toLowerCase(Locale.ROOT)));
        List<String> ids = new ArrayList<>();
        for (SkillView skill : skills) {
            ids.add(skill.id());
        }
        return ids;
    }

    @Test
    void mergedPartnersLeaveNoGapForAnyNumberOfMutationsWithEitherToggle() {
        Random random = new Random(42L);
        List<String> roster = roster();
        for (int[] size : SIZES) {
            ScreenFixture plain = fixture(size, List.of(), Set.of());
            List<UiRect> cells = new ArrayList<>(seats(plain).values());
            Map<String, UiRect> header = bounds(plain, HEADER);
            assertEquals(roster.size(), cells.size(), "every skill has a seat");
            for (int mutations = 0; mutations <= roster.size() / 2; mutations++) {
                for (boolean inactive : new boolean[] {true, false}) {
                    List<String[]> pairs = randomPairs(random, roster, mutations);
                    Set<String> unstarted = new HashSet<>();
                    for (String id : roster) {
                        if (random.nextInt(6) == 0) {
                            unstarted.add(id);
                        }
                    }
                    ScreenFixture mutated = fixture(size, pairs, unstarted);
                    mutated.router.themes().setInactiveSkills(inactive);
                    refresh(mutated);
                    String where = size[0] + "x" + size[1] + "@" + size[2] + " inactive " + inactive + " " + describe(pairs);
                    List<String> expected = new ArrayList<>();
                    for (String id : roster) {
                        boolean partner = pairs.stream().anyMatch((String[] pair) -> pair[1].equals(id));
                        boolean lead = pairs.stream().anyMatch((String[] pair) -> pair[0].equals(id));
                        if (!partner && (inactive || lead || !unstarted.contains(id))) {
                            expected.add("skill:" + id);
                        }
                    }
                    Map<String, UiRect> seats = seats(mutated);
                    assertEquals(expected, new ArrayList<>(seats.keySet()), where + ": order");
                    assertEquals(cells.subList(0, expected.size()), new ArrayList<>(seats.values()), where + ": a gap opened");
                    assertEquals(header, bounds(mutated, HEADER), where + ": the header moved");
                    for (String[] pair : pairs) {
                        assertNull(mutated.find("skill:" + pair[1]), where + ": a merged partner still has a card");
                        assertEquals("mutated-" + pair[0] + "-" + pair[1], ((SkillTile) mutated.find("skill:" + pair[0])).skill().id(), where);
                    }
                }
            }
        }
    }

    @Test
    void hidingInactiveSkillsClosesUpInRosterOrderWithTheSameGrid() {
        List<String> roster = roster();
        for (int[] size : SIZES) {
            ScreenFixture shown = fixture(size, List.of(), Set.of());
            List<UiRect> cells = new ArrayList<>();
            for (String id : roster) {
                cells.add(shown.find("skill:" + id).bounds());
            }
            List<String[]> pairs = List.of(new String[] {roster.get(0), roster.get(3)}, new String[] {roster.get(10), roster.get(2)});
            ScreenFixture hidden = fixture(size, pairs, Set.of(roster.get(5)));
            hidden.router.themes().setInactiveSkills(false);
            refresh(hidden);
            String where = size[0] + "x" + size[1] + "@" + size[2];
            assertEquals(bounds(shown, HEADER), bounds(hidden, HEADER), where + ": the header moved");
            List<String> expected = new ArrayList<>(roster);
            expected.remove(roster.get(3));
            expected.remove(roster.get(2));
            expected.remove(roster.get(5));
            List<String> packed = new ArrayList<>();
            for (int index = 0; index < expected.size(); index++) {
                UiNode tile = hidden.find("skill:" + expected.get(index));
                assertNotNull(tile, where + " " + expected.get(index));
                assertEquals(cells.get(index), tile.bounds(), where + ": " + expected.get(index) + " is not in seat " + index);
                packed.add(expected.get(index));
            }
            assertNull(hidden.find("skill:" + roster.get(3)), where + ": a merged seat shows while hidden");
            assertNull(hidden.find("skill:" + roster.get(5)), where + ": an unstarted skill shows while hidden");
            assertEquals(expected, packed);
        }
    }

    @Test
    void theServerOptionDecidesAndLocksTheToggle() {
        String unstarted = roster().get(5);
        ScreenFixture both = fixture(SIZES[0], List.of(), Set.of(unstarted));
        assertTrue(both.shows("skill:" + unstarted), "server shows and client shows");
        assertEquals(SkillTile.State.UNSTARTED, ((SkillTile) both.find("skill:" + unstarted)).state());

        ScreenFixture clientHides = fixture(SIZES[0], List.of(), Set.of(unstarted));
        clientHides.router.themes().setInactiveSkills(false);
        refresh(clientHides);
        assertNull(clientHides.find("skill:" + unstarted), "server shows, client hides");

        ScreenFixture serverHides = fixture(SIZES[0], List.of(), Set.of(unstarted));
        serverHides.state.acceptSpending(false, List.of());
        serverHides.render();
        assertNull(serverHides.find("skill:" + unstarted), "server hides");
        serverHides.click(serverHides.find("action:book"));
        Toggle toggle = (Toggle) serverHides.find("option:inactive-skills");
        assertFalse(toggle.enabled(), "the toggle is locked while the server hides");
        assertFalse(toggle.on());
        assertEquals(ScreenText.INACTIVE_HIDDEN.text(), toggle.tooltip().title());
    }

    @Test
    void anUnmutateFillsBothParentSeatsAgainInPlace() {
        String lead = roster().get(1);
        String partner = roster().get(4);
        ScreenFixture plain = fixture(SIZES[0], List.of(), Set.of());
        ScreenFixture fixture = fixture(SIZES[0], List.<String[]>of(new String[] {lead, partner}), Set.of());
        fixture.state.acceptCatalog(TypicalCatalog.catalog());
        fixture.state.acceptPlayer(plain.state.player());
        refresh(fixture);
        assertEquals(seats(plain), seats(fixture));
        assertEquals(lead, ((SkillTile) fixture.find("skill:" + lead)).skill().id());
        assertTrue(fixture.shows("skill:" + partner));
    }

    @Test
    void anUnstartedSkillIsWashedOutAndStillOpensItsPage() {
        String unstarted = roster().get(7);
        ScreenFixture fixture = fixture(SIZES[0], List.of(), Set.of(unstarted));
        SkillTile seat = (SkillTile) fixture.find("skill:" + unstarted);
        assertEquals(SkillTile.State.UNSTARTED, seat.state());
        assertTrue(seat.tooltip().lines().contains("§8" + ScreenText.NOT_STARTED.text()));
        fixture.click(seat);
        assertEquals(new Route.Skill(unstarted), fixture.current().route());
    }

    @Test
    void theHeaderChipsKeepTheirWidthWhateverTheTotals() {
        ScreenFixture small = fixture(SIZES[0], List.of(), Set.of());
        ScreenFixture large = fixture(SIZES[0], List.of(), Set.of());
        PlayerView player = large.state.player();
        List<SkillLineView> rich = new ArrayList<>();
        for (SkillLineView line : player.skills()) {
            rich.add(new SkillLineView(line.skillId(), line.xp(), line.level(), line.progress(), line.xpToNext(), 4_000L));
        }
        large.state.acceptPlayer(new PlayerView(player.header(), rich, player.adaptationLevels(), player.preferences(), player.access()));
        large.render();
        assertEquals(bounds(small, HEADER), bounds(large, HEADER));
        assertTrue(((StatChip) large.find("stat:knowledge")).value().length() > ((StatChip) small.find("stat:knowledge")).value().length());
    }

    @Test
    void theKnowledgeChipOpensWhereKnowledgeIsSpentInEitherOrder() {
        List<String> roster = roster();
        ScreenFixture fixture = fixture(SIZES[0], List.of(), Set.of());
        fixture.state.acceptSpending(true, List.of(
                new SpendView(roster.get(2), TypicalCatalog.adaptationId(index(roster.get(2)), 0), 2, 30, 1, SpendView.Source.LEARNED),
                new SpendView(roster.get(9), TypicalCatalog.adaptationId(index(roster.get(9)), 1), 3, 90, 1, SpendView.Source.LEARNED),
                new SpendView(roster.get(9), TypicalCatalog.adaptationId(index(roster.get(9)), 2), 1, 0, 0, SpendView.Source.KEPT)));
        fixture.render();
        StatChip chip = (StatChip) fixture.find("stat:knowledge");
        assertTrue(chip.focusable());
        fixture.click(chip);
        assertNotNull(fixture.find(SpendingPopover.KNOWLEDGE_ID));
        assertEquals(roster, ledgerOrder(fixture), "by seat");
        fixture.click(fixture.find("knowledge.sort"));
        List<String> bySpent = ledgerOrder(fixture);
        assertEquals(roster.get(9), bySpent.get(0));
        assertEquals(roster.get(2), bySpent.get(1));
        fixture.click(fixture.find("ledger.expand:" + roster.get(9)));
        assertNotNull(fixture.find("ledger.spend:" + TypicalCatalog.adaptationId(index(roster.get(9)), 2)));
        fixture.click(fixture.find("ledger.spend:" + TypicalCatalog.adaptationId(index(roster.get(9)), 1)));
        assertEquals(new Route.Adaptation(TypicalCatalog.adaptationId(index(roster.get(9)), 1)), fixture.current().route());
    }

    @Test
    void thePowerChipListsTheAdaptationsInSlots() {
        List<String> roster = roster();
        ScreenFixture fixture = fixture(SIZES[0], List.of(), Set.of());
        String slotted = TypicalCatalog.adaptationId(index(roster.get(3)), 4);
        fixture.state.acceptSpending(true, List.of(new SpendView(roster.get(3), slotted, 1, 12, 1, SpendView.Source.LEARNED),
                new SpendView(roster.get(3), TypicalCatalog.adaptationId(index(roster.get(3)), 5), 1, 0, 0, SpendView.Source.LENT)));
        fixture.render();
        fixture.click(fixture.find("stat:power"));
        assertNotNull(fixture.find(SpendingPopover.POWER_ID));
        assertNotNull(fixture.find("power.spend:" + slotted));
        assertNull(fixture.find("power.spend:" + TypicalCatalog.adaptationId(index(roster.get(3)), 5)), "a lent original takes no slot");
    }

    @Test
    void theInfoMarkOpensHowAMutatedSkillEarnsXp() {
        String lead = roster().get(1);
        String partner = roster().get(4);
        ScreenFixture fixture = fixture(SIZES[0], List.<String[]>of(new String[] {lead, partner}), Set.of());
        InfoMark mark = (InfoMark) fixture.find("info:" + lead);
        assertTrue(mark.focusable());
        fixture.click(mark);
        assertEquals(name(lead), ((Label) fixture.find("guide.title:0")).text());
        assertEquals(name(partner), ((Label) fixture.find("guide.title:1")).text());
        assertEquals("Larger award plus 25% of the smaller", ((Label) fixture.find("guide.line:2:0")).text());
        fixture.key(UiKey.ESCAPE);
        assertNull(fixture.find(XpGuidePopover.ID));
    }

    @Test
    void searchFiltersTheSeats() {
        String lead = roster().get(1);
        String partner = roster().get(4);
        ScreenFixture fixture = fixture(SIZES[0], List.<String[]>of(new String[] {lead, partner}), Set.of());
        SkillsScreen screen = (SkillsScreen) fixture.current();
        screen.focus(screen.search());
        for (char c : "mutation".toCharArray()) {
            screen.charTyped(new UiCharEvent(c, UiModifiers.NONE));
        }
        fixture.render();
        assertTrue(fixture.shows("skill:" + lead));
        assertNull(fixture.find("skill:" + partner), "a merged partner has no card to match");
    }

    @Test
    void seatedPagesFitEverySizeWithoutOverlap() {
        List<String> roster = roster();
        for (int[] size : new int[][] {{284, 160, 3}, {427, 240, 1}, {427, 240, 3}, {640, 360, 3}, {960, 540, 2}, {1920, 1080, 1},
                {1280, 720, 4}}) {
            ScreenFixture fixture = fixture(size, List.<String[]>of(new String[] {roster.get(0), roster.get(1)}), Set.of(roster.get(6)));
            ScreenLayoutTest.checkEveryLeaf(fixture, size[0] + "x" + size[1] + "@" + size[2]);
        }
    }

    private static void refresh(ScreenFixture fixture) {
        SkillsScreen screen = (SkillsScreen) fixture.current();
        screen.refresh();
        screen.requestLayout();
        fixture.render();
    }

    private static List<String> ledgerOrder(ScreenFixture fixture) {
        List<String> order = new ArrayList<>();
        collectLedgers(fixture.find("knowledge.list"), order);
        return order;
    }

    private static void collectLedgers(UiNode node, List<String> order) {
        if (node.id().startsWith("ledger.row:")) {
            order.add(node.id().substring("ledger.row:".length()));
        }
        for (UiNode child : node.children()) {
            collectLedgers(child, order);
        }
    }

    private static int index(String skillId) {
        List<SkillView> skills = TypicalCatalog.catalog().skills();
        for (int index = 0; index < skills.size(); index++) {
            if (skills.get(index).id().equals(skillId)) {
                return index;
            }
        }
        throw new IllegalArgumentException(skillId);
    }

    private static String name(String skillId) {
        return TypicalCatalog.catalog().skills().get(index(skillId)).name();
    }

    private static List<String[]> randomPairs(Random random, List<String> roster, int count) {
        List<String> free = new ArrayList<>(roster);
        List<String[]> pairs = new ArrayList<>();
        for (int pair = 0; pair < count && free.size() >= 2; pair++) {
            String lead = free.remove(random.nextInt(free.size()));
            String partner = free.remove(random.nextInt(free.size()));
            pairs.add(new String[] {lead, partner});
        }
        return pairs;
    }

    private static String describe(List<String[]> pairs) {
        StringBuilder text = new StringBuilder();
        for (String[] pair : pairs) {
            text.append(pair[0]).append('+').append(pair[1]).append(' ');
        }
        return text.toString().trim();
    }

    /** Every seat on every leaf by its id: leaf number and bounds. */
    private static Map<String, UiRect> seats(ScreenFixture fixture) {
        AdaptScreen screen = fixture.current();
        Map<String, UiRect> seats = new LinkedHashMap<>();
        for (int leaf = 0; leaf < screen.leaves(); leaf++) {
            screen.turnTo(leaf);
            fixture.render();
            collectSeats(screen.root(), seats, leaf);
        }
        screen.turnTo(0);
        fixture.render();
        return seats;
    }

    private static void collectSeats(UiNode node, Map<String, UiRect> seats, int leaf) {
        if (node instanceof SkillTile && node.shown()) {
            seats.put(node.id(), node.bounds().offset(leaf * 10_000, 0));
        }
        for (UiNode child : node.children()) {
            collectSeats(child, seats, leaf);
        }
    }

    private static Map<String, UiRect> bounds(ScreenFixture fixture, List<String> ids) {
        Map<String, UiRect> bounds = new HashMap<>();
        for (String id : ids) {
            bounds.put(id, fixture.find(id).bounds());
        }
        return bounds;
    }

    /**
     * The typical catalog with {@code pairs} merged, lead first, each mutation named "Mutation &lt;lead&gt;", and
     * {@code unstarted} skills with nothing earned or learned.
     */
    static ScreenFixture fixture(int[] size, List<String[]> pairs, Set<String> unstarted) {
        CatalogView base = TypicalCatalog.catalog();
        List<SkillView> skills = new ArrayList<>(base.skills());
        PlayerView typical = TypicalCatalog.player();
        Set<String> merged = new HashSet<>();
        List<SkillLineView> lines = new ArrayList<>();
        for (SkillLineView line : typical.skills()) {
            lines.add(unstarted.contains(line.skillId()) ? new SkillLineView(line.skillId(), 0.0D, 0, 0.0D, 100.0D, 0L) : line);
        }
        Map<String, Integer> levels = new HashMap<>();
        for (Map.Entry<String, Integer> level : typical.adaptationLevels().entrySet()) {
            String owner = level.getKey().substring(0, level.getKey().indexOf('-'));
            if (!unstarted.contains(owner)) {
                levels.put(level.getKey(), level.getValue());
            }
        }
        Map<String, List<List<String>>> rows = new HashMap<>(typical.access().adaptationRows());
        List<XpGuideView> guides = new ArrayList<>();
        for (String[] pair : pairs) {
            String id = "mutated-" + pair[0] + "-" + pair[1];
            skills.add(new SkillView(id, "Mutation " + pair[0], "Two skills in one.", IconRef.item("minecraft:bone"), 0x8040C0, true,
                    List.of(), List.of(), List.of()));
            merged.add(pair[1]);
            lines.add(new SkillLineView(id, 9000.0D, 94, 0.3D, 900.0D, 388L));
            rows.put(id, List.of());
            guides.add(new XpGuideView(id, List.of(pair[0], pair[1]), List.of(
                    new XpGuideView.Section(pair[0], name(pair[0]), List.of(name(pair[0]) + " source")),
                    new XpGuideView.Section(pair[1], name(pair[1]), List.of(name(pair[1]) + " source")),
                    new XpGuideView.Section("", "Mutation", List.of("Larger award plus 25% of the smaller")))));
        }
        for (SkillView skill : base.skills()) {
            guides.add(new XpGuideView(skill.id(), List.of(), List.of(new XpGuideView.Section(skill.id(), skill.name(),
                    List.of(skill.name() + " source")))));
        }
        List<String> listed = new ArrayList<>();
        for (String id : typical.access().skills()) {
            if (!merged.contains(id) && !unstarted.contains(id)) {
                listed.add(id);
            }
        }
        PlayerAccess access = new PlayerAccess(false, true, true, listed, rows);
        PlayerHeader header = typical.header();
        ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], new CatalogView(base.revision(), base.locale(), skills),
                new PlayerView(header, lines, levels, typical.preferences(), access), true);
        fixture.state.acceptXpGuides("Earning XP", guides);
        fixture.open(new Route.Skills());
        return fixture;
    }
}
