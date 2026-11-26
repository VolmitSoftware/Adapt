package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.ToolView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A skill page lists the tools its adaptations hand out, with what unlocks each recipe and the recipe. */
class ToolShelfTest {
    private static final IconRef ICON = IconRef.item("minecraft:fishing_rod");

    private final ScreenFixture fixture = new ScreenFixture();

    @Test
    void theToolsOfThePagesAdaptationsShowUnderTheirHeading() {
        fixture.state.acceptTools(List.of(
                new ToolView("agility-wall-jump", "tool-agility-grappling-line", "Grappling Line", ICON, 1,
                        List.of(new ToolView.Ingredient("minecraft:fishing_rod", 1), new ToolView.Ingredient("minecraft:string", 2))),
                new ToolView("agility-super-jump", "tool-agility-later", "Later Tool", ICON, 1, List.of()),
                new ToolView("rift-elsewhere", "tool-rift-anchor", "Rift Anchor", ICON, 1, List.of())));
        fixture.open(new Route.Skill("agility"));
        fixture.render();

        UiNode heading = fixture.find("heading:tools");
        assertTrue(heading.visible());
        assertEquals("Tools", heading.label());
        UiNode line = fixture.find("tool:tool-agility-grappling-line");
        assertTrue(line.bounds().width() > 0);
        assertEquals("Grappling Line", line.tooltip().title());
        assertEquals(List.of("§aRequires Wall Jump I", "§71 × Fishing rod", "§72 × String"), line.tooltip().lines());
        assertTrue(fixture.find("tool:tool-agility-later").tooltip().lines().get(0).startsWith("§7Requires "));
        assertNull(fixture.find("tool:tool-rift-anchor"), "another skill's tool stays off this page");

        fixture.click(line);
        Route.Adaptation opened = assertInstanceOf(Route.Adaptation.class, fixture.current().route());
        assertEquals("agility-wall-jump", opened.adaptationId());
    }

    @Test
    void aPageWithoutToolsHasNoHeading() {
        fixture.open(new Route.Skill("agility"));
        fixture.render();
        UiNode heading = fixture.find("heading:tools");
        assertFalse(heading != null && heading.parent() != null && heading.bounds().height() > 0);
    }
}
