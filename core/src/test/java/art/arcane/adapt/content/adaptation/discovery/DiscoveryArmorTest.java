package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryArmorTest {
  @Test
  void preservesTheExistingArmorSampleVolume() {
    assertThat(DiscoveryArmor.scanBlockCount()).isEqualTo(1331);
  }

  @Test
  void armorPointCalculationNormalizesInvalidAndNegativeResults() {
    assertThat(DiscoveryArmor.computeArmorPoints(0D, 0D)).isZero();
    assertThat(DiscoveryArmor.computeArmorPoints(6D, 1.5D)).isFinite().isPositive();
  }

  @Test
  void materialPrecomputationSkipsNonBlockMaterials() {
    ItemOps items = mock(ItemOps.class);
    Id item = Id.minecraft("stick");
    when(items.isBlock(item)).thenReturn(false);

    assertThat(DiscoveryArmor.computeArmorPoints(item, items)).isZero();
    verify(items, never()).blastResistance(item);
    verify(items, never()).blockHardness(item);

    Id block = Id.minecraft("stone");
    when(items.isBlock(block)).thenReturn(true);
    when(items.blastResistance(block)).thenReturn(6F);
    when(items.blockHardness(block)).thenReturn(1.5F);

    assertThat(DiscoveryArmor.computeArmorPoints(block, items))
        .isEqualTo(DiscoveryArmor.computeArmorPoints(6D, 1.5D));
  }

  @Test
  void emptySamplesProduceNoArmor() {
    assertThat(DiscoveryArmor.scaleArmor(0D, 0, 3)).isZero();
  }

  @Test
  void scaledArmorRetainsTheTenPointCap() {
    assertThat(DiscoveryArmor.scaleArmor(1000D, 1, 3)).isEqualTo(10D);
  }

  @Test
  void firstActiveSampleDoesNotPreCreditTime() {
    assertThat(DiscoveryArmor.elapsedActiveTicks(null, 3000L)).isZero();
  }

  @Test
  void laterActiveSamplesUseActualElapsedTime() {
    assertThat(DiscoveryArmor.elapsedActiveTicks(1000L, 4000L)).isEqualTo(60D);
  }

  @Test
  void lerpSeedFallsBackToZeroForMissingOrInvalidHistory() {
    assertThat(DiscoveryArmor.lerpSeed(null)).isZero();
    assertThat(DiscoveryArmor.lerpSeed(Double.NaN)).isZero();
    assertThat(DiscoveryArmor.lerpSeed(Double.POSITIVE_INFINITY)).isZero();
  }

  @Test
  void lerpSeedPreservesFiniteHistory() {
    assertThat(DiscoveryArmor.lerpSeed(4.5D)).isEqualTo(4.5D);
    assertThat(DiscoveryArmor.lerpSeed(0D)).isZero();
  }

  @Test
  void legacyModifierIdentityStaysPinnedToHistoricalPersistentKey() {
    assertThat(DiscoveryArmor.LEGACY_MODIFIER).isEqualTo(UUID.fromString("5da9acee-ca11-3388-a98e-cb41016f9253"));
    assertThat(DiscoveryArmor.LEGACY_MODIFIER_KEY.namespace()).isEqualTo("adapt");
    assertThat(DiscoveryArmor.LEGACY_MODIFIER_KEY.path()).isEqualTo("discovery-armor");
  }

  @Test
  void legacyPurgeMatchesTheKeyedUuidAndNamedForms() {
    assertThat(DiscoveryArmor.isLegacyModifier(Id.adapt("discovery-armor"))).isTrue();
    assertThat(DiscoveryArmor.isLegacyModifier(Id.minecraft("5da9acee-ca11-3388-a98e-cb41016f9253"))).isTrue();
    assertThat(DiscoveryArmor.isLegacyModifier(Id.minecraft("adapt-discovery-armor"))).isTrue();
    assertThat(DiscoveryArmor.isLegacyModifier(Id.adapt("discovery-world-armor-armor"))).isFalse();
  }
}
