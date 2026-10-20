package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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
  private Skeleton skeleton;
  private GolemHandle handle;
  private GolemEquipmentListener listener;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    skeleton = mock(Skeleton.class);
    handle = mock(GolemHandle.class);
    lenient().when(handle.entity()).thenReturn(skeleton);
    lenient().when(handle.platform()).thenReturn(platform);
    lenient().when(handle.body()).thenReturn(GolemBody.BONE);
    listener = new GolemEquipmentListener(entity -> entity == skeleton ? Optional.of(handle) : Optional.empty());
  }

  @Test
  void aSuccessfulMeleeHitWearsTheEquippedSword() {
    ItemStack sword = GolemTestItems.tool(Material.IRON_SWORD, 0, 250);
    when(handle.equipped(GolemEquipmentSlot.MAIN_HAND)).thenReturn(Optional.of(sword));

    listener.on(hit(skeleton, 4D));

    verify(handle).damageEquipment(GolemEquipmentSlot.MAIN_HAND, 1);
  }

  @Test
  void aToolUsedAsAMeleeWeaponTakesTwoDurability() {
    ItemStack axe = GolemTestItems.tool(Material.IRON_AXE, 0, 250);
    when(handle.equipped(GolemEquipmentSlot.MAIN_HAND)).thenReturn(Optional.of(axe));

    listener.on(hit(skeleton, 4D));

    verify(handle).damageEquipment(GolemEquipmentSlot.MAIN_HAND, 2);
  }

  @Test
  void aProjectileHitDoesNotChargeTheBowASecondTime() {
    listener.on(hit(mock(Arrow.class), 4D));
    listener.on(hit(skeleton, 0D));

    verify(handle, never()).damageEquipment(any(), anyInt());
  }

  @Test
  void thornsDoNotCountAsASwingOfTheHeldWeapon() {
    EntityDamageByEntityEvent event = hit(skeleton, 2D);
    when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.THORNS);

    listener.on(event);

    verify(handle, never()).damageEquipment(any(), anyInt());
  }

  @Test
  void aShotWearsTheHandThatFiredTheBow() {
    EntityShootBowEvent event = mock(EntityShootBowEvent.class);
    ItemStack bow = GolemTestItems.tool(Material.BOW, 0, 384);
    when(event.getEntity()).thenReturn(skeleton);
    when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
    when(event.getBow()).thenReturn(bow);

    listener.on(event);

    verify(handle).damageEquipment(GolemEquipmentSlot.OFF_HAND, 1);
    verify(handle, never()).damageEquipment(GolemEquipmentSlot.MAIN_HAND, 1);
  }

  @Test
  void crossbowShotsKeepTheirNativeDurabilityCost() {
    EntityShootBowEvent event = mock(EntityShootBowEvent.class);
    ItemStack crossbow = GolemTestItems.tool(Material.CROSSBOW, 0, 465);
    when(event.getBow()).thenReturn(crossbow);

    listener.on(event);

    verify(handle, never()).damageEquipment(any(), anyInt());
  }

  @Test
  void damageForAnotherRegionRunsOnTheGolemsOwningThread() {
    platform.ownsEntities = false;
    ItemStack sword = GolemTestItems.tool(Material.IRON_SWORD, 0, 250);
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
      EventHandler handler = GolemEquipmentListener.class.getMethod("on", type).getAnnotation(EventHandler.class);
      assertThat(handler.ignoreCancelled()).isTrue();
      assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
    }
  }

  private static EntityDamageByEntityEvent hit(Entity damager, double damage) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    lenient().when(event.getDamager()).thenReturn(damager);
    lenient().when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
    when(event.getDamage()).thenReturn(damage);
    return event;
  }
}
