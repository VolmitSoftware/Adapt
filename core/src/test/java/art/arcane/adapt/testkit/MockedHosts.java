package art.arcane.adapt.testkit;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.skill.SkillHost;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Gives mocked skills and adaptations a mocked engine host on the test thread, so engine code that asks
 * {@code SkillHost.of}, {@code AdaptationHost.of} or {@code ComponentHost.of} for one finds it. Close it after the test.
 */
public final class MockedHosts implements AutoCloseable {
  private final MockedStatic<ComponentHost> hosts = mockStatic(ComponentHost.class, CALLS_REAL_METHODS);

  /** A mocked host returned for {@code skill}. */
  public SkillHost skill(Skill<?> skill) {
    return host(skill, SkillHost.class);
  }

  /** A mocked host returned for {@code adaptation}. */
  public AdaptationHost adaptation(Adaptation<?> adaptation) {
    return host(adaptation, AdaptationHost.class);
  }

  private <H extends ComponentHost> H host(ContentComponent component, Class<H> type) {
    H host = mock(type);
    hosts.when(() -> ComponentHost.find(component)).thenReturn(host);
    return host;
  }

  @Override
  public void close() {
    hosts.close();
  }
}
