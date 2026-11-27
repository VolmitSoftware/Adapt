package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.protection.ProtectionProbes;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.HarvestMoon;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Rift leading Herbalism replaces Convergence with Chorus Orchard. */
class ChorusOrchardTest {
  private static final Id FLOWER = Id.minecraft("chorus_flower");
  private static final Id PLANT = Id.minecraft("chorus_plant");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Voidwalker", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillRift.class, SkillHerbalism.class));
    kit = new MutationKit(adapt, adapt.join(player));
    for (int x = -2; x <= 4; x++) {
      for (int z = -2; z <= 14; z++) {
        world.set(x, 60, z, "stone");
      }
    }
    world.set(3, 60, 3, "end_stone");
    world.set(new BlockPos(3, 61, 3), FakeBlockState.of("chorus_flower").with("age", "0"));
    world.set(new BlockPos(1, 61, 1), FakeBlockState.of("wheat").with("age", "7"));
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void chorusFruitLandsWhereYouLookWithinTheLevelsRange() {
    kit.mutate("rift", "herbalism");
    kit.learn("rift", "herbalism", Recipe.CONVERGENCE, 1);

    Loc to = eat().to();

    assertThat(kit.adaptation("rift", "herbalism", Recipe.CONVERGENCE).getSignature()).isInstanceOf(ChorusOrchard.class);
    assertThat(to.x()).isCloseTo(0.5D, within(1.0E-9D));
    assertThat(to.z()).isGreaterThan(4D).isLessThanOrEqualTo(8.6D);
    assertThat(to.y()).isCloseTo(61D, within(1.0E-9D));
  }

  @Test
  void herbalismLeadingRiftAndAPlayerWithoutTheMutationKeepTheRandomLanding() {
    assertThat(eat().to().x()).isEqualTo(40.5D);
    kit.mutate("herbalism", "rift");
    kit.learn("herbalism", "rift", Recipe.CONVERGENCE, 3);

    assertThat(eat().to().x()).isEqualTo(40.5D);
    harvest();
    assertThat(world.blockAt(3, 62, 3).type().isAir()).isTrue();
  }

  @Test
  void aHarvestGrowsNearbyChorusOneStepAsForcedGrowthAndThenCoolsDown() {
    kit.mutate("rift", "herbalism");
    kit.learn("rift", "herbalism", Recipe.CONVERGENCE, 1);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      harvest();
      provenance.verify(() -> XpProvenance.recordBonemeal(argThat((BlockRef grown) -> grown.pos().equals(new BlockPos(3, 62, 3)))));
    }
    world.set(new BlockPos(1, 61, 1), FakeBlockState.of("wheat").with("age", "7"));
    harvest();

    assertThat(world.blockAt(3, 61, 3).type()).isEqualTo(PLANT);
    assertThat(world.blockAt(3, 62, 3).type()).isEqualTo(FLOWER);
    assertThat(world.blockAt(3, 63, 3).type().isAir()).isTrue();
  }

  @Test
  void aForcedCropGrowsNoChorus() {
    kit.mutate("rift", "herbalism");
    kit.learn("rift", "herbalism", Recipe.CONVERGENCE, 3);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isBonemealed(any(BlockRef.class))).thenReturn(true);
      harvest();
    }

    assertThat(world.blockAt(3, 61, 3).type()).isEqualTo(FLOWER);
    assertThat(world.blockAt(3, 62, 3).type().isAir()).isTrue();
  }

  @Test
  void aHarvestDoesNotHoldBackTheAimedLanding() {
    kit.mutate("rift", "herbalism");
    kit.learn("rift", "herbalism", Recipe.CONVERGENCE, 1);

    harvest();
    Loc to = eat().to();

    assertThat(world.blockAt(3, 62, 3).type()).isEqualTo(FLOWER);
    assertThat(to.x()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void aHarvestMoonSweepGrowsNearbyChorus() {
    kit.mutate("rift", "herbalism");
    kit.learn("rift", "herbalism", Recipe.CONVERGENCE, 1);
    AdaptPlayer profile = adapt.join(player);
    for (Adaptation<?> upgrade : SkillHost.of(kit.registry().getSkill("herbalism")).getUpgrades()) {
      if (upgrade instanceof HarvestMoon moon) {
        profile.getData().getSkillLine("herbalism").setAdaptation(moon, 1);
      }
    }
    player.holding(FakeItem.tool("iron_hoe", 250));
    BlockRef crop = world.blockAt(1, 61, 1);

    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), crop, BlockFace.UP, crop.center().position(), UseResult.DEFAULT,
        UseResult.DEFAULT));

    assertThat(world.blockAt(1, 61, 1).type()).isNotEqualTo(Id.minecraft("wheat"));
    assertThat(world.blockAt(3, 62, 3).type()).isEqualTo(FLOWER);
  }

  @Test
  void aFlowerOnProtectedGroundDoesNotGrow() {
    kit.mutate("rift", "herbalism");
    kit.learn("rift", "herbalism", Recipe.CONVERGENCE, 1);

    try (MockedStatic<ProtectionProbes> probes = mockStatic(ProtectionProbes.class, CALLS_REAL_METHODS)) {
      probes.when(() -> ProtectionProbes.attemptBlockPlaceProbe(any(PlayerRef.class),
          argThat((BlockRef block) -> block != null && block.pos().equals(new BlockPos(3, 61, 3))))).thenReturn(false);
      harvest();
    }

    assertThat(world.blockAt(3, 61, 3).type()).isEqualTo(FLOWER);
    assertThat(world.blockAt(3, 62, 3).type().isAir()).isTrue();
  }

  private PlayerTeleportEvent eat() {
    return adapt.post(new PlayerTeleportEvent(player, player.location(), new Loc(world, 40.5D, 61D, 40.5D),
        TeleportCause.CHORUS_FRUIT));
  }

  private void harvest() {
    adapt.post(new BlockBreakEvent(player, world.blockAt(1, 61, 1), true, 0));
  }
}
