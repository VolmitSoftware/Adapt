package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.ShortNameView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A skill's own pages name adaptations as the server's short names do; lookups outside them keep the catalog name. */
class ShortNamesTest {
    private static final String SKILL = "pickaxe";
    private static final String FULL = "Pickaxe Drop to Inventory";
    private static final String SHORT = "Drop to Inventory";
    private static final String ID = AdaptationGridTest.id(SKILL, FULL);

    @Test
    void theSkillPageCardAndDetailShowTheShortName() {
        ScreenFixture fixture = withShortName();
        UiNode card = fixture.find("adaptation:" + ID);
        assertEquals(SHORT, card.label());
        for (int frame = 0; frame < 3; frame++) {
            fixture.current().render(fixture.draw, card.bounds().centerX(), card.bounds().centerY(), 0.0F);
            fixture.render();
        }
        assertEquals(SHORT, fixture.find("detail").label());
    }

    @Test
    void theAdaptationPageAndItsBreadcrumbShowTheShortName() {
        ScreenFixture fixture = withShortName();
        fixture.open(new Route.Adaptation(ID));
        fixture.render();
        assertEquals(SHORT, fixture.find("summary").label());
        assertEquals(SHORT, fixture.router.label(new Route.Adaptation(ID)));
    }

    @Test
    void catalogLookupsKeepTheFullName() {
        ScreenFixture fixture = withShortName();
        CatalogIndex index = fixture.router.index();
        assertEquals(FULL, index.adaptation(ID).name());
        assertEquals(FULL, index.adaptationName(ID));
        assertEquals(SHORT, index.shown(ID).name());
    }

    @Test
    void withoutShortNamesTheCardKeepsTheCatalogName() {
        ScreenFixture fixture = plain();
        assertEquals(FULL, fixture.find("adaptation:" + ID).label());
    }

    private static ScreenFixture withShortName() {
        ScreenFixture fixture = plain();
        fixture.state.acceptShortNames(List.of(new ShortNameView(ID, SHORT)));
        fixture.render();
        return fixture;
    }

    private static ScreenFixture plain() {
        return AdaptationGridTest.plain(new int[] {640, 360, 3}, SKILL, List.of(List.of(FULL, "Veinminer", "Autosmelt")), List.of());
    }
}
