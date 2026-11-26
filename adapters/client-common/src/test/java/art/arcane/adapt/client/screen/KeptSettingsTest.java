package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Tag;
import art.arcane.adapt.client.ui.kit.UiFocus;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A kept original opens from its medallion into its own page, themed for the mutated skill that keeps it, reading "Kept"
 * with its level, its kept levels marked as kept pips, the levels above them bought and refunded like any other and every
 * setting editable; an upgrade's page lists the originals it lends a level to the same way, without level controls.
 * Statuses on the adaptation page are tags that cannot be clicked or focused.
 */
class KeptSettingsTest {
    private final ScreenFixture fixture = new ScreenFixture();

    private void keepRiftGate() {
        PlayerView player = fixture.state.player();
        Map<String, Integer> levels = new HashMap<>(player.adaptationLevels());
        levels.remove("rift-gate");
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), levels, player.preferences(), player.access()));
        fixture.state.acceptKept("Kept without learning", List.of(new KeptView("agility", "rift-gate", 3)));
    }

    @Test
    void aKeptMedallionOpensItsPageWithEditableSettings() {
        keepRiftGate();
        fixture.open(new Route.Skill("agility"));
        KeptTile medallion = assertInstanceOf(KeptTile.class, fixture.find("kept:rift-gate"));
        assertTrue(medallion.focusable());
        assertEquals("Rift Gate III", medallion.tooltip().title());
        fixture.click(medallion);

        AdaptationScreen page = assertInstanceOf(AdaptationScreen.class, fixture.current());
        assertEquals(new Route.Adaptation("rift-gate"), page.route());
        assertEquals("agility", page.themeSkill());
        assertEquals(ScreenText.KEPT_AT.text("III"), fixture.find("summary").tooltip().lines().get(0).substring(2));
        assertTrue(fixture.shows("learn"), "the levels above the kept one can be bought");
        assertFalse(fixture.shows("unlearn"), "the kept level cannot be given up");
        assertEquals("KEPT", describe(page.levelNode(3)).get("pip"));
        assertEquals("EMPTY", describe(page.levelNode(4)).get("pip"));
        assertTrue(fixture.shows("heading:settings"));
        assertEquals(4, page.shownLevel());

        fixture.click(fixture.find("pref:gate-sounds"));
        assertEquals(List.of(new ClientFrame.SetPreference(PreferenceOwner.ADAPTATION, "rift-gate", "gate-sounds", "OFF")), fixture.sent);

        fixture.key(UiKey.BACKSPACE);
        assertEquals(new Route.Skill("agility"), fixture.current().route());
    }

    @Test
    void aKeptOriginalIsRaisedAndLoweredWithTheUsualControlsButNeverBelowItsKeptLevel() {
        keepRiftGate();
        fixture.open(new Route.Adaptation("rift-gate"));
        assertEquals("Level up IV", fixture.find("learn").label());
        assertEquals("9", value("stat:cost"), "level IV costs its own price only");
        fixture.click(fixture.find("learn"));
        assertEquals(List.of(new ClientFrame.Learn("rift-gate", 4, PreviewSamples.REVISION)), fixture.sent);

        fixture.sent.clear();
        raise(5);
        fixture.router.feedback(FeedbackKind.SUCCESS, "Learned Rift Gate V");
        fixture.render();
        AdaptationScreen page = (AdaptationScreen) fixture.current();
        assertEquals(List.of(new Route.Skills(), new Route.Skill("agility"), new Route.Adaptation("rift-gate")),
                fixture.router.path(new Route.Adaptation("rift-gate")), "a raised original stays under the skill that keeps it");
        assertEquals(ScreenText.RAISED_AT.text("III", "V"), fixture.find("summary").tooltip().lines().get(0).substring(2));
        assertEquals("Unlearn V", fixture.find("unlearn").label());
        fixture.click(fixture.find("unlearn"));
        assertEquals(List.of(new ClientFrame.Unlearn("rift-gate", 4, PreviewSamples.REVISION)), fixture.sent);

        fixture.router.feedback(FeedbackKind.SUCCESS, "Unlearned Rift Gate V");
        fixture.sent.clear();
        page.pick(1);
        assertFalse(fixture.shows("confirm"), "a kept pip offers nothing below the kept level");
        page.pick(3);
        assertEquals("Unlearn to III", fixture.find("confirm").label());
    }

    @Test
    void aRaisedMedallionReadsItsLevelAndStandsOutFromTheFreeOnes() {
        keepRiftGate();
        raise(4);
        fixture.open(new Route.Skill("agility"));
        KeptTile medallion = assertInstanceOf(KeptTile.class, fixture.find("kept:rift-gate"));
        assertEquals("Rift Gate IV", medallion.tooltip().title());
        assertEquals("true", describe(medallion).get("raised"));
        assertEquals("§8" + ScreenText.RAISED_AT.text("III", "IV"), medallion.tooltip().lines().get(0));
    }

    private void raise(int level) {
        PlayerView player = fixture.state.player();
        Map<String, Integer> levels = new HashMap<>(player.adaptationLevels());
        levels.put("rift-gate", level);
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), levels, player.preferences(), player.access()));
    }

    private String value(String id) {
        return describe(fixture.find(id)).get("value");
    }

    private static Map<String, String> describe(UiNode node) {
        Map<String, String> state = new HashMap<>();
        node.describe(state);
        return state;
    }

    @Test
    void aKeptMedallionOpensFromTheKeyboard() {
        keepRiftGate();
        SkillScreen skill = fixture.open(new Route.Skill("agility"));
        skill.reveal(fixture.find("kept:rift-gate"));
        fixture.render();
        skill.focus(fixture.find("kept:rift-gate"));
        fixture.key(UiKey.ENTER);
        assertEquals(new Route.Adaptation("rift-gate"), fixture.current().route());
    }

    @Test
    void anUpgradeListsTheOriginalsItLendsALevel() {
        PlayerView player = fixture.state.player();
        Map<String, Integer> levels = new HashMap<>(player.adaptationLevels());
        levels.remove("rift-gate");
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), levels, player.preferences(), player.access()));
        fixture.state.acceptKept("Kept without learning", List.of(new KeptView("agility-wall-jump", "rift-gate", 4)));
        fixture.open(new Route.Adaptation("agility-wall-jump"));
        assertNotNull(fixture.find("heading:lent"));
        fixture.click(fixture.find("lent:rift-gate"));
        assertEquals(ScreenText.LENT_AT.text("IV", "Wall Jump"), fixture.find("summary").tooltip().lines().get(0).substring(2));
        assertEquals(List.of(new Route.Skills(), new Route.Skill("agility"), new Route.Adaptation("agility-wall-jump"),
                new Route.Adaptation("rift-gate")), fixture.router.path(fixture.current().route()));
    }

    @Test
    void debugAndPermanentAreTagsNotButtons() {
        PlayerView player = fixture.state.player();
        PlayerAccess access = player.access();
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                new PlayerAccess(false, access.refunds(), access.preferences(), access.skills(), access.adaptationRows())));
        fixture.open(new Route.Adaptation("rift-resistance"));
        Tag permanent = assertInstanceOf(Tag.class, fixture.find("tag:permanent"));
        assertTrue(permanent.shown());
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                new PlayerAccess(true, access.refunds(), access.preferences(), access.skills(), access.adaptationRows())));
        fixture.open(new Route.Adaptation("agility-armor-up"));
        Tag debug = assertInstanceOf(Tag.class, fixture.find("tag:debug"));
        assertTrue(debug.shown());
        assertEquals("Debug", debug.label());
        assertTrue(debug.bounds().width() >= fixture.draw.textWidth("Debug"), "the whole word fits");
        assertEquals(ScreenText.DEBUG.text(), debug.tooltip().title());
        for (Tag tag : List.of(permanent, debug)) {
            assertFalse(tag.focusable());
            assertFalse(tag.activate());
            assertFalse(tag.mousePressed(new UiMouseEvent(tag.bounds().centerX(), tag.bounds().centerY(), UiMouseButton.LEFT, UiModifiers.NONE)));
        }
        List<UiNode> order = UiFocus.order(fixture.current().root());
        assertFalse(order.contains(debug));
        int sent = fixture.sent.size();
        fixture.click(debug);
        assertEquals(sent, fixture.sent.size());
    }
}
