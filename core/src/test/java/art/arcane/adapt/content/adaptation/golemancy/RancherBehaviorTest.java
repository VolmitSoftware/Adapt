package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class RancherBehaviorTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000025b1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000025b2");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000025b3");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000025b4");
  private static final NodeKey KEY = new NodeKey(WORLD, 0, 63, 0, BlockFace.UP);
  private static final GolemAnchor CHEST = GolemAnchor.of(WORLD, 8, 64, 8);
  private static final long NOW = 90_000L;

  private final GolemRancherCore.Config config = new GolemRancherCore.Config();
  private final WorldRef world = mock(WorldRef.class, withSettings().strictness(Strictness.LENIENT));
  private final List<MobRef> animals = new ArrayList<>();
  private final AnimalOps animalOps = mock(AnimalOps.class, withSettings().strictness(Strictness.LENIENT));
  private final Map<UUID, List<ItemRef>> lootTables = new HashMap<>();
  private final List<UUID> lootRolls = new ArrayList<>();
  private final List<NodeIndex.Entry> nearby = new ArrayList<>();
  private final List<ItemRef> cargo = new ArrayList<>();
  private final List<ItemRef> dropped = new ArrayList<>();
  private final ItemRef[] chest = new ItemRef[27];
  private final Random random = mock(Random.class);
  private NodeRecord record = NodeRecord.builder(OWNER, NodeType.PASTURE, BlockFace.UP).area(NodeCuboid.symmetric(6, 2, 6)).build();
  private GolemRancherCore rancher;
  private RancherActions actions;
  private RancherBehavior behavior;
  private NodeService nodes;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<RancherFeedback> feedback;
  private GolemAnchor homeChest = CHEST;
  private GolemBody body = GolemBody.COPPER;
  private int channels = ChannelMask.NONE;
  private int level = 5;
  private boolean mobLoot = true;
  private EntityDeathEvent death;
  private UUID current;

  @BeforeEach
  void setUp() {
    lenient().when(animalOps.isPeacefulAnimalType(any())).thenAnswer(invocation -> PeacefulAnimals.isPeaceful(invocation.getArgument(0)));
    lenient().when(AdaptCore.platform().host().animals()).thenReturn(animalOps);
    List<Id> species = RancherRules.parseSpecies(config.species);
    rancher = mock(GolemRancherCore.class);
    lenient().when(rancher.config()).thenReturn(config);
    lenient().when(rancher.ownerLevel(OWNER)).thenAnswer(invocation -> level);
    lenient().when(rancher.species()).thenReturn(species);
    lenient().when(random.nextInt(anyInt())).thenReturn(1);
    ItemOps items = mock(ItemOps.class);
    lenient().when(items.empty()).thenAnswer(invocation -> FakeItem.empty());
    lenient().when(AdaptCore.platform().host().items()).thenReturn(items);
    actions = new RancherActions(rancher, new RancherActions.Hooks(GolemTestItems::stack, () -> random, ignored -> mobLoot, this::rollLoot));
    behavior = new RancherBehavior(rancher, actions);
    lenient().when(world.uid()).thenReturn(WORLD);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    lenient().when(world.entitiesIn(any(Box.class), any())).thenAnswer(invocation -> {
      Box box = invocation.getArgument(0);
      Predicate<EntityRef> filter = invocation.getArgument(1);
      List<EntityRef> found = new ArrayList<>();
      for (MobRef animal : animals) {
        Loc at = animal.location();
        if (contains(box, at) && (filter == null || filter.test(animal))) {
          found.add(animal);
        }
      }
      return found;
    });
    lenient().when(world.blockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
      BlockRef block = mock(BlockRef.class);
      lenient().when(block.x()).thenReturn(invocation.getArgument(0));
      lenient().when(block.y()).thenReturn(invocation.getArgument(1));
      lenient().when(block.z()).thenReturn(invocation.getArgument(2));
      return block;
    });
    lenient().when(world.dropItemNaturally(any(Vec3.class), any(ItemRef.class))).thenAnswer(invocation -> {
      dropped.add(invocation.getArgument(1));
      return null;
    });
    NodeIndex index = mock(NodeIndex.class);
    lenient().when(index.near(eq(WORLD), anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> List.copyOf(nearby));
    nodes = mock(NodeService.class);
    lenient().when(nodes.index()).thenReturn(index);
    lenient().when(nodes.queryDue(any(NodeKey.class), anyLong())).thenReturn(true);
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    feedback = mockStatic(RancherFeedback.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(nodes));
  }

  @AfterEach
  void close() {
    feedback.close();
    runtime.close();
  }

  @Test
  void anEmptyPastureDefersItsQueryAndADeferredSeekDoesNotSurveyEntities() {
    node();
    assertThat(seek(golem(0))).isEmpty();
    verify(nodes).emptyQuery(KEY, record, NOW);
    when(nodes.queryDue(KEY, NOW)).thenReturn(false);
    clearInvocations(world);

    assertThat(seek(golem(0))).isEmpty();

    verify(world, never()).entitiesIn(any(Box.class), any());
  }

  @Test
  void unloadedPastureCoverageIsNotCachedAsAnEmptyHerd() {
    node();
    when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

    assertThat(seek(golem(0))).isEmpty();

    verify(nodes, never()).emptyQuery(any(NodeKey.class), any(NodeRecord.class), anyLong());
  }

  @Test
  void itWorksForTheRancherCoreAdaptation() {
    assertThat(behavior.policy()).isSameAs(rancher);
    assertThat(behavior.actIntervalTicks(golem(0), task("breed", cow(1, 64, 1)))).isEqualTo(config.actionIntervalTicks);
  }

  @Test
  void aReadyPairBelowTheKeepCountIsFedNearestFirst() {
    node();
    MobRef far = cow(4, 64, 0);
    MobRef near = cow(1, 64, 0);
    carry(Id.minecraft("wheat"), 2);

    assertThat(seek(golem(0))).isEqualTo("breed " + near.uuid());

    cargo.clear();
    assertThat(seek(golem(0))).isEmpty();
    assertThat(far.uuid()).isNotEqualTo(near.uuid());
  }

  @Test
  void onlyLoveARancherStartedGetsAPartner() {
    node();
    MobRef waiting = cow(1, 64, 0);
    when(animalOps.isInLove(waiting)).thenReturn(true);
    MobRef partner = cow(3, 64, 0);
    carry(Id.minecraft("wheat"), 1);

    assertThat(seek(golem(0))).isEqualTo("breed " + partner.uuid());

    when(animalOps.loveCause(waiting)).thenReturn(STRANGER);
    assertThat(seek(golem(0))).isEmpty();
  }

  @Test
  void breedingFoodCanComeFromTheLinkedHomeChest() {
    node();
    MobRef near = cow(1, 64, 0);
    cow(4, 64, 0);
    stock(4, Id.minecraft("wheat"), 2);

    assertThat(seek(golem(0))).isEqualTo("breed " + near.uuid());

    stock(4, Id.minecraft("wheat"), 1);
    assertThat(seek(golem(0))).isEmpty();

    stock(4, Id.minecraft("wheat"), 2);
    homeChest = null;
    assertThat(seek(golem(0))).isEmpty();
  }

  @Test
  void onlyTheOwnersPastureNodesOnAMatchingChannelCount() {
    cow(1, 64, 0);
    cow(2, 64, 0);
    carry(Id.minecraft("wheat"), 2);
    record = NodeRecord.builder(STRANGER, NodeType.PASTURE, BlockFace.UP).area(NodeCuboid.symmetric(6, 2, 6)).build();
    node();
    assertThat(seek(golem(0))).isEmpty();

    nearby.clear();
    record = NodeRecord.builder(OWNER, NodeType.PASTURE, BlockFace.UP).area(NodeCuboid.symmetric(6, 2, 6)).channel(DyeColor.RED).build();
    node();
    assertThat(seek(golem(0))).isEmpty();

    channels = ChannelMask.of(DyeColor.RED);
    assertThat(seek(golem(0))).startsWith("breed ");
  }

  @Test
  void theHerdIsQueriedFromTheLopsidedPastureBoxOnly() {
    NodeRecord lopsided = NodeRecord.builder(OWNER, NodeType.PASTURE, BlockFace.UP).area(new NodeCuboid(0, 0, -1, 8, 1, 1)).build();
    MobRef inside = cow(8, 64, 1);
    cow(-1, 64, 0);
    cow(9, 64, 0);
    cow(4, 66, 0);
    ArgumentCaptor<Box> query = ArgumentCaptor.forClass(Box.class);

    List<MobRef> herd = actions.herd(world, KEY, lopsided);

    assertThat(herd).containsExactly(inside);
    verify(world).entitiesIn(query.capture(), any());
    assertThat(query.getValue()).isEqualTo(new Box(0D, 64D, -1D, 9D, 66D, 2D));
  }

  @Test
  void higherPriorityPasturesComeFirstThenNearerOnes() {
    NodeKey farKey = new NodeKey(WORLD, 20, 63, 0, BlockFace.UP);
    NodeRecord far = NodeRecord.builder(OWNER, NodeType.PASTURE, BlockFace.UP).area(NodeCuboid.symmetric(6, 2, 6)).priority(5).build();
    node();
    nearby.add(new NodeIndex.Entry(farKey, far));
    MobRef near = cow(1, 64, 0);
    cow(2, 64, 0);
    MobRef distant = cow(21, 64, 0);
    cow(22, 64, 0);
    carry(Id.minecraft("wheat"), 2);

    assertThat(behavior.seek(golem(0)).map(task -> task.target().entity())).contains(distant.uuid());

    nearby.set(1, new NodeIndex.Entry(farKey, far.toBuilder().priority(NodeRecord.DEFAULT_PRIORITY).build()));
    assertThat(seek(golem(0))).isEqualTo("breed " + near.uuid());
  }

  @Test
  void animalsOutsideTheAreaOrOfAnotherSpeciesAreLeftAlone() {
    node();
    cow(9, 64, 0);
    cow(10, 64, 0);
    carry(Id.minecraft("wheat"), 4);
    assertThat(seek(golem(0))).isEmpty();

    animals.clear();
    cow(1, 64, 0);
    cow(2, 64, 0);
    nearby.clear();
    record = record.toBuilder().data(Map.of("species", "SHEEP")).build();
    node();
    assertThat(seek(golem(0))).isEmpty();
  }

  @Test
  void skippedAnimalsAndAnimalsBeyondTheWorkRadiusArePassedOver() {
    node();
    MobRef near = cow(1, 64, 0);
    MobRef far = cow(3, 64, 0);
    carry(Id.minecraft("wheat"), 2);
    GolemContext golem = golem(0);
    when(golem.skips(any(GolemTask.class))).thenAnswer(invocation -> invocation.<GolemTask>getArgument(0).target().entity().equals(near.uuid()));

    assertThat(seek(golem)).isEqualTo("breed " + far.uuid());

    when(golem.inWorkRadius(any(Loc.class))).thenAnswer(invocation -> invocation.<Loc>getArgument(0).x() < 2D);
    assertThat(seek(golem)).isEmpty();
  }

  @Test
  void ironRanchersCullTheNearestSpareAdultOnlyWhenCullingIsOn() {
    body = GolemBody.IRON;
    record = record.toBuilder().data(Map.of("keep", "2")).build();
    node();
    cow(3, 64, 0);
    MobRef nearest = cow(1, 64, 0);
    cow(2, 64, 0);
    assertThat(seek(golem(0))).isEmpty();

    nearby.clear();
    record = RancherRules.toggle(record, RancherRules.Action.CULL);
    node();
    assertThat(seek(golem(0))).isEqualTo("cull " + nearest.uuid());

    body = GolemBody.COPPER;
    assertThat(seek(golem(0))).isEmpty();
    body = GolemBody.IRON;
    level = 3;
    assertThat(seek(golem(0))).isEmpty();
  }

  @Test
  void feedingWindsUpThenTakesOneFoodFromCargoAndStartsLoveMode() {
    MobRef target = cow(1, 64, 0);
    cow(2, 64, 0);
    carry(Id.minecraft("wheat"), 2);
    stock(3, Id.minecraft("wheat"), 5);

    assertThat(behavior.act(golem(0), task("breed", target))).isEqualTo(GolemActResult.WORKING);
    assertThat(count(cargo, Id.minecraft("wheat"))).isEqualTo(2);

    level = 4;
    GolemContext golem = golem(1);
    assertThat(behavior.act(golem, task("breed", target))).isEqualTo(GolemActResult.DONE);

    assertThat(count(cargo, Id.minecraft("wheat"))).isEqualTo(1);
    assertThat(chest[3].amount()).isEqualTo(5);
    verify(animalOps).setInLove(target, RancherRules.LOVE_TICKS);
    verify(golem).workXp(config.workXpPerPair / 2D);
    verify(rancher).markFed(target.uuid(), NOW, false);
    feedback.verify(() -> RancherFeedback.play(eq(rancher), any(Loc.class), eq(RancherFeedback.Kind.BREED)));
  }

  @Test
  void feedingFallsBackToTheHomeChestAndHusbandryMarksTheParent() {
    MobRef target = cow(1, 64, 0);
    stock(3, Id.minecraft("wheat"), 3);

    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.DONE);

    assertThat(chest[3].amount()).isEqualTo(2);
    verify(rancher).markFed(target.uuid(), NOW, true);
  }

  @Test
  void foodTakenFromTheHomeChestOpensItsLidButFoodFromCargoDoesNot() {
    MobRef target = cow(1, 64, 0);
    stock(3, Id.minecraft("wheat"), 3);
    GolemContext fromChest = golem(1);

    assertThat(behavior.act(fromChest, task("breed", target))).isEqualTo(GolemActResult.DONE);
    verify(fromChest).visit(argThat(this::isHomeChest));

    carry(Id.minecraft("wheat"), 1);
    GolemContext fromCargo = golem(1);
    assertThat(behavior.act(fromCargo, task("breed", target))).isEqualTo(GolemActResult.DONE);
    verify(fromCargo, never()).visit(any(BlockRef.class));
  }

  @Test
  void aBucketOrBowlTakenFromTheHomeChestOpensItsLid() {
    MobRef cow = cow(1, 64, 0);
    stock(2, Id.minecraft("bucket"), 1);
    GolemContext milking = golem(1);

    assertThat(behavior.act(milking, task("milk", cow))).isEqualTo(GolemActResult.DONE);
    verify(milking).visit(argThat(this::isHomeChest));

    MobRef mooshroom = mooshroom(2, 64, 0);
    stock(5, Id.minecraft("bowl"), 2);
    GolemContext stewing = golem(1);
    assertThat(behavior.act(stewing, task("milk", mooshroom))).isEqualTo(GolemActResult.DONE);
    verify(stewing).visit(argThat(this::isHomeChest));
  }

  @Test
  void aTakeThatFindsNothingInTheHomeChestLeavesItsLidAlone() {
    MobRef cow = cow(1, 64, 0);
    GolemContext golem = golem(1);

    assertThat(behavior.act(golem, task("milk", cow))).isEqualTo(GolemActResult.FAILED);

    verify(golem, never()).visit(any(BlockRef.class));
  }

  @Test
  void feedingWithoutFoodAtTheKeepCountOrUnderProtectionNeverFeeds() {
    MobRef target = cow(1, 64, 0);
    cow(2, 64, 0);
    assertThat(behavior.act(golem(0), task("breed", target))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.FAILED);

    carry(Id.minecraft("wheat"), 2);
    record = record.toBuilder().data(Map.of("keep", "2")).build();
    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.FAILED);

    record = record.toBuilder().data(Map.of()).build();
    GolemContext denied = golem(1);
    when(denied.canHandle(target)).thenReturn(false);
    assertThat(behavior.act(denied, task("breed", target))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(count(cargo, Id.minecraft("wheat"))).isEqualTo(2);
    verify(animalOps, never()).setInLove(eq(target), anyInt());
  }

  @Test
  void theKeepCountCountsOnlyTheTargetsSpecies() {
    record = record.toBuilder().data(Map.of("keep", "3")).build();
    MobRef target = cow(1, 64, 0);
    cow(2, 64, 0);
    sheep(3, 64, 0, DyeColor.WHITE);
    sheep(4, 64, 0, DyeColor.WHITE);
    sheep(5, 64, 0, DyeColor.WHITE);
    carry(Id.minecraft("wheat"), 1);

    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.DONE);
  }

  @Test
  void aCalfInTheAreaBlocksFeedingAtTheKeepCount() {
    record = record.toBuilder().data(Map.of("keep", "3")).build();
    MobRef target = cow(1, 64, 0);
    cow(2, 64, 0);
    MobRef calf = cow(3, 64, 0);
    when(animalOps.isAdult(calf)).thenReturn(false);
    carry(Id.minecraft("wheat"), 2);

    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.FAILED);
    verify(animalOps, never()).setInLove(eq(target), anyInt());
  }

  @Test
  void protectedAnimalsAreNeverChosenOrWorked() {
    node();
    MobRef invulnerable = cow(1, 64, 0);
    when(invulnerable.isInvulnerable()).thenReturn(true);
    MobRef near = cow(2, 64, 0);
    cow(3, 64, 0);
    carry(Id.minecraft("wheat"), 2);

    assertThat(seek(golem(0))).isEqualTo("breed " + near.uuid());
    assertThat(behavior.act(golem(1), task("breed", invulnerable))).isEqualTo(GolemActResult.FAILED);
    verify(animalOps, never()).setInLove(eq(invulnerable), anyInt());
  }

  @Test
  void shearingMarksTheSheepShornAndCarriesWoolOfItsColor() {
    MobRef sheep = sheep(1, 64, 0, DyeColor.RED);
    GolemContext golem = golem(1);

    assertThat(behavior.act(golem, task("shear", sheep))).isEqualTo(GolemActResult.DONE);

    verify(animalOps).setSheared(sheep, true);
    assertThat(count(cargo, Id.minecraft("red_wool"))).isEqualTo(2);
    verify(golem).workXp(config.workXpPerShear);
    feedback.verify(() -> RancherFeedback.play(eq(rancher), any(Loc.class), eq(RancherFeedback.Kind.SHEAR)));
  }

  @Test
  void shearingNeedsItsLevelAnUnshornAdultAndTheToggle() {
    MobRef sheep = sheep(1, 64, 0, DyeColor.WHITE);
    level = 1;
    assertThat(behavior.act(golem(1), task("shear", sheep))).isEqualTo(GolemActResult.FAILED);

    level = 5;
    record = RancherRules.toggle(record, RancherRules.Action.SHEAR);
    assertThat(behavior.act(golem(1), task("shear", sheep))).isEqualTo(GolemActResult.FAILED);

    record = RancherRules.toggle(record, RancherRules.Action.SHEAR);
    when(animalOps.isSheared(sheep)).thenReturn(true);
    assertThat(behavior.act(golem(1), task("shear", sheep))).isEqualTo(GolemActResult.FAILED);

    verify(animalOps, never()).setSheared(sheep, true);
  }

  @Test
  void milkingFillsABucketFromTheHomeChest() {
    MobRef cow = cow(1, 64, 0);
    stock(2, Id.minecraft("bucket"), 1);
    GolemContext golem = golem(1);

    assertThat(behavior.act(golem, task("milk", cow))).isEqualTo(GolemActResult.DONE);

    assertThat(chest[2]).isNull();
    assertThat(count(cargo, Id.minecraft("milk_bucket"))).isEqualTo(1);
    verify(golem).workXp(config.workXpPerMilk);
    feedback.verify(() -> RancherFeedback.play(eq(rancher), any(Loc.class), eq(RancherFeedback.Kind.MILK)));

    assertThat(behavior.act(golem(1), task("milk", cow))).isEqualTo(GolemActResult.FAILED);
    level = 2;
    stock(2, Id.minecraft("bucket"), 1);
    assertThat(behavior.act(golem(1), task("milk", cow))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void mooshroomsFillBowlsWithStewUnlessTheyHoldASuspiciousStew() {
    MobRef mooshroom = mooshroom(1, 64, 0);
    stock(5, Id.minecraft("bowl"), 2);
    stock(6, Id.minecraft("bucket"), 1);

    assertThat(behavior.act(golem(1), task("milk", mooshroom))).isEqualTo(GolemActResult.DONE);
    assertThat(count(cargo, Id.minecraft("mushroom_stew"))).isEqualTo(1);
    assertThat(chest[5].amount()).isEqualTo(1);
    feedback.verify(() -> RancherFeedback.play(eq(rancher), any(Loc.class), eq(RancherFeedback.Kind.STEW)));

    when(animalOps.hasStewEffect(mooshroom)).thenReturn(true);
    assertThat(behavior.act(golem(1), task("milk", mooshroom))).isEqualTo(GolemActResult.DONE);
    assertThat(count(cargo, Id.minecraft("milk_bucket"))).isEqualTo(1);
    assertThat(chest[5].amount()).isEqualTo(1);

    assertThat(behavior.act(golem(1), task("milk", mooshroom))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void cullingStrikesAsTheGolemAndCarriesTheDrops() {
    body = GolemBody.IRON;
    level = 4;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    MobRef victim = cow(1, 64, 0);
    cow(2, 64, 0);
    cow(3, 64, 0);
    lootTables.put(victim.uuid(), List.of());
    dies(victim, GolemTestItems.stack(Id.minecraft("beef"), 2));
    GolemContext golem = golem(1);

    assertThat(behavior.act(golem, task("cull", victim))).isEqualTo(GolemActResult.DONE);

    assertThat(lootRolls).isEmpty();

    MobRef striker = golem.golem();
    verify(victim).damage(10D, striker);
    verify(death).setDroppedExp(0);
    assertThat(count(cargo, Id.minecraft("beef"))).isEqualTo(2);
    verify(golem).workXp(config.workXpPerCull);
    feedback.verify(() -> RancherFeedback.play(eq(rancher), any(Loc.class), eq(RancherFeedback.Kind.CULL)));
  }

  @Test
  void husbandryAddsALootRollToEachCull() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    MobRef victim = cow(1, 64, 0);
    cow(2, 64, 0);
    cow(3, 64, 0);
    ItemRef leather = GolemTestItems.stack(Id.minecraft("leather"), 1);
    lootTables.put(victim.uuid(), List.of(leather));
    dies(victim, GolemTestItems.stack(Id.minecraft("beef"), 1));

    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.DONE);

    assertThat(count(cargo, Id.minecraft("beef"))).isEqualTo(1);
    assertThat(count(cargo, Id.minecraft("leather"))).isEqualTo(1);
  }

  @Test
  void husbandryRollsNoExtraLootWhileMobsDropNothing() {
    body = GolemBody.IRON;
    mobLoot = false;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    MobRef victim = cow(1, 64, 0);
    cow(2, 64, 0);
    cow(3, 64, 0);
    lootTables.put(victim.uuid(), List.of());
    dies(victim, GolemTestItems.stack(Id.minecraft("beef"), 1));

    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.DONE);

    assertThat(lootRolls).isEmpty();
    assertThat(count(cargo, Id.minecraft("beef"))).isEqualTo(1);
  }

  @Test
  void tamedAnimalsAndAnimalsInAVehicleAreNeverCulled() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    MobRef tamed = animal(Id.minecraft("cow"), 1, 64, 0);
    when(animalOps.isTamed(tamed)).thenReturn(true);
    MobRef riding = cow(2, 64, 0);
    when(riding.vehicle()).thenReturn(mock(EntityRef.class));
    cow(3, 64, 0);
    cow(4, 64, 0);

    assertThat(behavior.act(golem(1), task("cull", tamed))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1), task("cull", riding))).isEqualTo(GolemActResult.FAILED);

    verify(tamed, never()).damage(anyDouble(), any(EntityRef.class));
    verify(riding, never()).damage(anyDouble(), any(EntityRef.class));
  }

  @Test
  void cullingSparesCopperGolemsGuardedAnimalsTheKeepCountAndProtectedPlaces() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    MobRef victim = cow(1, 64, 0);
    cow(2, 64, 0);
    MobRef third = cow(3, 64, 0);

    GolemContext denied = golem(1);
    when(denied.canDamage(victim)).thenReturn(false);
    assertThat(behavior.act(denied, task("cull", victim))).isEqualTo(GolemActResult.BLOCKED);

    body = GolemBody.COPPER;
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);

    body = GolemBody.IRON;
    when(victim.customName()).thenReturn("{\"text\":\"Bessie\"}");
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);
    when(victim.customName()).thenReturn(null);
    when(animalOps.isLeashed(victim)).thenReturn(true);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);
    when(animalOps.isLeashed(victim)).thenReturn(false);
    when(victim.passengers()).thenReturn(List.of(mock(PlayerRef.class)));
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);
    when(victim.passengers()).thenReturn(List.of());
    when(animalOps.isAdult(victim)).thenReturn(false);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);
    when(animalOps.isAdult(victim)).thenReturn(true);

    animals.remove(third);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);

    verify(victim, never()).damage(anyDouble(), any(EntityRef.class));
  }

  @Test
  void saddledPigsAreNeverCulled() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    MobRef pig = animal(Id.minecraft("pig"), 1, 64, 0);
    animal(Id.minecraft("pig"), 2, 64, 0);
    animal(Id.minecraft("pig"), 3, 64, 0);
    ItemRef saddle = GolemTestItems.stack(Id.minecraft("saddle"), 1);
    when(pig.equipment(EquipmentSlot.SADDLE)).thenReturn(saddle);

    assertThat(behavior.act(golem(1), task("cull", pig))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void aStrikeThatDoesNotKillKeepsWorkingAndARefusedOneIsBlocked() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    MobRef victim = cow(1, 64, 0);
    cow(2, 64, 0);
    cow(3, 64, 0);
    when(victim.health()).thenReturn(10D, 4D);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.WORKING);

    when(victim.health()).thenReturn(10D);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.BLOCKED);
  }

  @Test
  void aTargetThatIsGoneOrLeftTheAreaFails() {
    MobRef cow = cow(1, 64, 0);
    carry(Id.minecraft("wheat"), 2);
    GolemContext gone = golem(1);
    when(gone.targetEntity()).thenReturn(Optional.empty());
    assertThat(behavior.act(gone, task("breed", cow))).isEqualTo(GolemActResult.FAILED);

    when(cow.location()).thenReturn(new Loc(world, 20.5D, 64D, 0.5D));
    assertThat(behavior.act(golem(1), task("breed", cow))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1), task("tame", cow))).isEqualTo(GolemActResult.FAILED);
  }

  private void node() {
    nearby.add(new NodeIndex.Entry(KEY, record));
  }

  private void carry(Id type, int amount) {
    cargo.add(GolemTestItems.stack(type, amount));
  }

  private void stock(int slot, Id type, int amount) {
    chest[slot] = GolemTestItems.stack(type, amount);
  }

  private void dies(MobRef victim, ItemRef drop) {
    AtomicBoolean dead = new AtomicBoolean();
    when(victim.isDead()).thenAnswer(invocation -> dead.get());
    doAnswer(invocation -> {
      List<ItemRef> drops = new ArrayList<>(List.of(drop));
      death = mock(EntityDeathEvent.class);
      when(death.entity()).thenReturn(victim);
      when(death.drops()).thenReturn(drops);
      actions.on(death);
      assertThat(drops).isEmpty();
      dead.set(true);
      return null;
    }).when(victim).damage(anyDouble(), any(EntityRef.class));
  }

  private MobRef cow(int x, int y, int z) {
    return animal(Id.minecraft("cow"), x, y, z);
  }

  private MobRef sheep(int x, int y, int z, DyeColor color) {
    MobRef sheep = animal(Id.minecraft("sheep"), x, y, z);
    lenient().when(animalOps.woolColor(sheep)).thenReturn(color);
    return sheep;
  }

  private MobRef mooshroom(int x, int y, int z) {
    return animal(Id.minecraft("mooshroom"), x, y, z);
  }

  private MobRef animal(Id species, int x, int y, int z) {
    MobRef animal = mock(MobRef.class, withSettings().strictness(Strictness.LENIENT));
    UUID id = UUID.randomUUID();
    DataHolder data = mock(DataHolder.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(animal.data()).thenReturn(data);
    lenient().when(animal.uuid()).thenReturn(id);
    lenient().when(animal.type()).thenReturn(species);
    lenient().when(animal.has(EntityTrait.ANIMAL)).thenReturn(true);
    lenient().when(animal.world()).thenReturn(world);
    lenient().when(animal.location()).thenReturn(new Loc(world, x + 0.5D, y, z + 0.5D));
    lenient().when(animal.isValid()).thenReturn(true);
    lenient().when(animalOps.isAdult(animal)).thenReturn(true);
    lenient().when(animalOps.canBreed(animal)).thenReturn(true);
    lenient().when(animal.health()).thenReturn(10D);
    lenient().when(animalOps.isBreedItem(eq(animal), any(Id.class))).thenAnswer(invocation -> {
      Id food = invocation.getArgument(1);
      return species.equals(Id.minecraft("pig")) ? food.equals(Id.minecraft("carrot")) : food.equals(Id.minecraft("wheat"));
    });
    lenient().when(animal.passengers()).thenReturn(List.of());
    animals.add(animal);
    return animal;
  }

  private List<ItemRef> rollLoot(LivingRef victim, Random rolled) {
    lootRolls.add(victim.uuid());
    return rolled == random ? lootTables.getOrDefault(victim.uuid(), List.of()) : List.of();
  }

  private static boolean contains(Box box, Loc at) {
    return at.x() >= box.minX() && at.x() < box.maxX() && at.y() >= box.minY() && at.y() < box.maxY()
        && at.z() >= box.minZ() && at.z() < box.maxZ();
  }

  private String seek(GolemContext golem) {
    return behavior.seek(golem).map(task -> {
      assertThat(task.node()).contains(KEY);
      assertThat(task.nodeType()).contains(NodeType.PASTURE);
      assertThat(task.target().kind()).isEqualTo(GolemTarget.Kind.ENTITY);
      return task.type() + " " + task.target().entity();
    }).orElse("");
  }

  private GolemTask task(String type, MobRef animal) {
    current = animal.uuid();
    return GolemTask.builder(KEY, NodeType.PASTURE, type, GolemTarget.entity(animal)).build();
  }

  private GolemContext golem(int actIndex) {
    GolemContext golem = mock(GolemContext.class);
    PlayerRef owner = mock(PlayerRef.class);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    MobRef entity = mock(MobRef.class);
    lenient().when(entity.location()).thenReturn(new Loc(world, 0.5D, 64D, 0.5D));
    GolemState state = mock(GolemState.class);
    lenient().when(state.channels()).thenAnswer(invocation -> channels);
    lenient().when(state.cores()).thenReturn(List.of(GolemCoreType.RANCHER));
    lenient().when(state.homeChest()).thenAnswer(invocation -> homeChest);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(GOLEM);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.state()).thenReturn(state);
    lenient().when(handle.body()).thenAnswer(invocation -> body);
    lenient().when(golem.owner()).thenReturn(owner);
    lenient().when(golem.handle()).thenReturn(handle);
    lenient().when(golem.golem()).thenReturn(entity);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.node()).thenAnswer(invocation -> Optional.of(record));
    lenient().when(golem.nowMillis()).thenReturn(NOW);
    lenient().when(golem.actIndex()).thenReturn(actIndex);
    lenient().when(golem.home()).thenReturn(new Loc(world, 0D, 64D, 0D));
    lenient().when(golem.workRadius()).thenReturn(24D);
    lenient().when(golem.inWorkRadius(any(Loc.class))).thenReturn(true);
    lenient().when(golem.owns(any(Loc.class))).thenReturn(true);
    lenient().when(golem.canHandle(any())).thenReturn(true);
    lenient().when(golem.canDamage(any())).thenReturn(true);
    lenient().when(golem.room()).thenReturn(64);
    lenient().when(golem.carriedItems()).thenAnswer(invocation -> List.copyOf(cargo));
    lenient().when(golem.carry(any(ItemRef.class))).thenAnswer(invocation -> {
      cargo.add(invocation.getArgument(0));
      return Optional.empty();
    });
    lenient().when(golem.take(any(), anyInt())).thenAnswer(invocation -> take(invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(golem.container(any(BlockRef.class))).thenAnswer(invocation -> {
      BlockRef block = invocation.getArgument(0);
      return homeChest != null && block.x() == CHEST.x() && block.y() == CHEST.y() && block.z() == CHEST.z()
          ? Optional.of(inventory()) : Optional.empty();
    });
    lenient().when(golem.targetEntity()).thenAnswer(invocation -> target());
    return golem;
  }

  private boolean isHomeChest(BlockRef block) {
    return block.x() == CHEST.x() && block.y() == CHEST.y() && block.z() == CHEST.z();
  }

  private Optional<EntityRef> target() {
    for (MobRef animal : animals) {
      if (animal.uuid().equals(current)) {
        return Optional.of(animal);
      }
    }
    return Optional.empty();
  }

  private int take(Predicate<ItemRef> matches, int amount) {
    int taken = 0;
    for (ItemRef item : cargo) {
      while (taken < amount && item.amount() > 0 && matches.test(item.copy())) {
        item.setAmount(item.amount() - 1);
        taken++;
      }
    }
    cargo.removeIf(item -> item.amount() <= 0);
    return taken;
  }

  private InventoryRef inventory() {
    InventoryRef inventory = mock(InventoryRef.class);
    lenient().when(inventory.contents()).thenAnswer(invocation -> Arrays.asList(chest.clone()));
    lenient().when(inventory.item(anyInt())).thenAnswer(invocation -> chest[invocation.<Integer>getArgument(0)]);
    doAnswer(invocation -> {
      ItemRef item = invocation.getArgument(1);
      chest[invocation.<Integer>getArgument(0)] = item == null || item.isEmpty() ? null : item;
      return null;
    }).when(inventory).setItem(anyInt(), any());
    return inventory;
  }

  private static int count(Collection<ItemRef> items, Id type) {
    int total = 0;
    for (ItemRef item : items) {
      if (item != null && item.type().equals(type)) {
        total += item.amount();
      }
    }
    return total;
  }
}
