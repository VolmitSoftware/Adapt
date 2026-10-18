package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class GolemDeathDropsTest {
  private static final List<GolemCoreType> CORES = List.of(GolemCoreType.GUARDIAN, GolemCoreType.GATHERER);
  private static final List<GolemRune> RUNES = List.of(new GolemRune(GolemRuneType.MIGHT, 2), new GolemRune(GolemRuneType.TIDE, 1));

  @Test
  void eachItemRollsAgainstItsOwnChance() {
    GolemDeathDrops.Roll roll = GolemDeathDrops.roll(CORES, RUNES, 0.5D, 0.5D, sequence(0.49D, 0.5D, 0.0D, 0.99D));

    assertThat(roll.cores()).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(roll.runes()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 2));
  }

  @Test
  void zeroChanceNeverDropsAndFullChanceAlwaysDrops() {
    GolemDeathDrops.Roll never = GolemDeathDrops.roll(CORES, RUNES, 0D, 0D, () -> 0D);
    GolemDeathDrops.Roll always = GolemDeathDrops.roll(CORES, RUNES, 1D, 1D, () -> 0.999999D);

    assertThat(never.cores()).isEmpty();
    assertThat(never.runes()).isEmpty();
    assertThat(always.cores()).containsExactlyElementsOf(CORES);
    assertThat(always.runes()).containsExactlyElementsOf(RUNES);
  }

  @Test
  void coresAndRunesUseTheirOwnChances() {
    GolemDeathDrops.Roll roll = GolemDeathDrops.roll(CORES, RUNES, 1D, 0D, () -> 0.3D);

    assertThat(roll.cores()).containsExactlyElementsOf(CORES);
    assertThat(roll.runes()).isEmpty();
  }

  @Test
  void emptyLoadoutsRollNothing() {
    GolemDeathDrops.Roll roll = GolemDeathDrops.roll(List.of(), List.of(), 1D, 1D, () -> 0D);

    assertThat(roll.cores()).isEmpty();
    assertThat(roll.runes()).isEmpty();
  }

  private static DoubleSupplier sequence(double... values) {
    Iterator<Double> iterator = Arrays.stream(values).boxed().iterator();
    return iterator::next;
  }
}
