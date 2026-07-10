package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiftVoidMagnetRuntimeTest {
  @Test
  void coordinatorEnforcesCapacityAndExactRemoval() {
    RiftVoidMagnet.MagnetCoordinator<String> coordinator = new RiftVoidMagnet.MagnetCoordinator<>(2);
    UUID first = new UUID(1L, 1L);
    UUID second = new UUID(1L, 2L);
    UUID third = new UUID(1L, 3L);

    assertThat(coordinator.admit(first, "first", 0L)).isTrue();
    assertThat(coordinator.admit(first, "duplicate", 0L)).isFalse();
    assertThat(coordinator.admit(second, "second", 0L)).isTrue();
    assertThat(coordinator.admit(third, "third", 0L)).isFalse();
    assertThat(coordinator.size()).isEqualTo(2);

    assertThat(coordinator.remove(first)).isTrue();
    assertThat(coordinator.remove(first)).isFalse();
    assertThat(coordinator.admit(third, "third", 0L)).isTrue();
    assertThat(coordinator.clear()).isEqualTo(2);
    assertThat(coordinator.size()).isZero();
    assertThat(coordinator.poll(0L, 256, 64)).isEmpty();
  }

  @Test
  void coordinatorRotatesOneThousandDueOwnersWithoutStarvation() {
    RiftVoidMagnet.MagnetCoordinator<Integer> coordinator =
        new RiftVoidMagnet.MagnetCoordinator<>(RiftVoidMagnet.HARD_MAX_ACTIVE_SESSIONS);
    List<UUID> owners = new ArrayList<>(1_000);
    for (int index = 0; index < 1_000; index++) {
      UUID ownerId = new UUID(2L, index + 1L);
      owners.add(ownerId);
      assertThat(coordinator.admit(ownerId, index, 0L)).isTrue();
    }

    Set<UUID> dispatchedOwners = new LinkedHashSet<>();
    for (int round = 0; round < 16; round++) {
      List<RiftVoidMagnet.MagnetDispatch<Integer>> dispatches = coordinator.poll(
          0L,
          RiftVoidMagnet.HARD_MAX_SESSION_VISITS_PER_WINDOW,
          RiftVoidMagnet.HARD_MAX_SCAN_OWNER_HANDOFFS_PER_WINDOW
      );
      assertThat(dispatches).hasSizeLessThanOrEqualTo(RiftVoidMagnet.HARD_MAX_SCAN_OWNER_HANDOFFS_PER_WINDOW);
      for (RiftVoidMagnet.MagnetDispatch<Integer> dispatch : dispatches) {
        assertThat(dispatchedOwners.add(dispatch.ownerId())).isTrue();
        assertThat(coordinator.complete(dispatch.ownerId(), dispatch.generation(), Long.MAX_VALUE)).isTrue();
      }
    }

    assertThat(dispatchedOwners).containsExactlyElementsOf(owners);
    assertThat(coordinator.poll(0L, 256, 64)).isEmpty();
  }

  @Test
  void coordinatorHonorsDueTimeAndRejectsStaleCompletion() {
    RiftVoidMagnet.MagnetCoordinator<String> coordinator = new RiftVoidMagnet.MagnetCoordinator<>(1);
    UUID ownerId = new UUID(3L, 1L);
    assertThat(coordinator.admit(ownerId, "owner", 100L)).isTrue();
    assertThat(coordinator.poll(99L, 1, 1)).isEmpty();

    RiftVoidMagnet.MagnetDispatch<String> dispatch = coordinator.poll(100L, 1, 1).getFirst();

    assertThat(coordinator.poll(100L, 1, 1)).isEmpty();
    assertThat(coordinator.complete(ownerId, dispatch.generation() + 1L, 200L)).isFalse();
    assertThat(coordinator.complete(ownerId, dispatch.generation(), 200L)).isTrue();
    assertThat(coordinator.poll(199L, 1, 1)).isEmpty();
    assertThat(coordinator.poll(200L, 1, 1)).hasSize(1);
  }

  @Test
  void workBudgetEnforcesEveryFiftyMillisecondCeilingAndResets() {
    AtomicLong clock = new AtomicLong();
    RiftVoidMagnet.MagnetWorkBudget budget = new RiftVoidMagnet.MagnetWorkBudget(
        3,
        2,
        2,
        4,
        2,
        2,
        50L,
        clock::get
    );

    assertThat(budget.takeSessionVisits(10)).isEqualTo(3);
    assertThat(budget.takeSessionVisits(1)).isZero();
    assertThat(budget.remainingScanOwnerHandoffs()).isEqualTo(2);
    assertThat(budget.takeScanOwnerHandoffs(10)).isEqualTo(2);
    assertThat(budget.remainingScanOwnerHandoffs()).isZero();
    assertThat(budget.tryScanExecution()).isTrue();
    assertThat(budget.tryScanExecution()).isTrue();
    assertThat(budget.tryScanExecution()).isFalse();
    for (int index = 0; index < 4; index++) {
      assertThat(budget.tryCandidateInspection()).isTrue();
    }
    assertThat(budget.tryCandidateInspection()).isFalse();
    assertThat(budget.tryItemHandoff()).isTrue();
    assertThat(budget.tryItemHandoff()).isTrue();
    assertThat(budget.tryItemHandoff()).isFalse();
    assertThat(budget.tryItemExecution()).isTrue();
    assertThat(budget.tryItemExecution()).isTrue();
    assertThat(budget.tryItemExecution()).isFalse();

    clock.set(50L);

    assertThat(budget.takeSessionVisits(1)).isEqualTo(1);
    assertThat(budget.remainingScanOwnerHandoffs()).isEqualTo(2);
    assertThat(budget.tryScanExecution()).isTrue();
    assertThat(budget.tryCandidateInspection()).isTrue();
    assertThat(budget.tryItemHandoff()).isTrue();
    assertThat(budget.tryItemExecution()).isTrue();
  }

  @Test
  void runtimeCeilingsMatchTheOneThousandPlayerBudget() {
    assertThat(RiftVoidMagnet.WORK_WINDOW_MILLIS).isEqualTo(50L);
    assertThat(RiftVoidMagnet.HARD_MAX_ACTIVE_SESSIONS).isEqualTo(1_024);
    assertThat(RiftVoidMagnet.HARD_MAX_SESSION_VISITS_PER_WINDOW).isEqualTo(256);
    assertThat(RiftVoidMagnet.HARD_MAX_SCAN_OWNER_HANDOFFS_PER_WINDOW).isEqualTo(64);
    assertThat(RiftVoidMagnet.HARD_MAX_SCAN_EXECUTIONS_PER_WINDOW).isEqualTo(64);
    assertThat(RiftVoidMagnet.HARD_MAX_CANDIDATE_INSPECTIONS_PER_WINDOW).isEqualTo(1_024);
    assertThat(RiftVoidMagnet.HARD_MAX_ITEM_HANDOFFS_PER_WINDOW).isEqualTo(256);
    assertThat(RiftVoidMagnet.HARD_MAX_ITEM_EXECUTIONS_PER_WINDOW).isEqualTo(256);
    assertThat(RiftVoidMagnet.HARD_MAX_CANDIDATE_INSPECTIONS_PER_SCAN).isEqualTo(64);
    assertThat(RiftVoidMagnet.HARD_MAX_ITEMS_PER_PULSE).isEqualTo(32);
    assertThat(RiftVoidMagnet.HARD_MAX_RADIUS).isEqualTo(16D);

    RiftVoidMagnet.MagnetCoordinator<Integer> coordinator =
        new RiftVoidMagnet.MagnetCoordinator<>(RiftVoidMagnet.HARD_MAX_ACTIVE_SESSIONS);
    for (int index = 0; index < RiftVoidMagnet.HARD_MAX_ACTIVE_SESSIONS; index++) {
      assertThat(coordinator.admit(new UUID(4L, index + 1L), index, 0L)).isTrue();
    }
    assertThat(coordinator.admit(new UUID(4L, 2_000L), 2_000, 0L)).isFalse();
  }

  @Test
  void clampsConfigurationAndTransferReservationsAtHardLimits() {
    assertThat(RiftVoidMagnet.boundedRadius(-10D)).isEqualTo(1D);
    assertThat(RiftVoidMagnet.boundedRadius(14D)).isEqualTo(14D);
    assertThat(RiftVoidMagnet.boundedRadius(500D)).isEqualTo(RiftVoidMagnet.HARD_MAX_RADIUS);
    assertThat(RiftVoidMagnet.boundedRadius(Double.NaN)).isEqualTo(1D);
    assertThat(RiftVoidMagnet.boundedItemLimit(-1D)).isEqualTo(1);
    assertThat(RiftVoidMagnet.boundedItemLimit(20D)).isEqualTo(20);
    assertThat(RiftVoidMagnet.boundedItemLimit(500D)).isEqualTo(RiftVoidMagnet.HARD_MAX_ITEMS_PER_PULSE);
    assertThat(RiftVoidMagnet.boundedItemLimit(Double.POSITIVE_INFINITY)).isEqualTo(1);

    RiftVoidMagnet.MagnetTransferBudget transfers =
        new RiftVoidMagnet.MagnetTransferBudget(RiftVoidMagnet.HARD_MAX_ITEMS_PER_PULSE);
    assertThat(transfers.reserve(20)).isEqualTo(20);
    assertThat(transfers.reserve(20)).isEqualTo(12);
    assertThat(transfers.reserve(1)).isZero();
    transfers.release(7);
    assertThat(transfers.reserve(10)).isEqualTo(7);
    assertThat(transfers.reserved()).isEqualTo(RiftVoidMagnet.HARD_MAX_ITEMS_PER_PULSE);
  }

  @Test
  void rejectsInvalidRuntimeLimits() {
    assertThatThrownBy(() -> new RiftVoidMagnet.MagnetCoordinator<>(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiftVoidMagnet.MagnetWorkBudget(
        1, 1, 1, 1, 1, 1, 0L, System::currentTimeMillis))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiftVoidMagnet.MagnetTransferBudget(0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
