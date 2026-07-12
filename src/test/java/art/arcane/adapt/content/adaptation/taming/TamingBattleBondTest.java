package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TamingBattleBondTest {
  @Test
  void buffDurationGrowsWithLevel() {
    assertThat(TamingBattleBond.buffTicks(0.0, 80, 120)).isEqualTo(80);
    assertThat(TamingBattleBond.buffTicks(1.0, 80, 120)).isEqualTo(200);
  }

  @Test
  void buffDurationNeverDropsBelowTheFloor() {
    assertThat(TamingBattleBond.buffTicks(0.0, 5, 0)).isEqualTo(20);
  }
}
