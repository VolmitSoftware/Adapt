package art.arcane.adapt.content.skill.golemancy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class RollingXpCapTest {
  private static final long MINUTE = 60_000L;

  @Test
  void grantsUntilTheCapThenClipsTheRemainder() {
    RollingXpCap cap = new RollingXpCap();
    UUID player = UUID.randomUUID();

    assertThat(cap.claim(player, 250D, 600D, 0L)).isCloseTo(250D, offset(1e-9));
    assertThat(cap.claim(player, 250D, 600D, MINUTE)).isCloseTo(250D, offset(1e-9));
    assertThat(cap.claim(player, 250D, 600D, 2 * MINUTE)).isCloseTo(100D, offset(1e-9));
    assertThat(cap.claim(player, 50D, 600D, 3 * MINUTE)).isZero();
  }

  @Test
  void grantsRollOffSixtyMinutesAfterTheyWereEarned() {
    RollingXpCap cap = new RollingXpCap();
    UUID player = UUID.randomUUID();
    cap.claim(player, 400D, 600D, 0L);
    cap.claim(player, 200D, 600D, 30 * MINUTE);

    assertThat(cap.claim(player, 10D, 600D, 60 * MINUTE - 1)).isZero();
    assertThat(cap.claim(player, 500D, 600D, 60 * MINUTE)).isCloseTo(400D, offset(1e-9));
    assertThat(cap.claim(player, 10D, 600D, 90 * MINUTE - 1)).isZero();
    assertThat(cap.claim(player, 10D, 600D, 90 * MINUTE)).isCloseTo(10D, offset(1e-9));
  }

  @Test
  void playersHaveSeparateCaps() {
    RollingXpCap cap = new RollingXpCap();
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    cap.claim(alice, 600D, 600D, 0L);

    assertThat(cap.claim(alice, 1D, 600D, MINUTE)).isZero();
    assertThat(cap.claim(bob, 600D, 600D, MINUTE)).isCloseTo(600D, offset(1e-9));
  }

  @Test
  void rejectsInvalidRequestsAndCaps() {
    RollingXpCap cap = new RollingXpCap();
    UUID player = UUID.randomUUID();

    assertThat(cap.claim(player, 0D, 600D, 0L)).isZero();
    assertThat(cap.claim(player, -5D, 600D, 0L)).isZero();
    assertThat(cap.claim(player, Double.NaN, 600D, 0L)).isZero();
    assertThat(cap.claim(player, Double.POSITIVE_INFINITY, 600D, 0L)).isZero();
    assertThat(cap.claim(player, 10D, 0D, 0L)).isZero();
    assertThat(cap.claim(player, 10D, Double.NaN, 0L)).isZero();
    assertThat(cap.claim(player, 600D, 600D, 0L)).isCloseTo(600D, offset(1e-9));
  }

  @Test
  void sweepKeepsGrantsInsideTheWindow() {
    RollingXpCap cap = new RollingXpCap();
    UUID player = UUID.randomUUID();
    cap.claim(player, 600D, 600D, 0L);

    cap.sweep(30 * MINUTE);

    assertThat(cap.claim(player, 10D, 600D, 30 * MINUTE)).isZero();
  }
}
