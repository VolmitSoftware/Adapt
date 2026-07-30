package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismSeedSowerTransactionTest {
  private static final Path SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/herbalism/HerbalismSeedSower.java");

  @Test
  void plantingAcceptsAConsumedDefaultOrASettledSuppressedProvider() {
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(true, false, false)).isTrue();
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(true, false, true)).isTrue();
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(false, true, true)).isTrue();
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(false, true, false)).isFalse();
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(false, false, true)).isFalse();
  }

  @Test
  void seedValidationPrecedesPlantingAndInventoryConsumptionFollowsIt() throws Exception {
    String source = Files.readString(SOURCE);
    int charge = source.indexOf("AbilityCharge charge = payItemCostDeferred(");
    int planting = source.indexOf("int planted = plantTargets(targets, cropType);", charge);
    int consumption = source.indexOf("consumeHeldSeeds(p, seedType, planted)", planting);
    int settlement = source.indexOf("settleCost(charge.activationId())", consumption);
    int effects = source.indexOf("emitPlantingFx(targets);", settlement);

    assertThat(charge).isGreaterThanOrEqualTo(0);
    assertThat(planting).isGreaterThan(charge);
    assertThat(consumption).isGreaterThan(planting);
    assertThat(settlement).isGreaterThan(consumption);
    assertThat(effects).isGreaterThan(settlement);
    assertThat(source)
        .contains("charge.defaultCostSuppressed()", "refundCost(charge.activationId()")
        .doesNotContain("payItemCost(p, \"seeds\"");
  }
}
