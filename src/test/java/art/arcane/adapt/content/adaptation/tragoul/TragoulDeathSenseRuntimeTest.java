package art.arcane.adapt.content.adaptation.tragoul;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TragoulDeathSenseRuntimeTest {
  @Test
  void productionWorkLimitsRemainHardAtOneThousandContenders() {
    assertThat(DeathSenseWorkLimits.ownerRefreshes(1_000)).isEqualTo(24);
    assertThat(DeathSenseWorkLimits.monsterInspections(1_000)).isEqualTo(48);
    assertThat(DeathSenseWorkLimits.marks(1_000)).isEqualTo(12);
  }

  @Test
  void markBudgetHardCapsOneThousandContenders() {
    AtomicLong clock = new AtomicLong();
    DeathSenseWorkBudget budget = new DeathSenseWorkBudget(50L, clock::get);
    int grants = 0;

    for (int index = 0; index < 1_000; index++) {
      if (budget.tryAcquire(12)) {
        grants++;
      }
    }

    assertThat(grants).isEqualTo(12);
    assertThat(budget.used()).isEqualTo(12);
    clock.set(50L);
    assertThat(budget.tryAcquire(12)).isTrue();
    assertThat(budget.used()).isEqualTo(1);
  }

  @Test
  void spatialIndexChoosesNearestEligibleOwnerAcrossCellBoundary() {
    DeathSenseSpatialIndex index = new DeathSenseSpatialIndex();
    UUID world = new UUID(0L, 1L);
    UUID far = new UUID(0L, 2L);
    UUID near = new UUID(0L, 3L);
    index.put(new DeathSenseSpatialIndex.OwnerPoint(far, world, 10D, 64D, 0D, 24D, 0.5D, 100L));
    index.put(new DeathSenseSpatialIndex.OwnerPoint(near, world, 33D, 64D, 0D, 24D, 0.3D, 100L));

    DeathSenseSpatialIndex.OwnerPoint selected = index.findNearest(
        world, 31.5D, 64D, 0D, 0.25D, 32D, 200L, 5000L);

    assertThat(selected).isNotNull();
    assertThat(selected.ownerId()).isEqualTo(near);
  }

  @Test
  void spatialIndexRejectsHealthyStaleAndOtherWorldOwners() {
    DeathSenseSpatialIndex index = new DeathSenseSpatialIndex();
    UUID world = new UUID(0L, 1L);
    UUID otherWorld = new UUID(0L, 2L);
    index.put(new DeathSenseSpatialIndex.OwnerPoint(new UUID(0L, 3L), world, 0D, 64D, 0D, 16D, 0.2D, 100L));
    index.put(new DeathSenseSpatialIndex.OwnerPoint(new UUID(0L, 4L), otherWorld, 0D, 64D, 0D, 16D, 0.8D, 4900L));

    assertThat(index.findNearest(world, 1D, 64D, 1D, 0.4D, 32D, 200L, 5000L)).isNull();
    assertThat(index.findNearest(world, 1D, 64D, 1D, 0.1D, 32D, 6000L, 5000L)).isNull();
  }
}
