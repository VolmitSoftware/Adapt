package art.arcane.adapt.content.adaptation.ranged;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
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
        .filter(method -> method.isAnnotationPresent(EventHandler.class))
        .filter(method -> method.getParameterCount() == 1)
        .map(Method::getParameterTypes)
        .map(parameters -> parameters[0])
        .toList();

    assertThat(handledEvents).contains(EntityAddToWorldEvent.class);
    assertThat(handledEvents).doesNotContain(ProjectileLaunchEvent.class);
  }

  @Test
  void heartseekerClaimsLockedShotsBeforeOtherBowAdaptations() throws NoSuchMethodException {
    Method handler = RangedHeartseeker.class.getDeclaredMethod("on", EntityShootBowEvent.class);
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler.priority()).isEqualTo(EventPriority.LOWEST);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void steeringCurvesTowardTargetWithoutAddingLateralOrbit() {
    Vector current = new Vector(1D, 0D, 0D);
    Vector desired = new Vector(0D, 0D, 1D);
    Vector steered = HeartseekerFlightMath.turnTowards(current, desired, Math.toRadians(20D));

    assertThat(steered.length()).isCloseTo(1D, offset(0.000001D));
    assertThat(steered.getX()).isPositive();
    assertThat(steered.getZ()).isPositive();
    assertThat(steered.getY()).isZero();
    assertThat(Math.toDegrees(Math.acos(current.dot(steered))))
        .isCloseTo(20D, offset(0.0001D));
  }

  @Test
  void alignedAndOppositeSteeringRemainFiniteAndBounded() {
    Vector forward = new Vector(0D, 0D, 1D);
    Vector aligned = HeartseekerFlightMath.turnTowards(
        forward,
        forward,
        Math.toRadians(10D)
    );
    Vector opposite = HeartseekerFlightMath.turnTowards(
        forward,
        new Vector(0D, 0D, -1D),
        Math.toRadians(10D)
    );

    assertThat(aligned).isEqualTo(forward);
    assertThat(opposite.length()).isCloseTo(1D, offset(0.000001D));
    assertThat(opposite.getZ()).isPositive();
    assertThat(Double.isFinite(opposite.getX())).isTrue();
    assertThat(Double.isFinite(opposite.getY())).isTrue();
  }

  @Test
  void initialArcStartsInTheFiredDirectionAndThenBendsTowardTarget() {
    Vector origin = new Vector(0D, 0D, 0D);
    Vector firedUp = new Vector(0D, 1D, 0D);
    Vector target = new Vector(12D, 0D, 0D);

    Vector launch = HeartseekerFlightMath.quadraticArcGuidance(
        origin,
        firedUp,
        target,
        0D,
        8D,
        12D
    );
    Vector middle = HeartseekerFlightMath.quadraticArcGuidance(
        origin,
        firedUp,
        target,
        6D,
        8D,
        12D
    );
    Vector finish = HeartseekerFlightMath.quadraticArcGuidance(
        origin,
        firedUp,
        target,
        12D,
        8D,
        12D
    );

    assertThat(launch.getX()).isZero();
    assertThat(launch.getY()).isEqualTo(1D);
    assertThat(middle.getX()).isPositive();
    assertThat(finish.getX()).isPositive();
    assertThat(finish.getY()).isNegative();
  }

  @Test
  void initialArcPreservesArbitraryLaunchTangentsAndDegenerateInputsStayFinite() {
    Vector fired = new Vector(0.35D, 0.8D, -0.2D).normalize();
    Vector launch = HeartseekerFlightMath.quadraticArcGuidance(
        new Vector(4D, 2D, -3D),
        fired,
        new Vector(18D, 5D, 9D),
        0D,
        8D,
        12D
    );
    Vector degenerate = HeartseekerFlightMath.quadraticArcGuidance(
        new Vector(1D, 1D, 1D),
        new Vector(),
        new Vector(1D, 1D, 1D),
        12D,
        0D,
        0D
    );

    assertThat(launch.dot(fired)).isCloseTo(1D, offset(0.000001D));
    assertThat(Double.isFinite(degenerate.getX())).isTrue();
    assertThat(Double.isFinite(degenerate.getY())).isTrue();
    assertThat(Double.isFinite(degenerate.getZ())).isTrue();
  }

  @Test
  void continuationExitRequiresPhysicalClearanceInsteadOfDispatchTiming() {
    Vector origin = new Vector(0D, 0D, 0D);

    assertThat(HeartseekerFlightMath.hasTraveledMinimum(
        origin,
        new Vector(7.99D, 0D, 0D),
        8D
    )).isFalse();
    assertThat(HeartseekerFlightMath.hasTraveledMinimum(
        origin,
        new Vector(8D, 0D, 0D),
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
    HeartseekerFlightMath.Facing east = HeartseekerFlightMath.facing(new Vector(1D, 0D, 0D));
    HeartseekerFlightMath.Facing up = HeartseekerFlightMath.facing(new Vector(0D, 1D, 0D));

    assertThat(east.yaw()).isCloseTo(-90F, offset(0.0001F));
    assertThat(east.pitch()).isCloseTo(0F, offset(0.0001F));
    assertThat(up.pitch()).isCloseTo(-90F, offset(0.0001F));
  }

  @Test
  void avoidanceProbesEightDistinctForwardRoutesAroundAnObstacle() {
    Vector forward = new Vector(1D, 0D, 0D);
    Set<String> routes = new HashSet<>();

    for (int index = 0; index < 8; index++) {
      Vector route = HeartseekerFlightMath.avoidance(forward, index, 1.15D);
      assertThat(route.length()).isCloseTo(1D, offset(0.000001D));
      assertThat(route.dot(forward)).isPositive();
      routes.add(String.format("%.4f:%.4f:%.4f", route.getX(), route.getY(), route.getZ()));
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
