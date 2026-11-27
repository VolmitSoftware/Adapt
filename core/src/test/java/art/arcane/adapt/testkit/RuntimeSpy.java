package art.arcane.adapt.testkit;

import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.component.ComponentHost;

import java.lang.reflect.Field;

import static org.mockito.Mockito.spy;

/**
 * Swaps the runtime an adaptation calls into for a Mockito spy of its real host, so a test can stub or verify the
 * engine side (effects, stats, levels) of one instance without subclassing the content.
 */
public final class RuntimeSpy {
  private RuntimeSpy() {
  }

  /**
   * Installs a spy of {@code original}'s host as the runtime of {@code target}, which is {@code original} itself or a
   * Mockito spy of it, and returns the spy for stubbing.
   */
  public static AdaptationHost install(SimpleAdaptation<?> original, SimpleAdaptation<?> target) {
    AdaptationHost host = spy((AdaptationHost) ComponentHost.of(original));
    try {
      Field field = SimpleAdaptation.class.getDeclaredField("runtime");
      field.setAccessible(true);
      field.set(target, host);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot replace the runtime of " + original.getId(), failure);
    }
    return host;
  }
}
