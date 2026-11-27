package art.arcane.adapt.content.adaptation.chronos;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ChronosStasisFieldBudgetTest {
  @Test
  void admitsTwoHundredFiftySixOwnersAndRejectsGlobalOverflow() {
    StasisBubbleCoordinator coordinator = new StasisBubbleCoordinator(256, 2);

    for (int index = 0; index < 256; index++) {
      UUID ownerId = new UUID(1L, index + 1L);
      UUID bubbleId = new UUID(2L, index + 1L);
      long generation = coordinator.reserve(ownerId, bubbleId);
      assertThat(generation).isPositive();
      assertThat(coordinator.activate(bubbleId, generation)).isTrue();
    }

    assertThat(coordinator.reserve(new UUID(3L, 1L), new UUID(4L, 1L))).isEqualTo(-1L);
    assertThat(coordinator.activeCount()).isEqualTo(256);
  }

  @Test
  void perOwnerAdmissionPreventsOneCasterFromTakingAllBubbles() {
    StasisBubbleCoordinator coordinator = new StasisBubbleCoordinator(16, 2);
    UUID ownerId = new UUID(1L, 1L);

    assertThat(coordinator.reserve(ownerId, new UUID(2L, 1L))).isPositive();
    assertThat(coordinator.reserve(ownerId, new UUID(2L, 2L))).isPositive();
    assertThat(coordinator.reserve(ownerId, new UUID(2L, 3L))).isEqualTo(-1L);
  }

  @Test
  void fairQueueRotatesEveryActiveBubbleWithoutDuplicates() {
    StasisBubbleCoordinator coordinator = new StasisBubbleCoordinator(8, 2);
    UUID first = new UUID(2L, 1L);
    UUID second = new UUID(2L, 2L);
    UUID third = new UUID(2L, 3L);
    activate(coordinator, new UUID(1L, 1L), first);
    activate(coordinator, new UUID(1L, 2L), second);
    activate(coordinator, new UUID(1L, 3L), third);

    List<UUID> firstCycle = coordinator.take(8).stream()
        .map(StasisBubbleCoordinator.Lease::bubbleId)
        .toList();
    List<UUID> secondCycle = coordinator.take(2).stream()
        .map(StasisBubbleCoordinator.Lease::bubbleId)
        .toList();

    assertThat(firstCycle).containsExactly(first, second, third);
    assertThat(secondCycle).containsExactly(first, second);
  }

  @Test
  void sixtyFourWorkSlotsReachAllTwoHundredFiftySixBubblesInFourTicks() {
    StasisBubbleCoordinator coordinator = new StasisBubbleCoordinator(256, 2);
    Set<UUID> visited = new HashSet<>();
    for (int index = 0; index < 256; index++) {
      activate(coordinator, new UUID(1L, index + 1L), new UUID(2L, index + 1L));
    }

    for (int tick = 0; tick < 4; tick++) {
      List<StasisBubbleCoordinator.Lease> leases = coordinator.take(64);
      assertThat(leases).hasSize(64);
      for (StasisBubbleCoordinator.Lease lease : leases) {
        visited.add(lease.bubbleId());
      }
    }

    assertThat(visited).hasSize(256);
  }

  @Test
  void staleGenerationCannotRemoveReusedBubbleIdentity() {
    StasisBubbleCoordinator coordinator = new StasisBubbleCoordinator(4, 2);
    UUID ownerId = new UUID(1L, 1L);
    UUID bubbleId = new UUID(2L, 1L);
    long first = coordinator.reserve(ownerId, bubbleId);
    coordinator.activate(bubbleId, first);

    assertThat(coordinator.remove(bubbleId, first)).isTrue();
    long second = coordinator.reserve(ownerId, bubbleId);
    coordinator.activate(bubbleId, second);

    assertThat(second).isGreaterThan(first);
    assertThat(coordinator.remove(bubbleId, first)).isFalse();
    assertThat(coordinator.isCurrent(bubbleId, second)).isTrue();
  }

  @Test
  void frozenProjectileCapacityStopsExactlyAtOneThousandTwentyFour() {
    StasisCapacityGate capacity = new StasisCapacityGate(1_024);

    for (int index = 0; index < 1_024; index++) {
      assertThat(capacity.tryAcquire()).isTrue();
    }

    assertThat(capacity.tryAcquire()).isFalse();
    assertThat(capacity.count()).isEqualTo(1_024);
    capacity.release();
    assertThat(capacity.tryAcquire()).isTrue();
  }

  @Test
  void productionWindowCapsAggregateInspectionHandoffAndFxWork() {
    AtomicLong clock = new AtomicLong();
    StasisWindowBudget budget = new StasisWindowBudget(
        1_024,
        512,
        512,
        32,
        16,
        32,
        50L,
        clock::get
    );

    assertThat(grants(2_000, budget::tryInspection)).isEqualTo(1_024);
    assertThat(grants(2_000, budget::tryEntityHandoff)).isEqualTo(512);
    assertThat(grants(2_000, budget::tryOutlinePoint)).isEqualTo(512);
    assertThat(grants(100, budget::tryFreezeFx)).isEqualTo(32);
    assertThat(grants(100, budget::tryCastFx)).isEqualTo(16);
    assertThat(grants(100, budget::tryExpiryFx)).isEqualTo(32);

    clock.set(50L);

    assertThat(budget.tryInspection()).isTrue();
    assertThat(budget.tryEntityHandoff()).isTrue();
    assertThat(budget.tryOutlinePoint()).isTrue();
    assertThat(budget.tryFreezeFx()).isTrue();
    assertThat(budget.tryCastFx()).isTrue();
    assertThat(budget.tryExpiryFx()).isTrue();
  }

  private void activate(StasisBubbleCoordinator coordinator, UUID ownerId, UUID bubbleId) {
    long generation = coordinator.reserve(ownerId, bubbleId);
    assertThat(generation).isPositive();
    assertThat(coordinator.activate(bubbleId, generation)).isTrue();
  }

  private int grants(int attempts, Attempt attempt) {
    int grants = 0;
    for (int index = 0; index < attempts; index++) {
      if (attempt.acquire()) {
        grants++;
      }
    }
    return grants;
  }

  private interface Attempt {
    boolean acquire();
  }
}
