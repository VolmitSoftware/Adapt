package art.arcane.adapt.content.adaptation.excavation.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.api.world.BonusBreaks;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.protection.ProtectionProbes;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.item.ItemRef;
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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Excavation with Swords and Herbalism: each of the six blocks through the recipes that use it. */
class ExcavationBlocksRuntimeTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id GRASS = Id.minecraft("short_grass");
  private static final Id DIRT = Id.minecraft("dirt");

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
    player = platform.player("Dredger", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_shovel", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHerbalism.class, SkillExcavation.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftCutsTheArcOnTheEighthQuickDigAsLeadWithBothWorkCues() {
    kit.mutate("excavation", "swords");
    kit.learn("excavation", "swords", Recipe.GRAFT, 1);
    platform.world().set(4, 61, 1, "short_grass");

    digs(7);
    assertThat(platform.world().blockAt(4, 61, 1).type()).isEqualTo(GRASS);

    digs(1);
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(4, 61, 1).type().isAir()).isTrue();
    assertThat(player.heardSounds()).containsSubsequence(MutationEngine.palette(Id.adapt("excavation")).work(),
        MutationEngine.palette(Id.adapt("swords")).work());
  }

  @Test
  void echoCutsTheArcOnTheEighthQuickDigAsPartner() {
    kit.mutate("swords", "excavation");
    kit.learn("swords", "excavation", Recipe.ECHO, 1);
    platform.world().set(4, 61, 8, "short_grass");

    digs(8);

    assertThat(platform.world().blockAt(4, 61, 8).type().isAir()).isTrue();
  }

  @Test
  void digsOfPlacedBlocksDoNotPayOut() {
    kit.mutate("excavation", "swords");
    kit.learn("excavation", "swords", Recipe.GRAFT, 1);
    platform.world().set(4, 61, 8, "short_grass");

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      digs(8);
    }

    assertThat(platform.world().blockAt(4, 61, 8).type()).isEqualTo(GRASS);
  }

  @Test
  void echoMiresTheTargetOfASwordCrit() {
    kit.mutate("excavation", "swords");
    kit.learn("excavation", "swords", Recipe.ECHO, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    platform.world().set(2, 60, 0, "dirt");
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isCloseTo(0.6D, within(1.0E-9D));
    assertThat(platform.world().blockAt(2, 60, 0).type()).isEqualTo(Id.minecraft("mud"));
  }

  @Test
  void theMudTurnsBackToDirtWhenTheSlowEnds() {
    kit.mutate("excavation", "swords");
    kit.learn("excavation", "swords", Recipe.ECHO, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    platform.world().set(2, 60, 0, "coarse_dirt");

    kit.hit(player, MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D), 4D, true);
    platform.scheduler().advance(2);
    assertThat(platform.world().blockAt(2, 60, 0).type()).isEqualTo(Id.minecraft("mud"));

    platform.scheduler().advance(62);

    assertThat(platform.world().blockAt(2, 60, 0).type()).isEqualTo(Id.minecraft("coarse_dirt"));
  }

  @Test
  void aClaimThatRefusesTheBreakKeepsTheDirtButStillSlows() {
    kit.mutate("excavation", "swords");
    kit.learn("excavation", "swords", Recipe.ECHO, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    platform.world().set(2, 60, 0, "dirt");
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    try (MockedStatic<ProtectionProbes> probes = mockStatic(ProtectionProbes.class, CALLS_REAL_METHODS)) {
      probes.when(() -> ProtectionProbes.attemptBlockBreakProbe(any(PlayerRef.class), any(BlockRef.class)))
          .thenReturn(false);
      kit.hit(player, zombie, 4D, true);
      platform.scheduler().advance(2);
    }

    assertThat(platform.world().blockAt(2, 60, 0).type()).isEqualTo(DIRT);
    assertThat(zombie.attributeValue(Attributes.MOVEMENT_SPEED)).isCloseTo(0.6D, within(1.0E-9D));
  }

  @Test
  void convergenceDigsTheRingAroundTheLastDigWhileStandingInCrops() {
    kit.mutate("excavation", "herbalism");
    kit.learn("excavation", "herbalism", Recipe.CONVERGENCE, 1);
    platform.world().set(new BlockPos(0, 61, 0), new FakeBlockState(WHEAT, Map.of("age", "2")));
    ring(4, 60, 7);

    digs(8);
    platform.scheduler().advance(1);

    assertThat(platform.world().blockAt(5, 61, 7).type().isAir()).isTrue();
    assertThat(platform.world().blockAt(3, 59, 7).type().isAir()).isTrue();
    assertThat(dirtCollected()).isEqualTo(8);
  }

  @Test
  void theRingBreaksAsNormalBreaksAndWearsTheShovel() {
    kit.mutate("excavation", "herbalism");
    kit.learn("excavation", "herbalism", Recipe.CONVERGENCE, 1);
    platform.world().set(new BlockPos(0, 61, 0), new FakeBlockState(WHEAT, Map.of("age", "2")));
    ring(4, 60, 7);

    digs(8);
    platform.scheduler().advance(1);

    assertThat(platform.protection().broken()).hasSize(8);
    assertThat(player.inventory().mainHand().damage()).isEqualTo(8);
  }

  @Test
  void bonusBreaksNeverSetOffTheTrigger() {
    kit.learn("excavation", "swords", Recipe.GRAFT, 1);
    platform.world().set(4, 61, 8, "short_grass");

    for (int dig = 0; dig < 8; dig++) {
      BonusBreaks.breakBlock(player, platform.world().set(4, 60, dig, "dirt").blockAt(4, 60, dig), 0);
    }
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(4, 61, 8).type()).isEqualTo(GRASS);
  }

  @Test
  void anotherSkillPrimesTheNextSoftDigInstead() {
    kit.mutate("herbalism", "excavation");
    kit.learn("herbalism", "excavation", Recipe.GRAFT, 1);
    ring(0, 60, 3);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));
    assertThat(platform.world().blockAt(1, 61, 3).type()).isEqualTo(DIRT);

    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(0, 60, 3), true, 0));
    platform.world().set(0, 60, 3, "air");
    platform.scheduler().advance(1);
    assertThat(platform.world().blockAt(1, 61, 3).type().isAir()).isTrue();

    ring(0, 60, 3);
    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(0, 60, 3), true, 0));
    platform.world().set(0, 60, 3, "air");
    platform.scheduler().advance(1);
    assertThat(platform.world().blockAt(1, 61, 3).type()).isEqualTo(DIRT);
  }

  @Test
  void barterSpendsSwordDurabilityForOneSafeLanding() {
    kit.mutate("excavation", "swords");
    kit.learn("excavation", "swords", Recipe.BARTER, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);

    assertThat(fall().isCancelled()).isTrue();
    assertThat(fall().isCancelled()).isFalse();
  }

  @Test
  void barterSpendsEightSoilAndRefusesWithLess() {
    kit.mutate("swords", "excavation");
    kit.learn("swords", "excavation", Recipe.BARTER, 1);
    player.inventory().addItem(new FakeItem(DIRT, 5));
    player.inventory().addItem(new FakeItem(Id.minecraft("gravel"), 4));
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
    assertThat(player.inventory().contains(DIRT, 1)).isFalse();
    assertThat(player.inventory().contains(Id.minecraft("gravel"), 1)).isTrue();
    assertThat(player.inventory().contains(Id.minecraft("gravel"), 2)).isFalse();

    assertThat(swap().isCancelled()).isFalse();
  }

  @Test
  void attunementGrantsAttackSpeedOnlyAfterDigging() {
    kit.mutate("excavation", "swords");
    kit.learn("excavation", "swords", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    digs(1);
    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    platform.world().set(4, 61, 8, "short_grass");

    digs(8);
    assertThat(platform.world().blockAt(4, 61, 8).type()).isEqualTo(GRASS);

    kit.mutate("excavation", "swords");
    digs(8);
    assertThat(fall().isCancelled()).isFalse();
    assertThat(platform.world().blockAt(4, 61, 8).type()).isEqualTo(GRASS);
  }

  private void digs(int count) {
    for (int dig = 0; dig < count; dig++) {
      adapt.post(new BlockBreakEvent(player, platform.world().set(4, 60, dig, "dirt").blockAt(4, 60, dig), true, 0));
      platform.world().set(4, 60, dig, "air");
    }
  }

  /** Dirt the ring dropped: on the ground, or in the inventory where the kept Drop-To-Inventory put it. */
  private int dirtCollected() {
    int total = 0;
    for (FakeItemEntity item : platform.world().items()) {
      total += item.item().type().equals(DIRT) ? item.item().amount() : 0;
    }
    for (ItemRef item : player.inventory().contents()) {
      total += !item.isEmpty() && item.type().equals(DIRT) ? item.amount() : 0;
    }
    return total;
  }

  private void ring(int x, int y, int z) {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        platform.world().set(x + dx, y + dy, z, "dirt");
      }
    }
  }

  private BlockRef crop(int x, int y, int z) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", "7")));
    return platform.world().blockAt(x, y, z);
  }

  private EntityDamageEvent fall() {
    return adapt.post(new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), 6D, FinalDamage.IDENTITY));
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
