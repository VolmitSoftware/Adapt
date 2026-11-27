package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiScrollEvent;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.Toggle;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.GroupLabelView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceChoiceView;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.client.view.XpGuideView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A skill page's adaptations as one strict grid: three columns on a spread that every row of every page shares, rows as
 * tall as their tallest card, names whole on up to three lines, groups under their names, no scrollbar; what the right
 * page cannot hold turns on to further right pages by whole groups or rows, under a page count with arrows in the
 * header, by wheel, keys and Tab, while the left page and both pages' places never change.
 */
class AdaptationGridTest {
    /** A spread at GUI scale 3 and 2 on common windows, and a small window at scale 4. */
    private static final int[][] SIZES = {{640, 360, 3}, {960, 540, 2}, {854, 480, 3}, {683, 384, 2}, {480, 270, 4}};
    /** The book at full size at the GUI scales players use. */
    private static final int[][] FULL_BOOK = {{640, 360, 3}, {960, 540, 2}, {854, 480, 3}, {1280, 720, 3}};
    private static final List<String> LONG_NAMES = List.of("Air Dash", "Armor-Up", "Super-Seeing Spelunker", "Cat Reflexes",
            "Featherfoot", "Kip-Up", "Ladder Slide", "Marathoner", "Roll Landing", "Slipstream Slide", "Super Jump", "Vault",
            "Wall Jump", "Chains of Mephistopheles", "Wind Up", "Momentum Bank", "Inflated Pocket Dimension", "Organic Oxygen Tank",
            "Tame Regeneration");
    private static final String LEAD = "chronos";
    private static final String PARTNER = "seaborne";
    private static final String MUTATED = "mutated-chronos-seaborne";
    private static final List<String> LEAD_UPGRADES = List.of("Second Chance", "Time Lord", "Frozen Moment");
    private static final List<String> PARTNER_UPGRADES = List.of("Leviathan", "Maelstrom", "Deep Fortune");
    private static final List<String> OWN = List.of("Tidal Graft", "Tidal Echo", "Tidal Barter", "Tidal Attunement", "Tidal Convergence");

    @Test
    void groupRowsFillTheColumnsFromTheLeft() {
        for (int size = 1; size <= 24; size++) {
            List<Integer> rows = AdaptationGrid.rows(size, 3);
            assertEquals(size, rows.stream().mapToInt(Integer::intValue).sum(), size + " cards");
            assertEquals((size + 2) / 3, rows.size(), size + " cards in as few rows as can be: " + rows);
            for (int index = 0; index + 1 < rows.size(); index++) {
                assertEquals(3, rows.get(index), size + " cards: only the last row is short " + rows);
            }
        }
        assertEquals(List.of(3, 1), AdaptationGrid.rows(4, 3));
        assertEquals(List.of(3, 2), AdaptationGrid.rows(5, 3));
        assertEquals(List.of(3, 3, 1), AdaptationGrid.rows(7, 3));
    }

    @Test
    void everySkillFromThreeToNineteenCardsIsOneEvenGrid() {
        for (int count = 3; count <= LONG_NAMES.size(); count++) {
            for (int[] size : SIZES) {
                ScreenFixture fixture = plain(size, "kinetics", List.of(LONG_NAMES.subList(0, count)), List.of());
                checkGrid(fixture, count + " at " + where(size), count);
            }
        }
    }

    @Test
    void everyRealSkillIsOneEvenGridWithItsGroupsNamedWhenItScrolls() {
        for (Map.Entry<String, RealSkills.Skill> entry : RealSkills.SKILLS.entrySet()) {
            RealSkills.Skill skill = entry.getValue();
            for (int[] size : SIZES) {
                ScreenFixture fixture = plain(size, entry.getKey(), skill.groups(), skill.labels());
                String where = entry.getKey() + " at " + where(size);
                checkGrid(fixture, where, skill.size());
                boolean scrolls = fixture.find("adaptations.scroll") != null;
                for (int group = 0; group < skill.groups().size(); group++) {
                    UiNode heading = fixture.find("heading:group:" + group);
                    if (!skill.labels().isEmpty() && scrolls) {
                        assertNotNull(heading, where + ": a skill that scrolls keeps its group " + group);
                    }
                    if (heading != null && heading.shown()) {
                        assertEquals(skill.labels().get(group), heading.label(), where);
                    }
                }
            }
        }
    }

    /** A themed book's right page is {@link AdaptScreen#THEME_FOOT} shorter than the fixture's plain one. */
    @Test
    void skillsOfUpToTwelveCardsFitAThemedPageWithoutScrolling() {
        for (Map.Entry<String, RealSkills.Skill> entry : RealSkills.SKILLS.entrySet()) {
            List<String> names = new ArrayList<>();
            entry.getValue().groups().forEach(names::addAll);
            for (int[] size : FULL_BOOK) {
                ScreenFixture fixture = plain(size, "kinetics", List.of(LONG_NAMES.subList(0, 3)), List.of());
                UiRect page = fixture.find("sheet:1").bounds();
                List<AdaptationTile> tiles = new ArrayList<>();
                for (String name : names.subList(0, Math.min(12, names.size()))) {
                    tiles.add(tile(entry.getKey(), name));
                }
                AdaptationGrid.Arrangement arrangement = AdaptationGrid.arrange(List.of(new AdaptationGrid.Group(null, tiles)), page.width(),
                        SkillScreen.COLUMNS, page.height() - AdaptScreen.THEME_FOOT, fixture.draw);
                assertFalse(arrangement.scrolls(), entry.getKey() + " at " + where(size));
            }
        }
    }

    @Test
    void spareHeightSpreadsTheRowsEvenlyUpToACap() {
        for (int[] size : FULL_BOOK) {
            ScreenFixture twelve = plain(size, "ranged", List.of(RealSkills.SKILLS.get("ranged").groups().get(0)), List.of());
            int pitch = pitch(twelve);
            UiRect page = twelve.find("sheet:1").bounds();
            int gap = pitch - AdaptationTile.Look.FULL.height();
            assertTrue(gap > AdaptationGrid.MIN_ROW_GAP && gap <= AdaptationGrid.MAX_ROW_GAP, where(size) + " twelve cards " + gap);
            assertEquals(page.y(), twelve.find("group:0:0").bounds().y(), where(size) + ": the grid starts at the page's top");
            ScreenFixture six = plain(size, "kinetics", List.of(LONG_NAMES.subList(0, 6)), List.of());
            assertEquals(AdaptationTile.Look.FULL.height() + AdaptationGrid.MAX_ROW_GAP, pitch(six), where(size) + " six cards take the cap");
        }
    }

    @Test
    void namesAreBalancedAndKeepTheirGutter() {
        KitDraw two = KitDraw.vanilla(960, 540, 2);
        KitDraw three = KitDraw.vanilla(640, 360, 3);
        int card = AdaptationGrid.cardWidth(212, SkillScreen.COLUMNS);
        int room = AdaptationTile.nameRoom(card);
        assertTrue(AdaptationGrid.COLUMN_GAP >= 6);
        assertEquals(List.of("Axe", "Drop-To-", "Inventory"), CardName.fit(two, "Axe Drop-To-Inventory", room, 3));
        assertEquals(List.of("Verdant", "Conver-", "gence"), CardName.fit(three, "Verdant Convergence", room, 3));
        assertNull(CardName.fit(three, "Verdant Convergence", room, 2), "a broken word keeps four letters on each side");
        assertEquals(List.of("Cartogra-", "pher Pulse"), CardName.fit(three, "Cartographer Pulse", room, 3), "a pair that opens a syllable stays whole");
        for (KitDraw draw : List.of(two, three)) {
            List<String> pieces = CardName.fit(draw, "Antidisestablishmentarianism", room, AdaptationTile.Look.TALL.lines());
            assertNotNull(pieces, "a word several cells wide breaks into pieces");
            assertTrue(pieces.size() > 1, pieces.toString());
            assertWholeWordsOrFourLetters("Antidisestablishmentarianism", pieces);
            for (String line : pieces) {
                assertTrue(draw.textWidth(line) - 1 <= room, line + " runs into the gutter");
            }
        }
        for (KitDraw draw : List.of(two, three)) {
            for (RealSkills.Skill skill : RealSkills.SKILLS.values()) {
                for (List<String> group : skill.groups()) {
                    for (String name : group) {
                        List<String> fit = CardName.fit(draw, name, room, 3);
                        assertNotNull(fit, name + " does not fit three lines");
                        for (String line : fit) {
                            assertTrue(draw.textWidth(line) - 1 <= room, name + " line " + line + " runs into the gutter");
                            assertTrue(line.length() >= CardName.SHORT || fit.size() == 1 || noBetterBreak(draw, name, room, fit),
                                    name + " leaves a short line " + fit);
                        }
                        assertWholeWordsOrFourLetters(name, fit);
                    }
                }
            }
        }
    }

    /** Each line of {@code fit} ends a word, or holds and leaves four letters of a word it breaks. */
    static void assertWholeWordsOrFourLetters(String name, List<String> fit) {
        for (int index = 0; index + 1 < fit.size(); index++) {
            String line = fit.get(index);
            if (!line.endsWith("-") || name.contains(line + fit.get(index + 1).split("[ -]")[0])) {
                continue;
            }
            String[] words = line.substring(0, line.length() - 1).split("[ -]");
            String head = words[words.length - 1];
            String tail = fit.get(index + 1).split("[ -]")[0];
            assertTrue(head.length() >= CardName.SHORT && tail.length() >= CardName.SHORT, name + " breaks a word too short " + fit);
        }
    }

    @Test
    void everyNameOfAPageIsDrawnAtTheFontsOwnSize() {
        RealSkills.Skill discovery = RealSkills.SKILLS.get("discovery");
        List<List<String>> verdant = List.of(List.of("Crimson Tide", "Plague Nova", "Deathless"), List.of("Wildgrowth", "Harvest Moon", "Iron Stomach"),
                List.of("Verdant Graft", "Verdant Echo", "Verdant Barter", "Verdant Attunement", "Verdant Convergence"));
        for (int[] size : SIZES) {
            for (ScreenFixture fixture : List.of(plain(size, "verdant", verdant, List.of("TragOul", "Herbalism", "Verdant Bloodbinder")),
                    plain(size, "discovery", discovery.groups(), discovery.labels()))) {
                String where = where(size) + " " + AdaptScreen.routeId(fixture.current().route());
                fixture.draw.calls().clear();
                fixture.render();
                Set<String> drawn = new LinkedHashSet<>(fixture.draw.calls("text "));
                for (UiNode node : cards(fixture.current().root())) {
                    AdaptationTile card = (AdaptationTile) node;
                    if (!node.visible() || fixture.find("adaptations.scroll") != null) {
                        continue;
                    }
                    UiRect box = card.bounds();
                    List<String> lines = card.nameFit(box.width(), card.look(), fixture.draw);
                    assertNotNull(lines, where + " " + card.id());
                    assertWholeWordsOrFourLetters(card.adaptation().name(), lines);
                    int top = box.y() + AdaptationTile.PAD + card.look().icon() + AdaptationTile.NAME_GAP;
                    for (int index = 0; index < lines.size(); index++) {
                        String call = "text " + (box.x() + AdaptationTile.PAD) + "," + (top + index * CardName.PITCH) + " " + lines.get(index);
                        assertTrue(drawn.contains(call), where + " " + card.id() + " is not drawn full size: " + call);
                    }
                }
            }
        }
    }

    /** Whether every way of breaking {@code name} in as few lines leaves a line as short. */
    private static boolean noBetterBreak(KitDraw draw, String name, int room, List<String> fit) {
        List<String> words = List.of(name.split(" "));
        for (int split = 1; split < words.size(); split++) {
            String first = String.join(" ", words.subList(0, split));
            String second = String.join(" ", words.subList(split, words.size()));
            if (fit.size() == 2 && first.length() >= CardName.SHORT && second.length() >= CardName.SHORT
                    && draw.textWidth(first) - 1 <= room && draw.textWidth(second) - 1 <= room) {
                return false;
            }
        }
        return true;
    }

    @Test
    void aGridTooTallScrollsByWholeRowsBesideItsRailAndAboveItsFootnote() {
        RealSkills.Skill golemancy = RealSkills.SKILLS.get("golemancy");
        for (int[] size : FULL_BOOK) {
            ScreenFixture fixture = plain(size, "golemancy", golemancy.groups(), golemancy.labels());
            AdaptScreen screen = fixture.current();
            String where = where(size);
            assertEquals(1, screen.leaves(), where);
            assertNull(fixture.find("leaf:count"), where + ": no page control");
            assertFalse(fixture.shows("leaf:next") || fixture.shows("leaf:previous"), where);
            GridScroll scroll = (GridScroll) fixture.find("adaptations.scroll");
            assertNotNull(scroll, where);
            ScrollRail rail = (ScrollRail) fixture.find("adaptations.rail");
            assertTrue(rail.active(), where);
            UiRect page = fixture.find("sheet:1").bounds();
            assertTrue(rail.bounds().x() >= page.right(), where + ": the rail stands in the margin " + rail.bounds() + " " + page);
            MoreNote note = (MoreNote) fixture.find("adaptations.more");
            assertTrue(note.showing(), where);
            assertEquals(ScreenText.SCROLL_MORE.text(), note.label());
            assertTrue(note.bounds().y() >= scroll.bounds().bottom(), where + ": the footnote sits under the window");
            assertTrue(note.bounds().bottom() <= page.bottom(), where);
            Set<Integer> tops = new LinkedHashSet<>();
            for (UiNode block : scroll.content().children()) {
                tops.add(block.bounds().y() - scroll.content().bounds().y());
            }
            for (int stop : scroll.stops()) {
                assertTrue(tops.contains(stop), where + ": the window rests mid-row at " + stop + " of " + tops);
            }
            UiRect window = scroll.bounds();
            assertTrue(screen.mouseScrolled(new UiScrollEvent(window.centerX(), window.centerY(), 0.0, -1.0)));
            fixture.clock.advance(GridScroll.GLIDE_MILLIS * 2);
            fixture.render();
            assertEquals(scroll.stops().get(1), scroll.scroll(), where + ": the wheel moves one row");
            fixture.key(UiKey.PAGE_DOWN);
            fixture.clock.advance(GridScroll.GLIDE_MILLIS * 2);
            fixture.render();
            for (int step = 0; step < 6; step++) {
                fixture.key(UiKey.DOWN);
                fixture.clock.advance(GridScroll.GLIDE_MILLIS * 2);
                fixture.render();
            }
            assertEquals(scroll.maxScroll(), scroll.scroll(), where + ": the keys reach the end");
            assertFalse(note.showing(), where + ": the footnote leaves at the end");
            int rest = scroll.scroll();
            UiNode card = cards(scroll).get(cards(scroll).size() - 1);
            for (int frame = 0; frame < 3; frame++) {
                screen.render(fixture.draw, card.bounds().centerX(), card.bounds().centerY(), 0.0F);
                fixture.render();
            }
            assertEquals(rest, ((GridScroll) fixture.find("adaptations.scroll")).scroll(), where + ": pointing at a card moves the grid");
            fixture.open(new Route.Skill("golemancy-other"));
            fixture.open(new Route.Skill("golemancy"));
            fixture.clock.advance(GridScroll.GLIDE_MILLIS * 2);
            fixture.render();
            assertEquals(rest, ((GridScroll) fixture.find("adaptations.scroll")).scroll(), where + ": the skill keeps where it rested");
        }
    }

    @Test
    void tabBringsEachCardWhollyIntoTheWindow() {
        RealSkills.Skill golemancy = RealSkills.SKILLS.get("golemancy");
        ScreenFixture fixture = plain(new int[] {640, 360, 3}, "golemancy", golemancy.groups(), golemancy.labels());
        AdaptScreen screen = fixture.current();
        GridScroll scroll = (GridScroll) fixture.find("adaptations.scroll");
        List<String> expected = new ArrayList<>();
        for (List<String> group : golemancy.groups()) {
            for (String name : group) {
                expected.add("adaptation:" + id("golemancy", name));
            }
        }
        screen.focus(fixture.find(expected.get(0)));
        List<String> visited = new ArrayList<>(List.of(screen.focused().id()));
        for (int step = 1; step < expected.size(); step++) {
            UiNode next;
            do {
                fixture.key(UiKey.TAB);
                next = screen.focused();
            } while (!(next instanceof AdaptationTile));
            visited.add(next.id());
            UiRect window = scroll.bounds();
            assertTrue(next.bounds().y() >= window.y() && next.bounds().bottom() <= window.bottom(), next.id() + " " + next.bounds() + " outside " + window);
        }
        assertEquals(expected, visited);
    }

    @Test
    void theColumnsStandWhereTheyDoWhetherTheGridScrollsOrNot() {
        for (int[] size : FULL_BOOK) {
            RealSkills.Skill golemancy = RealSkills.SKILLS.get("golemancy");
            ScreenFixture scrolling = plain(size, "golemancy", golemancy.groups(), golemancy.labels());
            ScreenFixture still = plain(size, "agility", RealSkills.SKILLS.get("agility").groups(), List.of());
            assertNull(still.find("adaptations.scroll"), where(size));
            assertFalse(((ScrollRail) still.find("adaptations.rail")).active(), where(size) + ": a bar on a page that fits");
            assertFalse(still.shows("adaptations.more") && ((MoreNote) still.find("adaptations.more")).showing(), where(size));
            assertEquals(columnXs(still), columnXs(scrolling), where(size));
        }
    }

    @Test
    void theSwitchForEveryAdaptationSitsAfterTheTitleAndSettingsAreGone() {
        for (int[] size : FULL_BOOK) {
            ScreenFixture fixture = plain(size, "agility", RealSkills.SKILLS.get("agility").groups(), List.of());
            Toggle toggle = (Toggle) fixture.find("action:switch-all");
            assertTrue(toggle.shown() && toggle.enabled() && toggle.focusable(), where(size));
            UiRect title = fixture.find("page-title").bounds();
            assertTrue(toggle.bounds().x() > title.x() && toggle.bounds().right() <= fixture.find("chips").bounds().x(), where(size));
            assertEquals(ScreenText.SWITCH_ALL.text(), toggle.tooltip().title());
            assertTrue(toggle.on());
            assertFalse(fixture.shows("heading:settings"), where(size) + ": a Settings section with nothing in it");
            fixture.click(toggle);
            assertTrue(fixture.sent.stream().anyMatch((ClientFrame frame) -> frame instanceof ClientFrame.SetPreference set
                    && set.ownerId().equals("agility") && set.value().equals("OFF")), fixture.sent.toString());
        }
    }

    @Test
    void everyClientLocaleNamesTheScrollNoteAndTheSwitch() throws Exception {
        for (String code : List.of("de_de", "es_es", "fi_fi", "fr_fr", "he_il", "it_it", "ja_jp", "ko_kr", "lt_lt", "nl_nl", "pl_pl", "pt_pt",
                "ru_ru", "tr_tr", "vi_vn", "zh_cn", "zh_tw")) {
            try (java.io.InputStream in = AdaptationGridTest.class.getResourceAsStream("/assets/adapt/lang/" + code + ".json")) {
                assertNotNull(in, code);
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (ScreenText key : List.of(ScreenText.SCROLL_MORE, ScreenText.SWITCH_ALL, ScreenText.SWITCH_ON, ScreenText.SWITCH_OFF)) {
                    assertTrue(text.contains("\"" + key.key() + "\""), code + " " + key);
                }
            }
        }
    }

    private static Set<Integer> columnXs(ScreenFixture fixture) {
        Set<Integer> xs = new TreeSet<>();
        UiRect page = fixture.find("sheet:1").bounds();
        for (UiNode card : cards(fixture.find("sheet:1"))) {
            xs.add(card.bounds().x() - page.x());
        }
        return xs;
    }

    private static int pitch(ScreenFixture fixture) {
        return fixture.find("group:0:1").bounds().y() - fixture.find("group:0:0").bounds().y();
    }

    @Test
    void aMutationKeepsItsThreeNamedGroupsInTheStrictGrid() {
        for (int[] size : SIZES) {
            ScreenFixture fixture = mutated(size, false, 1, false);
            String where = where(size);
            checkGrid(fixture, where, 11);
            assertNull(fixture.find("adaptations.scroll"), where + ": eleven cards fit one page");
            if (!fixture.shows("heading:group:0")) {
                fail(where + ": the groups lost their names");
                continue;
            }
            assertEquals("Chronos", fixture.find("heading:group:0").label(), where);
            assertEquals("Seaborne", fixture.find("heading:group:1").label(), where);
            assertEquals("Tidal Timekeeper", fixture.find("heading:group:2").label(), where);
            List<Integer> rows = new ArrayList<>();
            for (String group : List.of("group:0:0", "group:1:0", "group:2:0", "group:2:1")) {
                rows.add(fixture.find(group).children().size());
            }
            assertEquals(List.of(3, 3, 3, 2), rows, where);
            assertEquals(fixture.find("group:2:0").children().get(0).bounds().x(), fixture.find("group:2:1").children().get(0).bounds().x(),
                    where + ": the short row keeps the first column");
        }
    }

    @Test
    void pointingAtAnythingOnAMutationNeverMovesThePages() {
        for (int[] size : new int[][] {{640, 360, 3}, {960, 540, 2}, {480, 270, 4}}) {
            for (boolean debug : new boolean[] {false, true}) {
                for (int settings = 1; settings <= 8; settings++) {
                    for (boolean tools : new boolean[] {false, true}) {
                        ScreenFixture fixture = mutated(size, debug, settings, tools);
                        AdaptScreen screen = fixture.current();
                        String where = where(size) + " debug " + debug + " settings " + settings + " tools " + tools;
                        UiRect left = fixture.find("sheet:0").bounds();
                        UiRect pinned = fixture.find("kept").bounds();
                        List<UiNode> targets = new ArrayList<>();
                        List<UiNode> cards = cards(fixture.find("sheet:1"));
                        targets.add(cards.get(0));
                        targets.add(cards.get(cards.size() - 1));
                        collect(fixture.find("kept"), KeptTile.class, targets);
                        if (tools) {
                            assertTrue(fixture.shows("heading:tools"), where + ": no tools");
                        }
                        for (UiNode target : targets) {
                            for (int frame = 0; frame < 2; frame++) {
                                screen.render(fixture.draw, target.bounds().centerX(), target.bounds().centerY(), 0.0F);
                                fixture.render();
                            }
                            String at = where + " pointing at " + target.id();
                            assertEquals(0, screen.leaf(), at);
                            assertEquals(left, fixture.find("sheet:0").bounds(), at);
                            assertEquals(pinned, fixture.find("kept").bounds(), at + ": the kept originals moved");
                            assertEquals(left.bottom(), fixture.find("kept").parent().bounds().bottom(), at + ": the kept row left the page's foot");
                            assertTrue(fixture.shows("identity") && fixture.shows("detail"), at + ": the left page lost the skill");
                            assertTrue(cards(fixture.find("sheet:0")).isEmpty(), at + ": cards on the left page");
                            assertEquals(11, cards(fixture.find("sheet:1")).size(), at);
                            UiRect right = fixture.find("sheet:1").bounds();
                            for (UiNode card : cards(fixture.find("sheet:1"))) {
                                assertTrue(card.bounds().x() >= right.x() && card.bounds().right() <= right.right(), at + " " + card.id());
                            }
                        }
                    }
                }
            }
        }
    }

    private static void checkGrid(ScreenFixture fixture, String where, int count) {
        int columns = SkillScreen.COLUMNS;
        assertTrue(cards(fixture.find("sheet:0")).isEmpty(), where + ": a card on the left page");
        UiNode sheet = fixture.find("sheet:1");
        UiRect page = sheet.bounds();
        int width = AdaptationGrid.cardWidth(page.width(), columns);
        Set<Integer> xs = new LinkedHashSet<>();
        for (int column = 0; column < columns; column++) {
            xs.add(page.x() + AdaptationGrid.columnX(page.width(), columns, column));
        }
        assertEquals(page.x() + AdaptationGrid.columnX(page.width(), columns, 0) - page.x(),
                page.right() - (page.x() + AdaptationGrid.columnX(page.width(), columns, columns - 1) + width), where + ": unequal margins");
        List<UiNode> placed = cards(sheet);
        Set<String> seen = new LinkedHashSet<>();
        Set<Integer> heights = new TreeSet<>();
        for (UiNode node : placed) {
            AdaptationTile card = (AdaptationTile) node;
            UiRect box = card.bounds();
            String at = where + " " + card.id();
            assertTrue(xs.contains(box.x()), at + " at x " + box.x() + " off the columns " + xs);
            assertEquals(width, box.width(), at);
            heights.add(box.height());
            assertTrue(box.x() >= page.x() && box.right() <= page.right(), at + " " + box + " leaves " + page);
            assertTrue(seen.add(card.id()), at + " shows twice");
            List<String> fit = card.nameFit(box.width(), card.look(), fixture.draw);
            assertNotNull(fit, at + " has no name that fits");
            assertTrue(fit.size() <= card.look().lines(), at + " " + fit);
            assertEquals(letters(card.adaptation().name()), letters(String.join("", fit)), at + " is cut short: " + fit);
            for (UiNode other : placed) {
                assertTrue(other == node || !other.bounds().intersects(box), at + " overlaps " + other.id());
            }
        }
        assertEquals(1, heights.size(), where + ": cells of different heights " + heights);
        Map<Integer, Integer> perRow = new HashMap<>();
        for (UiNode node : placed) {
            perRow.merge(node.bounds().y(), 1, Integer::sum);
        }
        for (int cards : perRow.values()) {
            assertTrue(cards <= columns, where + " a row of " + cards);
        }
        assertEquals(count, seen.size(), where);
    }

    private static String letters(String text) {
        return text.replace(" ", "").replace("-", "");
    }

    private static List<UiNode> cards(UiNode root) {
        List<UiNode> found = new ArrayList<>();
        if (root != null) {
            collect(root, AdaptationTile.class, found);
        }
        return found;
    }

    private static void collect(UiNode node, Class<?> kind, List<UiNode> found) {
        if (node == null) {
            return;
        }
        if (kind.isInstance(node) && node.shown()) {
            found.add(node);
        }
        for (UiNode child : node.children()) {
            collect(child, kind, found);
        }
    }

    private static String where(int[] size) {
        return size[0] + "x" + size[1] + "@" + size[2];
    }

    static String id(String skillId, String name) {
        return skillId + "-" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private static AdaptationView adaptation(String skillId, String id, String name) {
        List<Integer> costs = List.of(4, 6, 8, 10, 12);
        return new AdaptationView(id, skillId, name, "What " + name + " does, at some length so the box has lines to show.",
                IconRef.item("minecraft:feather"), true, 5, false, costs, costs, List.of(1, 0, 0, 0, 0), List.of(),
                List.of(List.of(), List.of(), List.of(), List.of(), List.of()), List.of(), List.of());
    }

    private static AdaptationTile tile(String skillId, String name) {
        return new AdaptationTile(adaptation(skillId, id(skillId, name), name), () -> {
        }, () -> true);
    }

    private static SkillView skill(String skillId, String name, int color, List<List<String>> groups) {
        List<AdaptationView> adaptations = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (List<String> group : groups) {
            List<String> ids = new ArrayList<>();
            for (String adaptation : group) {
                String id = id(skillId, adaptation);
                adaptations.add(adaptation(skillId, id, adaptation));
                ids.add(id);
            }
            rows.add(ids);
        }
        return new SkillView(skillId, name, "A skill.", IconRef.item("minecraft:piston"), color, true, rows,
                List.of(new PreferenceView("enabled", "Skill adaptations enabled", "ON", List.of(
                        new PreferenceChoiceView("ON", "On", IconRef.item("minecraft:lime_dye"), 0),
                        new PreferenceChoiceView("OFF", "Off", IconRef.item("minecraft:gray_dye"), 0)), true)), adaptations);
    }

    /** A plain skill with {@code groups} of named adaptations, the server's {@code labels} for them, and a second skill. */
    static ScreenFixture plain(int[] size, String skillId, List<List<String>> groups, List<String> labels) {
        SkillView skill = skill(skillId, skillId, 0x40A040, groups);
        SkillView other = skill(skillId + "-other", "Other", 0x4040A0, List.of(List.of("Lone One", "Lone Two", "Lone Three")));
        Map<String, List<List<String>>> rows = Map.of(skillId, skill.rowGroups(), other.id(), other.rowGroups());
        PlayerView player = new PlayerView(new PlayerHeader(40, 0.5D, 3, 12, 0L, true),
                List.of(new SkillLineView(skillId, 9000.0D, 40, 0.3D, 9300.0D, 120L), new SkillLineView(other.id(), 10.0D, 1, 0.3D, 90.0D, 0L)),
                Map.of(skill.rowGroups().get(0).get(0), 2), List.of(),
                new PlayerAccess(false, true, true, List.of(skillId, other.id()), rows));
        ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], new CatalogView(1L, "en_us", List.of(skill, other)), player, true);
        if (!labels.isEmpty()) {
            fixture.state.acceptGroupLabels(List.of(new GroupLabelView(skillId, labels)));
        }
        fixture.open(new Route.Skill(skillId));
        fixture.render();
        return fixture;
    }

    /**
     * Chronos and Seaborne as Tidal Timekeeper, with kept originals whose descriptions are one line, cards whose
     * descriptions run long, {@code settings} settings, the tools its upgrades hand out when {@code tools} and, when
     * {@code debug}, the Unmutate row.
     */
    private static ScreenFixture mutated(int[] size, boolean debug, int settings, boolean tools) {
        List<String> leadOwn = List.of("Pocket Watch", "Deja Vu", "Rewind");
        List<String> partnerOwn = List.of("Hydro Jet", "Tidecaller", "Ink Veil");
        List<SkillView> skills = new ArrayList<>();
        skills.add(brief(skill(LEAD, "Chronos", 0x40A0E0, List.of(leadOwn))));
        skills.add(brief(skill(PARTNER, "Seaborne", 0x2050C0, List.of(partnerOwn))));
        List<AdaptationView> own = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (List<String> group : List.of(LEAD_UPGRADES, PARTNER_UPGRADES, OWN)) {
            List<String> ids = new ArrayList<>();
            for (String name : group) {
                String id = mutatedId(name);
                AdaptationView plain = adaptation(MUTATED, id, name);
                own.add(new AdaptationView(id, MUTATED, name, (plain.description() + " ").repeat(6).trim(), plain.icon(), true, 5, false,
                        plain.knowledgeCosts(), plain.refunds(), plain.powerCosts(), plain.requirements(), plain.statLines(), plain.preferences(),
                        plain.preferenceGroups()));
                ids.add(id);
            }
            rows.add(ids);
        }
        SkillView base = skill(MUTATED, "Tidal Timekeeper", 0x4080C0, List.of(List.of("Placeholder")));
        List<PreferenceView> preferences = new ArrayList<>();
        for (int index = 0; index < settings; index++) {
            preferences.add(new PreferenceView("setting" + index, "Setting number " + index, "ON", base.preferences().get(0).choices(), true));
        }
        skills.add(new SkillView(MUTATED, "Tidal Timekeeper", "Two skills in one.", base.icon(), base.color(), true, rows, preferences, own));
        Map<String, Integer> levels = new HashMap<>();
        levels.put(mutatedId("Time Lord"), 2);
        PlayerView player = new PlayerView(new PlayerHeader(40, 0.5D, 3, 12, 0L, true),
                List.of(new SkillLineView(MUTATED, 9000.0D, 26, 0.3D, 9300.0D, 54L)), levels, List.of(),
                new PlayerAccess(debug, true, true, List.of(MUTATED), Map.of(MUTATED, rows)));
        ScreenFixture fixture = new ScreenFixture(size[0], size[1], size[2], new CatalogView(1L, "en_us", skills), player, true);
        fixture.state.negotiated(ClientProtocol.VERSION, new ServerInfo("test", "26.3", ClientProtocol.ALL));
        fixture.state.acceptXpGuides("Earning XP", List.of(new XpGuideView(MUTATED, List.of(LEAD, PARTNER), List.of(
                new XpGuideView.Section(LEAD, "Chronos", List.of("Chronos source")),
                new XpGuideView.Section(PARTNER, "Seaborne", List.of("Seaborne source")),
                new XpGuideView.Section("", "Mutation", List.of("Larger award"))))));
        List<KeptView> kept = new ArrayList<>();
        for (String name : leadOwn) {
            kept.add(new KeptView(MUTATED, id(LEAD, name), 1));
        }
        for (String name : partnerOwn) {
            kept.add(new KeptView(MUTATED, id(PARTNER, name), 1));
        }
        fixture.state.acceptKept("Kept without learning", kept);
        if (tools) {
            List<ToolView> handed = new ArrayList<>();
            for (String name : List.of("Second Chance", "Leviathan", "Tidal Graft")) {
                handed.add(new ToolView(mutatedId(name), "tool-" + mutatedId(name), name + " Tool", IconRef.item("minecraft:clock"), 1,
                        List.of(new ToolView.Ingredient("minecraft:string", 1))));
            }
            fixture.state.acceptTools(handed);
        }
        fixture.open(new Route.Skill(MUTATED));
        fixture.render();
        return fixture;
    }

    /** {@code skill} with one-line descriptions. */
    private static SkillView brief(SkillView skill) {
        List<AdaptationView> adaptations = new ArrayList<>();
        for (AdaptationView view : skill.adaptations()) {
            adaptations.add(new AdaptationView(view.id(), view.skillId(), view.name(), "Short.", view.icon(), true, view.maxLevel(), false,
                    view.knowledgeCosts(), view.refunds(), view.powerCosts(), view.requirements(), view.statLines(), view.preferences(),
                    view.preferenceGroups()));
        }
        return new SkillView(skill.id(), skill.name(), skill.description(), skill.icon(), skill.color(), skill.enabled(), skill.rowGroups(),
                skill.preferences(), adaptations);
    }

    /** The id the server gives: a parent's upgrade names its parent, the mutation's own adaptations the mutation. */
    private static String mutatedId(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replace(' ', '-');
        return LEAD_UPGRADES.contains(name) ? LEAD + "/upgrade/" + slug
                : PARTNER_UPGRADES.contains(name) ? PARTNER + "/upgrade/" + slug : MUTATED + "/" + slug;
    }
}
