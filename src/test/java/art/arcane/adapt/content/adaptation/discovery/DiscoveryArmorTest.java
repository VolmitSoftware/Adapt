package art.arcane.adapt.content.adaptation.discovery;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

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
    Material item = mock(Material.class);
    when(item.isBlock()).thenReturn(false);

    assertThat(DiscoveryArmor.computeArmorPoints(item)).isZero();
    verify(item, never()).getBlastResistance();
    verify(item, never()).getHardness();

    Material block = mock(Material.class);
    when(block.isBlock()).thenReturn(true);
    when(block.getBlastResistance()).thenReturn(6F);
    when(block.getHardness()).thenReturn(1.5F);

    assertThat(DiscoveryArmor.computeArmorPoints(block))
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
}
