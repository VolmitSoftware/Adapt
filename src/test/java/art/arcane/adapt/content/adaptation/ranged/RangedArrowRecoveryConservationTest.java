package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RangedArrowRecoveryConservationTest extends AdaptTestBase {
  @Test
  void reservesOriginalPickupUntilTheOwnerDecidesWhetherToRefund() {
    RangedArrowRecovery adaptation = new RangedArrowRecovery() {
      @Override
      public int getActiveLevel(Player player) {
        return 0;
      }
    };
    Arrow arrow = markedArrow();
    Player shooter = mock(Player.class);
    when(arrow.getShooter()).thenReturn(shooter);
    when(arrow.isValid()).thenReturn(true);
    when(arrow.getLocation()).thenReturn(new Location(null, 0D, 100D, 0D));
    AtomicReference<AbstractArrow.PickupStatus> pickup = new AtomicReference<>(AbstractArrow.PickupStatus.ALLOWED);
    when(arrow.getPickupStatus()).thenAnswer(call -> pickup.get());
    doAnswer(call -> {
      pickup.set(call.getArgument(0));
      return null;
    }).when(arrow).setPickupStatus(any(AbstractArrow.PickupStatus.class));
    ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
    when(hit.getEntity()).thenReturn(arrow);
    when(hit.getHitEntity()).thenReturn(mock(Entity.class));
    AtomicReference<Runnable> ownerWork = new AtomicReference<>();

    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.runEntity(eq(shooter), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
        ownerWork.set(call.getArgument(1));
        return true;
      });
      scheduler.when(() -> J.runEntity(eq(arrow), any(Runnable.class))).thenAnswer(call -> {
        Runnable work = call.getArgument(1);
        work.run();
        return true;
      });

      adaptation.onProjectileHit(hit);

      assertThat(pickup.get()).isEqualTo(AbstractArrow.PickupStatus.DISALLOWED);
      ownerWork.get().run();
      assertThat(pickup.get()).isEqualTo(AbstractArrow.PickupStatus.ALLOWED);
    }
  }

  @Test
  void retiredOwnerReleasesTheOriginalArrowOnItsOwningTaskWithoutRefunding() {
    Arrow arrow = markedArrow();
    Player shooter = mock(Player.class);
    when(arrow.getShooter()).thenReturn(shooter);
    when(arrow.isValid()).thenReturn(true);
    when(arrow.getLocation()).thenReturn(new Location(null, 0D, 100D, 0D));
    AtomicReference<AbstractArrow.PickupStatus> pickup = new AtomicReference<>(AbstractArrow.PickupStatus.ALLOWED);
    when(arrow.getPickupStatus()).thenAnswer(call -> pickup.get());
    doAnswer(call -> {
      pickup.set(call.getArgument(0));
      return null;
    }).when(arrow).setPickupStatus(any(AbstractArrow.PickupStatus.class));
    ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
    when(hit.getEntity()).thenReturn(arrow);
    when(hit.getHitEntity()).thenReturn(mock(Entity.class));
    AtomicReference<Runnable> ownerRetired = new AtomicReference<>();
    AtomicReference<Runnable> arrowWork = new AtomicReference<>();

    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.runEntity(eq(shooter), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
        ownerRetired.set(call.getArgument(2));
        return true;
      });
      scheduler.when(() -> J.runEntity(eq(arrow), any(Runnable.class))).thenAnswer(call -> {
        arrowWork.set(call.getArgument(1));
        return true;
      });

      new RangedArrowRecovery().onProjectileHit(hit);

      assertThat(pickup.get()).isEqualTo(AbstractArrow.PickupStatus.DISALLOWED);
      ownerRetired.get().run();
      assertThat(pickup.get()).isEqualTo(AbstractArrow.PickupStatus.DISALLOWED);
      arrowWork.get().run();
      assertThat(pickup.get()).isEqualTo(AbstractArrow.PickupStatus.ALLOWED);
      verify(shooter, never()).getInventory();
    }
  }

  @Test
  void blockImpactsPreserveEligibilityForRicochetReplacements() {
    Arrow arrow = markedArrow();
    ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
    when(hit.getEntity()).thenReturn(arrow);

    new RangedArrowRecovery().onProjectileHit(hit);

    verify(arrow.getPersistentDataContainer(), never()).remove(any(NamespacedKey.class));
    verify(arrow, never()).setPickupStatus(any(AbstractArrow.PickupStatus.class));
  }

  @Test
  void failedRecoveryDoesNotOverwriteAnotherPluginsPickupChange() {
    Arrow arrow = mock(Arrow.class);
    when(arrow.isValid()).thenReturn(true);
    when(arrow.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.CREATIVE_ONLY);

    RangedArrowRecovery.restorePickup(arrow, AbstractArrow.PickupStatus.ALLOWED);

    verify(arrow, never()).setPickupStatus(any(AbstractArrow.PickupStatus.class));
  }

  private Arrow markedArrow() {
    Arrow arrow = mock(Arrow.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(arrow.getPersistentDataContainer()).thenReturn(data);
    when(data.getOrDefault(any(NamespacedKey.class), eq(PersistentDataType.BYTE), eq((byte) 0))).thenReturn((byte) 1);
    return arrow;
  }
}
