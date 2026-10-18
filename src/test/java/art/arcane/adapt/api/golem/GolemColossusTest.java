package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GolemColossusTest {
  @Test
  void onlyTheRegisteredFormCanUnregisterItself() {
    GolemColossus.Form first = mock(GolemColossus.Form.class);
    GolemColossus.Form second = mock(GolemColossus.Form.class);
    try {
      GolemColossus.register(first);
      GolemColossus.register(second);
      GolemColossus.unregister(first);

      assertThat(GolemColossus.form()).contains(second);

      GolemColossus.unregister(second);
      assertThat(GolemColossus.form()).isEmpty();
    } finally {
      GolemColossus.unregister(first);
      GolemColossus.unregister(second);
    }
  }
}
