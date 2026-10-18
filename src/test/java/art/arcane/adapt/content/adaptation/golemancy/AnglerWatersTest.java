package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeKey;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AnglerWatersTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000027c1");
  private static final NodeKey KEY = new NodeKey(WORLD, 0, 64, 0, BlockFace.NORTH);
  private static final long NOW = 100_000L;
  private static final long FORGET = 60_000L;

  private final AnglerWaters waters = new AnglerWaters();
  private final AtomicInteger checks = new AtomicInteger();
  private boolean open = true;
  private final Supplier<AnglerWater.Survey> check = () -> {
    checks.incrementAndGet();
    return new AnglerWater.Survey(open, 0, 64, -3, 64, false);
  };

  @Test
  void theWaterIsCheckedAgainOnlyOnceTheRecheckIntervalPasses() {
    assertThat(waters.survey(KEY, NOW, check).open()).isTrue();
    open = false;

    assertThat(waters.survey(KEY, NOW + AnglerWaters.RECHECK_MILLIS - 1L, check).open()).isTrue();
    assertThat(checks).hasValue(1);
    assertThat(waters.survey(KEY, NOW + AnglerWaters.RECHECK_MILLIS, check).open()).isFalse();
    assertThat(checks).hasValue(2);
    assertThat(waters.get(KEY)).hasValueSatisfying(survey -> assertThat(survey.open()).isFalse());
  }

  @Test
  void nodesThatStopScanningAreForgotten() {
    waters.survey(KEY, NOW, check);
    waters.survey(KEY, NOW + 5_000L, check);

    waters.sweep(NOW + 5_000L + FORGET - 1L, FORGET);
    assertThat(waters.get(KEY)).isPresent();
    assertThat(waters.pending()).isTrue();
    assertThat(checks).hasValue(1);

    waters.sweep(NOW + 5_000L + FORGET, FORGET);
    assertThat(waters.get(KEY)).isEmpty();
    assertThat(waters.pending()).isFalse();
  }

  @Test
  void nodesAreForgottenAfterAMinuteOrThreeScanIntervalsWhicheverIsLonger() {
    assertThat(AnglerWaters.forgetMillis(40)).isEqualTo(60_000L);
    assertThat(AnglerWaters.forgetMillis(400)).isEqualTo(60_000L);
    assertThat(AnglerWaters.forgetMillis(600)).isEqualTo(90_000L);
    assertThat(AnglerWaters.forgetMillis(1200)).isEqualTo(180_000L);
  }

  @Test
  void forgettingClearsEveryNode() {
    waters.survey(KEY, NOW, check);

    waters.forget();

    assertThat(waters.get(KEY)).isEmpty();
  }
}
