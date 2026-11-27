package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.GolemActResult;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemTarget;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.GolemTaskBoard;
import art.arcane.adapt.content.golem.GolemTaskBoards;
import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.content.golem.GolemToolEnchants;
import art.arcane.adapt.content.golem.GolemToolPlan;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeContext;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeStatus;
import art.arcane.adapt.content.golem.node.ScanBudget;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FishingNodeBehaviorTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000027d1");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000027d2");
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-0000000027d3");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-0000000027d4");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 64, 0, BlockFace.NORTH);
  private static final NodeKey NEIGHBOR = new NodeKey(CropField.WORLD, 8, 64, 8, BlockFace.SOUTH);
  private static final long NOW = 80_000L;
  private static final long LEASE = 20_000L;

  private final GolemAnglerCore.Config config = new GolemAnglerCore.Config();
  private final CropField field = new CropField();
  private final AnglerWaters waters = new AnglerWaters();
  private final Map<NodeKey, NodeRecord> records = new HashMap<>();
  private final GolemTaskBoards boards = new GolemTaskBoards(key -> Optional.ofNullable(records.get(key)));
  private final GolemTaskBoard board = boards.board(CropField.WORLD);
  private final List<NodeStatus> statuses = new ArrayList<>();
  private final List<ItemRef> carried = new ArrayList<>();
  private final List<Double> xp = new ArrayList<>();
  private final Deque<Double> rolls = new ArrayDeque<>();
  private NodeRecord record = NodeRecord.builder(OWNER, NodeType.FISHING, BlockFace.NORTH).build();
  private GolemAnglerCore core;
  private FishingNodeBehavior behavior;
  private long now = NOW;
  private int roomLeft = 64;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<AnglerFeedback> feedback;
  private MockedStatic<GolemToolEnchants> enchants;
  private ItemRef rod;

  @BeforeEach
  void setUp() {
    core = mock(GolemAnglerCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.loot()).thenAnswer(invocation -> new AnglerLoot.Tables(table(config.fish), table(config.junk), table(config.treasure)));
    lenient().when(core.item(any(AnglerLoot.Catch.class))).thenAnswer(invocation -> {
      AnglerLoot.Catch caught = invocation.getArgument(0);
      return GolemTestItems.stack(caught.type(), caught.amount());
    });
    level(1);
    records.put(KEY, record);
    behavior = new FishingNodeBehavior(core, waters, () -> rolls.isEmpty() ? 0.0D : rolls.poll());
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    runtime.when(GolemancyRuntime::boards).thenReturn(Optional.of(boards));
    feedback = mockStatic(AnglerFeedback.class);
    enchants = mockStatic(GolemToolEnchants.class);
  }

  @AfterEach
  void close() {
    enchants.close();
    feedback.close();
    runtime.close();
  }

  @Test
  void itActsForTheAnglerCoreAdaptation() {
    assertThat(behavior.policy()).isSameAs(core);
  }

  @Test
  void withoutOpenWaterTheNodeOffersNothingWithdrawsItsSeatsAndShowsBlocked() {
    board.offer(fish(KEY, 0), 16);

    behavior.scan(node(), budget());

    assertThat(board.tasks(KEY)).isEmpty();
    assertThat(statuses).containsExactly(NodeStatus.BLOCKED);
  }

  @Test
  void besideOpenWaterTheNodeOffersOneOpenSeatAtATime() {
    pond();

    behavior.scan(node(), budget());
    behavior.scan(node(), budget());
    assertThat(seats()).containsExactly("0,64,-1");

    board.reserve(task(KEY, 0), FIRST, now, LEASE);
    behavior.scan(node(), budget());
    assertThat(seats()).containsExactly("0,64,-1", "1,64,-1");

    board.reserve(task(KEY, 1), SECOND, now, LEASE);
    behavior.scan(node(), budget());
    assertThat(seats()).containsExactly("0,64,-1", "1,64,-1");
    assertThat(statuses).isEmpty();
  }

  @Test
  void seatsAnAnglerLeftOpenCollapseToOneOpenSeat() {
    pond();
    board.offer(fish(KEY, 0), 16);
    board.offer(fish(KEY, 1), 16);

    behavior.scan(node(), budget());

    assertThat(board.tasks(KEY)).hasSize(1);
  }

  @Test
  void theChunkCapCountsAnglersAtEveryFishingNodeInTheChunk() {
    pond();
    records.put(NEIGHBOR, NodeRecord.builder(OWNER, NodeType.FISHING, BlockFace.SOUTH).build());
    board.offer(fish(KEY, 0), 16);
    GolemTask first = fish(NEIGHBOR, 0);
    GolemTask second = fish(NEIGHBOR, 1);
    board.offer(first, 16);
    board.offer(second, 16);
    board.reserve(first, FIRST, now, LEASE);
    board.reserve(second, SECOND, now, LEASE);

    behavior.scan(node(), budget());
    assertThat(board.tasks(KEY)).isEmpty();

    board.releaseReservation(second, SECOND);
    behavior.scan(node(), budget());
    assertThat(board.tasks(KEY)).isEmpty();

    board.withdraw(second);
    behavior.scan(node(), budget());
    assertThat(seats()).containsExactly("0,64,-1");
  }

  @Test
  void openSeatsAtOtherFishingNodesInTheChunkCountTowardTheCap() {
    pond();
    records.put(NEIGHBOR, NodeRecord.builder(OWNER, NodeType.FISHING, BlockFace.SOUTH).build());
    GolemTask seated = fish(NEIGHBOR, 0);
    GolemTask open = fish(NEIGHBOR, 1);
    board.offer(seated, 16);
    board.offer(open, 16);
    board.reserve(seated, FIRST, now, LEASE);

    behavior.scan(node(), budget());
    assertThat(board.tasks(KEY)).isEmpty();

    board.withdraw(open);
    behavior.scan(node(), budget());
    assertThat(seats()).containsExactly("0,64,-1");
  }

  @Test
  void theWaterIsSurveyedAgainOnlyAfterTheRecheckInterval() {
    pond();
    behavior.scan(node(), budget());
    field.set(0, 63, -3, Id.minecraft("stone"));

    now = NOW + AnglerWaters.RECHECK_MILLIS - 1L;
    behavior.scan(node(), budget());
    assertThat(seats()).containsExactly("0,64,-1");

    now = NOW + AnglerWaters.RECHECK_MILLIS;
    behavior.scan(node(), budget());
    assertThat(board.tasks(KEY)).isEmpty();
    assertThat(statuses).containsExactly(NodeStatus.BLOCKED);
  }

  @Test
  void anOfflineOwnerGetsNoSeats() {
    pond();
    level(0);

    behavior.scan(node(), budget());

    assertThat(board.tasks(KEY)).isEmpty();
  }

  @Test
  void seatsBeyondALoweredCapAreWithdrawn() {
    pond();
    config.maxAnglersPerChunk = 1;
    GolemTask outer = fish(KEY, 1);
    board.offer(outer, 16);
    board.reserve(outer, FIRST, now, LEASE);

    behavior.scan(node(), budget());

    assertThat(seats()).containsExactly("0,64,-1");
    assertThat(outer.isClosed()).isTrue();
  }

  @Test
  void rainIsSurveyedAtTheWaterSurfaceOnlyDuringAStorm() {
    pond();
    lenient().when(field.world.hasStorm()).thenReturn(true);
    lenient().when(field.world.highestBlockY(0, -3)).thenReturn(64);
    lenient().when(field.block(0, 65, -3).temperature()).thenReturn(0.8D);

    behavior.scan(node(), budget());

    assertThat(waters.get(KEY)).hasValueSatisfying(survey -> assertThat(survey.raining()).isTrue());
  }

  @Test
  void onlyFishJobsAtASeatOfAnOpenNodeAreWorked() {
    level(3);

    assertThat(behavior.act(golem(1), fish(KEY, 0))).isEqualTo(GolemActResult.FAILED);
    survey(false, false);
    assertThat(behavior.act(golem(1), fish(KEY, 0))).isEqualTo(GolemActResult.FAILED);
    survey(true, false);
    assertThat(behavior.act(golem(1), GolemTask.builder(KEY, NodeType.FISHING, "break", GolemTarget.block(CropField.WORLD, 0, 64, -1)).build()))
        .isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1), GolemTask.builder(KEY, NodeType.FISHING, AnglerRules.FISH, GolemTarget.block(CropField.WORLD, 0, 64, -3)).build()))
        .isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1), fish(KEY, 2))).isEqualTo(GolemActResult.FAILED);
    level(0);
    assertThat(behavior.act(golem(1), fish(KEY, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(carried).isEmpty();
  }

  @Test
  void onlyACastingAnglerOverTheChunkCapGivesUpWhileSeatedAnglersKeepFishing() {
    survey(true, false);
    records.put(NEIGHBOR, NodeRecord.builder(OWNER, NodeType.FISHING, BlockFace.SOUTH).build());
    GolemTask first = fish(NEIGHBOR, 0);
    GolemTask second = fish(NEIGHBOR, 1);
    board.offer(first, 16);
    board.offer(second, 16);
    board.reserve(first, FIRST, now, LEASE);

    assertThat(behavior.act(golem(0), fish(KEY, 0))).isEqualTo(GolemActResult.WORKING);

    board.reserve(second, SECOND, now, LEASE);

    assertThat(behavior.act(golem(0), fish(KEY, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(3), fish(KEY, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(carriedTypes()).containsExactly(Id.minecraft("cod"));
  }

  @Test
  void aProtectedSpotBlocks() {
    survey(true, false);
    GolemContext golem = golem(1);
    lenient().when(golem.canInteract(any(Loc.class))).thenReturn(false);

    assertThat(behavior.act(golem, fish(KEY, 0))).isEqualTo(GolemActResult.BLOCKED);
    assertThat(carried).isEmpty();
  }

  @Test
  void theFirstActCastsAndWaitsForABite() {
    survey(true, false);

    assertThat(behavior.act(golem(0), fish(KEY, 1))).isEqualTo(GolemActResult.WORKING);

    assertThat(carried).isEmpty();
    assertThat(xp).isEmpty();
    feedback.verify(() -> AnglerFeedback.play(eq(core), argThat(at -> at.x() == 1.5D && at.y() == 65.0D && at.z() == -2.5D),
        eq(AnglerFeedback.Kind.CAST)));
  }

  @Test
  void eachLaterActLandsACatchPaysWorkXpAndEndsTheTripSoTheCatchIsDelivered() {
    survey(true, false);

    assertThat(behavior.act(golem(1), fish(KEY, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(carriedTypes()).containsExactly(Id.minecraft("cod"));
    assertThat(xp).containsExactly(6.0D);
    feedback.verify(() -> AnglerFeedback.play(eq(core), argThat(at -> at.x() == 0.5D && at.y() == 65.0D && at.z() == -2.5D),
        eq(AnglerFeedback.Kind.CATCH)));
  }

  @Test
  void entropyRollsForEachCatch() {
    survey(true, false);
    GolemContext golem = golem(1);
    lenient().when(golem.withBonusYield(anyList())).thenAnswer(invocation -> {
      List<ItemRef> drops = new ArrayList<>(invocation.<List<ItemRef>>getArgument(0));
      drops.add(drops.getFirst().copy());
      return drops;
    });

    behavior.act(golem, fish(KEY, 0));

    assertThat(carriedTypes()).containsExactly(Id.minecraft("cod"), Id.minecraft("cod"));
    verify(golem, times(1)).withBonusYield(anyList());
  }

  @Test
  void junkIsDiscardedOnlyWhenTheFilterIsOnFromLevelThree() {
    survey(true, false);
    NodeRecord filtered = AnglerRules.toggleJunk(record);

    level(3);
    record = filtered;
    rolls.add(0.9D);
    assertThat(behavior.act(golem(1), fish(KEY, 0))).isEqualTo(GolemActResult.WORKING);
    assertThat(carried).isEmpty();
    assertThat(xp).containsExactly(6.0D);

    level(2);
    rolls.add(0.9D);
    behavior.act(golem(1), fish(KEY, 0));
    assertThat(carriedTypes()).containsExactly(Id.minecraft("lily_pad"));

    level(3);
    record = NodeRecord.builder(OWNER, NodeType.FISHING, BlockFace.NORTH).build();
    rolls.add(0.9D);
    behavior.act(golem(1), fish(KEY, 0));
    assertThat(carriedTypes()).containsExactly(Id.minecraft("lily_pad"), Id.minecraft("lily_pad"));
  }

  @Test
  void levelFiveSometimesLandsADoubleCatch() {
    survey(true, false);

    level(5);
    rolls.add(0.05D);
    behavior.act(golem(1), fish(KEY, 0));
    assertThat(carriedTypes()).containsExactly(Id.minecraft("cod"), Id.minecraft("cod"));
    assertThat(xp).containsExactly(12.0D);

    rolls.add(0.5D);
    behavior.act(golem(1), fish(KEY, 0));
    assertThat(carried).hasSize(3);

    level(4);
    rolls.add(0.05D);
    behavior.act(golem(1), fish(KEY, 0));
    assertThat(carried).hasSize(4);
    assertThat(xp).containsExactly(12.0D, 6.0D, 6.0D);
  }

  @Test
  void aFullAnglerEndsTheTripAndDropsWhatItCannotCarry() {
    survey(true, false);
    roomLeft = 1;
    GolemContext golem = golem(1);
    lenient().when(golem.withBonusYield(anyList())).thenAnswer(invocation -> {
      List<ItemRef> drops = new ArrayList<>(invocation.<List<ItemRef>>getArgument(0));
      drops.add(drops.getFirst().copy());
      return drops;
    });

    assertThat(behavior.act(golem, fish(KEY, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(carriedTypes()).containsExactly(Id.minecraft("cod"));
    verify(field.world).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void catchesSpawnNoExperienceOrbs() {
    survey(true, false);

    behavior.act(golem(1), fish(KEY, 0));

    verify(field.world, never()).spawnExperience(any(Vec3.class), anyInt());
    verify(field.world, never()).spawn(any(Id.class), any(Loc.class), any());
  }

  @Test
  void anAnglerDeliversItsCatchBeforeItClaimsAnotherSeat() {
    assertThat(behavior.deliversBeforeClaiming()).isTrue();
  }

  @Test
  void everySeatNeedsAFishingRodFromTheHomeChest() {
    GolemToolPlan plan = behavior.tools(golem(0), fish(KEY, 0));

    assertThat(plan.free()).isFalse();
    assertThat(plan.needs()).singleElement().satisfies(need -> {
      assertThat(need.suits().test(GolemTestItems.stack(Id.minecraft("fishing_rod"), 1))).isTrue();
      assertThat(need.suits().test(GolemTestItems.stack(Id.minecraft("stick"), 1))).isFalse();
      assertThat(need.note().key()).isEqualTo(Text.key("adapt.golemancy.tools.needs_rod"));
    });
  }

  @Test
  void eachCatchWearsTheRodOnceAndTheCastDoesNot() {
    survey(true, false);
    GolemContext casting = golem(0);
    GolemContext reeling = golem(1);
    level(5);
    rolls.add(0.05D);

    behavior.act(casting, fish(KEY, 0));
    behavior.act(reeling, fish(KEY, 0));

    verify(casting, never()).wearTool();
    verify(reeling, times(1)).wearTool();
    assertThat(carried).hasSize(2);
  }

  @Test
  void aThrownBackJunkCatchStillWearsTheRodAndKeepsFishing() {
    survey(true, false);
    level(3);
    record = AnglerRules.toggleJunk(record);
    rolls.add(0.9D);
    GolemContext golem = golem(1);

    assertThat(behavior.act(golem, fish(KEY, 0))).isEqualTo(GolemActResult.WORKING);

    verify(golem).wearTool();
  }

  @Test
  void luckOfTheSeaOnTheRodAddsToTheLevelLuck() {
    survey(true, false);
    holdRod(3, 0);
    rolls.add(0.9D);
    behavior.act(golem(1), fish(KEY, 0));

    holdRod(0, 0);
    rolls.add(0.9D);
    behavior.act(golem(1), fish(KEY, 0));

    assertThat(carriedTypes()).containsExactly(Id.minecraft("name_tag"), Id.minecraft("lily_pad"));
  }

  @Test
  void lureTakesFiveSecondsPerLevelOffTheCatchTimeBeforeTheRainButNeverBelowOneSecond() {
    survey(true, true);
    holdRod(0, 3);

    level(1);
    assertThat(behavior.actIntervalTicks(golem(0), fish(KEY, 0))).isEqualTo(900);
    level(5);
    assertThat(behavior.actIntervalTicks(golem(0), fish(KEY, 0))).isEqualTo(150);
    survey(true, false);
    assertThat(behavior.actIntervalTicks(golem(0), fish(KEY, 0))).isEqualTo(200);
    config.catchIntervalSecondsByLevel = new ArrayList<>(List.of(4));
    level(1);
    assertThat(behavior.actIntervalTicks(golem(0), fish(KEY, 0))).isEqualTo(20);
  }

  @Test
  void theCatchIntervalFollowsTheLevelAndShrinksInTheRainFromLevelFour() {
    survey(true, true);

    level(1);
    assertThat(behavior.actIntervalTicks(golem(0), fish(KEY, 0))).isEqualTo(1200);
    level(5);
    assertThat(behavior.actIntervalTicks(golem(0), fish(KEY, 0))).isEqualTo(375);
    level(4);
    assertThat(behavior.actIntervalTicks(golem(0), fish(KEY, 0))).isEqualTo(480);
    level(3);
    assertThat(behavior.actIntervalTicks(golem(0), fish(KEY, 0))).isEqualTo(800);
    survey(true, false);
    level(5);
    assertThat(behavior.actIntervalTicks(golem(0), fish(KEY, 0))).isEqualTo(500);
  }

  private void pond() {
    for (int x = -2; x <= 2; x++) {
      for (int z = -5; z <= -1; z++) {
        field.set(x, 63, z, Id.minecraft("water"), AnglerWaterTest.levelled(0));
        field.set(x, 64, z, Id.minecraft("water"), AnglerWaterTest.levelled(0));
      }
    }
  }

  private void holdRod(int luckOfTheSea, int lure) {
    rod = GolemTestItems.stack(Id.minecraft("fishing_rod"), 1);
    ItemRef held = rod;
    enchants.when(() -> GolemToolEnchants.luckOfTheSea(held)).thenReturn(luckOfTheSea);
    enchants.when(() -> GolemToolEnchants.lure(held)).thenReturn(lure);
  }

  private void survey(boolean open, boolean raining) {
    waters.forget();
    waters.survey(KEY, now, () -> new AnglerWater.Survey(open, 0, 64, -3, 64, raining));
  }

  private void level(int level) {
    lenient().when(core.ownerLevel(OWNER)).thenReturn(level);
  }

  private NodeContext node() {
    NodeContext node = mock(NodeContext.class);
    lenient().when(node.owner()).thenReturn(OWNER);
    lenient().when(node.key()).thenReturn(KEY);
    lenient().when(node.record()).thenAnswer(invocation -> record);
    lenient().when(node.world()).thenReturn(field.world);
    lenient().when(node.nowMillis()).thenAnswer(invocation -> now);
    lenient().when(node.owns(any(BlockRef.class))).thenReturn(true);
    lenient().when(node.tasks()).thenAnswer(invocation -> board.tasks(KEY));
    lenient().when(node.task(anyString(), any(GolemTarget.class)))
        .thenAnswer(invocation -> GolemTask.builder(KEY, NodeType.FISHING, invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(node.post(any(GolemTask.class))).thenAnswer(invocation -> board.offer(invocation.getArgument(0), 16) != GolemTaskBoard.Offer.FULL);
    lenient().when(node.withdraw(any(GolemTask.class))).thenAnswer(invocation -> board.withdraw(invocation.getArgument(0)));
    lenient().doAnswer(invocation -> statuses.add(invocation.getArgument(0))).when(node).status(any(NodeStatus.class));
    return node;
  }

  private static ScanBudget budget() {
    ScanBudget budget = mock(ScanBudget.class);
    lenient().when(budget.start()).thenReturn(0);
    lenient().when(budget.end()).thenReturn(64);
    lenient().when(budget.hasTaskRoom()).thenReturn(true);
    return budget;
  }

  private GolemContext golem(int actIndex) {
    GolemContext golem = mock(GolemContext.class);
    PlayerRef owner = mock(PlayerRef.class);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(GOLEM);
    MobRef body = mock(MobRef.class);
    lenient().when(body.location()).thenReturn(new Loc(field.world, 0.5D, 65D, 0.5D));
    lenient().when(golem.owner()).thenReturn(owner);
    lenient().when(golem.handle()).thenReturn(handle);
    lenient().when(golem.golem()).thenReturn(body);
    lenient().when(golem.node()).thenAnswer(invocation -> Optional.of(record));
    lenient().when(golem.world()).thenReturn(field.world);
    lenient().when(golem.nowMillis()).thenAnswer(invocation -> now);
    lenient().when(golem.actIndex()).thenReturn(actIndex);
    lenient().when(golem.owns(any(Loc.class))).thenReturn(true);
    lenient().when(golem.canInteract(any(Loc.class))).thenReturn(true);
    lenient().when(golem.withBonusYield(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(golem.room()).thenAnswer(invocation -> Math.max(0, roomLeft));
    lenient().when(golem.carried()).thenAnswer(invocation -> carried.stream().mapToInt(ItemRef::amount).sum());
    lenient().when(golem.heldTool()).thenAnswer(invocation -> Optional.ofNullable(rod));
    lenient().when(golem.carry(any(ItemRef.class))).thenAnswer(invocation -> {
      ItemRef item = invocation.getArgument(0);
      if (roomLeft <= 0) {
        return Optional.of(item);
      }
      roomLeft -= item.amount();
      carried.add(item);
      return Optional.empty();
    });
    lenient().doAnswer(invocation -> xp.add(invocation.getArgument(0))).when(golem).workXp(anyDouble());
    return golem;
  }

  private GolemTask task(NodeKey key, int seat) {
    int[] at = AnglerWater.seat(key, seat);
    for (GolemTask task : board.tasks(key)) {
      GolemTarget target = task.target();
      if (target.blockX() == at[0] && target.blockY() == at[1] && target.blockZ() == at[2]) {
        return task;
      }
    }
    throw new AssertionError("No seat " + seat + " at " + key);
  }

  private static GolemTask fish(NodeKey key, int seat) {
    int[] at = AnglerWater.seat(key, seat);
    return GolemTask.builder(key, NodeType.FISHING, AnglerRules.FISH, GolemTarget.block(CropField.WORLD, at[0], at[1], at[2])).build();
  }

  private List<String> seats() {
    List<String> lines = new ArrayList<>();
    for (GolemTask task : board.tasks(KEY)) {
      GolemTarget target = task.target();
      lines.add(target.blockX() + "," + target.blockY() + "," + target.blockZ());
    }
    lines.sort((left, right) -> Integer.compare(Math.abs(Integer.parseInt(left.split(",")[0])), Math.abs(Integer.parseInt(right.split(",")[0]))));
    return lines;
  }

  private List<Id> carriedTypes() {
    List<Id> types = new ArrayList<>(carried.size());
    for (ItemRef item : carried) {
      types.add(item.type());
    }
    return types;
  }

  private static AnglerLoot.Table table(List<String> entries) {
    return AnglerLoot.parse(entries, type -> true, name -> {
    });
  }
}
