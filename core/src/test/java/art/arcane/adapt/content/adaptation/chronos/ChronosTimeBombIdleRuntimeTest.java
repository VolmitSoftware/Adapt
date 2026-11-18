package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ChronosTimeBombIdleRuntimeTest {
  @TempDir
  Path dataDirectory;

  private TestAdapt adapt;

  @BeforeEach
  void setUp() {
    adapt = TestAdapt.start(TestPlatform.builder(dataDirectory).build(), List.of());
  }

  @AfterEach
  void tearDown() {
    adapt.close();
  }

  @Test
  void emptyRuntimeParksAndActivationWakesItRaceSafely() throws Exception {
    ChronosTimeBomb adaptation = new ChronosTimeBomb();

    assertThat(adaptation.getInterval()).isEqualTo(Long.MAX_VALUE);

    adaptation.onRuntimeActivated();

    assertThat(adaptation.isBursting()).isTrue();
    assertThat(adaptation.getInterval()).isZero();

    adaptation.setInterval(Long.MAX_VALUE);
    assertThat(adaptation.getInterval()).isZero();

    adaptation.stopBursting();
    startupSweepDone(adaptation).set(true);
    adaptation.onTick();

    assertThat(adaptation.getInterval()).isEqualTo(Long.MAX_VALUE);
  }

  private static AtomicBoolean startupSweepDone(ChronosTimeBomb adaptation) throws Exception {
    Field field = ChronosTimeBomb.class.getDeclaredField("startupSweepDone");
    field.setAccessible(true);
    return (AtomicBoolean) field.get(adaptation);
  }
}
