package art.arcane.adapt.content.adaptation.golemancy.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemWorkSpeed;
import art.arcane.adapt.api.golem.GolemWorkStats;
import art.arcane.adapt.api.golem.GolemWorkStatus;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.golemancy.GolemTaskDoneEvent;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Golemancy with Herbalism, Swords and Chronos: golem task completion, commanding and speeding golems, and the work
 * strike repeating the player's job or the golem's own last task. Bound golems and their brain come from a stubbed
 * registry.
 */
class GolemancyBlocksRuntimeTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id IRON_NUGGET = Id.minecraft("iron_nugget");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;
  private final List<GolemHandle> golems = new ArrayList<>();
  private final GolemBrain brain = mock(GolemBrain.class);
  private MockedStatic<GolemancyRuntime> runtime;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Artificer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillGolemancy.class, SkillHerbalism.class, SkillSwords.class,
        SkillChronos.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    GolemRegistry registry = mock(GolemRegistry.class);
    when(registry.loadedOf(player.uuid())).thenAnswer(invocation -> List.copyOf(golems));
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(() -> GolemancyRuntime.brain(any(EntityRef.class))).thenReturn(Optional.of(brain));
  }

  @AfterEach
  void stop() {
    runtime.close();
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftGrowsCropsAroundAGolemThatFinishesATaskNearItsOwner() {
    kit.mutate("golemancy", "herbalism");
    kit.learn("golemancy", "herbalism", Recipe.GRAFT, 1);
    GolemHandle far = golem(40.5D, 61D, 0.5D, GolemCoreType.HARVESTER, GolemWorkStatus.WORKING);
    GolemHandle near = golem(6.5D, 61D, 0.5D, GolemCoreType.HARVESTER, GolemWorkStatus.WORKING);
    BlockRef nearCrop = crop(6, 61, 2, 3);
    BlockRef farCrop = crop(40, 61, 2, 3);

    finished(far);
    assertThat(farCrop.state().age()).isEqualTo(3);

    finished(near);
    platform.scheduler().advance(4);

    assertThat(nearCrop.state().age()).isEqualTo(4);
    assertThat(player.heardSounds()).containsSubsequence(SoundPalettes.of("golemancy").work(),
        SoundPalettes.of("herbalism").work());
  }

  @Test
  void echoHasTheNearestGolemReapTheNextMatureCropOfTheHarvestedKind() {
    kit.mutate("golemancy", "herbalism");
    kit.learn("golemancy", "herbalism", Recipe.ECHO, 1);
    golem(6.5D, 61D, 0.5D, GolemCoreType.GATHERER, GolemWorkStatus.WAITING);
    BlockRef young = crop(0, 61, 3, 3);
    crop(2, 61, 2, 7);
    platform.world().set(0, 61, 1, "carrots");

    adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));

    assertThat(platform.world().blockAt(2, 61, 2).type().isAir()).isTrue();
    assertThat(young.state().age()).isEqualTo(3);
    assertThat(platform.world().blockAt(0, 61, 1).type()).isEqualTo(Id.minecraft("carrots"));
    assertThat(platform.world().items()).extracting((FakeItemEntity item) -> item.item().type()).contains(WHEAT);
  }

  @Test
  void theGolemNeverReapsThroughAWall() {
    kit.mutate("golemancy", "herbalism");
    kit.learn("golemancy", "herbalism", Recipe.ECHO, 1);
    golem(6.5D, 61D, 0.5D, GolemCoreType.GATHERER, GolemWorkStatus.WAITING);
    BlockRef walled = crop(2, 61, 2, 7);
    for (int y = 61; y <= 64; y++) {
      for (int z = 0; z <= 3; z++) {
        platform.world().set(4, y, z, "stone");
      }
    }

    adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));

    assertThat(walled.type()).isEqualTo(WHEAT);
  }

  @Test
  void theGolemNeverReapsBeyondItsReach() {
    kit.mutate("golemancy", "herbalism");
    kit.learn("golemancy", "herbalism", Recipe.ECHO, 1);
    golem(12.5D, 61D, 0.5D, GolemCoreType.GATHERER, GolemWorkStatus.WAITING);
    BlockRef distant = crop(5, 61, 2, 7);

    adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));

    assertThat(distant.type()).isEqualTo(WHEAT);
  }

  @Test
  void theGolemRollsDropsWithItsOwnHandNotThePlayersTool() {
    kit.mutate("golemancy", "herbalism");
    kit.learn("golemancy", "herbalism", Recipe.ECHO, 1);
    MobRef body = golem(6.5D, 61D, 0.5D, GolemCoreType.GATHERER, GolemWorkStatus.WAITING).entity();
    FakeItem hoe = FakeItem.tool("netherite_hoe", 2031).enchant("fortune", 3);
    player.holding(hoe);
    BlockRef next = crop(2, 61, 2, 7);

    adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));

    assertThat(next.type().isAir()).isTrue();
    verify(body).equipment(EquipmentSlot.MAIN_HAND);
    assertThat(hoe.damage()).isZero();
  }

  @Test
  void echoNeverTakesABoneMealedCropAndNeedsAGolem() {
    kit.mutate("golemancy", "herbalism");
    kit.learn("golemancy", "herbalism", Recipe.ECHO, 1);
    BlockRef boneMealed = crop(2, 61, 2, 7);
    BlockRef natural = crop(-2, 61, 2, 7);

    adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));
    assertThat(natural.type()).isEqualTo(WHEAT);

    golem(0.5D, 61D, 4.5D, GolemCoreType.HARVESTER, GolemWorkStatus.WORKING);
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isBonemealed(argThat((BlockRef block) -> block != null && block.x() == 2)))
          .thenReturn(true);
      adapt.post(new BlockBreakEvent(player, crop(0, 61, 2, 7), true, 0));
    }

    assertThat(boneMealed.type()).isEqualTo(WHEAT);
    assertThat(platform.world().blockAt(-2, 61, 2).type().isAir()).isTrue();
  }

  @Test
  void graftUnderChronosHasAHarvesterRepeatItsOwnJobAroundItself() {
    kit.mutate("chronos", "golemancy");
    kit.learn("chronos", "golemancy", Recipe.GRAFT, 1);
    golem(6.5D, 61D, 0.5D, GolemCoreType.HARVESTER, GolemWorkStatus.WORKING);
    crop(-6, 61, 0, 7);
    crop(8, 61, 1, 7);
    player.holding(new FakeItem(Id.minecraft("clock"), 1));
    player.setSneaking(true);

    adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));

    assertThat(platform.world().blockAt(8, 61, 1).type().isAir()).isTrue();
    assertThat(platform.world().blockAt(-6, 61, 0).type()).isEqualTo(WHEAT);
  }

  @Test
  void graftUnderChronosHasASorterRepeatTheTaskItFinishedLast() {
    GolemHandle sorter = golem(6.5D, 61D, 0.5D, GolemCoreType.SORTER, GolemWorkStatus.WORKING);
    GolemTask stock = task(NodeType.INPUT, "stock", 7);
    crop(8, 61, 1, 7);

    repeatedUnderChronos(sorter, stock);

    assertThat(platform.world().blockAt(8, 61, 1).type()).isEqualTo(WHEAT);
  }

  @Test
  void graftUnderChronosHasARancherRepeatTheTaskItFinishedLast() {
    GolemHandle rancher = golem(-6.5D, 61D, 0.5D, GolemCoreType.RANCHER, GolemWorkStatus.WORKING);

    repeatedUnderChronos(rancher, task(NodeType.PASTURE, "shear", -7));
  }

  private void repeatedUnderChronos(GolemHandle golem, GolemTask last) {
    kit.mutate("chronos", "golemancy");
    kit.learn("chronos", "golemancy", Recipe.GRAFT, 1);
    GolemWorkStats.completed(golem, last);
    when(brain.repeat(last)).thenReturn(true);
    player.holding(new FakeItem(Id.minecraft("clock"), 1));
    player.setSneaking(true);

    adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));

    verify(brain).repeat(last);
    verify(golem.entity()).swingHand(Hand.MAIN_HAND);
  }

  @Test
  void barterSpeedsUpTheOwnersGolemsByHalfForTenSeconds() {
    kit.mutate("golemancy", "herbalism");
    kit.learn("golemancy", "herbalism", Recipe.BARTER, 1);
    GolemHandle golem = golem(6.5D, 61D, 0.5D, GolemCoreType.HARVESTER, GolemWorkStatus.WORKING);
    GolemHandle stranger = mock(GolemHandle.class);
    when(stranger.owner()).thenReturn(UUID.randomUUID());
    player.inventory().addItem(new FakeItem(Id.minecraft("wheat_seeds"), 3));
    assertThat(GolemWorkSpeed.intervalMultiplier(golem)).isEqualTo(1D);
    player.setSneaking(true);

    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));

    assertThat(swap.isCancelled()).isTrue();
    assertThat(GolemWorkSpeed.intervalMultiplier(golem)).isCloseTo(1D / 1.5D, within(1.0E-9D));
    assertThat(GolemWorkSpeed.intervalMultiplier(stranger)).isEqualTo(1D);
  }

  @Test
  void attunementFeedsOnlyWhileAGolemWorksNearby() {
    kit.mutate("golemancy", "herbalism");
    kit.learn("golemancy", "herbalism", Recipe.ATTUNEMENT, 1);
    player.setFoodLevel(10);
    golem(6.5D, 61D, 0.5D, GolemCoreType.HARVESTER, GolemWorkStatus.WAITING);
    golem(30.5D, 61D, 0.5D, GolemCoreType.HARVESTER, GolemWorkStatus.WORKING);

    tickEngine();
    assertThat(player.foodLevel()).isEqualTo(10);

    golem(-6.5D, 61D, 0.5D, GolemCoreType.LUMBER, GolemWorkStatus.WORKING);
    tickEngine();
    assertThat(player.foodLevel()).isEqualTo(12);
  }

  @Test
  void graftUnderSwordsSendsTheNearestGolemAfterTheCritTarget() {
    kit.mutate("swords", "golemancy");
    kit.learn("swords", "golemancy", Recipe.GRAFT, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    verify(brain, never()).command(any(EntityRef.class), anyLong());

    golem(6.5D, 61D, 0.5D, GolemCoreType.GUARDIAN, GolemWorkStatus.WAITING);
    platform.scheduler().advance(200);
    kit.hit(player, zombie, 4D, true);

    verify(brain).command(eq(zombie), anyLong());
  }

  @Test
  void echoUnderSwordsCutsTheArcWhenAGolemFinishes() {
    kit.mutate("swords", "golemancy");
    kit.learn("swords", "golemancy", Recipe.ECHO, 1);
    GolemHandle golem = golem(6.5D, 61D, 0.5D, GolemCoreType.HARVESTER, GolemWorkStatus.WORKING);
    platform.world().set(6, 61, 1, "short_grass");

    finished(golem);

    assertThat(platform.world().blockAt(6, 61, 1).type().isAir()).isTrue();
  }

  @Test
  void barterUnderSwordsSpendsANuggetAndRefusesWithoutOne() {
    kit.mutate("swords", "golemancy");
    kit.learn("swords", "golemancy", Recipe.BARTER, 1);
    player.holding(FakeItem.tool("iron_sword", 250));
    player.inventory().addItem(new FakeItem(IRON_NUGGET, 1));
    player.setSneaking(true);

    PlayerSwapHandItemsEvent paid = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));
    PlayerSwapHandItemsEvent refused = adapt.post(new PlayerSwapHandItemsEvent(player,
        player.inventory().mainHand(), player.inventory().offHand()));

    assertThat(paid.isCancelled()).isTrue();
    assertThat(player.inventory().contains(IRON_NUGGET, 1)).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
    assertThat(refused.isCancelled()).isFalse();
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    GolemHandle golem = golem(6.5D, 61D, 0.5D, GolemCoreType.HARVESTER, GolemWorkStatus.WORKING);
    BlockRef young = crop(6, 61, 2, 3);

    finished(golem);
    platform.scheduler().advance(4);

    assertThat(young.state().age()).isEqualTo(3);
  }

  private GolemHandle golem(double x, double y, double z, GolemCoreType core, GolemWorkStatus status) {
    MobRef body = mock(MobRef.class);
    when(body.uuid()).thenReturn(UUID.randomUUID());
    when(body.isValid()).thenReturn(true);
    when(body.location()).thenReturn(new Loc(platform.world(), x, y, z));
    when(body.eyeLocation()).thenReturn(new Loc(platform.world(), x, y + 2.2D, z));
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.owner()).thenReturn(player.uuid());
    when(handle.entity()).thenReturn(body);
    when(handle.core()).thenReturn(core);
    when(handle.workStatus()).thenReturn(status);
    when(handle.state()).thenReturn(mock(GolemState.class));
    golems.add(handle);
    return handle;
  }

  private void finished(GolemHandle golem) {
    adapt.post(new GolemTaskDoneEvent(player, golem, golem.entity().location(), task(NodeType.HARVEST, "harvest", 6)));
  }

  private GolemTask task(NodeType type, String job, int x) {
    NodeKey node = new NodeKey(platform.world().uid(), x, 61, 0, BlockFace.UP);
    return GolemTask.builder(node, type, job, GolemTarget.block(platform.world().uid(), x, 61, 1)).build();
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
