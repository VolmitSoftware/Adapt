package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StealthSpeedMovementTest {
  private static final String SPRINTED = "stealth.speed.blocks-sneak-sprinted";

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Sneak", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillStealth.class));
    profile = adapt.join(player);
    StealthSpeed speed = adapt.core().getAdaptServer().getSkillRegistry().getSkill("stealth").getAdaptations().stream()
        .filter(StealthSpeed.class::isInstance).map(StealthSpeed.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("stealth").setAdaptation(speed, 3);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void walkingWhileSneakingCountsThoughTheServerVelocityIsStill() {
    player.setVelocity(Vec3.ZERO);
    player.setMovement(new Vec3(0.2D, 0D, 0D));
    sneak();

    assertThat(profile.getData().getStat(SPRINTED)).isPositive();
  }

  @Test
  void standingStillWhileSneakingDoesNotCount() {
    player.setVelocity(Vec3.ZERO);
    player.setMovement(Vec3.ZERO);
    sneak();

    assertThat(profile.getData().getStat(SPRINTED)).isZero();
  }

  private void sneak() {
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));
    platform.scheduler().advance(10);
  }
}
