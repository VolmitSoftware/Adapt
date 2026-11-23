package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpEarnings;
import art.arcane.adapt.platform.event.world.NightSkipEvent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillChronosSleepRuntimeTest {
  @TempDir
  Path data;

  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Sleeper", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void sleepingThroughConsecutiveNightsGrowsAPersistedStreak() {
    skipNight(1L);
    skipNight(2L);
    skipNight(3L);

    Map<String, Object> storage = profile.getData().getSkillLine("chronos").getStorage();
    assertThat(((Number) storage.get("sleep-streak")).longValue()).isEqualTo(3L);
    assertThat(sleepSource().awards()).isEqualTo(3L);
    assertThat(sleepSource().requested()).isEqualTo(150D + 165D + 180D);
  }

  @Test
  void theSameNightPaysOnceAndAMissedNightResetsTheStreak() {
    skipNight(1L);
    skipNight(1L);
    skipNight(3L);

    Map<String, Object> storage = profile.getData().getSkillLine("chronos").getStorage();
    assertThat(((Number) storage.get("sleep-streak")).longValue()).isEqualTo(1L);
    assertThat(sleepSource().awards()).isEqualTo(2L);
    assertThat(sleepSource().requested()).isEqualTo(300D);
  }

  @Test
  void playersNotAsleepAtTheSkipEarnNothing() {
    world.fullTime(18000L);
    adapt.post(new NightSkipEvent(world, List.of()));

    assertThat(profile.getData().getXpEarnings().snapshot())
        .noneMatch(snapshot -> snapshot.source().equals("chronos/chronos:sleep"));
  }

  private void skipNight(long day) {
    world.fullTime((day - 1L) * 24000L + 18000L);
    adapt.post(new NightSkipEvent(world, List.of(player)));
  }

  private XpEarnings.SourceSnapshot sleepSource() {
    return profile.getData().getXpEarnings().snapshot().stream()
        .filter(snapshot -> snapshot.source().equals("chronos/chronos:sleep"))
        .findFirst()
        .orElseThrow();
  }
}
