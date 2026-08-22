package art.arcane.adapt.content.adaptation.axe;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AxeBlockBreakSwingGuardTest {
  @Test
  void freshBreakSwingIsSuppressedExactlyOnce() {
    AxeBlockBreakSwingGuard guard = new AxeBlockBreakSwingGuard(1L);
    UUID playerId = UUID.randomUUID();

    guard.mark(playerId, 1_000L);

    assertThat(guard.consume(playerId, 1_001L)).isTrue();
    assertThat(guard.consume(playerId, 1_001L)).isFalse();
  }

  @Test
  void expiredOrOutOfOrderBreakDoesNotSuppressAirClicks() {
    AxeBlockBreakSwingGuard guard = new AxeBlockBreakSwingGuard(1L);
    UUID playerId = UUID.randomUUID();

    guard.mark(playerId, 1_000L);
    assertThat(guard.consume(playerId, 1_002L)).isFalse();

    guard.mark(playerId, 1_000L);
    assertThat(guard.consume(playerId, 999L)).isFalse();
  }

  @Test
  void breakSwingStateIsIsolatedPerPlayerAndClearable() {
    AxeBlockBreakSwingGuard guard = new AxeBlockBreakSwingGuard(1L);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    guard.mark(first, 1_000L);
    guard.mark(second, 1_000L);
    guard.clear(first);

    assertThat(guard.consume(first, 1_001L)).isFalse();
    assertThat(guard.consume(second, 1_001L)).isTrue();
  }
}
