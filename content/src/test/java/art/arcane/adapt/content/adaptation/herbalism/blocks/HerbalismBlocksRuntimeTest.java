package art.arcane.adapt.content.adaptation.herbalism.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
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
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Herbalism leading Swords ("Bladed Warden"): all five mutated adaptations and the fight-or-work rule. */
class HerbalismBlocksRuntimeTest {
  private static final Id WHEAT = Id.minecraft("wheat");

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
    player = platform.player("Warden", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    kit.mutate("herbalism", "swords");
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftCutsTheArcWhenAMatureCropIsHarvestedWithTheWorkCues() {
    kit.learn("herbalism", "swords", Recipe.GRAFT, 1);
    crop(0, 61, 2, 7);
    BlockRef harvested = crop(-1, 61, 2, 7);

    adapt.post(new BlockBreakEvent(player, crop(1, 61, 3, 3), true, 0));
    assertThat(platform.world().blockAt(0, 61, 2).type()).isEqualTo(WHEAT);

    adapt.post(new BlockBreakEvent(player, harvested, true, 0));
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
    assertThat(player.inventory().contains(WHEAT, 1)).isTrue();
    assertThat(player.heardSounds()).containsSubsequence(MutationEngine.palette(Id.adapt("herbalism")).work(),
        MutationEngine.palette(Id.adapt("swords")).work());
  }

  @Test
  void aBoneMealedHarvestDoesNotPayOut() {
    kit.learn("herbalism", "swords", Recipe.GRAFT, 1);
    crop(0, 61, 2, 7);
    BlockRef harvested = crop(-1, 61, 2, 7);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isBonemealed(any(BlockRef.class))).thenReturn(true);
      adapt.post(new BlockBreakEvent(player, harvested, true, 0));
    }

    assertThat(platform.world().blockAt(0, 61, 2).type()).isEqualTo(WHEAT);
  }

  @Test
  void aPlacedBambooAtItsLastStageDoesNotPayOut() {
    kit.learn("herbalism", "swords", Recipe.GRAFT, 1);
    crop(0, 61, 2, 7);
    platform.world().set(new BlockPos(-1, 61, 2), new FakeBlockState(Id.minecraft("bamboo"), Map.of("age", "7")));
    BlockRef bamboo = platform.world().blockAt(-1, 61, 2);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      adapt.post(new BlockBreakEvent(player, bamboo, true, 0));
    }
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(0, 61, 2).type()).isEqualTo(WHEAT);
  }

  @Test
  void echoUsesTheFightStrikeForASwordCritEvenAmongCrops() {
    kit.learn("herbalism", "swords", Recipe.ECHO, 1);
    BlockRef young = crop(0, 61, 2, 3);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isZero();
    assertThat(young.state().age()).isEqualTo(3);
  }

  @Test
  void barterSpendsSwordDurabilityForFood() {
    kit.learn("herbalism", "swords", Recipe.BARTER, 1);
    player.setFoodLevel(10);
    player.setSneaking(true);

    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));

    assertThat(swap.isCancelled()).isTrue();
    assertThat(player.foodLevel()).isEqualTo(12);
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);

    player.setSneaking(false);
    adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
    assertThat(player.foodLevel()).isEqualTo(12);
  }

  @Test
  void attunementInCropsGrantsAttackSpeed() {
    kit.learn("herbalism", "swords", Recipe.ATTUNEMENT, 3);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    platform.world().set(new BlockPos(0, 61, 0), new FakeBlockState(WHEAT, Map.of("age", "2")));
    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.3D, within(1.0E-9D));
  }

  @Test
  void convergenceGrowsAndCutsAfterASwordHitButLeavesTheNearbyHostileAlone() {
    kit.learn("herbalism", "swords", Recipe.CONVERGENCE, 1);
    BlockRef young = crop(0, 61, 4, 3);
    crop(0, 61, 2, 7);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));
    assertThat(young.state().age()).isEqualTo(3);

    kit.hit(player, zombie, 1D, false);
    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));
    platform.scheduler().advance(4);

    assertThat(young.state().age()).isEqualTo(4);
    assertThat(platform.world().blockAt(0, 61, 2).type().isAir()).isTrue();
    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isEqualTo(1D);
    assertThat(zombie.lastDamageType()).isNotEqualTo(Id.minecraft("magic"));
  }

  @Test
  void cropsTheStrikeGrewCountAsBoneMealed() {
    kit.learn("herbalism", "swords", Recipe.CONVERGENCE, 1);
    BlockRef young = crop(0, 61, 4, 3);
    kit.hit(player, MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D), 1D, false);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));
      platform.scheduler().advance(4);

      provenance.verify(() -> XpProvenance.recordBonemeal(
          argThat((BlockRef grown) -> grown.pos().equals(young.pos()))));
    }
    assertThat(young.state().age()).isEqualTo(4);
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
