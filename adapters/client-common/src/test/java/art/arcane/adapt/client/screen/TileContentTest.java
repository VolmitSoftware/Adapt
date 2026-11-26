package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.TextBox;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the cards draw: an adaptation is its icon, pips, cost and name, a skill its level, knowledge, bar and name. */
class TileContentTest {
    private final ScreenFixture fixture = new ScreenFixture();

    /** The draw calls of {@code kind} that start inside {@code box}, as "x,y rest". */
    private List<String> inside(String kind, UiRect box) {
        fixture.draw.calls().clear();
        fixture.render();
        List<String> found = new ArrayList<>();
        for (String call : fixture.draw.calls(kind + " ")) {
            String[] parts = call.split(" ", 3);
            String[] at = parts[1].split(",");
            if (box.contains(Integer.parseInt(at[0]), Integer.parseInt(at[1]))) {
                found.add(call.substring(kind.length() + 1));
            }
        }
        return found;
    }

    private static Map<String, String> describe(UiNode node) {
        Map<String, String> state = new LinkedHashMap<>();
        node.describe(state);
        return state;
    }

    /** Whether a fill call "x,y,right,bottom color" paints a pip's square. */
    private static boolean pip(String fill) {
        String[] edges = fill.split(" ")[0].split(",");
        return Integer.parseInt(edges[2]) - Integer.parseInt(edges[0]) == 3 && Integer.parseInt(edges[3]) - Integer.parseInt(edges[1]) == 3;
    }

    /** Whether the pip square {@code fill} is only an edge: another fill paints its middle. */
    private static boolean hollow(String fill, List<String> fills) {
        String[] edges = fill.split(" ")[0].split(",");
        int x = Integer.parseInt(edges[0]) + 1;
        int y = Integer.parseInt(edges[1]) + 1;
        String middle = x + "," + y + "," + (x + 1) + "," + (y + 1) + " ";
        return fills.stream().anyMatch((String other) -> other.startsWith(middle));
    }

    private static String hex(int color) {
        return Integer.toHexString(color);
    }

    /** A card names its adaptation under the icon's row at the card's padding, its next cost beside the icon's bottom. */
    @Test
    void anAdaptationCardIsItsNameUnderItsIconAndItsCostBesideIt() {
        SkillScreen screen = fixture.open(new Route.Skill("agility"));
        AdaptationTile tile = screen.tile("agility-wall-jump");
        screen.reveal(tile);
        fixture.render();
        UiRect box = tile.bounds();
        List<String> texts = inside("text", box);
        int nameTop = box.y() + AdaptationTile.PAD + tile.look().icon() + AdaptationTile.NAME_GAP;
        assertTrue(texts.contains((box.x() + Card.PAD) + "," + nameTop + " Wall Jump"), texts.toString());
        assertTrue(texts.stream().anyMatch((String text) -> text.endsWith(" 8") && text.contains("," + (box.y() + Card.LOWER_LINE) + " ")),
                "the next level's cost sits beside the icon's bottom: " + texts);
        assertEquals(new UiRect(box.x() + Card.PAD, box.y() + Card.PAD, 16, 16), tile.iconBounds());
    }

    /** Pips fill in the skill's colour for the levels held, gold once mastered, and every other pip is an empty neutral box. */
    @Test
    void theLevelIsAPipPerLevelBesideTheIcon() {
        SkillScreen agility = fixture.open(new Route.Skill("agility"));
        UiTheme theme = agility.theme();
        AdaptationTile learned = agility.tile("agility-wall-jump");
        agility.reveal(learned);
        fixture.render();
        String filled = hex(UiContrast.darkenFor(theme.accent(), theme.textInverse(), UiContrast.TEXT));
        List<String> fills = inside("fill", learned.bounds());
        List<String> pips = fills.stream().filter((String fill) -> fill.endsWith(" " + filled) && pip(fill) && !hollow(fill, fills)).toList();
        assertEquals(2, pips.size(), "two learned levels fill two pips: " + pips + " in " + learned.bounds());

        AdaptationTile mastered = agility.tile("agility-ladder-slide");
        agility.reveal(mastered);
        List<String> masteredFills = inside("fill", mastered.bounds());
        assertTrue(masteredFills.stream().anyMatch((String fill) -> fill.endsWith(" " + hex(UiRamp.GOLD[2]))),
                "a mastered card's pips are gold");
        assertTrue(inside("text", mastered.bounds()).stream().noneMatch((String text) -> text.endsWith("...")), "nothing on the card is cut short");

        AdaptationTile fresh = agility.tile("agility-super-jump");
        agility.reveal(fresh);
        assertEquals(0, fresh.level());
        assertEquals(AdaptationTile.Status.AFFORDABLE, fresh.status());
        List<String> freshFills = inside("fill", fresh.bounds());
        List<String> freshPips = freshFills.stream().filter(TileContentTest::pip).toList();
        assertFalse(freshPips.isEmpty(), "an unlearned card still shows its pips");
        for (String pip : freshPips) {
            assertTrue(pip.endsWith(" " + hex(theme.surfaceBorder())) && hollow(pip, freshFills),
                    "an unlearned card's pips are all empty and neutral: " + pip);
        }
        assertTrue(freshFills.stream().noneMatch((String fill) -> fill.endsWith(" " + hex(theme.accent())) || fill.endsWith(" " + filled)),
                "nothing on an unlearned card is drawn in the skill's colour: " + freshFills);
    }

    @Test
    void theNextLevelsCostIsInTheTooltipOnASinglePageAndBesideTheGridOnASpread() {
        SkillScreen single = fixture.open(new Route.Skill("agility"));
        Tooltip tooltip = single.tile("agility-wall-jump").tooltip();
        assertNotNull(tooltip);
        assertTrue(tooltip.lines().contains("§aLearn III for 8 knowledge"), tooltip.lines().toString());
        assertTrue(single.tile("agility-armor-up").tooltip().lines().contains("§cNeeds 10 more knowledge"));

        ScreenFixture wide = new ScreenFixture(960, 540, 2);
        SkillScreen spread = wide.open(new Route.Skill("agility"));
        AdaptationTile tile = spread.tile("agility-wall-jump");
        assertNull(tile.tooltip(), "the tooltip repeats the left page");
        spread.render(wide.draw, tile.bounds().centerX(), tile.bounds().centerY(), 0.0F);
        wide.render();
        assertEquals("Wall Jump", wide.find("detail").label());
        Map<String, String> state = describe(wide.find("detail"));
        assertEquals("Learn III for 8 knowledge", state.get("status"));
        assertEquals("Jump off walls while airborne.", ((TextBox) wide.find("detail.description")).text());
    }

    @Test
    void aSkillTileWritesItsKnowledgeInInkBehindTheBook() {
        fixture.open(new Route.Skills());
        SkillTile agility = (SkillTile) fixture.find("skill:agility");
        UiRect box = agility.bounds();
        int left = box.x() + Card.TEXT_LEFT;
        int line = box.y() + Card.LOWER_LINE;
        int lead = UiTome.Glyph.BOOK.width() + 3;
        List<String> texts = inside("text", box);
        assertEquals(3, texts.size(), texts.toString());
        assertTrue(texts.contains((box.x() + Card.PAD) + "," + (box.y() + Card.ROW_BOTTOM + Card.PAD + RowTile.BAR_HEIGHT + Card.PAD) + " Agility"),
                texts.toString());
        assertTrue(texts.contains(left + "," + (box.y() + Card.UPPER_LINE) + " " + ScreenText.LEVEL_SHORT.text(14)), texts.toString());
        assertTrue(texts.contains((left + lead) + "," + line + " 30"), "the knowledge follows the book: " + texts);
        assertTrue(inside("blit", box).stream().noneMatch((String blit) -> blit.contains("gui/mark/")), "the knowledge is led by a texture");
        List<String> fills = inside("fill", box);
        for (String fill : fills) {
            assertFalse(fill.endsWith(" " + hex(Palette.GOOD)), "a green dot: " + fill);
        }
        assertTrue(agility.learnable() > 0);
        String accent = hex(Palette.accent(agility.skill().color()));
        assertTrue(fills.contains(left + "," + line + "," + (left + 2) + "," + (line + 1) + " " + accent),
                "a skill with something to learn has its book in the skill's ink: " + fills);
        assertEquals(Palette.accent(agility.skill().color()), agility.secondColor(UiTheme.DEFAULT));

        PlayerView player = fixture.state.player();
        List<SkillLineView> lines = new ArrayList<>();
        for (SkillLineView skill : player.skills()) {
            lines.add(skill.skillId().equals("stealth")
                    ? new SkillLineView(skill.skillId(), skill.xp(), skill.level(), skill.progress(), skill.xpToNext(), 5L) : skill);
        }
        fixture.state.acceptPlayer(new PlayerView(player.header(), lines, player.adaptationLevels(), player.preferences(), player.access()));
        fixture.render();
        SkillTile stealth = (SkillTile) fixture.find("skill:stealth");
        assertEquals(0, stealth.learnable());
        assertEquals(UiTheme.DEFAULT.textMuted(), stealth.secondColor(UiTheme.DEFAULT), "knowledge that buys nothing yet is muted");
        UiRect quiet = stealth.bounds();
        int quietLeft = quiet.x() + Card.TEXT_LEFT;
        int quietTop = quiet.y() + Card.LOWER_LINE;
        assertTrue(inside("fill", quiet).contains((quietLeft + 1) + "," + quietTop + "," + (quietLeft + 7) + ","
                + (quietTop + 1) + " " + hex(UiTheme.DEFAULT.textMuted())), "and its book is closed");

        SkillTile hunter = (SkillTile) fixture.find("skill:hunter");
        assertEquals(2, inside("text", hunter.bounds()).size(), "a skill with no knowledge has no line for it");
    }
}
