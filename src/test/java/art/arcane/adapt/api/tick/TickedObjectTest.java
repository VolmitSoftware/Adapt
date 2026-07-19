package art.arcane.adapt.api.tick;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class TickedObjectTest extends AdaptTestBase {
  @Test
  void objectsWithInheritedNoOpTicksAreNotRegistered() {
    NoOpTicked noOp = new NoOpTicked();
    noOp.activateRuntime();

    verify(ticker, never()).register(noOp);
  }

  @Test
  void objectsWithCustomTicksAreRegistered() {
    ActiveTicked active = new ActiveTicked();
    active.activateRuntime();

    verify(ticker).register(active);
  }

  @Test
  void intervalsCannotRunFasterThanOneServerTick() {
    ActiveTicked active = new ActiveTicked();

    active.setInterval(5L);

    assertThat(active.getInterval()).isEqualTo(TickedObject.MIN_INTERVAL_MILLIS);
  }

  @Test
  void unregisteredObjectsRejectLateTicks() {
    ActiveTicked active = new ActiveTicked();
    active.activateRuntime();

    active.unregister();
    active.tick();

    assertThat(active.ticks.get()).isZero();
    assertThat(active.isRuntimeRegistered()).isFalse();
  }

  @Test
  void constructionDoesNotPublishTheObjectBeforeExplicitActivation() {
    ActiveTicked active = new ActiveTicked();

    assertThat(active.isRuntimeRegistered()).isFalse();
    verify(ticker, never()).register(active);
  }

  @Test
  void adaptationTicksRecordCompleteExecution() {
    MeasuredAdaptationTicked active = mock(
        MeasuredAdaptationTicked.class,
        withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS)
    );
    TickedObject.runMeasuredOnTick(active);

    AbilityCheckTelemetry.AbilitySnapshot snapshot = AbilityCheckTelemetry
        .abilitySnapshots(System.currentTimeMillis())
        .get("measured-tick");
    assertThat(snapshot.executionOps()).isEqualTo(1L);
    assertThat(snapshot.executionTimingMillis()).isGreaterThan(0D);
    AbilityCheckTelemetry.clear();
  }

  private static final class NoOpTicked extends TickedObject {
  }

  private static final class ActiveTicked extends TickedObject {
    private final AtomicInteger ticks = new AtomicInteger();

    @Override
    public void onTick() {
      ticks.incrementAndGet();
    }
  }

  public abstract static class MeasuredAdaptationTicked extends TickedObject implements Adaptation<Object> {
    public MeasuredAdaptationTicked() {
      super();
    }

    @Override
    public String getName() {
      return "measured-tick";
    }

    @Override
    public void onTick() {
    }
  }
}
