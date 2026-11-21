package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.net.FeedbackSink;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.FakeHost;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.ManualClock;
import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.view.PreferenceValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenPreviewTest {
    private final ManualClock clock = new ManualClock();
    private final FakeHost host = new FakeHost(427, 240);
    private final KitDraw draw = new KitDraw();
    private final ScreenPreview preview = new ScreenPreview(host, clock.motion(), FeedbackSink.NONE);

    private void ticks(int count) {
        for (int index = 0; index < count; index++) {
            preview.tick();
        }
    }

    private void click(String id) {
        AdaptScreen screen = preview.router().current();
        screen.render(draw, 0, 0, 0.0F);
        UiNode node = screen.root().find(id);
        screen.scroll().reveal(node.bounds());
        screen.render(draw, 0, 0, 0.0F);
        UiMouseEvent event = new UiMouseEvent(node.bounds().centerX(), node.bounds().centerY(), UiMouseButton.LEFT, UiModifiers.NONE);
        screen.mouseClicked(event);
        screen.mouseReleased(event);
    }

    @Test
    void keyOpensTheSkillsPageAfterTheStandInServerAnswers() {
        preview.open();
        assertNull(host.current());
        ticks(ScreenPreview.REPLY_TICKS);
        assertInstanceOf(SkillsScreen.class, host.current());
    }

    @Test
    void learningSpendsKnowledgeAndPowerOnceTheAnswerArrives() {
        preview.router().open(new Route.Adaptation("agility-super-jump"));
        AdaptationScreen screen = (AdaptationScreen) preview.router().current();
        click("learn");
        assertTrue(screen.pending());
        assertEquals(0, CatalogIndex.level(preview.state().player(), "agility-super-jump"));
        ticks(ScreenPreview.REPLY_TICKS);
        assertFalse(screen.pending());
        assertEquals(1, CatalogIndex.level(preview.state().player(), "agility-super-jump"));
        assertEquals(22L, LearnRules.knowledge(preview.state().player(), "agility"));
        assertEquals(5, preview.state().player().header().powerUsed());
        assertEquals(Snackbar.Tone.SUCCESS, screen.snackbar().tone());
    }

    @Test
    void unlearningRefundsAndDropsPreferences() {
        preview.router().open(new Route.Adaptation("agility-wall-jump"));
        AdaptationScreen screen = (AdaptationScreen) preview.router().current();
        screen.render(draw, 0, 0, 0.0F);
        click("unlearn");
        ticks(ScreenPreview.REPLY_TICKS);
        click("unlearn");
        ticks(ScreenPreview.REPLY_TICKS);
        assertEquals(0, CatalogIndex.level(preview.state().player(), "agility-wall-jump"));
        assertEquals(40L, LearnRules.knowledge(preview.state().player(), "agility"));
        assertEquals(2, preview.state().player().header().powerUsed());
        assertTrue(preview.state().player().preferences().stream()
                .noneMatch((PreferenceValue value) -> value.key().ownerId().equals("agility-wall-jump")));
    }

    @Test
    void refusedRequestsAnswerWithTheReason() {
        preview.router().open(new Route.Skills());
        preview.router().actions().learn("agility-armor-up", 1);
        ticks(ScreenPreview.REPLY_TICKS);
        AdaptScreen screen = preview.router().current();
        assertEquals(Snackbar.Tone.FAILURE, screen.snackbar().tone());
        assertEquals("Needs 10 more knowledge", screen.snackbar().text());
    }
}
