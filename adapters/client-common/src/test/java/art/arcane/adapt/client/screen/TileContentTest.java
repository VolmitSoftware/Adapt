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

/** What the row tiles draw: an adaptation is its token, name and pips, a skill adds its level and knowledge in ink. */
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

    private static String hex(int color) {
        return Integer.toHexString(color);
    }

    @Test
    void anAdaptationTileIsItsNameAndPips() {
        SkillScreen screen = fixture.open(new Route.Skill("agility"));
        for (String id : List.of("agility-wall-jump", "agility-super-jump", "agility-ladder-slide", "agility-armor-up")) {
            AdaptationTile tile = screen.tile(id);
            screen.reveal(tile);
            fixture.render();
            UiRect box = tile.bounds();
            List<String> texts = new ArrayList<>(inside("text", box));
            texts.removeIf((String text) -> Integer.parseInt(text.split(",")[0]) < tile.textBounds().x());
            assertEquals(List.of(tile.textBounds().x() + "," + (box.y() + tile.nameTop()) + " " + tile.label()), texts, id + ": only the name is written");
            assertTrue(inside("blit", box).stream().noneMatch((String blit) -> blit.contains("gui/mark/")), id + " draws a mark");
            for (String fill : inside("fill", box)) {
                assertFalse(fill.endsWith(" " + hex(Palette.GOOD)), id + " draws a green dot: " + fill);
            }
        }
    }

    /** The level sits on the medallion's corner: the learned level in numerals, gold when mastered, a ring before any. */
    @Test
    void theLevelIsABadgeOnTheMedallion() {
        SkillScreen agility = fixture.open(new Route.Skill("agility"));
        UiTheme theme = agility.theme();
        AdaptationTile learned = agility.tile("agility-wall-jump");
        agility.reveal(learned);
        fixture.render();
        UiRect icon = learned.iconBounds();
        List<String> texts = inside("text", learned.bounds());
        assertTrue(texts.stream().anyMatch((String text) -> text.endsWith(" II") && Integer.parseInt(text.split(",")[0]) < icon.right() + 2),
                "two learned levels read II on the medallion: " + texts);
        assertEquals(1, texts.stream().filter((String text) -> text.endsWith(" Wall Jump")).count(), texts.toString());

        AdaptationTile mastered = agility.tile("agility-ladder-slide");
        List<String> fills = inside("fill", mastered.bounds());
        assertTrue(fills.stream().anyMatch((String fill) -> fill.endsWith(" " + hex(UiRamp.GOLD[3]))), "a mastered badge is gold: " + fills);

        AdaptationTile fresh = agility.tile("agility-super-jump");
        assertEquals(0, fresh.level());
        texts = inside("text", fresh.bounds());
        assertEquals(List.of(), texts.stream().filter((String text) -> text.endsWith(" I")).toList(), "nothing learned shows a numeral");
        fills = inside("fill", fresh.bounds());
        assertTrue(fills.stream().anyMatch((String fill) -> fill.endsWith(" " + hex(theme.accent()))),
                "a level that can be learned now rings the corner in the skill's colour: " + fills);
    }

    @Test
    void aMasteredTileSaysSoWithItsTokenNotAWord() {
        SkillScreen screen = fixture.open(new Route.Skill("agility"));
        AdaptationTile tile = screen.tile("agility-ladder-slide");
        assertEquals(AdaptationTile.Status.MASTERED, tile.status());
        screen.reveal(tile);
        assertTrue(inside("text", tile.bounds()).stream().noneMatch((String text) -> text.endsWith(ScreenText.MASTERED.text())));
        assertTrue(tile.tooltip().lines().contains("§a" + ScreenText.MASTERED.text()), tile.tooltip().lines().toString());
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
        int left = agility.textBounds().x();
        int line = box.y() + RowTile.SECOND_LINE;
        int lead = UiTome.Glyph.BOOK.width() + 3;
        List<String> texts = inside("text", box);
        assertEquals(3, texts.size(), texts.toString());
        assertTrue(texts.contains(left + "," + (box.y() + RowTile.FIRST_LINE) + " Agility"), texts.toString());
        assertTrue(texts.contains((left + lead) + "," + line + " 30"), "the knowledge follows the book: " + texts);
        assertTrue(inside("blit", box).stream().noneMatch((String blit) -> blit.contains("gui/mark/")), "the knowledge is led by a texture");
        List<String> fills = inside("fill", box);
        for (String fill : fills) {
            assertFalse(fill.endsWith(" " + hex(Palette.GOOD)), "a green dot: " + fill);
        }
        int glyphTop = line + 7 - UiTome.Glyph.BOOK.height();
        assertTrue(agility.learnable() > 0);
        String accent = hex(Palette.accent(agility.skill().color()));
        assertTrue(fills.contains(left + "," + glyphTop + "," + (left + 2) + "," + (glyphTop + 1) + " " + accent),
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
        int quietTop = quiet.y() + RowTile.SECOND_LINE + 7 - UiTome.Glyph.BOOK_CLOSED.height();
        assertTrue(inside("fill", quiet).contains((stealth.textBounds().x() + 1) + "," + quietTop + "," + (stealth.textBounds().x() + 7) + ","
                + (quietTop + 1) + " " + hex(UiTheme.DEFAULT.textMuted())), "and its book is closed");

        SkillTile hunter = (SkillTile) fixture.find("skill:hunter");
        assertEquals(2, inside("text", hunter.bounds()).size(), "a skill with no knowledge has no line for it");
    }
}
