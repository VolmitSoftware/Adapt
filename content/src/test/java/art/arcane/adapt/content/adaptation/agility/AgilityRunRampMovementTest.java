package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.DoubleAdder;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityRunRampMovementTest {
  private static final AgilityRunRamp.Ramp RAMP = new AgilityRunRamp.Ramp(1D, true, (double progress) -> 0.1D, 1D, 0.015D);

  @TempDir
  Path data;

  private FakePlayer player;
  private TestAdapt adapt;
  private AgilityRunRamp ramp;
  private final DoubleAdder fullSpeedTicks = new DoubleAdder();

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Runner", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillAgility.class));
    adapt.join(player);
    AgilityWindUp windUp = adapt.core().getAdaptServer().getSkillRegistry().getSkill("agility").getAdaptations().stream()
        .filter(AgilityWindUp.class::isInstance).map(AgilityWindUp.class::cast).findFirst().orElseThrow();
    ramp = new AgilityRunRamp(windUp, new HashMap<>(), (PlayerRef runner, double ticks) -> fullSpeedTicks.add(ticks));
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void aRunAtFullSpeedCountsFromThePlayersMovement() {
    player.setVelocity(Vec3.ZERO);
    player.setMovement(new Vec3(0.28D, 0D, 0D));

    ramp.update(player, RAMP, 1_000L);
    ramp.update(player, RAMP, 2_000L);

    assertThat(fullSpeedTicks.sum()).isPositive();
  }

  @Test
  void standingAtFullProgressDoesNotCount() {
    player.setVelocity(Vec3.ZERO);
    player.setMovement(Vec3.ZERO);

    ramp.update(player, RAMP, 1_000L);
    ramp.update(player, RAMP, 2_000L);

    assertThat(fullSpeedTicks.sum()).isZero();
  }
}
