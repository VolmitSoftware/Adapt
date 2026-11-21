package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.qa.UiProbe;
import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.kit.Breadcrumbs;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.IconTile;
import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.StatChip;
import art.arcane.adapt.client.ui.kit.Toggle;
import art.arcane.adapt.client.ui.kit.UiNode;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenFlowTest {
    private final ScreenFixture fixture = new ScreenFixture();

    private static PlayerView withLevel(PlayerView player, String adaptationId, int level) {
        Map<String, Integer> levels = new HashMap<>(player.adaptationLevels());
        if (level == 0) {
            levels.remove(adaptationId);
        } else {
            levels.put(adaptationId, level);
        }
        return new PlayerView(player.header(), player.skills(), levels, player.preferences(), player.access());
    }

    private static PlayerAccess access(boolean debug, boolean refunds, boolean preferences) {
        PlayerAccess base = PreviewSamples.access();
        return new PlayerAccess(debug, refunds, preferences, base.skills(), base.adaptationRows());
    }

    @Test
    void cardsOpenPagesAndBackWalksUpThePath() {
        SkillsScreen skills = fixture.open(new Route.Skills());
        fixture.click(fixture.find("skill:agility"));
        SkillScreen skill = assertInstanceOf(SkillScreen.class, fixture.current());
        fixture.render();
        fixture.click(fixture.find("adaptation:agility-wall-jump"));
        AdaptationScreen adaptation = assertInstanceOf(AdaptationScreen.class, fixture.current());
        assertEquals(List.of(new Route.Skills(), new Route.Skill("agility"), new Route.Adaptation("agility-wall-jump")),
                fixture.router.path(adaptation.route()));
        assertTrue(fixture.key(UiKey.ESCAPE));
        assertEquals(new Route.Skill("agility"), fixture.current().route());
        fixture.click(fixture.find("back"));
        assertEquals(new Route.Skills(), fixture.current().route());
        fixture.key(UiKey.ESCAPE);
        assertNull(fixture.host.current());
        assertNotNull(skills);
        assertNotNull(skill);
    }

    @Test
    void breadcrumbsJumpToAnyParent() {
        fixture.open(new Route.Adaptation("rift-blink"));
        Breadcrumbs crumbs = (Breadcrumbs) fixture.find("crumb");
        assertEquals(List.of("Skills", "Rift", "Rift Blink"), crumbs.labels());
        fixture.click(fixture.find("crumb:0"));
        assertEquals(new Route.Skills(), fixture.current().route());
    }

    @Test
    void eKeyClosesUnlessTypingInSearch() {
        SkillsScreen skills = fixture.open(new Route.Skills());
        skills.focus(skills.search());
        assertTrue(fixture.key(UiKey.E));
        assertSame(skills, fixture.host.current());
        skills.focus(null);
        fixture.key(UiKey.E);
        assertNull(fixture.host.current());
    }

    @Test
    void searchFiltersCardsAndEscapeClearsItFirst() {
        SkillsScreen skills = fixture.open(new Route.Skills());
        skills.focus(skills.search());
        for (char c : "rif".toCharArray()) {
            skills.charTyped(new UiCharEvent(c, UiModifiers.NONE));
        }
        fixture.render();
        assertNotNull(fixture.find("skill:rift"));
        assertNull(fixture.find("skill:agility"));
        fixture.key(UiKey.ESCAPE);
        assertSame(skills, fixture.host.current());
        assertNotNull(fixture.find("skill:agility"));
    }

    @Test
    void learnWaitsForTheServerWithoutChangingTheLevel() {
        AdaptationScreen screen = fixture.open(new Route.Adaptation("agility-wall-jump"));
        assertEquals(3, screen.selectedLevel());
        Button learn = (Button) fixture.find("learn");
        assertTrue(learn.enabled());
        fixture.click(learn);
        assertEquals(List.of(new ClientFrame.Learn("agility-wall-jump", 3, PreviewSamples.REVISION)), fixture.sent);
        assertTrue(screen.pending());
        assertFalse(((Button) fixture.find("learn")).enabled());
        assertEquals("false", describe(screen.levelNode(3)).get("learned"));
        assertEquals("true", describe(screen.levelNode(3)).get("pending"));
        assertTrue(screen.snackbar().pending());

        fixture.click(learn);
        assertEquals(1, fixture.sent.size());

        fixture.state.acceptPlayer(withLevel(fixture.state.player(), "agility-wall-jump", 3));
        fixture.render();
        assertTrue(screen.pending());
        assertEquals("true", describe(screen.levelNode(3)).get("learned"));

        fixture.router.feedback(FeedbackKind.SUCCESS, "Learned Wall Jump III");
        fixture.render();
        assertFalse(screen.pending());
        assertEquals(Snackbar.Tone.SUCCESS, screen.snackbar().tone());
        assertEquals("Learned Wall Jump III", screen.snackbar().text());
        assertEquals(4, screen.selectedLevel());
    }

    @Test
    void unansweredRequestsTimeOut() {
        AdaptationScreen screen = fixture.open(new Route.Adaptation("agility-super-jump"));
        fixture.click(fixture.find("learn"));
        assertTrue(screen.pending());
        fixture.clock.advance(AdaptScreen.ANSWER_TIMEOUT_MILLIS);
        screen.tick();
        assertFalse(screen.pending());
        assertEquals(Snackbar.Tone.FAILURE, screen.snackbar().tone());
        assertEquals(ScreenText.NO_ANSWER.text(), screen.snackbar().text());
    }

    @Test
    void failedSendsShowAtOnce() {
        AdaptationScreen screen = fixture.open(new Route.Adaptation("agility-super-jump"));
        fixture.connected = false;
        fixture.click(fixture.find("learn"));
        assertFalse(screen.pending());
        assertEquals(Snackbar.Tone.FAILURE, screen.snackbar().tone());
        assertEquals(ScreenText.NOT_CONNECTED.text(), screen.snackbar().text());
        assertTrue(fixture.find("learn").enabled());
    }

    @Test
    void selectingALearnedLevelOffersUnlearnWithItsRefund() {
        AdaptationScreen screen = fixture.open(new Route.Adaptation("agility-wall-jump"));
        fixture.click(screen.levelNode(2));
        assertEquals(2, screen.selectedLevel());
        Button unlearn = (Button) fixture.find("unlearn");
        assertTrue(unlearn.visible());
        assertFalse(fixture.find("learn").visible());
        assertEquals("6", ((StatChip) fixture.find("stat:refund")).value());
        fixture.click(unlearn);
        assertEquals(List.of(new ClientFrame.Unlearn("agility-wall-jump", 1, PreviewSamples.REVISION)), fixture.sent);
    }

    @Test
    void permanentLearnAsksFirst() {
        AdaptationScreen screen = fixture.open(new Route.Adaptation("rift-resistance"));
        fixture.click(fixture.find("learn"));
        assertNotNull(screen.confirmation());
        assertTrue(fixture.sent.isEmpty());
        fixture.click(fixture.find("confirm.cancel"));
        assertNull(screen.confirmation());
        assertTrue(fixture.sent.isEmpty());

        fixture.click(fixture.find("learn"));
        fixture.key(UiKey.ESCAPE);
        assertNull(screen.confirmation());
        assertSame(screen, fixture.current());
        assertTrue(fixture.sent.isEmpty());

        fixture.click(fixture.find("learn"));
        fixture.click(fixture.find("confirm.confirm"));
        assertEquals(List.of(new ClientFrame.Learn("rift-resistance", 1, PreviewSamples.REVISION)), fixture.sent);
        assertTrue(screen.pending());
    }

    @Test
    void blockedLevelsExplainWhy() {
        fixture.open(new Route.Adaptation("rift-blink"));
        assertFalse(fixture.find("learn").enabled());
        assertEquals("Requires Rift Gate II", fixture.find("reason").label());
        fixture.open(new Route.Adaptation("stealth-vision"));
        assertEquals("Needs 6 power, 4 free", fixture.find("reason").label());
    }

    @Test
    void liveUpdatesKeepScrollAndFocus() {
        ScreenFixture small = new ScreenFixture(320, 180, 4.0, MaxCatalog.catalog(), MaxCatalog.player());
        AdaptationScreen screen = small.open(new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION));
        assertTrue(screen.scroll().maxScroll() > 0);
        screen.scroll().setScroll(30);
        UiNode node = screen.levelNode(4);
        screen.focus(node);
        int scrolled = screen.scroll().scroll();
        small.state.acceptPlayer(small.state.player());
        small.render();
        assertSame(node, screen.focused());
        assertEquals(scrolled, screen.scroll().scroll());
        assertSame(screen, small.current());
    }

    @Test
    void catalogChangesRebuildInPlace() {
        SkillScreen screen = fixture.open(new Route.Skill("agility"));
        UiNode card = fixture.find("adaptation:agility-super-jump");
        fixture.state.acceptCatalog(PreviewSamples.catalog());
        fixture.render();
        assertSame(card, fixture.find("adaptation:agility-super-jump"));
        assertSame(screen, fixture.current());
    }

    @Test
    void preferencesSendRequestsAndDoNotMoveUntilTheServerAnswers() {
        AdaptationScreen screen = fixture.open(new Route.Adaptation("rift-gate"));
        PreferencePanel.IconSwitch particles = (PreferencePanel.IconSwitch) fixture.find("pref:gate-particles");
        assertFalse(particles.on());
        particles.flip();
        assertFalse(particles.on());
        assertEquals(List.of(new ClientFrame.SetPreference(PreferenceOwner.ADAPTATION, "rift-gate", "gate-particles", "ON")), fixture.sent);
        fixture.render();
        assertFalse(fixture.find("pref:gate-particles").enabled());
        fixture.router.feedback(FeedbackKind.SUCCESS, "Saved");
        fixture.render();
        assertTrue(fixture.find("pref:gate-particles").enabled());

        IconTile fancy = (IconTile) fixture.find("pref:gate-style:FANCY");
        assertTrue(fancy.locked());
        IconTile minimal = (IconTile) fixture.find("pref:gate-style:MINIMAL");
        assertFalse(minimal.locked());
        fixture.click(minimal);
        assertEquals(new ClientFrame.SetPreference(PreferenceOwner.ADAPTATION, "rift-gate", "gate-style", "MINIMAL"), fixture.sent.get(1));
        fixture.router.feedback(FeedbackKind.SUCCESS, "Saved");
        fixture.click(fixture.find("pref-reset"));
        assertEquals(new ClientFrame.ResetPreference(PreferenceOwner.ADAPTATION, "rift-gate"), fixture.sent.get(2));
        assertTrue(screen.pending());
    }

    @Test
    void unlearnedAdaptationsLockTheirPreferences() {
        fixture.state.acceptPlayer(withLevel(fixture.state.player(), "rift-gate", 0));
        fixture.open(new Route.Adaptation("rift-gate"));
        assertEquals(ScreenText.LEARN_FIRST.text(), fixture.find("preferences.locked").label());
        assertEquals("setting", fixture.find("pref:gate-style").kind());
        assertFalse(fixture.find("pref:gate-particles").enabled());
        assertEquals("Off", describe(fixture.find("pref:gate-particles")).get("value"));
        assertNull(fixture.find("pref-reset"));
        fixture.open(new Route.Adaptation("agility-wall-jump"));
        assertNull(fixture.find("preferences.locked"));
        assertTrue(fixture.find("pref:jump-sound").enabled());
    }

    @Test
    void serverControlledSkillPreferencesAreDisabled() {
        fixture.open(new Route.Skill("stealth"));
        assertFalse(fixture.find("pref:enabled").enabled());
        fixture.open(new Route.Skill("agility"));
        assertTrue(fixture.find("pref:enabled").enabled());
        ((Toggle) fixture.find("pref:enabled")).flip();
        assertEquals(new ClientFrame.SetPreference(PreferenceOwner.SKILL, "agility", "enabled", "OFF"), fixture.sent.get(0));
    }

    @Test
    void feedbackWithoutARequestStillShows() {
        SkillsScreen screen = fixture.open(new Route.Skills());
        fixture.router.feedback(FeedbackKind.FAILURE, "The catalog changed");
        assertEquals(Snackbar.Tone.FAILURE, screen.snackbar().tone());
        assertTrue(screen.snackbar().live());
        fixture.clock.advance(Snackbar.LIFETIME_MILLIS);
        assertFalse(screen.snackbar().live());
    }

    @Test
    void feedbackWithNoPageOpenGoesToTheHud() {
        fixture.router.feedback(FeedbackKind.SUCCESS, "Learned Wall Jump III");
        assertEquals(List.of("SUCCESS Learned Wall Jump III"), fixture.hudFeedback);
        fixture.open(new Route.Skills());
        fixture.router.feedback(FeedbackKind.INFO, "Shown on the page");
        assertEquals(1, fixture.hudFeedback.size());
    }

    @Test
    void deniedSkillPageSaysSoInsteadOfShowingAdaptations() {
        PlayerAccess base = PreviewSamples.access();
        Map<String, List<List<String>>> rows = new HashMap<>(base.adaptationRows());
        rows.remove("rift");
        PlayerView player = fixture.state.player();
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                new PlayerAccess(false, true, true, base.skills(), rows)));
        SkillsScreen skills = fixture.open(new Route.Skills());
        assertEquals("false", describe(fixture.find("skill:rift")).get("access"));
        assertNotNull(skills);
        fixture.open(new Route.Skill("rift"));
        assertEquals(ScreenText.NO_ACCESS.text(), fixture.find("notice").label());
        assertNull(fixture.find("adaptation:rift-gate"));
        assertFalse(fixture.find("preferences").visible());
        fixture.open(new Route.Adaptation("rift-gate"));
        assertEquals(ScreenText.DENIED.text(), fixture.find("notice").label());
        assertFalse(fixture.find("detail").visible());
    }

    @Test
    void emptyAccessShowsHowToDiscoverSkills() {
        PlayerView player = fixture.state.player();
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                PlayerAccess.NONE));
        fixture.open(new Route.Skills());
        assertEquals(ScreenText.NO_SKILLS.text(), fixture.find("empty").label());
        assertEquals(ScreenText.NO_SKILLS_HINT.text(), fixture.find("empty.hint").label());
    }

    @Test
    void disabledPlayerSettingsHidePreferences() {
        PlayerView player = fixture.state.player();
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                access(false, true, false)));
        fixture.open(new Route.Adaptation("rift-gate"));
        assertFalse(fixture.find("preferences").visible());
        fixture.open(new Route.Skill("agility"));
        assertFalse(fixture.find("preferences").visible());
    }

    @Test
    void noRefundServersShowNoRefund() {
        PlayerView player = fixture.state.player();
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                access(false, false, true)));
        AdaptationScreen screen = fixture.open(new Route.Adaptation("agility-wall-jump"));
        fixture.click(screen.levelNode(2));
        assertFalse(fixture.find("stat:refund").visible());
        assertEquals(ScreenText.NO_REFUND.text(), fixture.find("reason").label());
        assertTrue(fixture.find("unlearn").enabled());
    }

    @Test
    void debugModeShowsLearningAsFreeAndSkipsTheConfirmation() {
        PlayerView player = fixture.state.player();
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                access(true, true, true)));
        fixture.open(new Route.Adaptation("agility-armor-up"));
        assertEquals(ScreenText.FREE_COST.text(), ((StatChip) fixture.find("stat:cost")).value());
        assertTrue(fixture.find("stat:debug").visible());
        assertTrue(fixture.find("learn").enabled());
        AdaptationScreen permanent = fixture.open(new Route.Adaptation("rift-resistance"));
        fixture.click(fixture.find("learn"));
        assertNull(permanent.confirmation());
        assertEquals(List.of(new ClientFrame.Learn("rift-resistance", 1, PreviewSamples.REVISION)), fixture.sent);
    }

    @Test
    void sessionEndClosesThePage() {
        fixture.open(new Route.Skill("agility"));
        fixture.state.reset();
        assertNull(fixture.host.current());
    }

    @Test
    void scrollIsRememberedPerPage() {
        ScreenFixture small = new ScreenFixture(284, 160, 3.0, MaxCatalog.catalog(), MaxCatalog.player());
        SkillScreen skill = small.open(new Route.Skill(MaxCatalog.LARGEST_SKILL));
        skill.scroll().setScroll(40);
        int scrolled = skill.scroll().scroll();
        assertTrue(scrolled > 0);
        small.open(new Route.Adaptation(MaxCatalog.GROUPED_ADAPTATION));
        small.key(UiKey.ESCAPE);
        small.render();
        SkillScreen back = (SkillScreen) small.current();
        assertEquals(scrolled, back.scroll().scroll());
    }

    @Test
    void probeNamesEveryActionableWidget() {
        UiProbe.snapshot();
        ScreenFixture big = new ScreenFixture(960, 540, 2.0);
        big.open(new Route.Skills());
        String skills = probe(big, "skill:agility");
        assertTrue(skills.contains("\"screen\":\"adapt-skills\""), skills);
        assertTrue(skills.contains("\"id\":\"skill:agility\""));
        assertTrue(skills.contains("\"id\":\"search\""));
        big.open(new Route.Skill("rift"));
        assertTrue(probe(big, "adaptation:rift-blink").contains("\"id\":\"adaptation:rift-blink\""));
        assertTrue(probe(big, "pref:enabled").contains("\"id\":\"pref:enabled\""));
        big.open(new Route.Adaptation("agility-wall-jump"));
        String adaptation = probe(big, "level:3");
        assertTrue(adaptation.contains("\"id\":\"level:3\""));
        assertTrue(adaptation.contains("\"route\":\"adaptation:agility-wall-jump\""));
        assertTrue(probe(big, "learn").contains("\"id\":\"learn\""));
        assertTrue(probe(big, "pref:jump-sound").contains("\"id\":\"pref:jump-sound\""));
    }

    private static String probe(ScreenFixture fixture, String id) {
        AdaptScreen screen = fixture.current();
        screen.scroll().reveal(fixture.find(id).bounds());
        fixture.clock.advance(200L);
        fixture.render();
        return UiProbe.snapshot();
    }

    private static Map<String, String> describe(UiNode node) {
        Map<String, String> state = new HashMap<>();
        node.describe(state);
        return state;
    }
}
