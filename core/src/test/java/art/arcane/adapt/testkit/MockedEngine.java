package art.arcane.adapt.testkit;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.engine.addon.AddonLifecycle;
import art.arcane.adapt.engine.addon.BuildScope;
import art.arcane.adapt.engine.bridge.ComponentBuilder;
import art.arcane.adapt.engine.bridge.CoreBridge;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import java.util.function.Supplier;

import static org.mockito.Mockito.mock;

/**
 * Runs the engine bridge over a test core that never started, so content can be built and attached to it the way the
 * built-in addon's is. Close it after the test.
 */
public final class MockedEngine implements AutoCloseable {
  private final CoreBridge bridge;

  public MockedEngine(AdaptCore core) {
    bridge = new CoreBridge(core, mock(AddonLifecycle.class));
    CoreBridge.start(bridge);
  }

  /** A built-in skill built with its declared adaptations. */
  public <S extends SimpleSkill<?>> S skill(Supplier<S> factory) {
    return TestContent.skill(factory);
  }

  /** A built-in adaptation built and listed on {@code skill}, as if the skill had declared it. */
  public <A extends SimpleAdaptation<?>> A adaptation(SimpleSkill<?> skill, Supplier<A> factory) {
    SkillHost host = SkillHost.of(skill);
    A adaptation = BuildScope.run(Id.ADAPT, () -> ComponentBuilder.buildFor(host, factory));
    ComponentBuilder.place(host, adaptation);
    return adaptation;
  }

  @Override
  public void close() {
    CoreBridge.stop(bridge);
  }
}
