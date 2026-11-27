package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.MockedEngine;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RangedArrowRecoveryConservationTest extends AdaptTestBase {
  private static final DataKey<Byte> RECOVERY_KEY = DataKey.adapt("arrow-recovery", DataCodecs.BYTE);

  @Test
  void reservesOriginalPickupUntilTheOwnerDecidesWhetherToRefund() {
    RangedArrowRecovery adaptation = recovery();
    FakeProjectile arrow = markedArrow();
    PlayerRef shooter = mock(PlayerRef.class);
    arrow.setShooter(shooter);
    arrow.setPickupRule(ArrowPickup.ALLOWED);
    ProjectileHitEvent hit = new ProjectileHitEvent(arrow, mock(EntityRef.class), null, null);
    AtomicReference<Runnable> ownerWork = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.runEntity(eq(shooter), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
        ownerWork.set(call.getArgument(1));
        return true;
      });
      scheduler.when(() -> Tasks.runEntity(eq(arrow), any(Runnable.class))).thenAnswer(call -> {
        Runnable work = call.getArgument(1);
        work.run();
        return true;
      });

      adaptation.onProjectileHit(hit);

      assertThat(arrow.pickupRule()).isEqualTo(ArrowPickup.DISALLOWED);
      ownerWork.get().run();
      assertThat(arrow.pickupRule()).isEqualTo(ArrowPickup.ALLOWED);
    }
  }

  @Test
  void retiredOwnerReleasesTheOriginalArrowOnItsOwningTaskWithoutRefunding() {
    FakeProjectile arrow = markedArrow();
    PlayerRef shooter = mock(PlayerRef.class);
    arrow.setShooter(shooter);
    arrow.setPickupRule(ArrowPickup.ALLOWED);
    ProjectileHitEvent hit = new ProjectileHitEvent(arrow, mock(EntityRef.class), null, null);
    AtomicReference<Runnable> ownerRetired = new AtomicReference<>();
    AtomicReference<Runnable> arrowWork = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.runEntity(eq(shooter), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
        ownerRetired.set(call.getArgument(2));
        return true;
      });
      scheduler.when(() -> Tasks.runEntity(eq(arrow), any(Runnable.class))).thenAnswer(call -> {
        arrowWork.set(call.getArgument(1));
        return true;
      });

      recovery().onProjectileHit(hit);

      assertThat(arrow.pickupRule()).isEqualTo(ArrowPickup.DISALLOWED);
      ownerRetired.get().run();
      assertThat(arrow.pickupRule()).isEqualTo(ArrowPickup.DISALLOWED);
      arrowWork.get().run();
      assertThat(arrow.pickupRule()).isEqualTo(ArrowPickup.ALLOWED);
      verify(shooter, never()).inventory();
    }
  }

  @Test
  void blockImpactsPreserveEligibilityForRicochetReplacements() {
    FakeProjectile arrow = markedArrow();
    arrow.setPickupRule(ArrowPickup.ALLOWED);
    ProjectileHitEvent hit = new ProjectileHitEvent(arrow, null, null, null);

    recovery().onProjectileHit(hit);

    assertThat(arrow.data().get(RECOVERY_KEY)).isEqualTo((byte) 1);
    assertThat(arrow.pickupRule()).isEqualTo(ArrowPickup.ALLOWED);
  }

  @Test
  void failedRecoveryDoesNotOverwriteAnotherPluginsPickupChange() {
    FakeProjectile arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(new FakeWorld("world"), 0D, 100D, 0D));
    arrow.setPickupRule(ArrowPickup.CREATIVE_ONLY);

    RangedArrowRecovery.restorePickup(arrow, ArrowPickup.ALLOWED);

    assertThat(arrow.pickupRule()).isEqualTo(ArrowPickup.CREATIVE_ONLY);
  }

  /** A built recovery adaptation; its shooters are no Adapt players, so it is at level 0 for them. */
  private RangedArrowRecovery recovery() {
    try (MockedEngine engine = new MockedEngine(core)) {
      return TestContent.adaptation(Id.adapt("ranged"), RangedArrowRecovery::new);
    }
  }

  private FakeProjectile markedArrow() {
    FakeProjectile arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(new FakeWorld("world"), 0D, 100D, 0D));
    arrow.data().set(RECOVERY_KEY, (byte) 1);
    return arrow;
  }
}
