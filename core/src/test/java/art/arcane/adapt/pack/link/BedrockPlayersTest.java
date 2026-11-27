package art.arcane.adapt.pack.link;

import org.geysermc.floodgate.api.FloodgateApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class BedrockPlayersTest {
  private final UUID java = UUID.fromString("5f1e8d6a-3c2b-4a7e-9d10-2b3c4d5e6f70");
  private final UUID linkedBedrock = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-8e9f0a1b2c3d");

  @AfterEach
  void clear() {
    FloodgateApi.BEDROCK.clear();
  }

  @Test
  void floodgateIdsAreBedrockWithoutAnyApi() {
    BedrockPlayers players = new BedrockPlayers(List::of);

    assertThat(players.test(new UUID(0L, 42L))).isTrue();
    assertThat(players.test(java)).isFalse();
    assertThat(players.test(null)).isFalse();
  }

  @Test
  void floodgateApiIsFoundByReflection() {
    FloodgateApi.BEDROCK.add(linkedBedrock);
    BedrockPlayers players = new BedrockPlayers(() -> List.of(getClass().getClassLoader()));

    assertThat(players.test(linkedBedrock)).isTrue();
    assertThat(players.test(java)).isFalse();
  }

  @Test
  void aMissingApiIsProbedAgainOnlyAfterTheRetryDelay() {
    AtomicLong clock = new AtomicLong();
    List<ClassLoader> loaders = new ArrayList<>();
    BedrockPlayers players = new BedrockPlayers(() -> List.copyOf(loaders), clock::get);
    FloodgateApi.BEDROCK.add(linkedBedrock);

    assertThat(players.test(linkedBedrock)).isFalse();
    loaders.add(getClass().getClassLoader());
    assertThat(players.test(linkedBedrock)).isFalse();

    clock.addAndGet(5_000L);
    assertThat(players.test(linkedBedrock)).isTrue();
  }
}
