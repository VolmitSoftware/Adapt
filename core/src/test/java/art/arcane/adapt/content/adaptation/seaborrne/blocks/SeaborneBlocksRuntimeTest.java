package art.arcane.adapt.content.adaptation.seaborrne.blocks;

import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
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

/** Seaborne with Herbalism in both orderings, and leading into a Swords crit for its water burst. */
class SeaborneBlocksRuntimeTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id FARMLAND = Id.minecraft("farmland");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Diver", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSeaborne.class, SkillHerbalism.class, SkillSwords.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftGrowsCropsWhenDivingFromAirIntoWaterWithSeaborneFirst() {
    kit.mutate("seaborne", "herbalism");
    kit.learn("seaborne", "herbalism", Recipe.GRAFT, 1);
    BlockRef young = crop(0, 61, 2, 3);
    platform.world().set(0, 61, 0, "water");

    adapt.post(new PlayerMoveEvent(player, at(0.5D, 61.2D, 0.5D), at(0.5D, 61.1D, 2.5D)));
    adapt.post(new PlayerMoveEvent(player, at(0.5D, 60.5D, 0.5D), at(0.5D, 61.5D, 0.5D)));
    assertThat(young.state().age()).isEqualTo(3);

    adapt.post(new PlayerMoveEvent(player, at(0.5D, 62.5D, 0.5D), at(0.5D, 61.5D, 0.5D)));
    platform.scheduler().advance(4);

    assertThat(young.state().age()).isEqualTo(4);
    assertThat(player.heardSounds()).containsSubsequence(SoundPalettes.of("seaborne").work(),
        SoundPalettes.of("herbalism").work());
  }

  @Test
  void echoPutsOutFireAndWatersFarmlandWhenACropIsHarvested() {
    kit.mutate("seaborne", "herbalism");
    kit.learn("seaborne", "herbalism", Recipe.ECHO, 1);
    platform.world().set(1, 61, 1, "fire");
    platform.world().set(-1, 61, 0, "soul_fire");
    platform.world().set(new BlockPos(0, 60, 2), new FakeBlockState(FARMLAND, Map.of("moisture", "0")));
    platform.world().set(new BlockPos(0, 60, 9), new FakeBlockState(FARMLAND, Map.of("moisture", "0")));

    adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));

    assertThat(platform.world().blockAt(1, 61, 1).type().isAir()).isTrue();
    assertThat(platform.world().blockAt(-1, 61, 0).type().isAir()).isTrue();
    assertThat(platform.world().blockAt(0, 60, 2).state().property("moisture")).isEqualTo("7");
    assertThat(platform.world().blockAt(0, 60, 9).state().property("moisture")).isEqualTo("0");
  }

  @Test
  void barterGrantsWaterBreathingAndDolphinsGraceForSixSeconds() {
    kit.mutate("seaborne", "herbalism");
    kit.learn("seaborne", "herbalism", Recipe.BARTER, 1);
    player.inventory().addItem(new FakeItem(Id.minecraft("wheat_seeds"), 3));
    player.setSneaking(true);

    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));

    assertThat(swap.isCancelled()).isTrue();
    assertThat(player.effect(Id.minecraft("water_breathing")).durationTicks()).isEqualTo(120);
    assertThat(player.effect(Id.minecraft("dolphins_grace")).durationTicks()).isEqualTo(120);
  }

  @Test
  void attunementFeedsOnlyWhileWet() {
    kit.mutate("seaborne", "herbalism");
    kit.learn("seaborne", "herbalism", Recipe.ATTUNEMENT, 1);
    player.setFoodLevel(10);

    tickEngine();
    assertThat(player.foodLevel()).isEqualTo(10);

    player.setSwimming(true);
    tickEngine();
    assertThat(player.foodLevel()).isEqualTo(12);
  }

  @Test
  void graftUnderSwordsThrowsTheCritTargetBackAndDousesIt() {
    kit.mutate("swords", "seaborne");
    kit.learn("swords", "seaborne", Recipe.GRAFT, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    zombie.setFireTicks(100);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(zombie.velocity().x()).isGreaterThan(1D);
    assertThat(zombie.velocity().z()).isZero();
    assertThat(zombie.velocity().y()).isPositive();
    assertThat(zombie.fireTicks()).isZero();
  }

  @Test
  void echoAsPartnerFiresOnTheDive() {
    kit.mutate("herbalism", "seaborne");
    kit.learn("herbalism", "seaborne", Recipe.ECHO, 1);
    BlockRef young = crop(0, 61, 2, 3);
    platform.world().set(0, 61, 0, "water");

    adapt.post(new PlayerMoveEvent(player, at(0.5D, 62.5D, 0.5D), at(0.5D, 61.5D, 0.5D)));
    platform.scheduler().advance(4);

    assertThat(young.state().age()).isEqualTo(4);
  }

  @Test
  void barterAsPartnerSpendsThreeBubblesOnlyUnderwater() {
    kit.mutate("herbalism", "seaborne");
    kit.learn("herbalism", "seaborne", Recipe.BARTER, 1);
    player.setFoodLevel(10);
    player.setSneaking(true);

    swapHands();
    assertThat(player.foodLevel()).isEqualTo(10);
    assertThat(player.remainingAir()).isEqualTo(300);

    for (int y = 60; y <= 64; y++) {
      platform.world().set(0, y, 0, "water");
    }
    player.setRemainingAir(80);
    swapHands();
    assertThat(player.foodLevel()).isEqualTo(10);

    player.setRemainingAir(300);
    swapHands();
    assertThat(player.foodLevel()).as("paid 2, the Herbalism Boon gave 2 back").isEqualTo(10);
    assertThat(player.remainingAir()).isEqualTo(210);
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    BlockRef young = crop(0, 61, 2, 3);
    platform.world().set(0, 61, 0, "water");

    adapt.post(new PlayerMoveEvent(player, at(0.5D, 62.5D, 0.5D), at(0.5D, 61.5D, 0.5D)));
    platform.scheduler().advance(4);

    assertThat(young.state().age()).isEqualTo(3);
  }

  private void swapHands() {
    adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private Loc at(double x, double y, double z) {
    return new Loc(platform.world(), x, y, z);
  }

  private BlockRef crop(int x, int y, int z, int age) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", Integer.toString(age))));
    return platform.world().blockAt(x, y, z);
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
