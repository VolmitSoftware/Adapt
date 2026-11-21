package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiNode;
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
            UiRect box = tile.bounds();
            List<String> texts = inside("text", box);
            assertEquals(List.of(tile.textBounds().x() + "," + (box.y() + tile.nameTop()) + " " + tile.label()), texts, id + ": only the name is written");
            assertTrue(inside("blit", box).stream().noneMatch((String blit) -> blit.contains("gui/mark/")), id + " draws a mark");
            for (String fill : inside("fill", box)) {
                assertFalse(fill.endsWith(" " + hex(Palette.GOOD)), id + " draws a green dot: " + fill);
            }
        }
    }

    @Test
    void theNextPipIsOpenOnlyWhileItCanBeLearned() {
        SkillScreen screen = fixture.open(new Route.Skill("agility"));
        assertEquals("2", describe(screen.tile("agility-wall-jump")).get("openPip"), "two learned, the third open");
        assertEquals("0", describe(screen.tile("agility-super-jump")).get("openPip"));
        assertEquals("-1", describe(screen.tile("agility-ladder-slide")).get("openPip"), "mastered");
        assertEquals("-1", describe(screen.tile("agility-armor-up")).get("openPip"), "too expensive");
        SkillScreen rift = fixture.open(new Route.Skill("rift"));
        assertEquals("-1", describe(rift.tile("rift-blink")).get("openPip"), "behind a requirement");

        SkillScreen agility = fixture.open(new Route.Skill("agility"));
        AdaptationTile tile = agility.tile("agility-wall-jump");
        UiRect box = tile.bounds();
        UiTheme theme = agility.theme();
        String ink = hex(UiContrast.darkenFor(theme.accent(), theme.backdrop(), UiContrast.GRAPHIC));
        int top = box.y() + tile.barTop();
        int left = tile.textBounds().x();
        int step = 8 + ProgressBar.PIP_GAP;
        List<String> fills = inside("fill", box);
        assertTrue(fills.contains(left + "," + (top + 1) + "," + (left + 8) + "," + (top + AdaptationTile.PIP_HEIGHT - 1) + " " + ink),
                "a learned level is a solid pip: " + fills);
        int open = left + 2 * step;
        assertTrue(fills.contains((open + 1) + "," + top + "," + (open + 7) + "," + (top + 1) + " " + ink)
                && fills.contains(open + "," + (top + 1) + "," + (open + 1) + "," + (top + AdaptationTile.PIP_HEIGHT - 1) + " " + ink),
                "the level that can be learned is an outline in the same ink: " + fills);
        assertFalse(fills.contains(open + "," + (top + 1) + "," + (open + 8) + "," + (top + AdaptationTile.PIP_HEIGHT - 1) + " " + ink),
                "the open pip is filled: " + fills);
        int rest = left + 3 * step;
        assertTrue(fills.contains(rest + "," + (top + AdaptationTile.PIP_HEIGHT - 1) + "," + (rest + 8) + "," + (top + AdaptationTile.PIP_HEIGHT) + " "
                + hex(ProgressBar.rail(theme))), "later levels are a hairline: " + fills);
    }

    @Test
    void aMasteredTileSaysSoWithItsTokenNotAWord() {
        SkillScreen screen = fixture.open(new Route.Skill("agility"));
        AdaptationTile tile = screen.tile("agility-ladder-slide");
        assertEquals(AdaptationTile.Status.MASTERED, tile.status());
        assertTrue(inside("text", tile.bounds()).stream().noneMatch((String text) -> text.endsWith(ScreenText.MASTERED.text())));
        assertEquals(ScreenText.MASTERED.text(), tile.detail().second());
    }

    @Test
    void theNextLevelsCostIsInTheTooltipAndOnTheDetailPage() {
        SkillScreen single = fixture.open(new Route.Skill("agility"));
        assertFalse(single.spreadOpen());
        Tooltip tooltip = single.tile("agility-wall-jump").tooltip();
        assertNotNull(tooltip);
        assertTrue(tooltip.lines().contains("§aLearn III for 8 knowledge"), tooltip.lines().toString());
        assertTrue(single.tile("agility-armor-up").tooltip().lines().contains("§cNeeds 10 more knowledge"));

        ScreenFixture wide = new ScreenFixture(960, 540, 2);
        SkillScreen spread = wide.open(new Route.Skill("agility"));
        assertTrue(spread.spreadOpen());
        assertEquals("Learn III for 8 knowledge", spread.tile("agility-wall-jump").detail().second());
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
