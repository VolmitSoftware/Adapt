package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.UiNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutateButtonTest {
    private final ScreenFixture fixture = new ScreenFixture();

    @Test
    void anOfferedSkillPageShowsMutateAndPressingItAsksTheServer() {
        fixture.state.negotiated(ClientProtocol.VERSION, new ServerInfo("test", "26.3",
                ClientProtocol.SCREENS | ClientProtocol.PANEL | ClientProtocol.MUTATE));
        fixture.state.acceptMutateOffers(List.of("agility"));
        fixture.open(new Route.Skill("agility"));
        fixture.render();

        Button mutate = assertInstanceOf(Button.class, fixture.find(SkillScreen.MUTATE));
        assertTrue(mutate.visible());
        fixture.click(mutate);

        assertEquals(new ClientFrame.Mutate("agility"), fixture.sent.getLast());
    }

    @Test
    void noButtonWithoutAnOfferOrWithoutTheCapability() {
        fixture.state.acceptMutateOffers(List.of("agility"));
        fixture.open(new Route.Skill("agility"));
        fixture.render();
        assertHidden(fixture.find(SkillScreen.MUTATE));

        fixture.state.negotiated(ClientProtocol.VERSION, new ServerInfo("test", "26.3",
                ClientProtocol.SCREENS | ClientProtocol.PANEL | ClientProtocol.MUTATE));
        fixture.open(new Route.Skill("rift"));
        fixture.render();
        assertHidden(fixture.find(SkillScreen.MUTATE));
    }

    private static void assertHidden(UiNode node) {
        assertTrue(node == null || !node.visible() || node.parent() == null || !node.parent().visible()
                || node.bounds().width() == 0, "Mutate button is shown");
    }
}
