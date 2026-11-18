package art.arcane.adapt.api.adaptation;

import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class CooldownsTest {
  @Test
  void readyChecksEvictExpiredEntries() {
    Map<UUID, Long> uses = new ConcurrentHashMap<>();
    UUID id = UUID.randomUUID();
    uses.put(id, M.ms() - 2_000L);
    Cooldowns cooldowns = new Cooldowns(uses);

    assertThat(cooldowns.isReady(id, 1_000L)).isTrue();
    assertThat(uses).doesNotContainKey(id);
  }

  @Test
  void sweepsExpiredEntriesWithoutRemovingActiveCooldowns() {
    Map<UUID, Long> uses = new ConcurrentHashMap<>();
    UUID expired = UUID.randomUUID();
    UUID active = UUID.randomUUID();
    long now = M.ms();
    uses.put(expired, now - 2_000L);
    uses.put(active, now);
    Cooldowns cooldowns = new Cooldowns(uses);

    cooldowns.clearExpired(1_000L);

    assertThat(uses).doesNotContainKey(expired);
    assertThat(uses).containsKey(active);
  }
}
