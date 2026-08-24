package art.arcane.adapt.api.tick;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import org.bukkit.event.EventHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
  void activeIntervalChangesRescheduleTheExistingRegistration() {
    ActiveTicked active = new ActiveTicked();
    active.activateRuntime();
    reset(ticker);

    active.setInterval(250L);

    verify(ticker).reschedule(active);
  }

  @Test
  void burstingReschedulesAnActiveObjectImmediately() {
    ActiveTicked active = new ActiveTicked();
    active.activateRuntime();
    reset(ticker);

    active.retick();

    verify(ticker).reschedule(active);
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
  void tickerInvalidationRejectsAlreadyDispatchedOwnerTicks() {
    ActiveTicked active = new ActiveTicked();
    active.activateRuntime();

    active.invalidateTickDispatch();
    active.tick();

    assertThat(active.ticks.get()).isZero();
    assertThat(active.isRuntimeRegistered()).isTrue();
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

  @Test
  void constructionSurvivesUnresolvableEventSignatures() throws Exception {
    Class<?> type = Class.forName(
        MissingSignatureTicked.class.getName(), false, new MissingEventTypeHidingLoader());

    TickedObject ticked = (TickedObject) type.getConstructor().newInstance();
    ticked.activateRuntime();

    verify(ticker).register(ticked);
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

  /**
   * Stand-in for a platform-only event class (e.g. Paper's PlayerJumpEvent on
   * Spigot). {@link MissingEventTypeHidingLoader} refuses to load it so method
   * signature resolution fails with NoClassDefFoundError.
   */
  public static final class MissingEventType {
  }

  public static final class MissingSignatureTicked extends TickedObject {
    public MissingSignatureTicked() {
      super("missing-signature-test");
    }

    @EventHandler
    public void on(MissingEventType event) {
    }
  }

  /**
   * Re-defines {@link MissingSignatureTicked} in an isolated loader that
   * throws for {@link MissingEventType}, mimicking Spigot missing a Paper-only
   * event class referenced by a handler signature.
   */
  private static final class MissingEventTypeHidingLoader extends ClassLoader {
    private static final String HIDDEN = MissingEventType.class.getName();
    private static final String RELOADED = MissingSignatureTicked.class.getName();

    private MissingEventTypeHidingLoader() {
      super(TickedObjectTest.class.getClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (HIDDEN.equals(name)) {
        throw new ClassNotFoundException(name);
      }
      if (RELOADED.equals(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
          byte[] bytes = readClassBytes(name);
          loaded = defineClass(name, bytes, 0, bytes.length);
        }
        if (resolve) {
          resolveClass(loaded);
        }
        return loaded;
      }
      return super.loadClass(name, resolve);
    }

    private byte[] readClassBytes(String name) throws ClassNotFoundException {
      String resource = name.replace('.', '/') + ".class";
      try (InputStream in = getParent().getResourceAsStream(resource)) {
        if (in == null) {
          throw new ClassNotFoundException(name);
        }
        return in.readAllBytes();
      } catch (IOException e) {
        throw new ClassNotFoundException(name, e);
      }
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
