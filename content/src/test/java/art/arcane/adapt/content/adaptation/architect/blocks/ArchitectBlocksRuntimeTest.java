package art.arcane.adapt.content.adaptation.architect.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.content.adaptation.architect.ArchitectFoundation;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationBlocks;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Architect with Swords and Herbalism: each of the six blocks through the recipes that use it. */
class ArchitectBlocksRuntimeTest {
  private static final Id GRASS = Id.minecraft("short_grass");
  private static final Id STONE = Id.minecraft("stone");
  private static final Id TINTED_GLASS = Id.minecraft("tinted_glass");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;
  private int placed;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Mason", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHerbalism.class, SkillArchitect.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftCutsTheArcOnTheEighthQuickPlacementAsLeadWithBothWorkCues() {
    kit.mutate("architect", "swords");
    kit.learn("architect", "swords", Recipe.GRAFT, 1);
    platform.world().set(-3, 61, 8, "short_grass");

    places(7);
    assertThat(platform.world().blockAt(-3, 61, 8).type()).isEqualTo(GRASS);

    places(1);
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(-3, 61, 8).type().isAir()).isTrue();
    assertThat(player.heardSounds()).containsSubsequence(MutationEngine.palette(Id.adapt("architect")).work(),
        MutationEngine.palette(Id.adapt("swords")).work());
  }

  @Test
  void echoCutsTheArcOnTheEighthQuickPlacementAsPartner() {
    kit.mutate("swords", "architect");
    kit.learn("swords", "architect", Recipe.ECHO, 1);
    platform.world().set(-3, 61, 8, "short_grass");

    places(8);

    assertThat(platform.world().blockAt(-3, 61, 8).type().isAir()).isTrue();
  }

  @Test
  void refillingFreshlyBrokenSpotsDoesNotPayOut() {
    kit.mutate("architect", "swords");
    kit.learn("architect", "swords", Recipe.GRAFT, 1);
    platform.world().set(-3, 61, 8, "short_grass");

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isReplaceDenied(any(BlockRef.class))).thenReturn(true);
      places(8);
    }

    assertThat(platform.world().blockAt(-3, 61, 8).type()).isEqualTo(GRASS);
  }

  @Test
  void echoRaisesATemporaryWallBetweenThePlayerAndTheTargetOfASwordCrit() {
    kit.mutate("architect", "swords");
    kit.learn("architect", "swords", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 4.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);

    for (int z = -1; z <= 1; z++) {
      assertThat(platform.world().blockAt(2, 61, z).type()).isEqualTo(TINTED_GLASS);
      assertThat(platform.world().blockAt(2, 62, z).type()).isEqualTo(TINTED_GLASS);
    }
    assertThat(platform.world().blockAt(2, 63, 0).type().isAir()).isTrue();

    platform.scheduler().advance(200);
    assertThat(platform.world().blockAt(2, 61, 0).type().isAir()).isTrue();
  }

  @Test
  void theWallStandsAndCannotBeMinedWithMagicFoundationDisabled() {
    for (Adaptation<?> adaptation : kit.registry().getSkill("architect").getAdaptations()) {
      if (adaptation instanceof ArchitectFoundation) {
        ((AdaptationConfig) adaptation.getConfig()).enabled = false;
      }
    }
    kit.mutate("architect", "swords");
    kit.learn("architect", "swords", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 4.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);

    assertThat(platform.world().blockAt(2, 61, 0).type()).isEqualTo(TINTED_GLASS);
    assertThat(adapt.post(new BlockBreakEvent(player, platform.world().blockAt(2, 61, 0), true, 0)).isCancelled())
        .isTrue();
  }

  @Test
  void graftLengthensPlacementReachForAFewSecondsAfterAHarvest() {
    kit.mutate("herbalism", "architect");
    kit.learn("herbalism", "architect", Recipe.GRAFT, 1);
    player.setAttributeBase(Attributes.BLOCK_INTERACTION_RANGE, 4.5D);

    harvest();
    platform.scheduler().advance(1);
    assertThat(player.attributeValue(Attributes.BLOCK_INTERACTION_RANGE)).isCloseTo(7.5D, within(1.0E-9D));

    platform.scheduler().advance(101);
    assertThat(player.attributeValue(Attributes.BLOCK_INTERACTION_RANGE)).isCloseTo(4.5D, within(1.0E-9D));
  }

  @Test
  void withoutAReachAttributeAClickIntoTheAirPlacesWithinTheLongerReach() {
    kit.mutate("herbalism", "architect");
    kit.learn("herbalism", "architect", Recipe.GRAFT, 1);
    player.withoutAttributes(Attributes.BLOCK_INTERACTION_RANGE);
    platform.world().set(0, 62, 7, "stone");
    platform.world().set(0, 62, 11, "stone");
    harvest();
    player.holding(stone(10));

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      assertThat(clickAir().isCancelled()).isTrue();

      provenance.verify(() -> XpProvenance.recordPlacement(argThat((BlockRef block) -> block.z() == 6)));
    }
    assertThat(platform.world().blockAt(0, 62, 6).type()).isEqualTo(STONE);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(9);

    platform.world().set(0, 62, 6, "air");
    platform.world().set(0, 62, 7, "air");
    assertThat(clickAir().isCancelled()).isFalse();
    assertThat(platform.world().blockAt(0, 62, 10).type().isAir()).isTrue();
  }

  @Test
  void aPlacementAimedNearTheChalkGuideSnapsOntoIt() {
    kit.mutate("herbalism", "architect");
    kit.learn("herbalism", "architect", Recipe.GRAFT, 1);
    player.setAttributeBase(Attributes.BLOCK_INTERACTION_RANGE, 4.5D);
    ItemRef wand = ChalkWandItem.create(ChalkWandItem.Tool.STRAIGHTEDGE);
    ChalkWandItem.writePlan(wand, new ChalkWandItem.Plan(platform.world().uid(),
        List.of(new ChalkWandItem.Point(1, 62, 6), new ChalkWandItem.Point(4, 62, 6)), ChalkWandItem.Plane.XY));
    player.inventory().setItem(1, wand);
    harvest();
    platform.scheduler().advance(1);
    player.holding(stone(10));

    assertThat(clickAir().isCancelled()).isTrue();

    assertThat(platform.world().blockAt(1, 62, 6).type()).isEqualTo(STONE);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(9);
  }

  @Test
  void barterSpendsSwordDurabilityToReinforceTheNextEightPlacements() {
    kit.mutate("architect", "swords");
    kit.learn("architect", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);

    places(9);
    List<BlockRef> blast = new ArrayList<>();
    for (int z = 0; z < 9; z++) {
      blast.add(platform.world().blockAt(-4, 61, z));
    }
    EntityExplodeEvent explosion = adapt.post(new EntityExplodeEvent(
        MutationKit.zombie(platform.world(), -4.5D, 61D, 4.5D), new Loc(platform.world(), -4.5D, 61D, 4.5D), blast, 1F));

    assertThat(explosion.blocks()).extracting(BlockRef::z).containsExactly(8);
    assertThat(adapt.post(new BlockBurnEvent(platform.world().blockAt(-4, 61, 0), null)).isCancelled()).isTrue();
    assertThat(adapt.post(new BlockBurnEvent(platform.world().blockAt(-4, 61, 8), null)).isCancelled()).isFalse();
    assertThat(player.inventory().contains(STONE, 1)).isFalse();
  }

  @Test
  void reinforcementEndsAndNeverGivesABlockBack() {
    kit.mutate("architect", "swords");
    kit.learn("architect", "swords", Recipe.BARTER, 1);
    ((ArchitectBoon) MutationBlocks.find(kit.registry().getSkill("architect"), BlockSlot.BOON))
        .getConfig().reinforcedMillis = -1L;
    player.setSneaking(true);
    swap();

    place(new FakeItem(Id.minecraft("diamond_block"), 1));
    BlockExplodeEvent explosion = adapt.post(new BlockExplodeEvent(platform.world().blockAt(-4, 60, 0), null,
        List.of(platform.world().blockAt(-4, 61, 0)), 1F));

    assertThat(explosion.blocks()).hasSize(1);
    assertThat(player.inventory().contains(Id.minecraft("diamond_block"), 1)).isFalse();
  }

  @Test
  void barterSpendsFourBlocksFromTheHandAndRefusesWithFewer() {
    kit.mutate("swords", "architect");
    kit.learn("swords", "architect", Recipe.BARTER, 1);
    player.holding(stone(6));
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(2);
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(2);
  }

  @Test
  void attunementGrantsAttackSpeedOnlyAfterPlacing() {
    kit.mutate("architect", "swords");
    kit.learn("architect", "swords", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    places(1);
    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    platform.world().set(-3, 61, 8, "short_grass");
    FakeLiving zombie = MutationKit.zombie(platform.world(), 4.5D, 61D, 0.5D);

    places(8);
    kit.hit(player, zombie, 4D, true);
    assertThat(platform.world().blockAt(-3, 61, 8).type()).isEqualTo(GRASS);

    kit.mutate("architect", "swords");
    places(8);
    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(platform.world().blockAt(-3, 61, 8).type()).isEqualTo(GRASS);
    assertThat(platform.world().blockAt(2, 61, 0).type().isAir()).isTrue();
    assertThat(player.inventory().contains(STONE, 1)).isFalse();
  }

  private void places(int count) {
    for (int placement = 0; placement < count; placement++) {
      place(stone(10));
    }
  }

  private void place(FakeItem item) {
    int x = -4;
    int z = placed++;
    BlockStateRef replaced = platform.world().blockAt(x, 61, z).state();
    platform.world().set(x, 61, z, "stone");
    adapt.post(new BlockPlaceEvent(player, platform.world().blockAt(x, 61, z), replaced,
        platform.world().blockAt(x, 60, z), item, Hand.MAIN_HAND));
  }

  private void harvest() {
    platform.world().set(new BlockPos(-1, 61, 2), new FakeBlockState(Id.minecraft("wheat"), Map.of("age", "7")));
    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(-1, 61, 2), true, 0));
  }

  private PlayerInteractEvent clickAir() {
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private static FakeItem stone(int amount) {
    return new FakeItem(STONE, amount);
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
