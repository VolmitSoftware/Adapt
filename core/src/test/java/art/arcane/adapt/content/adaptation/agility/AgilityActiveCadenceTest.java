package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class AgilityActiveCadenceTest {
  @Test
  void firstEventAdvancesOneGameplayTick() {
    assertThat(AgilityArmorUp.elapsedTicks(0L, 1000L)).isEqualTo(1D);
    assertThat(AgilityWindUp.elapsedTicks(0L, 1000L)).isEqualTo(1D);
  }

  @Test
  void repeatedEventsInOneInstantDoNotDoubleAdvance() {
    assertThat(AgilityArmorUp.elapsedTicks(1000L, 1000L)).isZero();
    assertThat(AgilityWindUp.elapsedTicks(1000L, 1000L)).isZero();
  }

  @Test
  void delayedUpdatesPreserveElapsedProgressWithACap() {
    assertThat(AgilityArmorUp.elapsedTicks(1000L, 1100L)).isEqualTo(2D);
    assertThat(AgilityWindUp.elapsedTicks(1000L, 5000L)).isEqualTo(20D);
  }

  @Test
  void smoothingComposesAcrossElapsedTicks() {
    assertThat(AgilityWindUp.elapsedSmoothing(0.5D, 2D)).isCloseTo(0.75F, offset(0.0001F));
  }
}
