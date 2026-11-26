package art.arcane.adapt.content.adaptation.axe.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.axe.AxePreferences;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
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
import org.mockito.invocation.InvocationOnMock;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** The Axes blocks paired with Swords, and with Herbalism for the work Strike. */
class AxesBlocksRuntimeTest {
  private static final Id OAK_LOG = Id.minecraft("oak_log");
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id STRIPPED_OAK_LOG = Id.minecraft("stripped_oak_log");
  private static final Id OAK_PLANKS = Id.minecraft("oak_planks");
  private static final Id OAK_SAPLING = Id.minecraft("oak_sapling");
  private static final Id OAK_LEAVES = Id.minecraft("oak_leaves");
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id ABSORPTION = Id.minecraft("absorption");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private MutationKit kit;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Reaver", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_axe", 250));
    adapt = TestAdapt.start(platform, List.of(SkillAxes.class, SkillSwords.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftCutsArmorOnlyOnAFullyChargedAxeHitWithBothCues() {
    kit.mutate("axes", "swords");
    kit.learn("axes", "swords", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 0.5D, false);
    platform.scheduler().advance(30);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);

    kit.hit(player, zombie, 4D, false);
    platform.scheduler().advance(30);
    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
    assertThat(player.heardSounds()).containsSubsequence(SoundPalettes.of("axes").fight(),
        SoundPalettes.of("swords").fight());
  }

  @Test
  void echoAsPartnerBleedsTheTargetOfAChargedAxeHit() {
    kit.mutate("swords", "axes");
    kit.learn("swords", "axes", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, false);
    platform.scheduler().advance(30);

    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void aSwordCritCutsArmorByAShareForSixSeconds() {
    kit.mutate("swords", "axes");
    kit.learn("swords", "axes", Recipe.GRAFT, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    zombie.setAttributeBase(Attributes.ARMOR, 10D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);
    assertThat(zombie.attributeValue(Attributes.ARMOR)).isCloseTo(8.5D, within(1.0E-9D));

    platform.scheduler().advance(130);
    assertThat(zombie.attributeValue(Attributes.ARMOR)).isCloseTo(10D, within(1.0E-9D));
  }

  @Test
  void aHarvestPrimesTheNextLogToFellItsConnectedLogs() {
    kit.mutate("herbalism", "axes");
    kit.learn("herbalism", "axes", Recipe.GRAFT, 1);
    for (int y = 61; y <= 67; y++) {
      log(4, y, 4);
    }

    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(4, 61, 4), true, 0));
    assertThat(platform.world().blockAt(4, 62, 4).type()).isEqualTo(OAK_LOG);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(4, 61, 4), true, 0));

    for (int y = 62; y <= 67; y++) {
      assertThat(platform.world().blockAt(4, y, 4).type().isAir()).as("log at y %s", y).isTrue();
    }

    log(4, 62, 4);
    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(4, 61, 4), true, 0));
    assertThat(platform.world().blockAt(4, 62, 4).type()).isEqualTo(OAK_LOG);
  }

  @Test
  void felledLogsBreakAsNormalBreaksAndWearTheAxe() {
    kit.mutate("herbalism", "axes");
    kit.learn("herbalism", "axes", Recipe.GRAFT, 1);
    player.holding(FakeItem.tool("iron_axe", 250).withDamage(246));
    for (int y = 61; y <= 67; y++) {
      log(4, y, 4);
    }

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(4, 61, 4), true, 0));

    assertThat(platform.protection().broken()).hasSize(3);
    assertThat(player.inventory().mainHand().damage()).isEqualTo(249);
  }

  @Test
  void aPlayerPlacedLogNeitherFallsNorSpendsThePrime() {
    kit.mutate("herbalism", "axes");
    kit.learn("herbalism", "axes", Recipe.GRAFT, 1);
    BlockRef placed = log(4, 61, 4);
    log(4, 62, 4);
    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      adapt.post(new BlockBreakEvent(player, placed, true, 0));
    }
    assertThat(platform.world().blockAt(4, 62, 4).type()).isEqualTo(OAK_LOG);

    log(6, 61, 6);
    log(6, 62, 6);
    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(6, 61, 6), true, 0));
    assertThat(platform.world().blockAt(6, 62, 6).type().isAir()).isTrue();
  }

  @Test
  void aFelledTreeDropsStrippedLogs() {
    primeForester();
    for (int y = 61; y <= 64; y++) {
      log(4, y, 4);
    }

    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(4, 61, 4), true, 0));

    assertThat(droppedCount(STRIPPED_OAK_LOG)).isEqualTo(3);
    assertThat(droppedCount(OAK_LOG)).isZero();
  }

  @Test
  void thePlanksSettingTurnsFelledLogsIntoFourPlanksEachAndNoMore() {
    profile.getData().getPreferences().set("mutated-herbalism-axes-graft", "felled-logs", "PLANKS");
    primeForester();
    for (int y = 61; y <= 64; y++) {
      log(4, y, 4);
    }

    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(4, 61, 4), true, 0));

    assertThat(droppedCount(OAK_PLANKS)).isEqualTo(3 * 4);
    assertThat(droppedCount(OAK_LOG) + droppedCount(STRIPPED_OAK_LOG)).isZero();
  }

  @Test
  void theMutatedAdaptationOffersTheFelledLogsSetting() {
    kit.mutate("herbalism", "axes");
    MutationAdaptation graft = kit.adaptation("herbalism", "axes", Recipe.GRAFT);

    assertThat(graft.getPlayerPreferences()).contains(CommonPreferences.ENABLED, AxePreferences.FELLED_LOGS);
  }

  @Test
  void sneakingLeavesFelledLogsStripped() {
    primeForester();
    for (int y = 61; y <= 64; y++) {
      log(4, y, 4);
    }
    player.setSneaking(true);

    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(4, 61, 4), true, 0));

    assertThat(droppedCount(STRIPPED_OAK_LOG)).isEqualTo(3);
    assertThat(droppedCount(OAK_PLANKS)).isZero();
  }

  @Test
  void theTreeReplantsWithTheSaplingItsLeavesDropped() {
    primeForester();
    tree();
    dropSaplingFromFirstLeaf();

    fellTree();

    assertThat(platform.world().blockAt(4, 61, 4).type()).isEqualTo(OAK_SAPLING);
    assertThat(held(OAK_SAPLING)).isZero();
  }

  @Test
  void withoutASaplingFromTheLeavesNothingIsPlanted() {
    primeForester();
    tree();
    player.inventory().addItem(new FakeItem(OAK_SAPLING, 5));

    fellTree();

    assertThat(platform.world().blockAt(4, 61, 4).type().isAir()).isTrue();
    assertThat(held(OAK_SAPLING)).isEqualTo(5);
  }

  @Test
  void aBlockedReplantHandsBackTheOneSaplingTaken() {
    primeForester();
    tree();
    dropSaplingFromFirstLeaf();

    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(4, 61, 4), true, 0));
    platform.world().set(4, 61, 4, "air");
    platform.protection().deny(true);
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(4, 61, 4).type().isAir()).isTrue();
    assertThat(held(OAK_SAPLING)).isEqualTo(1);
  }

  @Test
  void playerPlacedLeavesAreNeitherBrokenNorASaplingSource() {
    primeForester();
    tree();
    dropSaplingFromFirstLeaf();

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class)))
          .thenAnswer((InvocationOnMock invocation) -> invocation.<BlockRef>getArgument(0).type().equals(OAK_LEAVES));
      fellTree();
    }

    assertThat(platform.world().blockAt(4, 65, 4).type()).isEqualTo(OAK_LEAVES);
    assertThat(platform.world().blockAt(4, 61, 4).type().isAir()).isTrue();
  }

  @Test
  void aPlayerPlacedTrunkIsNotConvertedOrReplanted() {
    primeForester();
    tree();
    dropSaplingFromFirstLeaf();

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      fellTree();
    }

    assertThat(platform.world().blockAt(4, 62, 4).type()).isEqualTo(OAK_LOG);
    assertThat(platform.world().blockAt(4, 61, 4).type().isAir()).isTrue();
    assertThat(droppedCount(STRIPPED_OAK_LOG)).isZero();
  }

  @Test
  void logsConvertOnlyAtTheCraftingRatio() {
    assertThat(AxesWorkStrike.planksOf(Id.minecraft("dark_oak_log"))).isEqualTo(Id.minecraft("dark_oak_planks"));
    assertThat(AxesWorkStrike.planksOf(Id.minecraft("stripped_cherry_wood"))).isEqualTo(Id.minecraft("cherry_planks"));
    assertThat(AxesWorkStrike.planksOf(Id.minecraft("mangrove_roots"))).isNull();
    assertThat(AxesWorkStrike.strippedOf(OAK_LOG)).isEqualTo(STRIPPED_OAK_LOG);
    assertThat(AxesWorkStrike.strippedOf(STRIPPED_OAK_LOG)).isNull();
    assertThat(AxesWorkStrike.strippedOf(Id.minecraft("mushroom_stem"))).isNull();
  }

  @Test
  void barterSpendsSwordDurabilityForAbsorptionHearts() {
    kit.mutate("axes", "swords");
    kit.learn("axes", "swords", Recipe.BARTER, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);

    PlayerSwapHandItemsEvent swap = swap();

    assertThat(swap.isCancelled()).isTrue();
    EffectInstance absorption = player.effect(ABSORPTION);
    assertThat(absorption).isNotNull();
    assertThat(absorption.amplifier()).isZero();
    assertThat(absorption.durationTicks()).isEqualTo(120);
  }

  @Test
  void theAxePaysFiveDurabilityAndRefusesWhenNearlyBroken() {
    kit.mutate("swords", "axes");
    kit.learn("swords", "axes", Recipe.BARTER, 1);
    player.setSneaking(true);
    player.holding(FakeItem.tool("iron_axe", 250).withDamage(245));

    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(245);

    player.holding(FakeItem.tool("iron_axe", 250));
    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void attunementHoldsAfterChoppingWood() {
    kit.mutate("axes", "swords");
    kit.learn("axes", "swords", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    adapt.post(new BlockBreakEvent(player, log(3, 61, 3), true, 0));
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergenceAsksForAnAxeSwingBeforeTheSwordCrit() {
    kit.mutate("swords", "axes");
    kit.learn("swords", "axes", Recipe.CONVERGENCE, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    zombie.setAttributeBase(Attributes.ARMOR, 10D);
    player.holding(FakeItem.tool("iron_sword", 250));

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);
    assertThat(zombie.attributeValue(Attributes.ARMOR)).isEqualTo(10D);

    player.holding(FakeItem.tool("iron_axe", 250));
    kit.hit(player, zombie, 0.5D, false);
    player.holding(FakeItem.tool("iron_sword", 250));
    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);
    assertThat(zombie.attributeValue(Attributes.ARMOR)).isCloseTo(8.5D, within(1.0E-9D));
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    zombie.setAttributeBase(Attributes.ARMOR, 10D);

    kit.hit(player, zombie, 4D, false);
    platform.scheduler().advance(2);

    assertThat(zombie.attributeValue(Attributes.ARMOR)).isEqualTo(10D);
  }

  private void primeForester() {
    kit.mutate("herbalism", "axes");
    kit.learn("herbalism", "axes", Recipe.GRAFT, 1);
    platform.world().set(4, 60, 4, "dirt");
    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
  }

  private void tree() {
    for (int y = 61; y <= 64; y++) {
      log(4, y, 4);
    }
    for (int x = 3; x <= 5; x++) {
      for (int z = 3; z <= 5; z++) {
        platform.world().set(x, 65, z, "oak_leaves");
        if (x != 4 || z != 4) {
          platform.world().set(x, 64, z, "oak_leaves");
        }
      }
    }
  }

  private void fellTree() {
    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(4, 61, 4), true, 0));
    platform.world().set(4, 61, 4, "air");
    platform.scheduler().advance(4);
  }

  private void dropSaplingFromFirstLeaf() {
    AtomicBoolean dropped = new AtomicBoolean();
    adapt.bus().register(this, BlockDropItemEvent.class, EventPriority.LOWEST, false, (BlockDropItemEvent e) -> {
      if (e.brokenState().type().equals(OAK_LEAVES) && !e.items().isEmpty() && dropped.compareAndSet(false, true)) {
        e.items().get(0).setItem(new FakeItem(OAK_SAPLING, 1));
      }
    });
  }

  /** What the breaks yielded: on the ground, or in the inventory where the kept Drop-To-Inventory put it. */
  private int droppedCount(Id type) {
    int count = held(type);
    for (FakeItemEntity item : platform.world().items()) {
      if (item.isValid() && item.item().type().equals(type)) {
        count += item.item().amount();
      }
    }
    return count;
  }

  private int held(Id type) {
    int count = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && item.type().equals(type)) {
        count += item.amount();
      }
    }
    return count;
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private BlockRef log(int x, int y, int z) {
    platform.world().set(x, y, z, "oak_log");
    return platform.world().blockAt(x, y, z);
  }

  private BlockRef crop(int x, int y, int z) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", "7")));
    return platform.world().blockAt(x, y, z);
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
