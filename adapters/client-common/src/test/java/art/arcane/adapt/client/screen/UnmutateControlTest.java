package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Unmutate control on a mutation's page: shown only in debug mode to a client that negotiated it, sent only after a
 * full hold, and once the server took the mutation away the book turns to the lead's page.
 */
class UnmutateControlTest {
    private static final int[] SIZE = {960, 540, 2};
    private static final int WITH_UNMUTATE = ClientProtocol.SCREENS | ClientProtocol.PANEL | ClientProtocol.XP_GUIDE | ClientProtocol.UNMUTATE;

    @Test
    void theControlShowsOnlyInDebugModeWithTheCapability() {
        assertFalse(page(false, WITH_UNMUTATE).shows(SkillScreen.UNMUTATE), "no debug mode");
        assertFalse(page(true, WITH_UNMUTATE & ~ClientProtocol.UNMUTATE).shows(SkillScreen.UNMUTATE), "no capability");
        ScreenFixture debug = page(true, WITH_UNMUTATE);
        assertTrue(debug.shows(SkillScreen.UNMUTATE));
        assertTrue(debug.shows("tag:debug"));
    }

    @Test
    void aShortPressSendsNothingAndAFullHoldSendsTheUnmutateOnce() {
        ScreenFixture fixture = page(true, WITH_UNMUTATE);
        UiNode hold = fixture.find(SkillScreen.UNMUTATE);
        press(fixture, hold);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS / 2);
        renderOver(fixture, hold);
        release(fixture, hold);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS);
        renderOver(fixture, hold);
        assertTrue(fixture.sent.isEmpty());

        press(fixture, hold);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS);
        renderOver(fixture, hold);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS);
        renderOver(fixture, hold);
        release(fixture, hold);

        assertEquals(List.of(new ClientFrame.Unmutate(mutationId())), fixture.sent);
    }

    @Test
    void theBookTurnsToTheLeadsPageOnceTheMutationIsGone() {
        ScreenFixture fixture = page(true, WITH_UNMUTATE);
        UiNode hold = fixture.find(SkillScreen.UNMUTATE);
        press(fixture, hold);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS);
        renderOver(fixture, hold);
        release(fixture, hold);

        fixture.state.acceptCatalog(TypicalCatalog.catalog());
        fixture.router.feedback(FeedbackKind.SUCCESS, "Removed");
        fixture.current().tick();
        fixture.render();

        assertEquals(new Route.Skill(lead()), fixture.current().route());
        assertNotNull(fixture.find("identity"));
    }

    private static ScreenFixture page(boolean debug, int capabilities) {
        ScreenFixture fixture = SkillSeatsTest.fixture(SIZE, List.<String[]>of(new String[] {lead(), partner()}), Set.of());
        fixture.state.negotiated(ClientProtocol.VERSION, new ServerInfo("test", "26.3", capabilities));
        PlayerView player = fixture.state.player();
        PlayerAccess access = player.access();
        fixture.state.acceptPlayer(new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(),
                new PlayerAccess(debug, access.refunds(), access.preferences(), access.skills(), access.adaptationRows())));
        fixture.open(new Route.Skill(mutationId()));
        return fixture;
    }

    private static List<String> roster() {
        List<SkillView> skills = new ArrayList<>(TypicalCatalog.catalog().skills());
        skills.sort((SkillView left, SkillView right) -> left.name().toLowerCase(Locale.ROOT).compareTo(right.name().toLowerCase(Locale.ROOT)));
        List<String> ids = new ArrayList<>();
        for (SkillView skill : skills) {
            ids.add(skill.id());
        }
        return ids;
    }

    private static String lead() {
        return roster().get(1);
    }

    private static String partner() {
        return roster().get(4);
    }

    private static String mutationId() {
        return "mutated-" + lead() + "-" + partner();
    }

    private static void press(ScreenFixture fixture, UiNode node) {
        renderOver(fixture, node);
        fixture.current().mouseClicked(mouse(node));
        renderOver(fixture, node);
    }

    private static void release(ScreenFixture fixture, UiNode node) {
        fixture.current().mouseReleased(mouse(node));
        renderOver(fixture, node);
    }

    private static void renderOver(ScreenFixture fixture, UiNode node) {
        fixture.current().render(fixture.draw, node.bounds().centerX(), node.bounds().centerY(), 0.0F);
    }

    private static UiMouseEvent mouse(UiNode node) {
        return new UiMouseEvent(node.bounds().centerX(), node.bounds().centerY(), UiMouseButton.LEFT, UiModifiers.NONE);
    }
}
