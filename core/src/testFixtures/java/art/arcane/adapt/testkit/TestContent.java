package art.arcane.adapt.testkit;

import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.engine.addon.BuildScope;
import art.arcane.adapt.engine.bridge.ComponentBuilder;
import art.arcane.adapt.platform.value.Id;

import java.util.function.Supplier;

/** Builds built-in content the way the engine does, for tests that need an instance outside the registry. */
public final class TestContent {
  private TestContent() {
  }

  /** An adaptation of {@code skill}, built as the built-in addon's; a core must be running. */
  public static <A extends SimpleAdaptation<?>> A adaptation(Id skill, Supplier<A> factory) {
    return BuildScope.run(Id.ADAPT, () -> ComponentBuilder.buildAdaptation(skill, factory));
  }

  /** A skill with its adaptations and mutation pieces, built as the built-in addon's; a core must be running. */
  public static <S extends SimpleSkill<?>> S skill(Supplier<S> factory) {
    return BuildScope.run(Id.ADAPT, () -> ComponentBuilder.buildSkill(factory));
  }
}
