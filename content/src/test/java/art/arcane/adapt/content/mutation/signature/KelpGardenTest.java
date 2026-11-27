package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.HarvestMoon;
import art.arcane.adapt.content.adaptation.seaborrne.upgraded.Leviathan;
import art.arcane.adapt.content.adaptation.seaborrne.upgraded.TideShrine;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Seaborne and Herbalism replace Convergence with Kelp Garden: Wildgrowth and Harvest Moon under water. */
class KelpGardenTest {
  private static final Id KELP = Id.minecraft("kelp");
  private static final Id KELP_PLANT = Id.minecraft("kelp_plant");
  private static final Id SEA_PICKLE = Id.minecraft("sea_pickle");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private AdaptPlayer profile;
  private FakeWorld world;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Diver", 0.5D, 61D, 0.5D);
    player.setFoodLevel(20);
    adapt = TestAdapt.start(platform, List.of(SkillSeaborne.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void swimmingGrowsTheKelpAboveYouOneBlock() {
    KelpGarden garden = learn("seaborne", "herbalism", 1);
    world.set(0, 58, 0, "stone");
    world.set(0, 59, 0, "kelp_plant");
    world.set(new BlockPos(0, 60, 0), FakeBlockState.of("kelp").with("age", "3"));
    water(0, 61, 0);

    player.setSwimming(true);
    garden.onTick();

    assertThat(world.blockAt(0, 60, 0).type()).isEqualTo(KELP_PLANT);
    assertThat(world.blockAt(0, 61, 0).type()).isEqualTo(KELP);
    assertThat(world.blockAt(0, 61, 0).state().property("age")).isEqualTo("4");
  }

  @Test
  void swimmingAddsASeaPickleAndSpreadsACoralFan() {
    KelpGarden garden = learn("herbalism", "seaborne", 1);
    world.set(0, 59, 0, "tube_coral_block");
    pickle(0, 60, 0, "1");

    player.setSwimming(true);
    garden.onTick();
    assertThat(world.blockAt(0, 60, 0).state().property("pickles")).isEqualTo("2");

    world.set(0, 59, 0, "stone");
    world.set(1, 59, 0, "stone");
    world.set(0, 60, 0, "tube_coral_fan");
    water(1, 60, 0);
    garden.onTick();

    assertThat(world.blockAt(1, 60, 0).type()).isEqualTo(Id.minecraft("tube_coral_fan"));
  }

  @Test
  void aSeaPickleGrowsOnlyWaterloggedOnLivingCoral() {
    KelpGarden garden = learn("seaborne", "herbalism", 1);
    world.set(0, 59, 0, "stone");
    pickle(0, 60, 0, "1");
    player.setSwimming(true);
    garden.onTick();
    assertThat(world.blockAt(0, 60, 0).state().property("pickles")).isEqualTo("1");

    world.set(0, 59, 0, "dead_tube_coral_block");
    garden.onTick();
    assertThat(world.blockAt(0, 60, 0).state().property("pickles")).isEqualTo("1");

    world.set(0, 59, 0, "tube_coral_block");
    world.set(new BlockPos(0, 60, 0), FakeBlockState.of("sea_pickle").with("pickles", "1").with("waterlogged", "false"));
    garden.onTick();

    assertThat(world.blockAt(0, 60, 0).state().property("pickles")).isEqualTo("1");
  }

  @Test
  void yourTideShrineNearYouIsAFieldEvenOnDryLand() {
    KelpGarden garden = learn("seaborne", "herbalism", 1);
    garden.getConfig().growthRadiusAtFirstLevel = 3D;
    garden.getConfig().samplesAtFirstLevel = 4D;
    placeShrine();
    for (int x = -3; x <= 3; x++) {
      for (int z = -1; z <= 5; z++) {
        world.set(new BlockPos(x, 41, z), FakeBlockState.of("kelp").with("age", "3"));
        water(x, 42, z);
      }
    }
    player.moveTo(new Loc(world, 20.5D, 61D, 20.5D));
    player.setSwimming(false);

    garden.onTick();

    int grown = 0;
    for (int x = -3; x <= 3; x++) {
      for (int z = -1; z <= 5; z++) {
        grown += KELP.equals(world.blockAt(x, 42, z).type()) ? 1 : 0;
      }
    }
    assertThat(grown).isPositive();
  }

  @Test
  void aHoeOnAFullGrownKelpHeadLeavesHarvestMoonOut() {
    learn("seaborne", "herbalism", 1);
    HarvestMoon moon = (HarvestMoon) kit.registry().getAdaptation(Id.adapt("herbalism/upgrade/harvest_moon"));
    profile.getData().getSkillLine("herbalism").setAdaptation(moon, 1);
    world.set(0, 58, 0, "stone");
    world.set(0, 59, 0, "kelp_plant");
    world.set(new BlockPos(0, 60, 0), FakeBlockState.of("kelp").with("age", "7"));
    world.set(2, 60, 0, "farmland");
    world.set(new BlockPos(2, 61, 0), FakeBlockState.of("wheat").with("age", "7"));
    player.holding(FakeItem.tool("iron_hoe", 250));

    harvest(world.blockAt(0, 60, 0));
    platform.scheduler().advance(5);

    assertThat(world.blockAt(2, 61, 0).type()).isEqualTo(Id.minecraft("wheat"));
  }

  @Test
  void theSeaBedStopsGrowingWhenTheSignatureIsTurnedOff() {
    KelpGarden garden = learn("seaborne", "herbalism", 1);
    garden.getConfig().enabled = false;
    world.set(0, 59, 0, "tube_coral_block");
    pickle(0, 60, 0, "1");

    player.setSwimming(true);
    garden.onTick();

    assertThat(world.blockAt(0, 60, 0).state().property("pickles")).isEqualTo("1");
  }

  @Test
  void aHoeHarvestsTheSeaBedAndLeavesTheRoots() {
    learn("seaborne", "herbalism", 3);
    for (int x = 0; x <= 2; x++) {
      world.set(x, 57, 0, "stone");
      world.set(x, 58, 0, "kelp_plant");
      world.set(x, 59, 0, "kelp_plant");
      world.set(new BlockPos(x, 60, 0), FakeBlockState.of("kelp").with("age", "5"));
    }
    world.set(new BlockPos(0, 58, 2), FakeBlockState.of("sea_pickle").with("pickles", "3"));
    player.holding(FakeItem.tool("iron_hoe", 250));

    harvest(world.blockAt(0, 60, 0));
    platform.scheduler().advance(5);

    for (int x = 0; x <= 2; x++) {
      assertThat(world.blockAt(x, 58, 0).type()).isEqualTo(KELP_PLANT);
      assertThat(world.blockAt(x, 59, 0).type().isAir()).isTrue();
    }
    assertThat(world.blockAt(0, 58, 2).state().property("pickles")).isEqualTo("1");
    assertThat(world.items()).anySatisfy((FakeItemEntity item) -> {
      assertThat(item.item().type()).isEqualTo(SEA_PICKLE);
      assertThat(item.item().amount()).isEqualTo(2);
    });
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void theNextHarvestWaitsForTheCooldown() {
    learn("seaborne", "herbalism", 3);
    world.set(new BlockPos(0, 60, 0), FakeBlockState.of("sea_pickle").with("pickles", "3"));
    player.holding(FakeItem.tool("iron_hoe", 250));
    harvest(world.blockAt(0, 60, 0));
    world.set(new BlockPos(0, 60, 0), FakeBlockState.of("sea_pickle").with("pickles", "4"));

    harvest(world.blockAt(0, 60, 0));

    assertThat(world.blockAt(0, 60, 0).state().property("pickles")).isEqualTo("4");
  }

  @Test
  void protectedSeaBedNeverGrowsOrYields() {
    KelpGarden garden = learn("seaborne", "herbalism", 3);
    platform.protection().deny(true);
    world.set(new BlockPos(0, 60, 0), FakeBlockState.of("sea_pickle").with("pickles", "3"));
    player.holding(FakeItem.tool("iron_hoe", 250));

    player.setSwimming(true);
    garden.onTick();
    harvest(world.blockAt(0, 60, 0));

    assertThat(world.blockAt(0, 60, 0).state().property("pickles")).isEqualTo("3");
    assertThat(world.items()).isEmpty();
  }

  @Test
  void nothingGrowsWithoutTheLearnedAdaptation() {
    kit.mutate("seaborne", "herbalism");
    KelpGarden garden = (KelpGarden) kit.adaptation("seaborne", "herbalism", Recipe.CONVERGENCE).getSignature();
    world.set(new BlockPos(0, 60, 0), FakeBlockState.of("sea_pickle").with("pickles", "1"));
    player.holding(FakeItem.tool("iron_hoe", 250));

    player.setSwimming(true);
    garden.onTick();
    harvest(world.blockAt(0, 60, 0));

    assertThat(world.blockAt(0, 60, 0).state().property("pickles")).isEqualTo("1");
  }

  private KelpGarden learn(String lead, String partner, int level) {
    kit.mutate(lead, partner);
    kit.learn(lead, partner, Recipe.CONVERGENCE, level);
    KelpGarden garden = (KelpGarden) kit.adaptation(lead, partner, Recipe.CONVERGENCE).getSignature();
    garden.getConfig().growthRadiusAtFirstLevel = 0.1D;
    garden.getConfig().samplesAtFirstLevel = 1D;
    return garden;
  }

  private void placeShrine() {
    Leviathan leviathan = (Leviathan) kit.registry().getAdaptation(Leviathan.ID);
    profile.getData().getSkillLine("seaborne").setAdaptation(leviathan, 1);
    world.set(0, 39, 2, "stone");
    water(0, 40, 2);
    water(0, 41, 2);
    TideShrine tool = (TideShrine) leviathan.getTools().getFirst();
    player.holding(tool.create(CustomItem.Plain.INSTANCE));
    player.inventory().setItem(20, new FakeItem(Id.minecraft("nautilus_shell"), 1));
    harvest(world.blockAt(0, 39, 2));
    platform.scheduler().advance(3);
    assertThat(leviathan.shrinesOf(player)).hasSize(1);
  }

  private void pickle(int x, int y, int z, String count) {
    world.set(new BlockPos(x, y, z), FakeBlockState.of("sea_pickle").with("pickles", count).with("waterlogged", "true"));
  }

  private void water(int x, int y, int z) {
    world.set(new BlockPos(x, y, z), FakeBlockState.of("water").with("level", "0"));
  }

  private void harvest(BlockRef block) {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, player.inventory().mainHand(),
        block, BlockFace.UP, new Vec3(block.x() + 0.5D, block.y() + 1D, block.z() + 0.5D), UseResult.DEFAULT, UseResult.DEFAULT));
  }
}
