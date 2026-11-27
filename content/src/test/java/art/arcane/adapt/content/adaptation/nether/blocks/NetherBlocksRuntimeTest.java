package art.arcane.adapt.content.adaptation.nether.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationBlocks;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerBucketEmptyEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.Environment;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Nether with Swords in both orders, and with Herbalism for the smelting work Strike. */
class NetherBlocksRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id BLAZE_POWDER = Id.minecraft("blaze_powder");
  private static final Id FIRE_RESISTANCE = Id.minecraft("fire_resistance");

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
    player = platform.player("Ashwalker", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHerbalism.class, SkillNether.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftAsLeadBleedsANearbyHostileWhenBurnedWithBothCues() {
    kit.mutate("nether", "swords");
    kit.learn("nether", "swords", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    adapt.post(new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), 4D, FinalDamage.IDENTITY));
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);

    burn();
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
    assertThat(player.heardSounds()).containsSubsequence(MutationEngine.palette(Id.adapt("nether")).fight(),
        MutationEngine.palette(Id.adapt("swords")).fight());
  }

  @Test
  void graftAsLeadCutsTheArcWhenBurnedWithNoHostileNear() {
    kit.mutate("nether", "swords");
    kit.learn("nether", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 1, "short_grass");

    burn();

    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isTrue();
  }

  @Test
  void echoAsPartnerBleedsANearbyHostileWhenBurned() {
    kit.mutate("swords", "nether");
    kit.learn("swords", "nether", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    burn();
    platform.scheduler().advance(10);

    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void aSwordCritSetsTheTargetBurning() {
    kit.mutate("swords", "nether");
    kit.learn("swords", "nether", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(zombie.fireTicks()).isEqualTo(80);
  }

  @Test
  void aHarvestSmeltsTheNextThreeBlocksThatHaveSomethingToSmelt() {
    kit.mutate("herbalism", "nether");
    kit.learn("herbalism", "nether", Recipe.GRAFT, 1);

    assertThat(drop("raw_iron")).isEqualTo(Id.minecraft("raw_iron"));

    adapt.post(new BlockBreakEvent(player, crop(1, 61, 2, 7), true, 0));

    assertThat(drop("dirt")).isEqualTo(Id.minecraft("dirt"));
    assertThat(drop("raw_iron")).isEqualTo(Id.minecraft("iron_ingot"));
    assertThat(drop("oak_log")).isEqualTo(Id.minecraft("charcoal"));
    assertThat(drop("iron_ore")).isEqualTo(Id.minecraft("iron_ingot"));
    assertThat(drop("sand")).isEqualTo(Id.minecraft("sand"));
  }

  @Test
  void theSmeltingPrimeRunsOutWhenUnused() {
    kit.mutate("herbalism", "nether");
    kit.learn("herbalism", "nether", Recipe.GRAFT, 1);
    ((NetherWorkStrike) MutationBlocks.find(kit.registry().getSkill("nether"), BlockSlot.STRIKE_WORK))
        .getConfig().primeMillis = -1L;

    adapt.post(new BlockBreakEvent(player, crop(1, 61, 2, 7), true, 0));

    assertThat(drop("raw_iron")).isEqualTo(Id.minecraft("raw_iron"));
  }

  @Test
  void aPlayerPlacedBlockIsNotSmelted() {
    kit.mutate("herbalism", "nether");
    kit.learn("herbalism", "nether", Recipe.GRAFT, 1);
    adapt.post(new BlockBreakEvent(player, crop(1, 61, 2, 7), true, 0));

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      assertThat(drop("sand")).isEqualTo(Id.minecraft("sand"));
    }
    assertThat(drop("sand")).isEqualTo(Id.minecraft("glass"));
  }

  @Test
  void aBurnFromANaturalSourceGrowsThePartnersCrops() {
    kit.mutate("nether", "herbalism");
    kit.learn("nether", "herbalism", Recipe.GRAFT, 1);
    platform.world().set(1, 60, 0, "magma_block");
    BlockRef wheat = crop(2, 61, 2, 3);

    adapt.post(new EntityDamageEvent(player, DamageCause.HOT_FLOOR, Id.minecraft("hot_floor"), 1D, FinalDamage.IDENTITY));

    assertThat(wheat.state().property("age")).isEqualTo("4");
  }

  @Test
  void aBurnFromAPlacedSourceWithinTwoBlocksPaysNothingAndItsLingeringBurnStaysFarmed() {
    kit.mutate("nether", "herbalism");
    kit.learn("nether", "herbalism", Recipe.GRAFT, 1);
    BlockRef magma = platform.world().blockAt(1, 60, 0);
    platform.world().set(1, 60, 0, "magma_block");
    BlockRef wheat = crop(2, 61, 2, 3);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class)))
          .thenAnswer((org.mockito.invocation.InvocationOnMock call) -> magma.pos().equals(((BlockRef) call.getArgument(0)).pos()));
      adapt.post(new EntityDamageEvent(player, DamageCause.HOT_FLOOR, Id.minecraft("hot_floor"), 1D, FinalDamage.IDENTITY));
    }
    platform.world().set(1, 60, 0, "stone");
    burn();

    assertThat(wheat.state().property("age")).isEqualTo("3");
  }

  @Test
  void aBurnFromLavaPouredFromABucketPaysNothing() {
    kit.mutate("nether", "herbalism");
    kit.learn("nether", "herbalism", Recipe.GRAFT, 1);
    BlockRef pool = platform.world().blockAt(1, 60, 0);
    BlockRef wheat = crop(2, 61, 2, 3);

    Set<BlockPos> placed = new HashSet<>();
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.recordPlacement(any(BlockRef.class)))
          .thenAnswer((InvocationOnMock call) -> placed.add(((BlockRef) call.getArgument(0)).pos()));
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class)))
          .thenAnswer((InvocationOnMock call) -> placed.contains(((BlockRef) call.getArgument(0)).pos()));
      adapt.post(new PlayerBucketEmptyEvent(player, pool, Id.minecraft("lava_bucket")));
      platform.world().set(1, 60, 0, "lava");
      adapt.post(new EntityDamageEvent(player, DamageCause.LAVA, Id.minecraft("lava"), 1D, FinalDamage.IDENTITY));
    }

    assertThat(placed).containsExactly(pool.pos());
    assertThat(wheat.state().property("age")).isEqualTo("3");
  }

  @Test
  void aFlameBurningAgainstAPlacedBlockPaysNothing() {
    kit.mutate("nether", "herbalism");
    kit.learn("nether", "herbalism", Recipe.GRAFT, 1);
    platform.world().set(1, 61, 0, "fire");
    BlockRef wool = platform.world().blockAt(2, 61, 0);
    platform.world().set(2, 61, 0, "white_wool");
    BlockRef wheat = crop(2, 61, 2, 3);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class)))
          .thenAnswer((org.mockito.invocation.InvocationOnMock call) -> wool.pos().equals(((BlockRef) call.getArgument(0)).pos()));
      adapt.post(new EntityDamageEvent(player, DamageCause.FIRE, Id.minecraft("in_fire"), 1D, FinalDamage.IDENTITY));
    }

    assertThat(wheat.state().property("age")).isEqualTo("3");
  }

  @Test
  void aBurnFromNaturalLavaStillPays() {
    kit.mutate("nether", "herbalism");
    kit.learn("nether", "herbalism", Recipe.GRAFT, 1);
    platform.world().set(1, 60, 0, "lava");
    BlockRef wheat = crop(2, 61, 2, 3);

    adapt.post(new EntityDamageEvent(player, DamageCause.LAVA, Id.minecraft("lava"), 1D, FinalDamage.IDENTITY));

    assertThat(wheat.state().property("age")).isEqualTo("4");
  }

  @Test
  void aFlameAmongNaturalBlocksStillPays() {
    kit.mutate("nether", "herbalism");
    kit.learn("nether", "herbalism", Recipe.GRAFT, 1);
    platform.world().set(1, 61, 0, "fire");
    platform.world().set(2, 61, 0, "white_wool");
    BlockRef wheat = crop(2, 61, 2, 3);

    adapt.post(new EntityDamageEvent(player, DamageCause.FIRE, Id.minecraft("in_fire"), 1D, FinalDamage.IDENTITY));

    assertThat(wheat.state().property("age")).isEqualTo("4");
  }

  @Test
  void barterGrantsFireResistanceForSwordDurability() {
    kit.mutate("nether", "swords");
    kit.learn("nether", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();

    assertThat(player.hasEffect(FIRE_RESISTANCE)).isTrue();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
  }

  @Test
  void barterSpendsBlazePowderForAttackSpeedAndRefusesWithoutIt() {
    kit.mutate("swords", "nether");
    kit.learn("swords", "nether", Recipe.BARTER, 1);
    player.inventory().addItem(new FakeItem(BLAZE_POWDER, 1));
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().contains(BLAZE_POWDER, 1)).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

    assertThat(swap().isCancelled()).isFalse();
  }

  @Test
  void attunementGrantsAttackSpeedWhileBurningOrInTheNether() {
    kit.mutate("nether", "swords");
    kit.learn("nether", "swords", Recipe.ATTUNEMENT, 3);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.setFireTicks(40);
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.3D, within(1.0E-9D));
  }

  @Test
  void theNetherItselfHoldsTheState() {
    kit.mutate("nether", "swords");
    kit.learn("nether", "swords", Recipe.ATTUNEMENT, 1);
    platform.world().environment(Environment.NETHER, 0L);

    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void nothingHappensWithoutTheMutationOrWithoutLearningIt() {
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    burn();
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);

    kit.mutate("nether", "swords");
    burn();
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);
    assertThat(zombie.fireTicks()).isZero();
  }

  private void burn() {
    adapt.post(new EntityDamageEvent(player, DamageCause.FIRE_TICK, Id.minecraft("on_fire"), 1D, FinalDamage.IDENTITY));
  }

  private Id drop(String item) {
    BlockRef block = platform.world().blockAt(4, 61, 4);
    FakeItemEntity dropped = new FakeItemEntity(new Loc(platform.world(), 4.5D, 61D, 4.5D),
        new FakeItem(Id.minecraft(item), 1));
    List<ItemEntityRef> items = List.of(dropped);
    BlockDropItemEvent event = adapt.post(new BlockDropItemEvent(player, block,
        new FakeBlockState(Id.minecraft("stone"), Map.of()), items));
    return event.items().get(0).item().type();
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
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
