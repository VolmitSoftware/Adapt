package art.arcane.adapt.api.world;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptPlayerScheduleTest {
  @Test
  void playerWorkIsSpreadAcrossTheConfiguredInterval() {
    Set<Long> updateDelays = new HashSet<>();
    Set<Long> saveDelays = new HashSet<>();
    for (int i = 0; i < 1_000; i++) {
      UUID playerId = new UUID(i * 31L, i * 67L + 1L);
      updateDelays.add(AdaptPlayer.staggerDelay(playerId, 1_000L, 0x5DEECE66DL));
      saveDelays.add(AdaptPlayer.staggerDelay(playerId, 60_000L, 0x9E3779B97F4A7C15L));
    }

    assertThat(updateDelays).allMatch(delay -> delay >= 0L && delay < 1_000L);
    assertThat(saveDelays).allMatch(delay -> delay >= 0L && delay < 60_000L);
    assertThat(updateDelays.size()).isGreaterThan(500);
    assertThat(saveDelays.size()).isGreaterThan(900);
  }
}
