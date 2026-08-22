package art.arcane.adapt.papi;

import art.arcane.adapt.api.adaptation.Adaptation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

class AdaptCatalogSnapshotTest {
  @Test
  void shouldIndexSkillsAndAdaptationsByTheirGrammarConformantIdentifiers() {
    AdaptCatalogSnapshot catalog = AdaptPapiFixtures.catalog();

    assertEquals(7L, catalog.revision());
    assertNotNull(catalog.skill("mining"));
    assertNotNull(catalog.skill("hunter"));
    assertNull(catalog.skill("Mining"));
    assertNull(catalog.skill("nothing"));
    assertNotNull(catalog.adaptation("mining-vein"));
    assertNull(catalog.adaptation("mining-nothing"));
    assertEquals("2", catalog.skillCountText());
    assertEquals("2", catalog.adaptationCountText());
  }

  @Test
  void shouldPrecomputeCumulativeKnowledgeCostsThatMatchTheAdaptationCostContract() {
    Adaptation<?> source = mock(Adaptation.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    doReturn("mining-vein").when(source).getName();
    doReturn("Vein Miner").when(source).getDisplayName();
    doReturn(5).when(source).getMaxLevel();
    doReturn(4).when(source).getBaseCost();
    doReturn(0.45D).when(source).getCostFactor();
    doReturn(2).when(source).getInitialCost();

    AdaptCatalogSnapshot catalog = AdaptCatalogSnapshot.build(
        1L,
        List.of(AdaptPapiFixtures.skill("mining", true, "Mining", source))
    );
    AdaptCatalogAdaptation adaptation = catalog.adaptation("mining-vein");
    assertEquals(5, adaptation.maxLevel());

    for (int current = 0; current <= 5; current++) {
      for (int target = 0; target <= 5; target++) {
        assertEquals(
            source.getCostFor(target, current),
            adaptation.knowledgeCostFor(target, current),
            "knowledge cost for target=" + target + " current=" + current
        );
        assertEquals(
            Math.max(0, source.getPowerCostFor(target, current)),
            adaptation.powerCostFor(target, current),
            "power cost for target=" + target + " current=" + current
        );
      }
    }

    assertTrue(adaptation.knowledgeCostFor(1, 0) > 0, "the first level must cost knowledge");
  }

  @Test
  void shouldClampTargetsToTheAdaptationMaxLevel() {
    AdaptCatalogAdaptation adaptation = AdaptPapiFixtures.catalog().adaptation("mining-vein");

    assertEquals(3, adaptation.clampLevel(99));
    assertEquals(0, adaptation.clampLevel(-4));
    assertEquals(adaptation.knowledgeCostFor(3, 0), adaptation.knowledgeCostFor(500, 0));
    assertEquals(3, adaptation.nextLevel(3));
    assertEquals(2, adaptation.nextLevel(1));
  }

  @Test
  void shouldStripLegacyColourCodesFromEveryDisplayedName() {
    AdaptCatalogSnapshot catalog = AdaptPapiFixtures.catalog();

    assertEquals("Vein Miner", catalog.adaptation("mining-vein").nameText());
    assertEquals("Mining", catalog.skill("mining").nameText());

    for (String value : List.of(
        catalog.adaptation("mining-vein").nameText(),
        catalog.adaptation("mining-ore-scan").nameText(),
        catalog.skill("mining").nameText(),
        catalog.mutation("bastion-spine").nameText()
    )) {
      assertTrue(value.indexOf('§') < 0, value);
      assertTrue(value.indexOf('%') < 0, value);
    }
  }

  @Test
  void shouldCarryTheEnabledAndPermanentFlagsCapturedOffThePlaceholderThread() {
    AdaptCatalogSnapshot catalog = AdaptPapiFixtures.catalog();

    assertTrue(catalog.adaptation("mining-vein").enabled());
    assertTrue(catalog.adaptation("mining-vein").skillEnabled());
    assertTrue(catalog.adaptation("mining-ore-scan").permanent());
    assertTrue(!catalog.adaptation("mining-ore-scan").enabled());
    assertEquals("mining", catalog.adaptation("mining-vein").skillId());
  }

  @Test
  void shouldIndexEveryMutationTypeByIdentifier() {
    AdaptCatalogSnapshot catalog = AdaptPapiFixtures.catalog();

    assertNotNull(catalog.mutation("bastion-spine"));
    assertNotNull(catalog.mutation("verdant-molt"));
    assertNull(catalog.mutation("bastion_spine"));
    assertNull(catalog.mutation("nothing-here"));
  }
}
