package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.player.LearnOptions;
import art.arcane.adapt.api.player.LearnResult;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveLevelTest {
  static final Id LIFT = Id.of("lift", "lift");
  static final Id BOOSTER = Id.of("lift", "lift/booster");
  static final Id LIFTED = Id.of("lift", "lift/lifted");

  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private Lifted lifted;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Lifter", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, new LiftAddon());
    adapt.join(player);
    lifted = (Lifted) AdaptApi.content().adaptation(LIFTED).orElseThrow();
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void aHeldBoosterRaisesTheActiveLevelOfAnUnlearnedAdaptation() {
    assertThat(lifted.getActiveLevel(player)).isZero();
    assertThat(lifted.hasActiveAdaptation(player)).isFalse();

    learn(BOOSTER, 1);

    assertThat(lifted.getLevel(player)).isZero();
    assertThat(lifted.getActiveLevel(player)).isEqualTo(2);
    assertThat(lifted.hasActiveAdaptation(player)).isTrue();
    assertThat(lifted.getActiveDeathLevel(player)).isEqualTo(2);
  }

  @Test
  void theRaisedLevelStaysWithinTheMaxLevelAndTheQuerysTerms() {
    learn(BOOSTER, 3);

    assertThat(lifted.getActiveLevel(player)).isEqualTo(lifted.getMaxLevel());
    assertThat(lifted.getActiveLevel(player, (PlayerRef candidate) -> false)).isZero();
    assertThat(lifted.getActiveBlockBreakLevel(player, player.location())).isZero();
  }

  @Test
  void moveHandlersReachAPlayerWhoseLevelIsOnlyRaised() {
    adapt.post(new PlayerMoveEvent(player, player.location(), player.location().add(1D, 0D, 0D)));
    assertThat(lifted.moves.get()).isZero();

    learn(BOOSTER, 1);
    adapt.post(new PlayerMoveEvent(player, player.location(), player.location().add(1D, 0D, 0D)));

    assertThat(lifted.moves.get()).isEqualTo(1);
  }

  private void learn(Id adaptation, int level) {
    PlayerProfile profile = AdaptApi.players().require(player);
    assertThat(profile.learn(adaptation, level, LearnOptions.forced().quietly()).status())
        .isEqualTo(LearnResult.Status.LEARNED);
  }

  static final class LiftAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "lift";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(LIFT, LiftSkill::new);
    }
  }

  public static final class LiftSkill extends SimpleSkill<LiftSkill.Config> {
    public LiftSkill() {
      super(LIFT);
      registerConfiguration(Config.class);
      setColor(Rgb.of(0x3F8F5A));
      setIcon(Id.minecraft("feather"));
      registerAdaptation(BOOSTER, Booster::new);
      registerAdaptation(LIFTED, Lifted::new);
    }

    public static final class Config extends SkillConfig {
    }
  }

  public static final class Booster extends SimpleAdaptation<AdaptationConfig> {
    public Booster() {
      super(BOOSTER);
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }

  /** Runs at twice the booster's level for a holder, the way Slipstream runs Featherfoot. */
  public static final class Lifted extends SimpleAdaptation<AdaptationConfig> {
    private final AtomicInteger moves = new AtomicInteger();

    public Lifted() {
      super(LIFTED);
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    protected int effectiveLevel(PlayerRef player, int activeLevel) {
      for (Adaptation<?> sibling : getSkill().getAdaptations()) {
        if (sibling instanceof Booster booster) {
          return Math.max(activeLevel, booster.getActiveLevel(player) * 2);
        }
      }
      return activeLevel;
    }

    @Handle
    public void on(PlayerMoveEvent event) {
      if (getActiveLevel(event.player()) > 0) {
        moves.incrementAndGet();
      }
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
