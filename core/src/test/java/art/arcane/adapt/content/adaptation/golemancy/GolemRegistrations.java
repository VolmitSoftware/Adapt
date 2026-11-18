package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemMend;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemWorkSpeed;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mockStatic;

final class GolemRegistrations<T> implements AutoCloseable {
  private final MockedStatic<?> registry;
  private final Class<T> type;
  private final Consumer<T> register;
  private final Consumer<T> unregister;

  private GolemRegistrations(MockedStatic<?> registry, Class<T> type, Consumer<T> register, Consumer<T> unregister) {
    this.registry = registry;
    this.type = type;
    this.register = register;
    this.unregister = unregister;
  }

  static GolemRegistrations<GolemStatSource> stats() {
    return new GolemRegistrations<>(mockStatic(GolemStats.class, CALLS_REAL_METHODS), GolemStatSource.class, GolemStats::register,
        GolemStats::unregister);
  }

  static GolemRegistrations<GolemCapacity.Source> carry() {
    return new GolemRegistrations<>(mockStatic(GolemCapacity.class, CALLS_REAL_METHODS), GolemCapacity.Source.class, GolemCapacity::register,
        GolemCapacity::unregister);
  }

  static GolemRegistrations<GolemWorkSpeed.Source> workSpeed() {
    return new GolemRegistrations<>(mockStatic(GolemWorkSpeed.class, CALLS_REAL_METHODS), GolemWorkSpeed.Source.class, GolemWorkSpeed::register,
        GolemWorkSpeed::unregister);
  }

  static GolemRegistrations<GolemRunes.Augments> augments() {
    return new GolemRegistrations<>(mockStatic(GolemRunes.class, CALLS_REAL_METHODS), GolemRunes.Augments.class, GolemRunes::register,
        GolemRunes::unregister);
  }

  static GolemRegistrations<GolemMend.Mender> menders() {
    return new GolemRegistrations<>(mockStatic(GolemMend.class, CALLS_REAL_METHODS), GolemMend.Mender.class, GolemMend::register,
        GolemMend::unregister);
  }

  @Override
  public void close() {
    registry.close();
  }

  List<T> registered() {
    return calls(register);
  }

  List<T> unregistered() {
    return calls(unregister);
  }

  private List<T> calls(Consumer<T> method) {
    ArgumentCaptor<T> captor = ArgumentCaptor.forClass(type);
    registry.verify(() -> method.accept(captor.capture()), atLeast(0));
    return List.copyOf(captor.getAllValues());
  }
}
