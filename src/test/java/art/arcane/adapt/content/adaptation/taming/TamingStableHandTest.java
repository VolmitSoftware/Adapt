package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.version.IAttribute;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TamingStableHandTest {
  @Test
  void biasGrowsWithLevelAndFavorsHigherLevels() {
    assertThat(TamingStableHand.bias(0.0, 0.1, 0.2, 0.3)).isCloseTo(0.1, offset(1.0E-9));
    assertThat(TamingStableHand.bias(0.5, 0.1, 0.2, 0.3)).isCloseTo(0.2, offset(1.0E-9));
    assertThat(TamingStableHand.bias(1.0, 0.1, 0.2, 0.3)).isCloseTo(0.3, offset(1.0E-9));
  }

  @Test
  void biasIsClampedToTheConfiguredMaximum() {
    assertThat(TamingStableHand.bias(1.0, 0.2, 0.5, 0.4)).isCloseTo(0.4, offset(1.0E-9));
  }

  @Test
  void fallBonusScalesTenBlocksPerFullBias() {
    assertThat(TamingStableHand.fallBonusBlocks(0.1)).isCloseTo(1.0, offset(1.0E-9));
    assertThat(TamingStableHand.fallBonusBlocks(0.2)).isCloseTo(2.0, offset(1.0E-9));
    assertThat(TamingStableHand.fallBonusBlocks(0.3)).isCloseTo(3.0, offset(1.0E-9));
  }

  @Test
  void successfulShapingUsesThreeAscendingDings() {
    assertThat(TamingStableHand.confirmationDings())
        .extracting(TamingStableHand.ConfirmationDing::delayTicks)
        .containsExactly(0, 3, 6);
    assertThat(TamingStableHand.confirmationDings())
        .extracting(TamingStableHand.ConfirmationDing::pitch)
        .containsExactly(1.2F, 1.5F, 1.8F);
  }

  @Test
  void stableHandStateRecognizesAnyAppliedAttributeModifier() {
    IAttribute speed = mock(IAttribute.class);
    when(speed.hasModifier(any(), any())).thenReturn(true);

    assertThat(TamingStableHand.hasAppliedBias(speed, null, null)).isTrue();
    assertThat(TamingStableHand.hasAppliedBias(null, null, null)).isFalse();
  }
}
