package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.platform.entity.Pose;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
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

import static org.assertj.core.api.Assertions.assertThat;

class AgilityRuntimeTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Runner", 0.5D, 64D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillAgility.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    Skill<?> skill = SkillRegistry.skills.get("agility");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("agility").setAdaptation(adaptation, level);
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  @Test
  void walkingRecordsTheMoveStat() {
    Loc from = player.location();
    Loc to = from.add(1D, 0D, 0D);
    player.moveTo(to);

    adapt.post(new PlayerMoveEvent(player, from, to));

    assertThat(profile.getData().getStat("move")).isEqualTo(1D);
  }

  @Test
  void lookingDownAMidColumnLadderSuppressesClientClimbingAndSendsDescent() {
    learn(AgilityLadderSlide.class, 1);
    Id ladder = Id.minecraft("ladder");
    platform.host().tagBlocks(Id.minecraft("climbable"), ladder);
    for (int y = 60; y <= 70; y++) {
      world.set(0, y, 0, "ladder");
    }
    player.setClimbing(true);
    Loc from = new Loc(world, 0.5D, 65D, 0.5D, 0F, 70F);
    Loc to = from.add(0D, -0.1D, 0D);
    player.moveTo(to);

    adapt.post(new PlayerMoveEvent(player, from, to));
    platform.scheduler().advance(1);

    assertThat(player.climbableTagOverrides()).containsExactly(ladder);
    assertThat(player.verticalMotions()).isNotEmpty();
    assertThat(player.verticalMotions().getLast()).isLessThan(0D);

    adapt.post(new PlayerToggleSneakEvent(player, true));

    assertThat(player.climbableTagOverrides()).containsExactly(ladder, null);
    assertThat(player.verticalMotions().getLast()).isZero();
  }

  @Test
  void sprintingOntoFarmlandDeniesTheTrample() {
    learn(AgilityFeatherfoot.class, 1);
    world.set(0, 63, 0, "farmland");
    player.setSprinting(true);
    BlockRef farmland = world.blockAt(0, 63, 0);

    PlayerInteractEvent event = adapt.post(new PlayerInteractEvent(player, InteractAction.PHYSICAL, null, null, farmland,
        null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(event.useBlock()).isEqualTo(UseResult.DENY);
    assertThat(profile.getData().getStat("agility.featherfoot.surfaces-ignored")).isEqualTo(1D);
  }

  @Test
  void sprintSneakStartsAFixedProneSlideAndATeleportRestoresThePose() {
    learn(AgilitySlipstreamSlide.class, 1);
    player.setSprinting(true);
    player.setOnGround(true);

    adapt.post(new PlayerToggleSneakEvent(player, true));

    assertThat(player.pose()).isEqualTo(Pose.SWIMMING);
    assertThat(player.hasFixedPose()).isTrue();

    platform.scheduler().advance(1);
    adapt.post(new PlayerTeleportEvent(player, player.location(), player.location().add(10D, 0D, 0D), TeleportCause.COMMAND));

    assertThat(player.hasFixedPose()).isFalse();
    assertThat(player.pose()).isEqualTo(Pose.STANDING);
  }
}
