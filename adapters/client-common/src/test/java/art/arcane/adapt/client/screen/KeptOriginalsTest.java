package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.view.KeptView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A skill's kept originals show as their own group of medallions on the adaptations side, named in their tooltips. */
class KeptOriginalsTest {
    private final ScreenFixture fixture = new ScreenFixture();

    @Test
    void keptOriginalsGetTheirOwnGroup() {
        fixture.state.acceptKept("Kept without learning", List.of(new KeptView("agility", "agility-wall-jump", 2),
                new KeptView("rift", "agility-super-jump", 1)));
        fixture.open(new Route.Skill("agility"));
        fixture.render();

        UiNode heading = fixture.find("heading:kept");
        assertTrue(heading.visible());
        assertEquals("Kept without learning", heading.label());
        KeptTile tile = assertInstanceOf(KeptTile.class, fixture.find("kept:agility-wall-jump"));
        assertEquals("Wall Jump II", tile.tooltip().title());
        assertTrue(tile.bounds().width() > 0);
        assertNull(fixture.find("kept:agility-super-jump"), "another skill's kept original stays off this page");
    }

    @Test
    void aSkillThatKeepsNothingHasNoGroup() {
        fixture.open(new Route.Skill("agility"));
        fixture.render();
        UiNode heading = fixture.find("heading:kept");
        assertFalse(heading != null && heading.parent() != null && heading.bounds().height() > 0);
    }
}
