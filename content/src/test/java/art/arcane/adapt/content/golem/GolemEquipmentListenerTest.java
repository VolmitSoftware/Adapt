package art.arcane.adapt.content.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemEquipmentListenerTest extends AdaptTestBase {
  private FakeGolemPlatform platform;
  private MobRef skeleton;
  private GolemHandle handle;
  private GolemEquipmentListener listener;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    skeleton = mock(MobRef.class);
    handle = mock(GolemHandle.class);
    lenient().when(handle.entity()).thenReturn(skeleton);
    lenient().when(handle.platform()).thenReturn(platform);
    lenient().when(handle.body()).thenReturn(GolemBody.BONE);
    listener = new GolemEquipmentListener(entity -> entity == skeleton ? Optional.of(handle) : Optional.empty());
  }

  @Test
  void aSuccessfulMeleeHitWearsTheEquippedSword() {
    ItemRef sword = GolemTestItems.tool(Id.minecraft("iron_sword"), 0, 250);
    when(handle.equipped(GolemEquipmentSlot.MAIN_HAND)).thenReturn(Optional.of(sword));

    listener.on(hit(skeleton, 4D));

    verify(handle).damageEquipment(GolemEquipmentSlot.MAIN_HAND, 1);
  }

  @Test
  void aToolUsedAsAMeleeWeaponTakesTwoDurability() {
    ItemRef axe = GolemTestItems.tool(Id.minecraft("iron_axe"), 0, 250);
    when(handle.equipped(GolemEquipmentSlot.MAIN_HAND)).thenReturn(Optional.of(axe));

    listener.on(hit(skeleton, 4D));

    verify(handle).damageEquipment(GolemEquipmentSlot.MAIN_HAND, 2);
  }

  @Test
  void aProjectileHitDoesNotChargeTheBowASecondTime() {
    listener.on(hit(mock(ProjectileRef.class), 4D));
    listener.on(hit(skeleton, 0D));

    verify(handle, never()).damageEquipment(any(), anyInt());
  }

  @Test
  void thornsDoNotCountAsASwingOfTheHeldWeapon() {
    EntityDamageByEntityEvent event = hit(skeleton, 2D);
    when(event.cause()).thenReturn(DamageCause.THORNS);

    listener.on(event);

    verify(handle, never()).damageEquipment(any(), anyInt());
  }

  @Test
  void aShotWearsTheHandThatFiredTheBow() {
    EntityShootBowEvent event = mock(EntityShootBowEvent.class);
    ItemRef bow = GolemTestItems.tool(Id.minecraft("bow"), 0, 384);
    when(event.shooter()).thenReturn(skeleton);
    when(event.hand()).thenReturn(Hand.OFF_HAND);
    when(event.bow()).thenReturn(bow);

    listener.on(event);

    verify(handle).damageEquipment(GolemEquipmentSlot.OFF_HAND, 1);
    verify(handle, never()).damageEquipment(GolemEquipmentSlot.MAIN_HAND, 1);
  }

  @Test
  void crossbowShotsKeepTheirNativeDurabilityCost() {
    EntityShootBowEvent event = mock(EntityShootBowEvent.class);
    ItemRef crossbow = GolemTestItems.tool(Id.minecraft("crossbow"), 0, 465);
    when(event.bow()).thenReturn(crossbow);

    listener.on(event);

    verify(handle, never()).damageEquipment(any(), anyInt());
  }

  @Test
  void damageForAnotherRegionRunsOnTheGolemsOwningThread() {
    platform.ownsEntities = false;
    ItemRef sword = GolemTestItems.tool(Id.minecraft("iron_sword"), 0, 250);
    when(handle.equipped(GolemEquipmentSlot.MAIN_HAND)).thenReturn(Optional.of(sword));

    listener.on(hit(skeleton, 4D));

    verify(handle, never()).damageEquipment(any(), anyInt());
    platform.runDelayed();
    verify(handle).damageEquipment(GolemEquipmentSlot.MAIN_HAND, 1);
  }

  @Test
  void nonBoneBodiesDoNotWearEquipmentThroughThisListener() {
    when(handle.body()).thenReturn(GolemBody.COPPER);

    listener.on(hit(skeleton, 4D));

    verify(handle, never()).damageEquipment(any(), anyInt());
  }

  @Test
  void cancelledDamageAndShotsAreExcludedAfterProtectionHandlers() throws NoSuchMethodException {
    for (Class<?> type : new Class<?>[]{EntityDamageByEntityEvent.class, EntityDamageEvent.class, EntityShootBowEvent.class}) {
      Handle handler = GolemEquipmentListener.class.getMethod("on", type).getAnnotation(Handle.class);
      assertThat(handler.ignoreCancelled()).isTrue();
      assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
    }
  }

  private static EntityDamageByEntityEvent hit(EntityRef damager, double damage) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    lenient().when(event.damager()).thenReturn(damager);
    lenient().when(event.cause()).thenReturn(DamageCause.ENTITY_ATTACK);
    when(event.damage()).thenReturn(damage);
    return event;
  }
}
