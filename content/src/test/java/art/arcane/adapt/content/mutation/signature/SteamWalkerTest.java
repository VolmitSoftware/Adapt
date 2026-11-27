package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockFormEvent;
import art.arcane.adapt.platform.event.block.BlockFromToEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.player.PlayerBucketEmptyEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.Environment;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Seaborne and Nether replace Convergence with Steam Walker: wet steps cool lava, buckets work in the Nether. */
class SteamWalkerTest {
  private static final Id LAVA = Id.minecraft("lava");
  private static final Id OBSIDIAN = Id.minecraft("obsidian");
  private static final Id COBBLESTONE = Id.minecraft("cobblestone");
  private static final Id WATER = Id.minecraft("water");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private FakeWorld world;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    world.environment(Environment.NETHER, 1L);
    player = platform.player("Steam", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSeaborne.class, SkillNether.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        world.set(new BlockPos(x, 60, z), FakeBlockState.of("lava").with("level", x == 1 ? "3" : "0"));
      }
    }
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aWetStepCoolsTheLavaUnderYouThenItMeltsBackAsItWas() {
    kit.mutate("seaborne", "nether");
    kit.learn("seaborne", "nether", Recipe.CONVERGENCE, 1);
    assertThat(kit.adaptation("seaborne", "nether", Recipe.CONVERGENCE).getSignature()).isInstanceOf(SteamWalker.class);

    wetStep();

    assertThat(block(0, 0).type()).isEqualTo(OBSIDIAN);
    assertThat(block(1, 1).type()).isEqualTo(COBBLESTONE);
    assertThat(block(2, 0).type()).isEqualTo(LAVA);
    assertThat(player.heardSounds()).isNotEmpty();

    platform.scheduler().advance(61);

    assertThat(block(0, 0).type()).isEqualTo(LAVA);
    assertThat(block(1, 1).type()).isEqualTo(LAVA);
    assertThat(block(1, 1).state().property("level")).isEqualTo("3");
  }

  @Test
  void cooledBlocksSurviveExplosionsAndDropNothing() {
    kit.mutate("seaborne", "nether");
    kit.learn("seaborne", "nether", Recipe.CONVERGENCE, 1);
    wetStep();

    EntityExplodeEvent blast = adapt.post(new EntityExplodeEvent(player, block(0, 0).location(),
        List.of(block(0, 0), world.blockAt(5, 60, 5)), 1F));
    BlockDropItemEvent drops = adapt.post(new BlockDropItemEvent(player, block(0, 0), FakeBlockState.of("obsidian"),
        new ArrayList<>(List.of(new FakeItemEntity(block(0, 0).center(), new FakeItem(OBSIDIAN, 1)))), null));

    assertThat(blast.blocks()).containsExactly(world.blockAt(5, 60, 5));
    assertThat(drops.isCancelled()).isTrue();
  }

  @Test
  void cooledLavaLeftByACrashMeltsBackWhenItsChunkLoads() {
    kit.mutate("seaborne", "nether");
    kit.learn("seaborne", "nether", Recipe.CONVERGENCE, 1);
    wetStep();
    assertThat(block(0, 0).type()).isEqualTo(OBSIDIAN);

    restart();

    assertThat(block(0, 0).type()).isEqualTo(LAVA);
    assertThat(block(1, 1).type()).isEqualTo(LAVA);
    assertThat(block(1, 1).state().property("level")).isEqualTo("3");
  }

  @Test
  void netherWaterLeftByACrashEvaporatesWhenItsChunkLoads() {
    kit.mutate("seaborne", "nether");
    kit.learn("seaborne", "nether", Recipe.CONVERGENCE, 1);
    player.holding(FakeItem.of("water_bucket"));
    BlockRef pool = world.blockAt(5, 61, 5);
    adapt.post(new PlayerBucketEmptyEvent(player, pool, Id.minecraft("water_bucket")));
    assertThat(pool.type()).isEqualTo(WATER);

    restart();

    assertThat(pool.type().isAir()).isTrue();
  }

  @Test
  void aPoolNeverFlowsNorHardensLava() {
    kit.mutate("seaborne", "nether");
    kit.learn("seaborne", "nether", Recipe.CONVERGENCE, 1);
    player.holding(FakeItem.of("water_bucket"));
    BlockRef pool = world.blockAt(5, 61, 5);
    adapt.post(new PlayerBucketEmptyEvent(player, pool, Id.minecraft("water_bucket")));
    world.set(9, 61, 9, "water");

    BlockFromToEvent spread = adapt.post(new BlockFromToEvent(pool, world.blockAt(6, 61, 5), BlockFace.EAST));
    BlockFromToEvent otherWater = adapt.post(new BlockFromToEvent(world.blockAt(9, 61, 9), world.blockAt(10, 61, 9), BlockFace.EAST));
    BlockFormEvent obsidian = adapt.post(new BlockFormEvent(world.blockAt(6, 61, 5), FakeBlockState.of("obsidian"), null));
    BlockFormEvent stone = adapt.post(new BlockFormEvent(pool, FakeBlockState.of("stone"), null));
    BlockFormEvent elsewhere = adapt.post(new BlockFormEvent(world.blockAt(10, 61, 9), FakeBlockState.of("obsidian"), null));

    assertThat(spread.isCancelled()).isTrue();
    assertThat(otherWater.isCancelled()).isFalse();
    assertThat(obsidian.isCancelled()).isTrue();
    assertThat(stone.isCancelled()).isTrue();
    assertThat(elsewhere.isCancelled()).isFalse();
  }

  @Test
  void cooledObsidianCannotBeMined() {
    kit.mutate("seaborne", "nether");
    kit.learn("seaborne", "nether", Recipe.CONVERGENCE, 1);
    wetStep();

    BlockBreakEvent mined = adapt.post(new BlockBreakEvent(player, block(0, 0), true, 0));

    assertThat(mined.isCancelled()).isTrue();
  }

  @Test
  void eitherSkillMayLeadButADryStepCoolsNothing() {
    kit.mutate("nether", "seaborne");
    kit.learn("nether", "seaborne", Recipe.CONVERGENCE, 1);

    step();
    assertThat(block(0, 0).type()).isEqualTo(LAVA);

    wetStep();
    assertThat(block(0, 0).type()).isEqualTo(OBSIDIAN);
  }

  @Test
  void theNextWalkWaitsForTheCooldown() {
    kit.mutate("seaborne", "nether");
    kit.learn("seaborne", "nether", Recipe.CONVERGENCE, 1);
    SteamWalker walker = (SteamWalker) kit.adaptation("seaborne", "nether", Recipe.CONVERGENCE).getSignature();
    walker.getConfig().walkTicksAtFirstLevel = 0;
    walker.getConfig().cooledTicks = 1;
    wetStep();
    platform.scheduler().advance(2);
    assertThat(block(0, 0).type()).isEqualTo(LAVA);

    wetStep();

    assertThat(block(0, 0).type()).isEqualTo(LAVA);
  }

  @Test
  void netherWaterEvaporatesAndOnlyTheLevelsPoolsStay() {
    kit.mutate("seaborne", "nether");
    kit.learn("seaborne", "nether", Recipe.CONVERGENCE, 1);
    player.holding(FakeItem.of("water_bucket"));
    BlockRef first = world.blockAt(5, 61, 5);
    BlockRef second = world.blockAt(8, 61, 8);

    PlayerBucketEmptyEvent poured = adapt.post(new PlayerBucketEmptyEvent(player, first, Id.minecraft("water_bucket")));

    assertThat(poured.isCancelled()).isTrue();
    assertThat(first.type()).isEqualTo(WATER);
    assertThat(player.inventory().mainHand().type()).isEqualTo(Id.minecraft("bucket"));

    player.holding(FakeItem.of("water_bucket"));
    adapt.post(new PlayerBucketEmptyEvent(player, second, Id.minecraft("water_bucket")));
    platform.scheduler().advance(1);
    assertThat(first.type().isAir()).isTrue();
    assertThat(second.type()).isEqualTo(WATER);

    platform.scheduler().advance(600);
    assertThat(second.type().isAir()).isTrue();
  }

  @Test
  void nothingHappensWithoutTheLearnedAdaptation() {
    kit.mutate("seaborne", "nether");
    player.holding(FakeItem.of("water_bucket"));

    wetStep();
    PlayerBucketEmptyEvent poured = adapt.post(new PlayerBucketEmptyEvent(player, world.blockAt(5, 61, 5), Id.minecraft("water_bucket")));

    assertThat(block(0, 0).type()).isEqualTo(LAVA);
    assertThat(poured.isCancelled()).isFalse();
    assertThat(world.blockAt(5, 61, 5).type().isAir()).isTrue();
  }

  private void restart() {
    adapt.close();
    adapt = TestAdapt.start(platform, List.of(SkillSeaborne.class, SkillNether.class));
    adapt.join(player);
    adapt.post(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    platform.scheduler().advance(2);
  }

  private void wetStep() {
    player.setSwimming(true);
    step();
    player.setSwimming(false);
  }

  private void step() {
    adapt.post(new PlayerMoveEvent(player, new Loc(world, 0.5D, 61D, -0.5D), new Loc(world, 0.5D, 61D, 0.5D)));
  }

  private BlockRef block(int x, int z) {
    return world.blockAt(x, 60, z);
  }
}
