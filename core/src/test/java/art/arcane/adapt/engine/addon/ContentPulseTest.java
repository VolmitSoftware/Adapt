package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.player.LearnOptions;
import art.arcane.adapt.api.player.LearnResult;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Addon content pulses its players through the public {@code pulseLearners} and {@code pulseOwners}. */
class ContentPulseTest {
  static final Id TIDE = Id.of("tide", "tide");
  static final Id SWELL = Id.of("tide", "tide/swell");
  static final long CADENCE = 100L;
  static final List<Long> LEARNER_PULSES = new CopyOnWriteArrayList<>();
  static final List<UUID> OWNER_PULSES = new CopyOnWriteArrayList<>();

  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;

  @BeforeEach
  void start() {
    LEARNER_PULSES.clear();
    OWNER_PULSES.clear();
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Swimmer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, new TideAddon());
    adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aSkillPulsesEveryLoadedPlayerWithBoundedElapsedTime() {
    adapt.awaitTicks(() -> LEARNER_PULSES.size() >= 3, 10_000L);

    assertThat(LEARNER_PULSES).allSatisfy(elapsed ->
        assertThat(elapsed).isBetween(0L, CADENCE * 4L));
  }

  @Test
  void anAdaptationPulsesOnlyPlayersWhoLearnedIt() {
    long due = System.currentTimeMillis() + CADENCE * 4L;
    adapt.awaitTicks(() -> System.currentTimeMillis() >= due, 5_000L);
    assertThat(OWNER_PULSES).isEmpty();

    assertThat(AdaptApi.players().require(player).learn(SWELL, 1, LearnOptions.forced().quietly()).status())
        .isEqualTo(LearnResult.Status.LEARNED);
    adapt.awaitTicks(() -> OWNER_PULSES.size() >= 2, 10_000L);

    assertThat(OWNER_PULSES).containsOnly(player.uuid());
  }

  @Test
  void uninstallingTheAddonStopsBothPulses() {
    assertThat(AdaptApi.players().require(player).learn(SWELL, 1, LearnOptions.forced().quietly()).status())
        .isEqualTo(LearnResult.Status.LEARNED);
    adapt.awaitTicks(() -> !LEARNER_PULSES.isEmpty() && !OWNER_PULSES.isEmpty(), 10_000L);

    AddonHandle handle = adapt.addons().get(0);
    handle.uninstall();
    int learnerPulses = LEARNER_PULSES.size();
    int ownerPulses = OWNER_PULSES.size();
    long due = System.currentTimeMillis() + CADENCE * 5L;
    adapt.awaitTicks(() -> System.currentTimeMillis() >= due, 5_000L);

    assertThat(LEARNER_PULSES).hasSize(learnerPulses);
    assertThat(OWNER_PULSES).hasSize(ownerPulses);
  }

  static final class TideAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "tide";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(TIDE, Tide::new);
    }
  }

  public static final class Tide extends SimpleSkill<Tide.Config> {
    public Tide() {
      super(TIDE);
      registerConfiguration(Config.class);
      setColor(Rgb.of(0x2F6F9F));
      setIcon(Id.minecraft("kelp"));
      registerAdaptation(SWELL, Swell::new);
      pulseLearners(() -> CADENCE, (PlayerRef pulsed, long elapsedMillis) -> LEARNER_PULSES.add(elapsedMillis));
    }

    public static final class Config extends SkillConfig {
    }
  }

  public static final class Swell extends SimpleAdaptation<AdaptationConfig> {
    public Swell() {
      super(SWELL);
      registerConfiguration(AdaptationConfig.class);
      pulseOwners(() -> CADENCE, (PlayerRef pulsed) -> OWNER_PULSES.add(pulsed.uuid()));
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
