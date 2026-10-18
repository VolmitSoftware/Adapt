package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeStatus;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemTaskBoardTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID RIVAL = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final long LEASE = 20_000L;
  private static final long RESERVATION = GolemTaskBoard.RESERVATION_MILLIS;

  private final Map<NodeKey, NodeRecord> nodes = new HashMap<>();
  private GolemTaskBoard board;
  private long now = 1_000_000L;

  @BeforeEach
  void setUp() {
    board = new GolemTaskBoard(WORLD, key -> Optional.ofNullable(nodes.get(key)));
  }

  @Test
  void offeringTheSameTargetTwiceKeepsOneOpenTask() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);

    assertThat(board.offer(crop(node, 1, 64, 1), 16)).isEqualTo(GolemTaskBoard.Offer.ADDED);
    assertThat(board.offer(crop(node, 1, 64, 1), 16)).isEqualTo(GolemTaskBoard.Offer.EXISTS);
    assertThat(board.offer(crop(node, 2, 64, 1), 16)).isEqualTo(GolemTaskBoard.Offer.ADDED);

    assertThat(board.openCount(node)).isEqualTo(2);
  }

  @Test
  void aNodeHoldsAtMostItsOpenTaskLimit() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);

    assertThat(board.offer(crop(node, 1, 64, 1), 2)).isEqualTo(GolemTaskBoard.Offer.ADDED);
    assertThat(board.offer(crop(node, 2, 64, 1), 2)).isEqualTo(GolemTaskBoard.Offer.ADDED);
    assertThat(board.offer(crop(node, 3, 64, 1), 2)).isEqualTo(GolemTaskBoard.Offer.FULL);

    assertThat(board.openCount(node)).isEqualTo(2);
  }

  @Test
  void tasksFromAnotherWorldAreRefused() {
    NodeKey foreign = new NodeKey(OTHER_WORLD, 0, 64, 0, BlockFace.UP);

    assertThatThrownBy(() -> board.offer(GolemTask.builder(foreign, NodeType.HARVEST, "harvest", GolemTarget.block(OTHER_WORLD, 1, 64, 1)).build(), 16))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onlyOneGolemWinsAClaim() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 1);
    board.offer(task, 16);

    assertThat(board.claim(seeker(GOLEM), ignored -> false, now, LEASE)).containsSame(task);
    assertThat(board.claim(seeker(RIVAL), ignored -> false, now, LEASE)).isEmpty();
    assertThat(task.heldBy(GOLEM, now)).isTrue();
    assertThat(task.heldBy(RIVAL, now)).isFalse();
  }

  @Test
  void concurrentClaimsOfOneTaskHaveExactlyOneWinner() throws Exception {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 1);
    board.offer(task, 16);
    int contenders = 16;
    ExecutorService pool = Executors.newFixedThreadPool(contenders);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger winners = new AtomicInteger();
    List<Future<?>> futures = new ArrayList<>(contenders);
    try {
      for (int i = 0; i < contenders; i++) {
        UUID golem = new UUID(7L, i);
        futures.add(pool.submit(() -> {
          start.await();
          if (board.claim(seeker(golem), ignored -> false, now, LEASE).isPresent()) {
            winners.incrementAndGet();
          }
          return null;
        }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(winners).hasValue(1);
  }

  @Test
  void anExpiredLeaseCanBeClaimedByAnotherGolemAndTheOldHolderCannotRenewIt() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 1);
    board.offer(task, 16);
    board.claim(seeker(GOLEM), ignored -> false, now, LEASE);

    now += LEASE - 1L;
    assertThat(board.claim(seeker(RIVAL), ignored -> false, now, LEASE)).isEmpty();
    now += 1L;
    assertThat(task.heldBy(GOLEM, now)).isFalse();
    assertThat(board.claim(seeker(RIVAL), ignored -> false, now, LEASE)).containsSame(task);

    assertThat(board.renew(task, GOLEM, now, LEASE)).isFalse();
    assertThat(board.complete(task, GOLEM)).isFalse();
    assertThat(task.heldBy(RIVAL, now)).isTrue();
  }

  @Test
  void aLapsedLeaseCannotBeRenewedEvenWhenNobodyTookIt() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 1);
    board.offer(task, 16);
    board.claim(seeker(GOLEM), ignored -> false, now, LEASE);

    now += LEASE;

    assertThat(board.renew(task, GOLEM, now, LEASE)).isFalse();
    assertThat(task.heldBy(GOLEM, now)).isFalse();
    assertThat(board.claim(seeker(GOLEM), ignored -> false, now, LEASE)).containsSame(task);
  }

  @Test
  void renewingOnProgressKeepsTheClaimPastTheFirstLease() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 1);
    board.offer(task, 16);
    board.claim(seeker(GOLEM), ignored -> false, now, LEASE);

    now += LEASE - 1_000L;
    assertThat(board.renew(task, GOLEM, now, LEASE)).isTrue();
    now += LEASE - 1_000L;

    assertThat(task.heldBy(GOLEM, now)).isTrue();
    assertThat(board.claim(seeker(RIVAL), ignored -> false, now, LEASE)).isEmpty();
  }

  @Test
  void concurrentRenewAndExpiryClaimNeverLeaveTwoHolders() throws Exception {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    for (int round = 0; round < 200; round++) {
      GolemTaskBoard roundBoard = new GolemTaskBoard(WORLD, key -> Optional.ofNullable(nodes.get(key)));
      GolemTask task = crop(node, 1, 64, 1);
      roundBoard.offer(task, 16);
      roundBoard.claim(seeker(GOLEM), ignored -> false, now, LEASE);
      long expired = now + LEASE;
      ExecutorService pool = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      try {
        Future<Boolean> renew = pool.submit(() -> {
          start.await();
          return roundBoard.renew(task, GOLEM, expired - 1L, LEASE);
        });
        Future<Boolean> steal = pool.submit(() -> {
          start.await();
          return roundBoard.claim(seeker(RIVAL), ignored -> false, expired, LEASE).isPresent();
        });
        start.countDown();
        boolean renewed = renew.get(10, TimeUnit.SECONDS);
        boolean stolen = steal.get(10, TimeUnit.SECONDS);

        assertThat(renewed).isNotEqualTo(stolen);
        assertThat(task.heldBy(GOLEM, expired)).isEqualTo(renewed);
        assertThat(task.heldBy(RIVAL, expired)).isEqualTo(stolen);
      } finally {
        pool.shutdownNow();
      }
    }
  }

  @Test
  void releasingReopensTheTaskAndOnlyTheHolderCanRelease() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 1);
    board.offer(task, 16);
    board.claim(seeker(GOLEM), ignored -> false, now, LEASE);

    assertThat(board.release(task, RIVAL)).isFalse();
    assertThat(board.release(task, GOLEM)).isTrue();

    assertThat(board.claim(seeker(RIVAL), ignored -> false, now, LEASE)).containsSame(task);
  }

  @Test
  void completingRemovesTheTaskForTheHolderOnly() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 1);
    board.offer(task, 16);
    board.claim(seeker(GOLEM), ignored -> false, now, LEASE);

    assertThat(board.complete(task, RIVAL)).isFalse();
    assertThat(board.complete(task, GOLEM)).isTrue();

    assertThat(task.isClosed()).isTrue();
    assertThat(board.openCount(node)).isZero();
    assertThat(board.claim(seeker(RIVAL), ignored -> false, now, LEASE)).isEmpty();
    assertThat(board.offer(crop(node, 1, 64, 1), 16)).isEqualTo(GolemTaskBoard.Offer.ADDED);
  }

  @Test
  void aClaimRacingAWithdrawalNeverHandsOutAClosedTask() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 1);
    board.offer(task, 16);

    assertThat(board.withdraw(task)).isTrue();

    assertThat(board.claim(seeker(GOLEM), ignored -> false, now, LEASE)).isEmpty();
    assertThat(task.heldBy(GOLEM, now)).isFalse();
  }

  @Test
  void purgingClearsExpiredLeasesAndDropsTasksWhoseNodeIsGone() {
    NodeKey kept = harvestNode(0, 64, 0, null, 3);
    NodeKey removed = harvestNode(8, 64, 0, null, 3);
    GolemTask leased = crop(kept, 1, 64, 1);
    GolemTask orphan = crop(removed, 9, 64, 1);
    board.offer(leased, 16);
    board.offer(orphan, 16);
    board.claim(seekerFor(GOLEM, leased), ignored -> false, now, LEASE);
    nodes.remove(removed);

    now += LEASE;
    board.purge(now);

    assertThat(leased.holder(now)).isEmpty();
    assertThat(board.complete(leased, GOLEM)).isFalse();
    assertThat(leased.isClosed()).isFalse();
    assertThat(orphan.isClosed()).isTrue();
    assertThat(board.openCount(removed)).isZero();
    assertThat(board.openCount(kept)).isEqualTo(1);
  }

  @Test
  void channelsFollowTheNoChannelRules() {
    NodeKey open = harvestNode(0, 64, 0, null, 3);
    NodeKey red = harvestNode(4, 64, 0, DyeColor.RED, 3);
    board.offer(crop(open, 1, 64, 1), 16);
    board.offer(crop(red, 5, 64, 1), 16);

    assertThat(targetsX(board.candidates(seeker(GOLEM, ChannelMask.NONE), ignored -> false, now))).containsExactlyInAnyOrder(1);
    assertThat(targetsX(board.candidates(seeker(GOLEM, ChannelMask.of(DyeColor.RED)), ignored -> false, now))).containsExactlyInAnyOrder(1, 5);
    assertThat(targetsX(board.candidates(seeker(GOLEM, ChannelMask.of(DyeColor.BLUE)), ignored -> false, now))).containsExactlyInAnyOrder(1);
  }

  @Test
  void onlyTheOwnersGolemsWithTheConsumingCoreMatch() {
    NodeKey harvest = harvestNode(0, 64, 0, null, 3);
    NodeKey lumber = node(NodeType.LUMBER, OWNER, 4, 64, 0, null, 3);
    NodeKey foreign = node(NodeType.HARVEST, STRANGER, 8, 64, 0, null, 3);
    board.offer(crop(harvest, 1, 64, 1), 16);
    board.offer(GolemTask.builder(lumber, NodeType.LUMBER, "fell", GolemTarget.block(WORLD, 5, 64, 1)).build(), 16);
    board.offer(crop(foreign, 9, 64, 1), 16);

    assertThat(targetsX(board.candidates(seeker(GOLEM), ignored -> false, now))).containsExactly(1);
    assertThat(targetsX(board.candidates(seeker(GOLEM, ChannelMask.NONE, List.of(GolemCoreType.LUMBER), GolemBody.IRON), ignored -> false, now)))
        .containsExactly(5);
    assertThat(board.candidates(seeker(GOLEM, ChannelMask.NONE, List.of(GolemCoreType.BLANK), GolemBody.IRON), ignored -> false, now)).isEmpty();
  }

  @Test
  void taskBodyRestrictionsAreHonored() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    board.offer(GolemTask.builder(node, NodeType.HARVEST, "harvest", GolemTarget.block(WORLD, 1, 64, 1))
        .bodies(EnumSet.of(GolemBody.IRON)).build(), 16);
    board.offer(crop(node, 2, 64, 1), 16);

    assertThat(targetsX(board.candidates(seeker(GOLEM, ChannelMask.NONE, List.of(GolemCoreType.HARVESTER), GolemBody.FROST), ignored -> false, now)))
        .containsExactly(2);
    assertThat(targetsX(board.candidates(seeker(GOLEM, ChannelMask.NONE, List.of(GolemCoreType.HARVESTER), GolemBody.IRON), ignored -> false, now)))
        .containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void targetsBeyondTheWorkRadiusOfHomeAreIgnored() {
    NodeKey near = harvestNode(20, 64, 0, null, 3);
    NodeKey far = harvestNode(40, 64, 0, null, 3);
    board.offer(crop(near, 22, 64, 0), 16);
    board.offer(crop(far, 40, 64, 0), 16);

    assertThat(targetsX(board.candidates(seeker(GOLEM), ignored -> false, now))).containsExactly(22);
  }

  @Test
  void candidatesSortByPriorityThenDistanceFromTheGolem() {
    NodeKey low = harvestNode(0, 64, 0, null, 1);
    NodeKey high = harvestNode(16, 64, 0, null, 5);
    NodeKey mid = harvestNode(-16, 64, 0, null, 3);
    board.offer(crop(low, 1, 64, 0), 16);
    board.offer(crop(high, 18, 64, 0), 16);
    board.offer(crop(high, 12, 64, 0), 16);
    board.offer(crop(mid, -10, 64, 0), 16);
    board.offer(crop(mid, -2, 64, 0), 16);

    assertThat(targetsX(board.candidates(seeker(GOLEM), ignored -> false, now))).containsExactly(12, 18, -2, -10, 1);
  }

  @Test
  void skippedTasksAreNeitherCandidatesNorClaimed() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask first = crop(node, 1, 64, 0);
    GolemTask second = crop(node, 6, 64, 0);
    board.offer(first, 16);
    board.offer(second, 16);

    assertThat(board.claim(seeker(GOLEM), task -> task == first, now, LEASE)).containsSame(second);
  }

  @Test
  void leasedTasksAreNotCandidatesForOtherGolems() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 0);
    board.offer(task, 16);
    board.claim(seeker(GOLEM), ignored -> false, now, LEASE);

    assertThat(board.candidates(seeker(RIVAL), ignored -> false, now)).isEmpty();
  }

  @Test
  void nodeActivityTurnsIntoAStatus() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    GolemTask task = crop(node, 1, 64, 0);
    board.offer(task, 16);

    assertThat(board.status(node, now)).isEqualTo(NodeStatus.IDLE);
    board.claim(seeker(GOLEM), ignored -> false, now, LEASE);
    assertThat(board.status(node, now)).isEqualTo(NodeStatus.WORKING);
    board.release(task, GOLEM);
    board.markBlocked(node, now);
    assertThat(board.status(node, now)).isEqualTo(NodeStatus.BLOCKED);
    now += GolemTaskBoard.ACTIVITY_MILLIS;
    assertThat(board.status(node, now)).isEqualTo(NodeStatus.IDLE);
    board.markWorked(node, now);
    assertThat(board.status(node, now)).isEqualTo(NodeStatus.WORKING);
  }

  @Test
  void theItemPayloadIsPartOfTheTaskIdentity() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    ItemStack wheat = selfCloning(Material.WHEAT);
    ItemStack carrot = selfCloning(Material.CARROT);

    assertThat(board.offer(sort(node, wheat), 16)).isEqualTo(GolemTaskBoard.Offer.ADDED);
    assertThat(board.offer(sort(node, carrot), 16)).isEqualTo(GolemTaskBoard.Offer.ADDED);
    assertThat(board.offer(sort(node, wheat), 16)).isEqualTo(GolemTaskBoard.Offer.EXISTS);
    assertThat(board.openCount(node)).isEqualTo(2);
  }

  @Test
  void tasksWithoutANodeNeverGoOnABoard() {
    GolemTask loose = GolemTask.builder("pickup", GolemTarget.block(WORLD, 1, 64, 1)).build();

    assertThat(loose.node()).isEmpty();
    assertThatThrownBy(() -> board.offer(loose, 16)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aNodeRemovedAndPlacedAgainIsFoundAgain() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    board.offer(crop(node, 1, 64, 1), 16);
    NodeRecord record = nodes.remove(node);
    board.purge(now);
    nodes.put(node, record);

    GolemTask replacement = crop(node, 2, 64, 1);
    board.offer(replacement, 16);

    assertThat(board.claim(seeker(GOLEM), ignored -> false, now, LEASE)).containsSame(replacement);
  }

  @Test
  void theItemPayloadIdentityIgnoresTheStackAmount() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    AtomicInteger hashes = new AtomicInteger();

    assertThat(board.offer(sort(node, new PayloadStack(Material.WHEAT, 5, hashes)), 16)).isEqualTo(GolemTaskBoard.Offer.ADDED);
    assertThat(board.offer(sort(node, new PayloadStack(Material.WHEAT, 9, hashes)), 16)).isEqualTo(GolemTaskBoard.Offer.EXISTS);
    assertThat(board.offer(sort(node, new PayloadStack(Material.CARROT, 5, hashes)), 16)).isEqualTo(GolemTaskBoard.Offer.ADDED);
    assertThat(board.openCount(node)).isEqualTo(2);
  }

  @Test
  void aTaskKeyHashesItsItemAtMostOnce() {
    NodeKey node = harvestNode(0, 64, 0, null, 3);
    AtomicInteger hashes = new AtomicInteger();
    GolemTask task = sort(node, new PayloadStack(Material.WHEAT, 5, hashes));

    for (int i = 0; i < 5; i++) {
      board.offer(task, 16);
    }
    board.withdraw(task);

    assertThat(hashes.get()).isLessThanOrEqualTo(1);
  }

  @Test
  void aReservationKeepsOtherGolemsOffItsKeyUntilItIsReleased() {
    GolemTask mine = loose(4);
    GolemTask theirs = loose(4);

    assertThat(board.reserve(mine, GOLEM, now, RESERVATION)).isTrue();
    assertThat(board.reserve(mine, GOLEM, now, RESERVATION)).isTrue();
    assertThat(board.reserve(theirs, RIVAL, now + 1_000L, RESERVATION)).isFalse();
    assertThat(board.reservedByOther(theirs, RIVAL, now + 1_000L)).isTrue();
    assertThat(board.reservedByOther(mine, GOLEM, now + 1_000L)).isFalse();
    assertThat(board.reservedByOther(loose(5), RIVAL, now + 1_000L)).isFalse();
    assertThat(board.releaseReservation(theirs, RIVAL)).isFalse();
    assertThat(board.releaseReservation(mine, GOLEM)).isTrue();

    assertThat(board.reservedByOther(theirs, RIVAL, now + 1_000L)).isFalse();
    assertThat(board.reserve(theirs, RIVAL, now + 1_000L, RESERVATION)).isTrue();
  }

  @Test
  void keepingAReservationRenewsItAndALapsedOneGoesToTheNextGolem() {
    GolemTask mine = loose(4);
    GolemTask theirs = loose(4);
    board.reserve(mine, GOLEM, now, RESERVATION);

    assertThat(board.keepReservation(mine, GOLEM, now + 3_000L, RESERVATION)).isTrue();
    assertThat(board.keepReservation(mine, GOLEM, now + 7_000L, RESERVATION)).isTrue();
    assertThat(board.reserve(theirs, RIVAL, now + 11_000L, RESERVATION)).isFalse();
    assertThat(board.keepReservation(theirs, RIVAL, now + 11_000L, RESERVATION)).isFalse();

    long lapsed = now + 12_000L;
    assertThat(board.reservedByOther(theirs, RIVAL, lapsed)).isFalse();
    assertThat(board.reserve(theirs, RIVAL, lapsed, RESERVATION)).isTrue();
    assertThat(board.keepReservation(mine, GOLEM, lapsed, RESERVATION)).isFalse();
    assertThat(board.releaseReservation(mine, GOLEM)).isFalse();
    assertThat(board.reservedByOther(mine, GOLEM, lapsed)).isTrue();
  }

  @Test
  void concurrentReservationsOfOneKeyHaveExactlyOneWinner() throws Exception {
    int contenders = 16;
    ExecutorService pool = Executors.newFixedThreadPool(contenders);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger winners = new AtomicInteger();
    List<Future<?>> futures = new ArrayList<>(contenders);
    try {
      for (int i = 0; i < contenders; i++) {
        UUID golem = new UUID(9L, i);
        GolemTask task = loose(4);
        futures.add(pool.submit(() -> {
          start.await();
          if (board.reserve(task, golem, now, RESERVATION)) {
            winners.incrementAndGet();
          }
          return null;
        }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(winners).hasValue(1);
  }

  @Test
  void purgingKeepsLiveReservations() {
    board.reserve(loose(4), GOLEM, now, RESERVATION);
    board.reserve(loose(5), GOLEM, now + 4_000L, RESERVATION);

    board.purge(now + 6_000L);

    assertThat(board.reservedByOther(loose(5), RIVAL, now + 6_000L)).isTrue();
    assertThat(board.reservedByOther(loose(4), RIVAL, now + 6_000L)).isFalse();
    assertThat(board.reserve(loose(4), RIVAL, now + 6_000L, RESERVATION)).isTrue();
  }

  @Test
  void chunkTasksListTheOpenTasksOfEveryNodeInOneChunk() {
    NodeKey first = harvestNode(0, 64, 0, null, 3);
    NodeKey second = harvestNode(15, 200, 15, null, 3);
    NodeKey elsewhere = harvestNode(16, 64, 0, null, 3);
    GolemTask kept = crop(first, 1, 64, 1);
    GolemTask high = crop(second, 14, 200, 14);
    GolemTask done = crop(first, 2, 64, 1);
    board.offer(kept, 16);
    board.offer(high, 16);
    board.offer(done, 16);
    board.offer(crop(elsewhere, 17, 64, 1), 16);
    board.withdraw(done);

    assertThat(board.chunkTasks(first.chunkKey())).containsExactlyInAnyOrder(kept, high);
    assertThat(board.chunkTasks(GolemRoster.chunkKey(5, 5))).isEmpty();
  }

  private NodeKey harvestNode(int x, int y, int z, DyeColor channel, int priority) {
    return node(NodeType.HARVEST, OWNER, x, y, z, channel, priority);
  }

  private NodeKey node(NodeType type, UUID owner, int x, int y, int z, DyeColor channel, int priority) {
    NodeKey key = new NodeKey(WORLD, x, y, z, BlockFace.UP);
    nodes.put(key, NodeRecord.builder(owner, type, BlockFace.UP).channel(channel).priority(priority).build());
    return key;
  }

  private static GolemTask crop(NodeKey node, int x, int y, int z) {
    return GolemTask.builder(node, NodeType.HARVEST, "harvest", GolemTarget.block(WORLD, x, y, z)).build();
  }

  private static GolemTask loose(int x) {
    return GolemTask.builder("gather", GolemTarget.block(WORLD, x, 64, 0)).build();
  }

  private static GolemTask sort(NodeKey node, ItemStack item) {
    return GolemTask.builder(node, NodeType.HARVEST, "sort", GolemTarget.block(WORLD, 1, 64, 1)).item(item).build();
  }

  private static ItemStack selfCloning(Material type) {
    ItemStack item = mock(ItemStack.class);
    lenient().when(item.getType()).thenReturn(type);
    lenient().when(item.clone()).thenReturn(item);
    return item;
  }

  private static GolemTaskBoard.Seeker seeker(UUID golem) {
    return seeker(golem, ChannelMask.NONE);
  }

  private static GolemTaskBoard.Seeker seeker(UUID golem, int channels) {
    return seeker(golem, channels, List.of(GolemCoreType.HARVESTER), GolemBody.IRON);
  }

  private static GolemTaskBoard.Seeker seekerFor(UUID golem, GolemTask task) {
    GolemTarget target = task.target();
    return new GolemTaskBoard.Seeker(golem, OWNER, List.of(GolemCoreType.HARVESTER), GolemBody.IRON, ChannelMask.NONE,
        target.x(), target.y(), target.z(), 24D, target.x(), target.y(), target.z());
  }

  private static GolemTaskBoard.Seeker seeker(UUID golem, int channels, List<GolemCoreType> cores, GolemBody body) {
    return new GolemTaskBoard.Seeker(golem, OWNER, cores, body, channels, 0.5D, 64D, 0.5D, 24D, 0.5D, 64D, 0.5D);
  }

  private static List<Integer> targetsX(List<GolemTask> tasks) {
    List<Integer> xs = new ArrayList<>(tasks.size());
    for (GolemTask task : tasks) {
      xs.add(task.target().blockX());
    }
    return xs;
  }

  private static final class PayloadStack extends ItemStack {
    private final Material material;
    private final AtomicInteger hashes;
    private int count;

    private PayloadStack(Material material, int count, AtomicInteger hashes) {
      this.material = material;
      this.count = count;
      this.hashes = hashes;
    }

    @Override
    public Material getType() {
      return material;
    }

    @Override
    public int getAmount() {
      return count;
    }

    @Override
    public void setAmount(int amount) {
      count = amount;
    }

    @Override
    public ItemStack clone() {
      return new PayloadStack(material, count, hashes);
    }

    @Override
    public boolean isSimilar(ItemStack other) {
      return other instanceof PayloadStack stack && stack.material == material;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof PayloadStack stack && stack.count == count && isSimilar(stack);
    }

    @Override
    public int hashCode() {
      hashes.incrementAndGet();
      return 31 * material.hashCode() + count;
    }
  }
}
