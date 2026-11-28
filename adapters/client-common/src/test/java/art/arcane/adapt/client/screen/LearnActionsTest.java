package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each control of the adaptation page does in every state. The samples: Super Jump is unlearned and affordable,
 * Wall Jump is at II of V, Ladder Slide is mastered, Armor Up costs more knowledge than the player has, Night Vision
 * more power, Rift Blink waits on a requirement and Rift Resistance is permanent.
 */
class LearnActionsTest {
    private static final String WALL_JUMP = "agility-wall-jump";

    private final ScreenFixture fixture = new ScreenFixture();

    private AdaptationScreen open(String adaptationId) {
        return fixture.open(new Route.Adaptation(adaptationId));
    }

    private void level(String adaptationId, int level) {
        PlayerView player = fixture.state.player();
        Map<String, Integer> levels = new HashMap<>(player.adaptationLevels());
        if (level == 0) {
            levels.remove(adaptationId);
        } else {
            levels.put(adaptationId, level);
        }
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), levels, player.preferences(), player.access()));
        fixture.render();
    }

    private void access(boolean debug, boolean refunds) {
        PlayerView player = fixture.state.player();
        PlayerAccess base = player.access();
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                new PlayerAccess(debug, refunds, base.preferences(), base.skills(), base.adaptationRows())));
    }

    private static Map<String, String> describe(UiNode node) {
        Map<String, String> state = new LinkedHashMap<>();
        node.describe(state);
        return state;
    }

    private String value(String id) {
        return describe(fixture.find(id)).get("value");
    }

    private boolean shows(String id) {
        return fixture.find(id).shown();
    }

    private String pips(AdaptationScreen screen, int levels) {
        StringBuilder out = new StringBuilder();
        for (int level = 1; level <= levels; level++) {
            Map<String, String> state = describe(screen.levelNode(level));
            String mark = switch (state.get("pip")) {
                case "LEARNED" -> "#";
                default -> ".";
            };
            out.append(Boolean.parseBoolean(state.get("offered")) ? "[" + mark + "]" : mark);
        }
        return out.toString();
    }

    @Test
    void nothingLearnedOffersTheFirstLevelAndNoUnlearn() {
        AdaptationScreen screen = open("agility-super-jump");
        assertEquals("Learn I", fixture.find("learn").label());
        assertTrue(fixture.find("learn").enabled());
        assertEquals("8", value("stat:cost"));
        assertEquals("1", value("stat:power-cost"));
        assertFalse(shows("unlearn"));
        assertFalse(shows("stat:refund"));
        assertFalse(shows("confirm"));
        assertFalse(shows("cancel"));
        assertEquals("...", pips(screen, 3));
        assertEquals(1, screen.shownLevel());
        assertEquals("", fixture.find("reason").label());

        fixture.key(UiKey.DELETE);
        assertTrue(fixture.sent.isEmpty(), "Delete has nothing to unlearn");
        fixture.key(UiKey.ENTER);
        assertEquals(List.of(new ClientFrame.Learn("agility-super-jump", 1, PreviewSamples.REVISION)), fixture.sent);
    }

    @Test
    void onlyTheLastLevelsUnlearnShowsThePowerItFrees() {
        open("agility-ladder-slide");
        assertTrue(shows("unlearn"));
        assertTrue(shows("stat:power-refund"));
        assertEquals("+1", value("stat:power-refund"));
        AdaptationScreen screen = open(WALL_JUMP);
        fixture.click(screen.levelNode(1));
        assertFalse(shows("stat:power-cost"), "an offer that keeps a level frees no power");
    }

    @Test
    void aPartlyLearnedAdaptationLevelsUpAndUnlearnsItsTopLevel() {
        AdaptationScreen screen = open(WALL_JUMP);
        assertEquals("Level up III", fixture.find("learn").label());
        assertEquals("8", value("stat:cost"));
        assertFalse(shows("stat:power-cost"), "a learned adaptation's next level shows no power");
        assertEquals("Unlearn II", fixture.find("unlearn").label());
        assertTrue(fixture.find("unlearn").enabled());
        assertEquals("+6", value("stat:refund"));
        assertFalse(shows("stat:power-refund"), "unlearning a level above the first frees no power");
        assertEquals("##...", pips(screen, 5));
        assertEquals(3, screen.shownLevel());

        fixture.click(fixture.find("learn"));
        assertEquals(List.of(new ClientFrame.Learn(WALL_JUMP, 3, PreviewSamples.REVISION)), fixture.sent);
        assertEquals("true", describe(screen.levelNode(3)).get("pending"));
        assertFalse(fixture.find("learn").enabled());
        assertFalse(fixture.find("unlearn").enabled());
        level(WALL_JUMP, 3);
        fixture.router.feedback(FeedbackKind.SUCCESS, "Learned Wall Jump III");
        fixture.render();
        assertEquals("Level up IV", fixture.find("learn").label());
        assertEquals("Unlearn III", fixture.find("unlearn").label());
        assertEquals("+8", value("stat:refund"));
        assertEquals("###..", pips(screen, 5));

        fixture.sent.clear();
        fixture.click(fixture.find("unlearn"));
        assertEquals(List.of(new ClientFrame.Unlearn(WALL_JUMP, 2, PreviewSamples.REVISION)), fixture.sent);
        assertEquals("true", describe(screen.levelNode(3)).get("pending"));
    }

    @Test
    void deleteUnlearnsTheTopLevelAndBackspaceStillGoesBack() {
        fixture.open(new Route.Skill("agility"));
        AdaptationScreen screen = open(WALL_JUMP);
        fixture.key(UiKey.DELETE);
        assertEquals(List.of(new ClientFrame.Unlearn(WALL_JUMP, 1, PreviewSamples.REVISION)), fixture.sent);
        fixture.router.feedback(FeedbackKind.FAILURE, "Needed by Something");
        fixture.render();
        assertEquals(Snackbar.Tone.FAILURE, screen.snackbar().tone());
        assertEquals("Needed by Something", screen.snackbar().text());
        assertTrue(fixture.find("unlearn").enabled(), "a refusal leaves the page ready to act again");

        fixture.sent.clear();
        fixture.key(UiKey.BACKSPACE);
        assertTrue(fixture.sent.isEmpty());
        assertTrue(fixture.current() instanceof SkillScreen);
    }

    @Test
    void aMasteredAdaptationSaysSoAndOnlyUnlearns() {
        AdaptationScreen screen = open("agility-ladder-slide");
        assertEquals(ScreenText.MASTERED.text(), fixture.find("learn").label());
        assertFalse(fixture.find("learn").enabled());
        assertFalse(shows("stat:cost"));
        assertFalse(shows("stat:power-cost"));
        assertEquals("Unlearn I", fixture.find("unlearn").label());
        assertEquals("+5", value("stat:refund"));
        assertEquals("#", pips(screen, 1));
        fixture.key(UiKey.ENTER);
        fixture.click(fixture.find("learn"));
        assertTrue(fixture.sent.isEmpty());
    }

    @Test
    void anUnaffordableLevelIsDisabledWithTheShortage() {
        AdaptationScreen screen = open("agility-armor-up");
        assertEquals("Learn I", fixture.find("learn").label());
        assertFalse(fixture.find("learn").enabled());
        assertEquals("Needs 10 more knowledge", fixture.find("reason").label());
        assertEquals("SHORT", describe(fixture.find("reason")).get("tone"));
        assertEquals(".....", pips(screen, 5), "a level the player cannot afford is a plain pip");
        fixture.key(UiKey.ENTER);
        fixture.click(fixture.find("learn"));
        assertTrue(fixture.sent.isEmpty());

        open("stealth-vision");
        assertFalse(fixture.find("learn").enabled());
        assertEquals("Needs 6 power, 5 free", fixture.find("reason").label());
        assertEquals("SHORT", describe(fixture.find("reason")).get("tone"));
    }

    @Test
    void aRequirementLocksTheLevelBehindAPadlock() {
        AdaptationScreen screen = open("rift-blink");
        assertFalse(fixture.find("learn").enabled());
        assertEquals("Requires Rift Gate II", fixture.find("reason").label());
        assertEquals("LOCK", describe(fixture.find("reason")).get("tone"));
        assertEquals("....", pips(screen, 4));
        fixture.click(screen.levelNode(3));
        assertEquals(new AdaptationScreen.Offer(3, true), screen.offer());
        assertFalse(fixture.find("confirm").enabled(), "an offer the server would refuse cannot be confirmed");
        fixture.key(UiKey.ENTER);
        assertTrue(fixture.sent.isEmpty());
    }

    @Test
    void aPermanentAdaptationAsksBeforeLearningAndNeverUnlearns() {
        AdaptationScreen screen = open("rift-resistance");
        assertTrue(shows("tag:permanent"));
        assertFalse(shows("unlearn"));
        fixture.click(fixture.find("learn"));
        assertEquals(new AdaptationScreen.Offer(1, true), screen.offer());
        assertEquals("Learn I", fixture.find("confirm").label());
        assertEquals(ScreenText.PERMANENT_TIP.text(), fixture.find("reason").label());
        assertEquals("LOCK", describe(fixture.find("reason")).get("tone"));
        assertFalse(shows("tag:permanent"));
        assertTrue(shows("cancel"));
        assertTrue(fixture.sent.isEmpty());
        fixture.key(UiKey.ENTER);
        assertEquals(List.of(new ClientFrame.Learn("rift-resistance", 1, PreviewSamples.REVISION)), fixture.sent);

        level("rift-resistance", 2);
        fixture.router.feedback(FeedbackKind.SUCCESS, "Learned");
        fixture.render();
        fixture.sent.clear();
        assertFalse(shows("unlearn"));
        fixture.key(UiKey.DELETE);
        assertNull(screen.offer());
        fixture.click(screen.levelNode(1));
        assertEquals(new AdaptationScreen.Offer(1, false), screen.offer());
        assertFalse(fixture.find("confirm").enabled());
        assertEquals(ScreenText.NO_UNLEARN.text(), fixture.find("reason").label());
        fixture.key(UiKey.ENTER);
        assertTrue(fixture.sent.isEmpty());
    }

    @Test
    void debugModeIsFreeAndAsksNothing() {
        access(true, true);
        level("rift-resistance", 1);
        AdaptationScreen screen = open("rift-resistance");
        assertEquals(ScreenText.FREE_COST.text(), value("stat:cost"));
        assertTrue(shows("tag:debug"));
        assertFalse(shows("tag:permanent"));
        assertEquals("Unlearn I", fixture.find("unlearn").label());
        assertFalse(shows("stat:refund"), "debug mode refunds nothing");
        fixture.click(fixture.find("learn"));
        assertNull(screen.offer());
        assertEquals(List.of(new ClientFrame.Learn("rift-resistance", 2, PreviewSamples.REVISION)), fixture.sent);
        fixture.router.feedback(FeedbackKind.SUCCESS, "Learned");
        fixture.sent.clear();
        fixture.click(fixture.find("unlearn"));
        assertNull(screen.offer());
        assertEquals(List.of(new ClientFrame.Unlearn("rift-resistance", 0, PreviewSamples.REVISION)), fixture.sent);

        ScreenFixture short_ = new ScreenFixture();
        PlayerView player = short_.state.player();
        PlayerAccess base = player.access();
        short_.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                new PlayerAccess(true, true, base.preferences(), base.skills(), base.adaptationRows())));
        short_.open(new Route.Adaptation("agility-armor-up"));
        assertTrue(short_.find("learn").enabled(), "debug mode ignores the knowledge it would cost");
    }

    @Test
    void clickingAnUnlearnedPipOffersToLearnUpToIt() {
        AdaptationScreen screen = open(WALL_JUMP);
        fixture.click(screen.levelNode(5));
        assertEquals(new AdaptationScreen.Offer(5, true), screen.offer());
        assertEquals("Learn to V", fixture.find("confirm").label());
        assertTrue(fixture.find("confirm").enabled());
        assertEquals("30", value("stat:cost"));
        assertFalse(shows("stat:power-cost"), "learning more levels of a held adaptation shows no power");
        assertEquals("##[.][.][.]", pips(screen, 5));
        assertEquals(5, screen.shownLevel());
        assertFalse(shows("learn"));
        assertFalse(shows("unlearn"));
        assertFalse(shows("stat:refund"));
        assertTrue(shows("cancel"));
        assertTrue(fixture.sent.isEmpty());

        fixture.click(screen.levelNode(4));
        assertEquals(new AdaptationScreen.Offer(4, true), screen.offer());
        assertEquals("Learn to IV", fixture.find("confirm").label());
        assertEquals("18", value("stat:cost"));

        fixture.click(fixture.find("confirm"));
        assertEquals(List.of(new ClientFrame.Learn(WALL_JUMP, 4, PreviewSamples.REVISION)), fixture.sent);
        assertNull(screen.offer());
        assertEquals("true", describe(screen.levelNode(4)).get("pending"));
        level(WALL_JUMP, 4);
        fixture.router.feedback(FeedbackKind.SUCCESS, "Learned Wall Jump IV");
        fixture.render();
        assertEquals("####.", pips(screen, 5));
        assertEquals("Level up V", fixture.find("learn").label());
        assertEquals("Unlearn IV", fixture.find("unlearn").label());
    }

    @Test
    void clickingALearnedPipOffersToUnlearnDownToIt() {
        level(WALL_JUMP, 4);
        AdaptationScreen screen = open(WALL_JUMP);
        fixture.click(screen.levelNode(1));
        assertEquals(new AdaptationScreen.Offer(1, false), screen.offer());
        assertEquals("Unlearn to I", fixture.find("confirm").label());
        assertEquals("+24", value("stat:cost"));
        assertFalse(shows("stat:power-cost"));
        assertEquals("#[#][#][#].", pips(screen, 5), "an unlearn offer quiets the pip that could be learned");
        assertEquals(1, screen.shownLevel());
        assertTrue(fixture.sent.isEmpty());

        fixture.click(screen.levelNode(3));
        assertEquals("Unlearn IV", fixture.find("confirm").label(), "one level down reads like the unlearn button");
        assertEquals("+10", value("stat:cost"));

        fixture.click(screen.levelNode(2));
        fixture.key(UiKey.ENTER);
        assertEquals(List.of(new ClientFrame.Unlearn(WALL_JUMP, 2, PreviewSamples.REVISION)), fixture.sent);
        assertEquals("true", describe(screen.levelNode(4)).get("pending"));
    }

    @Test
    void anOfferClosesOnEscapeACancelAClickElsewhereOrTheLevelHeld() {
        fixture.open(new Route.Skill("agility"));
        AdaptationScreen screen = open(WALL_JUMP);
        fixture.click(screen.levelNode(5));
        fixture.key(UiKey.ESCAPE);
        assertNull(screen.offer());
        assertSame(screen, fixture.current(), "Escape closes the offer before it leaves the page");
        assertTrue(shows("learn"));

        fixture.click(screen.levelNode(5));
        fixture.click(fixture.find("cancel"));
        assertNull(screen.offer());

        fixture.click(screen.levelNode(5));
        fixture.click(fixture.find("description"));
        assertNull(screen.offer());

        fixture.click(screen.levelNode(5));
        fixture.click(screen.levelNode(2));
        assertNull(screen.offer(), "the level already held has nothing to offer");

        fixture.click(screen.levelNode(5));
        level(WALL_JUMP, 5);
        assertNull(screen.offer(), "an offer the server's state overtook goes away");
        assertTrue(fixture.sent.isEmpty());
        fixture.key(UiKey.ESCAPE);
        assertTrue(fixture.current() instanceof SkillScreen);
    }

    @Test
    void pointingAtAPipShowsItsLevelWithoutChangingTheActions() {
        AdaptationScreen screen = open(WALL_JUMP);
        UiRect pip = screen.levelNode(5).bounds();
        screen.render(fixture.draw, pip.centerX(), pip.centerY(), 0.0F);
        screen.render(fixture.draw, pip.centerX(), pip.centerY(), 0.0F);
        assertEquals(5, screen.shownLevel());
        assertEquals("Learn V for 30 knowledge", fixture.find("reason").label());
        assertEquals("INFO", describe(fixture.find("reason")).get("tone"));
        assertEquals("Level up III", fixture.find("learn").label());
        assertEquals("8", value("stat:cost"));
        assertNull(screen.offer());
        fixture.draw.calls().clear();
        screen.render(fixture.draw, pip.centerX(), pip.centerY(), 0.0F);
        UiRect stats = fixture.find("detail.stats").bounds();
        assertTrue(fixture.draw.calls().contains("text " + stats.x() + "," + stats.y() + " +"), "the stat lines lose their mark");
        assertTrue(fixture.draw.calls("wrapped ").stream().anyMatch((String call) -> call.endsWith(" 50% strength")),
                "the stat lines are not the pointed level's");

        pip = screen.levelNode(1).bounds();
        screen.render(fixture.draw, pip.centerX(), pip.centerY(), 0.0F);
        assertEquals(1, screen.shownLevel());
        assertEquals("Unlearn to I, refunds 6 knowledge", fixture.find("reason").label());

        fixture.render();
        assertEquals(3, screen.shownLevel());
        assertEquals("", fixture.find("reason").label());
    }

    @Test
    void arrowsWalkThePipsAndEnterOffersThenConfirms() {
        AdaptationScreen screen = open(WALL_JUMP);
        fixture.key(UiKey.RIGHT);
        assertSame(screen.levelNode(3), screen.focused(), "the first arrow lands on the level shown");
        fixture.key(UiKey.RIGHT);
        assertSame(screen.levelNode(4), screen.focused());
        assertEquals(4, screen.shownLevel());
        fixture.key(UiKey.LEFT);
        fixture.key(UiKey.LEFT);
        assertSame(screen.levelNode(2), screen.focused());
        fixture.key(UiKey.RIGHT);
        fixture.key(UiKey.RIGHT);
        fixture.key(UiKey.ENTER);
        assertEquals(new AdaptationScreen.Offer(4, true), screen.offer());
        fixture.key(UiKey.RIGHT);
        fixture.key(UiKey.ENTER);
        assertEquals(new AdaptationScreen.Offer(5, true), screen.offer(), "Enter on another pip offers that level instead");
        fixture.key(UiKey.DELETE);
        assertTrue(fixture.sent.isEmpty(), "Delete waits while an offer is open");
        fixture.key(UiKey.ENTER);
        assertEquals(List.of(new ClientFrame.Learn(WALL_JUMP, 5, PreviewSamples.REVISION)), fixture.sent);
    }

    @Test
    void aServerWithoutRefundsUnlearnsAtOnce() {
        access(false, false);
        AdaptationScreen screen = open(WALL_JUMP);
        fixture.key(UiKey.DELETE);
        assertNull(screen.offer());
        assertEquals(List.of(new ClientFrame.Unlearn(WALL_JUMP, 1, PreviewSamples.REVISION)), fixture.sent);
    }

    @Test
    void nothingInTheRowMovesWhenLevelsOrOffersChange() {
        for (int[] size : new int[][] {{427, 240, 2}, {640, 360, 3}, {960, 540, 2}}) {
            ScreenFixture sized = new ScreenFixture(size[0], size[1], size[2]);
            AdaptationScreen screen = sized.open(new Route.Adaptation(WALL_JUMP));
            String where = size[0] + "x" + size[1];
            List<String> ids = List.of("learn", "confirm", "stat:cost", "stat:power-cost", "stat:refund", "unlearn", "cancel", "detail.action",
                    "reason", "levels", "frame");
            Map<String, UiRect> first = new LinkedHashMap<>();
            for (String id : ids) {
                first.put(id, sized.find(id).bounds());
            }
            assertEquals(first.get("learn"), first.get("confirm"), where + ": the confirm button takes the primary slot");
            assertEquals(first.get("unlearn"), first.get("cancel"), where + ": Cancel takes the quiet slot");
            List<Runnable> changes = new ArrayList<>();
            for (int level = 1; level <= 5; level++) {
                int pick = level;
                changes.add(() -> screen.pick(pick));
            }
            for (int level = 1; level <= 5; level++) {
                int held = level;
                changes.add(() -> {
                    PlayerView player = sized.state.player();
                    Map<String, Integer> levels = new HashMap<>(player.adaptationLevels());
                    levels.put(WALL_JUMP, held);
                    sized.state.acceptPlayer(new PlayerView(player.header(), player.skills(), levels, player.preferences(), player.access()));
                });
            }
            for (int index = 0; index < changes.size(); index++) {
                changes.get(index).run();
                sized.render();
                for (String id : ids) {
                    assertEquals(first.get(id), sized.find(id).bounds(), where + " change " + index + " moves " + id);
                }
            }
        }
    }

    @Test
    void aPipFadesBetweenItsLooks() {
        AdaptationScreen screen = open(WALL_JUMP);
        UiTheme theme = screen.theme();
        LevelTrack.Look empty = LevelTrack.look(new LevelTrack.LevelState(LevelTrack.Pip.EMPTY, false), theme);
        LevelTrack.Look learned = LevelTrack.look(new LevelTrack.LevelState(LevelTrack.Pip.LEARNED, false), theme);
        assertEquals(empty, empty.toward(learned, 0.0F));
        assertEquals(learned, empty.toward(learned, 1.0F));
        LevelTrack.Look half = empty.toward(learned, 0.5F);
        assertTrue(half.fill() != empty.fill() && half.fill() != learned.fill());

        fixture.draw.calls().clear();
        fixture.render();
        List<String> resting = List.copyOf(fixture.draw.calls("fill "));
        level(WALL_JUMP, 3);
        fixture.draw.calls().clear();
        fixture.render();
        List<String> filling = List.copyOf(fixture.draw.calls("fill "));
        fixture.clock.advance(LevelTrack.FILL_MILLIS);
        fixture.draw.calls().clear();
        fixture.render();
        List<String> filled = List.copyOf(fixture.draw.calls("fill "));
        UiRect pip = screen.levelNode(3).bounds();
        String body = "fill " + pip.x() + "," + (pip.y() + 1) + "," + pip.right() + "," + (pip.bottom() - 1) + " ";
        String before = colour(resting, body);
        String during = colour(filling, body);
        String after = colour(filled, body);
        assertEquals(Integer.toHexString(learned.fill()), after);
        assertEquals(before, during, "the pip starts from the look it had");
    }

    private static String colour(List<String> fills, String prefix) {
        for (String fill : fills) {
            if (fill.startsWith(prefix)) {
                return fill.substring(prefix.length());
            }
        }
        throw new AssertionError("no " + prefix + "in " + fills);
    }
}
