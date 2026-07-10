package art.arcane.adapt.content.adaptation.ranged;

import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class HeartseekerRuntimeTest {
  @Test
  void admitsOneArrowForEachOfOneThousandOwnersAndRejectsGlobalOverflow() {
    HeartseekerCoordinator coordinator = new HeartseekerCoordinator(1_024, 3);

    for (int index = 0; index < 1_000; index++) {
      UUID ownerId = new UUID(1L, index + 1L);
      UUID arrowId = new UUID(2L, index + 1L);
      assertThat(coordinator.admit(ownerId, arrowId)).isPositive();
    }

    for (int index = 0; index < 24; index++) {
      assertThat(coordinator.admit(new UUID(3L, index + 1L), new UUID(4L, index + 1L))).isPositive();
    }
    assertThat(coordinator.admit(new UUID(5L, 1L), new UUID(6L, 1L))).isEqualTo(-1L);
    assertThat(coordinator.activeCount()).isEqualTo(1_024);
  }

  @Test
  void perOwnerAdmissionPreventsOneShooterFromMonopolizingCapacity() {
    HeartseekerCoordinator coordinator = new HeartseekerCoordinator(10, 2);
    UUID ownerId = new UUID(1L, 1L);

    assertThat(coordinator.admit(ownerId, new UUID(2L, 1L))).isPositive();
    assertThat(coordinator.admit(ownerId, new UUID(2L, 2L))).isPositive();
    assertThat(coordinator.admit(ownerId, new UUID(2L, 3L))).isEqualTo(-1L);
    assertThat(coordinator.ownerActiveCount(ownerId)).isEqualTo(2);
  }

  @Test
  void dispatchesOwnersRoundRobinBeforeReturningToBusyOwner() {
    HeartseekerCoordinator coordinator = new HeartseekerCoordinator(10, 3);
    UUID firstOwner = new UUID(1L, 1L);
    UUID secondOwner = new UUID(1L, 2L);
    UUID thirdOwner = new UUID(1L, 3L);
    UUID firstArrow = new UUID(2L, 1L);
    UUID secondArrow = new UUID(2L, 2L);
    UUID thirdArrow = new UUID(2L, 3L);
    UUID fourthArrow = new UUID(2L, 4L);

    coordinator.admit(firstOwner, firstArrow);
    coordinator.admit(firstOwner, secondArrow);
    coordinator.admit(secondOwner, thirdArrow);
    coordinator.admit(thirdOwner, fourthArrow);

    List<HeartseekerCoordinator.Dispatch> dispatches = coordinator.takeDispatches(4);

    assertThat(dispatches).extracting(HeartseekerCoordinator.Dispatch::arrowId)
        .containsExactly(firstArrow, thirdArrow, fourthArrow, secondArrow);
  }

  @Test
  void staleCompletionCannotReviveRemovedArrowGeneration() {
    HeartseekerCoordinator coordinator = new HeartseekerCoordinator(4, 2);
    UUID ownerId = new UUID(1L, 1L);
    UUID arrowId = new UUID(2L, 1L);
    long firstGeneration = coordinator.admit(ownerId, arrowId);
    HeartseekerCoordinator.Dispatch firstDispatch = coordinator.takeDispatches(1).getFirst();

    assertThat(coordinator.remove(arrowId, firstGeneration)).isTrue();
    long secondGeneration = coordinator.admit(ownerId, arrowId);

    assertThat(secondGeneration).isGreaterThan(firstGeneration);
    assertThat(coordinator.complete(firstDispatch.arrowId(), firstDispatch.generation())).isFalse();
    assertThat(coordinator.isCurrent(arrowId, secondGeneration)).isTrue();
    assertThat(coordinator.activeCount()).isEqualTo(1);
  }

  @Test
  void continuationTransferKeepsTheReservedOwnerSlot() {
    HeartseekerCoordinator coordinator = new HeartseekerCoordinator(1, 1);
    UUID ownerId = new UUID(1L, 1L);
    UUID sourceArrow = new UUID(2L, 1L);
    UUID continuationArrow = new UUID(2L, 2L);
    long generation = coordinator.admit(ownerId, sourceArrow);

    assertThat(coordinator.suspend(sourceArrow, generation)).isTrue();
    assertThat(coordinator.transfer(sourceArrow, generation, continuationArrow)).isTrue();
    assertThat(coordinator.activeCount()).isEqualTo(1);
    assertThat(coordinator.ownerActiveCount(ownerId)).isEqualTo(1);
    assertThat(coordinator.isCurrent(sourceArrow, generation)).isFalse();
    assertThat(coordinator.isCurrent(continuationArrow, generation)).isTrue();
    assertThat(coordinator.resume(continuationArrow, generation)).isTrue();
    assertThat(coordinator.takeDispatches(1).getFirst().arrowId()).isEqualTo(continuationArrow);
  }

  @Test
  void frameBudgetHardCapsDispatchRayAndCandidateWork() {
    AtomicLong clock = new AtomicLong();
    HeartseekerFrameBudget budget = new HeartseekerFrameBudget(3, 5, 7, 2, 4, 6, 50L, clock::get);

    assertThat(grants(10, budget::tryDispatch)).isEqualTo(3);
    assertThat(grants(10, budget::tryRayTrace)).isEqualTo(5);
    assertThat(grants(10, budget::tryTargetSnapshot)).isEqualTo(7);
    assertThat(grants(10, budget::tryCandidateScan)).isEqualTo(2);
    assertThat(grants(10, budget::tryCandidateHandoff)).isEqualTo(4);
    assertThat(grants(10, budget::tryTrailPoint)).isEqualTo(6);

    clock.set(50L);

    assertThat(budget.tryDispatch()).isTrue();
    assertThat(budget.tryRayTrace()).isTrue();
    assertThat(budget.tryCandidateScan()).isTrue();
    assertThat(budget.tryCandidateHandoff()).isTrue();
    assertThat(budget.tryTrailPoint()).isTrue();
  }

  @Test
  void productionWindowCapsOneThousandConcurrentWorkRequests() {
    AtomicLong clock = new AtomicLong();
    HeartseekerFrameBudget budget = new HeartseekerFrameBudget(
        256,
        512,
        256,
        24,
        192,
        512,
        50L,
        clock::get
    );

    assertThat(grants(1_000, budget::tryDispatch)).isEqualTo(256);
    assertThat(grants(1_000, budget::tryRayTrace)).isEqualTo(512);
    assertThat(grants(1_000, budget::tryTargetSnapshot)).isEqualTo(256);
    assertThat(grants(1_000, budget::tryCandidateScan)).isEqualTo(24);
    assertThat(grants(1_000, budget::tryCandidateHandoff)).isEqualTo(192);
    assertThat(grants(1_000, budget::tryTrailPoint)).isEqualTo(512);
  }

  @Test
  void lifecycleInvalidationRejectsCleanupCallbacksFromEarlierRun() {
    HeartseekerLifecycle lifecycle = new HeartseekerLifecycle();
    long running = lifecycle.current();

    assertThat(lifecycle.isCurrent(running)).isTrue();
    long restarted = lifecycle.invalidate();

    assertThat(restarted).isGreaterThan(running);
    assertThat(lifecycle.isCurrent(running)).isFalse();
    assertThat(lifecycle.isCurrent(restarted)).isTrue();
  }

  @Test
  void finalLaunchLevelsDrivePiercingAndRicochetPasses() {
    assertThat(HeartseekerChainRules.resolvePasses(3, 2, 8)).isEqualTo(5);
    assertThat(HeartseekerChainRules.resolvePasses(0, 4, 8)).isEqualTo(4);
    assertThat(HeartseekerChainRules.resolvePasses(12, 12, 8)).isEqualTo(8);
    assertThat(HeartseekerChainRules.resolvePasses(-1, -1, 8)).isZero();
  }

  @Test
  void piercingAndRicochetPassesRetainTheirInteractionRules() {
    HeartseekerPassBudget budget = HeartseekerChainRules.resolveBudget(3, 2, 8);

    assertThat(budget.total()).isEqualTo(5);
    assertThat(budget.afterEntityPass()).isEqualTo(new HeartseekerPassBudget(2, 2));
    assertThat(budget.afterBlockRicochet()).isEqualTo(new HeartseekerPassBudget(3, 1));
    assertThat(HeartseekerChainRules.resolveBudget(7, 7, 8))
        .isEqualTo(new HeartseekerPassBudget(7, 1));
  }

  @Test
  void continuationKeepsTheIncomingDirectionThroughTheTarget() {
    Vector incoming = new Vector(0.35D, 0.1D, 1D);
    Vector direction = RangedHeartseeker.resolveContinuationDirection(
        incoming,
        new Vector(-1D, 0D, 0D),
        new Vector(0D, 0D, -1D)
    );

    assertThat(direction.length()).isCloseTo(1D, offset(0.000001D));
    assertThat(direction.dot(incoming.clone().normalize())).isCloseTo(1D, offset(0.000001D));
  }

  @Test
  void blockRicochetReflectsBeforeSeekingResumes() {
    RicochetProfile profile = new RicochetProfile(2, 0.25D, 1.5D, 0.09D, 0.45D);
    RicochetTransition transition = profile.next(
        0,
        0D,
        new Vector(1D, 0.2D, 0D),
        new Vector(-1D, 0D, 0D)
    );

    assertThat(transition).isNotNull();
    assertThat(transition.direction().length()).isCloseTo(1D, offset(0.000001D));
    assertThat(transition.direction().getX()).isNegative();
    assertThat(transition.direction().getY()).isPositive();
    assertThat(transition.count()).isEqualTo(1);
    assertThat(transition.bonusDamage()).isEqualTo(1.5D);
    assertThat(transition.speed()).isCloseTo(new Vector(1D, 0.2D, 0D).length() * 1.25D,
        offset(0.000001D));
    assertThat(profile.next(2, 3D, new Vector(1D, 0D, 0D), new Vector(-1D, 0D, 0D)))
        .isNull();
  }

  @Test
  void continuationExitDistanceCrossesTheFarSideOfTheTarget() {
    BoundingBox target = new BoundingBox(0D, 0D, 0D, 1D, 2D, 1D);

    assertThat(RangedHeartseeker.continuationExitDistance(
        new Vector(-1D, 1D, 0.5D),
        target,
        new Vector(1D, 0D, 0D)
    )).isCloseTo(2D, offset(0.000001D));
    assertThat(RangedHeartseeker.continuationExitDistance(
        new Vector(-1D, 3D, 0.5D),
        target,
        new Vector(1D, 0D, 0D)
    )).isZero();
  }

  @Test
  void rayChunkTraversalChecksCornerAdjacentRegions() {
    Set<String> visited = new HashSet<>();

    assertThat(HeartseekerChunkTraversal.allChunks(
        1D,
        1D,
        33D,
        33D,
        (chunkX, chunkZ) -> {
          visited.add(chunkX + ":" + chunkZ);
          return true;
        }
    )).isTrue();
    assertThat(visited).contains("0:0", "1:0", "0:1", "1:1", "2:1", "1:2", "2:2");
    assertThat(HeartseekerChunkTraversal.allChunks(
        1D,
        1D,
        33D,
        33D,
        (chunkX, chunkZ) -> chunkX != 1 || chunkZ != 0
    )).isFalse();
  }

  @Test
  void pendingWorkCapacityReleasesWithoutGrowingPastItsLimit() {
    HeartseekerPendingBudget budget = new HeartseekerPendingBudget(2);

    assertThat(budget.tryReserve()).isTrue();
    assertThat(budget.tryReserve()).isTrue();
    assertThat(budget.tryReserve()).isFalse();
    assertThat(budget.activeCount()).isEqualTo(2);

    budget.release();
    assertThat(budget.tryReserve()).isTrue();
    assertThat(budget.activeCount()).isEqualTo(2);
  }

  @Test
  void pendingWorkCapacityRejectsInvalidLimits() {
    assertThatThrownBy(() -> new HeartseekerPendingBudget(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private int grants(int attempts, Attempt attempt) {
    int grants = 0;
    for (int index = 0; index < attempts; index++) {
      if (attempt.tryAcquire()) {
        grants++;
      }
    }
    return grants;
  }

  private interface Attempt {
    boolean tryAcquire();
  }
}
