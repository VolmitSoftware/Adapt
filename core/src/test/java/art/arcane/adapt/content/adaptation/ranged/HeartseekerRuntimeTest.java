package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.EntityWorldEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
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
  void continuationPassesThroughTheMobItJustPiercedWhileSeekingAnother() {
    UUID victim = UUID.randomUUID();
    UUID next = UUID.randomUUID();
    UUID bystander = UUID.randomUUID();
    assertThat(HeartseekerChainRules.passesThroughPreviousVictim(victim, next, victim)).isTrue();
    assertThat(HeartseekerChainRules.passesThroughPreviousVictim(victim, null, victim)).isTrue();
    assertThat(HeartseekerChainRules.passesThroughPreviousVictim(victim, victim, victim)).isFalse();
    assertThat(HeartseekerChainRules.passesThroughPreviousVictim(victim, next, next)).isFalse();
    assertThat(HeartseekerChainRules.passesThroughPreviousVictim(victim, next, bystander)).isFalse();
    assertThat(HeartseekerChainRules.passesThroughPreviousVictim(null, next, victim)).isFalse();
  }

  @Test
  void initialArcEndsWhenTheArrowReachesLungeRangeBeforeTheArcLength() {
    assertThat(HeartseekerFlightMath.initialArcContinues(true, 7.6D, 12D, 1.5D, 2.5D)).isFalse();
    assertThat(HeartseekerFlightMath.initialArcContinues(true, 7.6D, 12D, 3D, 2.5D)).isTrue();
    assertThat(HeartseekerFlightMath.initialArcContinues(true, 12D, 12D, 6D, 2.5D)).isFalse();
    assertThat(HeartseekerFlightMath.initialArcContinues(false, 1D, 12D, 6D, 2.5D)).isFalse();
  }

  @Test
  void seekingSpeedUsesRawLaunchSpeedWithFloorAndFullDrawCeiling() {
    assertThat(HeartseekerFlightMath.seekSpeed(3D)).isEqualTo(1.5D);
    assertThat(HeartseekerFlightMath.seekSpeed(4D)).isEqualTo(1.5D);
    assertThat(HeartseekerFlightMath.seekSpeed(1.8D)).isEqualTo(0.9D);
    assertThat(HeartseekerFlightMath.seekSpeed(0D)).isEqualTo(0.9D);
    assertThat(HeartseekerFlightMath.seekSpeed(-4D)).isEqualTo(0.9D);
  }

  @Test
  void initialAdmissionWaitsUntilPaperMarksTheArrowValid() {
    List<Class<?>> handledEvents = Arrays.stream(RangedHeartseeker.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(Handle.class))
        .filter(method -> method.getParameterCount() == 1)
        .map(Method::getParameterTypes)
        .map(parameters -> parameters[0])
        .toList();

    // Admission waits for the arrow to enter the world, not for its launch.
    assertThat(handledEvents).contains(EntityWorldEvent.class);
    assertThat(handledEvents).doesNotContain(ProjectileLaunchEvent.class);
  }

  @Test
  void heartseekerClaimsLockedShotsBeforeOtherBowAdaptations() throws NoSuchMethodException {
    Method handler = RangedHeartseeker.class.getDeclaredMethod("on", EntityShootBowEvent.class);
    Handle eventHandler = handler.getAnnotation(Handle.class);

    assertThat(eventHandler.priority()).isEqualTo(EventPriority.LOWEST);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void steeringCurvesTowardTargetWithoutAddingLateralOrbit() {
    Vec3 current = new Vec3(1D, 0D, 0D);
    Vec3 desired = new Vec3(0D, 0D, 1D);
    Vec3 steered = HeartseekerFlightMath.turnTowards(current, desired, Math.toRadians(20D));

    assertThat(steered.length()).isCloseTo(1D, offset(0.000001D));
    assertThat(steered.x()).isPositive();
    assertThat(steered.z()).isPositive();
    assertThat(steered.y()).isZero();
    assertThat(Math.toDegrees(Math.acos(current.dot(steered))))
        .isCloseTo(20D, offset(0.0001D));
  }

  @Test
  void alignedAndOppositeSteeringRemainFiniteAndBounded() {
    Vec3 forward = new Vec3(0D, 0D, 1D);
    Vec3 aligned = HeartseekerFlightMath.turnTowards(
        forward,
        forward,
        Math.toRadians(10D)
    );
    Vec3 opposite = HeartseekerFlightMath.turnTowards(
        forward,
        new Vec3(0D, 0D, -1D),
        Math.toRadians(10D)
    );

    assertThat(aligned).isEqualTo(forward);
    assertThat(opposite.length()).isCloseTo(1D, offset(0.000001D));
    assertThat(opposite.z()).isPositive();
    assertThat(Double.isFinite(opposite.x())).isTrue();
    assertThat(Double.isFinite(opposite.y())).isTrue();
  }

  @Test
  void initialArcStartsInTheFiredDirectionAndThenBendsTowardTarget() {
    Vec3 origin = new Vec3(0D, 0D, 0D);
    Vec3 firedUp = new Vec3(0D, 1D, 0D);
    Vec3 target = new Vec3(12D, 0D, 0D);

    Vec3 launch = HeartseekerFlightMath.quadraticArcGuidance(
        origin,
        firedUp,
        target,
        0D,
        8D,
        12D
    );
    Vec3 middle = HeartseekerFlightMath.quadraticArcGuidance(
        origin,
        firedUp,
        target,
        6D,
        8D,
        12D
    );
    Vec3 finish = HeartseekerFlightMath.quadraticArcGuidance(
        origin,
        firedUp,
        target,
        12D,
        8D,
        12D
    );

    assertThat(launch.x()).isZero();
    assertThat(launch.y()).isEqualTo(1D);
    assertThat(middle.x()).isPositive();
    assertThat(finish.x()).isPositive();
    assertThat(finish.y()).isNegative();
  }

  @Test
  void initialArcPreservesArbitraryLaunchTangentsAndDegenerateInputsStayFinite() {
    Vec3 fired = new Vec3(0.35D, 0.8D, -0.2D).normalize();
    Vec3 launch = HeartseekerFlightMath.quadraticArcGuidance(
        new Vec3(4D, 2D, -3D),
        fired,
        new Vec3(18D, 5D, 9D),
        0D,
        8D,
        12D
    );
    Vec3 degenerate = HeartseekerFlightMath.quadraticArcGuidance(
        new Vec3(1D, 1D, 1D),
        Vec3.ZERO,
        new Vec3(1D, 1D, 1D),
        12D,
        0D,
        0D
    );

    assertThat(launch.dot(fired)).isCloseTo(1D, offset(0.000001D));
    assertThat(Double.isFinite(degenerate.x())).isTrue();
    assertThat(Double.isFinite(degenerate.y())).isTrue();
    assertThat(Double.isFinite(degenerate.z())).isTrue();
  }

  @Test
  void continuationExitRequiresPhysicalClearanceInsteadOfDispatchTiming() {
    Vec3 origin = new Vec3(0D, 0D, 0D);

    assertThat(HeartseekerFlightMath.hasTraveledMinimum(
        origin,
        new Vec3(7.99D, 0D, 0D),
        8D
    )).isFalse();
    assertThat(HeartseekerFlightMath.hasTraveledMinimum(
        origin,
        new Vec3(8D, 0D, 0D),
        8D
    )).isTrue();
    assertThat(new RangedHeartseeker.Config().continuationExitDistance).isEqualTo(8D);
  }

  @Test
  void repeatExitExpandsForDamageImmunityAtAggressiveTurnRates() {
    assertThat(HeartseekerFlightMath.repeatExitDistance(1.5D, 20, 18D, 8D))
        .isEqualTo(9D);
    assertThat(HeartseekerFlightMath.repeatExitDistance(1.5D, 20, 90D, 8D))
        .isEqualTo(15D);
    assertThat(HeartseekerFlightMath.repeatExitDistance(0.9D, 20, 18D, 8D))
        .isEqualTo(8D);
  }

  @Test
  void facingMatchesTheFlightVectorIncludingVerticalAim() {
    HeartseekerFlightMath.Facing east = HeartseekerFlightMath.facing(new Vec3(1D, 0D, 0D));
    HeartseekerFlightMath.Facing up = HeartseekerFlightMath.facing(new Vec3(0D, 1D, 0D));

    assertThat(east.yaw()).isCloseTo(-90F, offset(0.0001F));
    assertThat(east.pitch()).isCloseTo(0F, offset(0.0001F));
    assertThat(up.pitch()).isCloseTo(-90F, offset(0.0001F));
  }

  @Test
  void avoidanceProbesEightDistinctForwardRoutesAroundAnObstacle() {
    Vec3 forward = new Vec3(1D, 0D, 0D);
    Set<String> routes = new HashSet<>();

    for (int index = 0; index < 8; index++) {
      Vec3 route = HeartseekerFlightMath.avoidance(forward, index, 1.15D);
      assertThat(route.length()).isCloseTo(1D, offset(0.000001D));
      assertThat(route.dot(forward)).isPositive();
      routes.add(String.format("%.4f:%.4f:%.4f", route.x(), route.y(), route.z()));
    }

    assertThat(routes).hasSize(8);
  }

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
    assertThat(HeartseekerChainRules.resolveBudget(7, 7, 8))
        .isEqualTo(new HeartseekerPassBudget(7, 1));
  }

  @Test
  void continuationKeepsTheIncomingDirectionThroughTheTarget() {
    Vec3 incoming = new Vec3(0.35D, 0.1D, 1D);
    Vec3 direction = RangedHeartseeker.resolveContinuationDirection(
        incoming,
        new Vec3(-1D, 0D, 0D),
        new Vec3(0D, 0D, -1D)
    );

    assertThat(direction.length()).isCloseTo(1D, offset(0.000001D));
    assertThat(direction.dot(incoming.normalize())).isCloseTo(1D, offset(0.000001D));
  }

  @Test
  void continuationExitDistanceCrossesTheFarSideOfTheTarget() {
    Box target = new Box(0D, 0D, 0D, 1D, 2D, 1D);

    assertThat(RangedHeartseeker.continuationExitDistance(
        new Vec3(-1D, 1D, 0.5D),
        target,
        new Vec3(1D, 0D, 0D)
    )).isCloseTo(2D, offset(0.000001D));
    assertThat(RangedHeartseeker.continuationExitDistance(
        new Vec3(-1D, 3D, 0.5D),
        target,
        new Vec3(1D, 0D, 0D)
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
