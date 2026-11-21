package art.arcane.adapt.client.server.hud;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XpEarnCoalescerTest {
  private final XpEarnCoalescer coalescer = new XpEarnCoalescer();
  private final List<String> drained = new ArrayList<>();

  @Test
  void firstAwardGoesOutAtOnce() {
    assertThat(coalescer.earn("mining", 4D, 1_000L)).isEqualTo(4D);
    assertThat(coalescer.waiting()).isFalse();
  }

  @Test
  void awardsInsideTheIntervalAddUpUntilTheNextDrain() {
    coalescer.earn("mining", 4D, 1_000L);

    assertThat(coalescer.earn("mining", 2D, 1_050L)).isZero();
    assertThat(coalescer.earn("mining", 3D, 1_100L)).isZero();
    assertThat(coalescer.waiting()).isTrue();

    coalescer.drain(1_200L, this::record);
    assertThat(drained).isEmpty();

    coalescer.drain(1_250L, this::record);
    assertThat(drained).containsExactly("mining 5.0");
    assertThat(coalescer.waiting()).isFalse();
  }

  @Test
  void anAwardAfterTheIntervalGoesOutWithWhatWaited() {
    coalescer.earn("mining", 4D, 1_000L);
    coalescer.earn("mining", 2D, 1_100L);

    assertThat(coalescer.earn("mining", 1D, 1_300L)).isEqualTo(3D);
    assertThat(coalescer.waiting()).isFalse();
  }

  @Test
  void skillsAreLimitedSeparately() {
    assertThat(coalescer.earn("mining", 1D, 1_000L)).isEqualTo(1D);
    assertThat(coalescer.earn("hunter", 2D, 1_010L)).isEqualTo(2D);
    assertThat(coalescer.earn("mining", 3D, 1_020L)).isZero();
  }

  @Test
  void aBurstStaysAtFourFramesPerSecond() {
    int sent = 0;
    for (long now = 0L; now < 1_000L; now += 10L) {
      if (coalescer.earn("mining", 1D, now) > 0D) {
        sent++;
      }
    }
    assertThat(sent).isEqualTo(4);
  }

  private void record(String skillId, double amount) {
    drained.add(skillId + " " + amount);
  }
}
