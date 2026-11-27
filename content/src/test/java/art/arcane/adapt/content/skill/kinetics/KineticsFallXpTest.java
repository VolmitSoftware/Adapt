package art.arcane.adapt.content.skill.kinetics;

import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KineticsFallXpTest {
  private static final FakeWorld WORLD = new FakeWorld("world");

  @Test
  void fallXpPaysPastTheFreeBlocksAndCaps() {
    assertThat(KineticsMotion.fallXp(3D, 3D, 1.5D, 40D)).isZero();
    assertThat(KineticsMotion.fallXp(13D, 3D, 1.5D, 40D)).isEqualTo(15D);
    assertThat(KineticsMotion.fallXp(100D, 3D, 1.5D, 40D)).isEqualTo(40D);
    assertThat(KineticsMotion.fallXp(Double.NaN, 3D, 1.5D, 40D)).isZero();
  }

  @Test
  void landingsNearARecentPaidLandingAreRecognised() {
    List<Loc> recent = List.of(new Loc(WORLD, 0D, 64D, 0D), new Loc(WORLD, 100D, 64D, 0D));
    assertThat(KineticsMotion.nearAnyLanding(recent, new Loc(WORLD, 5D, 64D, 5D), 8D)).isTrue();
    assertThat(KineticsMotion.nearAnyLanding(recent, new Loc(WORLD, 50D, 64D, 0D), 8D)).isFalse();
    assertThat(KineticsMotion.nearAnyLanding(List.of(), new Loc(WORLD, 0D, 64D, 0D), 8D)).isFalse();
  }
}
