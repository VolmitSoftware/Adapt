package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityEquipmentChangedEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

final class GolemEquipmentListener {
  private static final GolemEquipmentSlot[] ARMOR = {
      GolemEquipmentSlot.HELMET, GolemEquipmentSlot.CHESTPLATE, GolemEquipmentSlot.LEGGINGS, GolemEquipmentSlot.BOOTS
  };
  private static final Id BYPASSES_ARMOR = Id.minecraft("bypasses_armor");
  private static final Id BOW = Id.minecraft("bow");
  private static final Id ELYTRA = Id.minecraft("elytra");

  private final Function<EntityRef, Optional<GolemHandle>> handles;

  GolemEquipmentListener(Function<EntityRef, Optional<GolemHandle>> handles) {
    this.handles = Objects.requireNonNull(handles);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent event) {
    if (event.damage() <= 0D || !(event.damager() instanceof LivingRef)
        || (event.cause() != DamageCause.ENTITY_ATTACK && event.cause() != DamageCause.ENTITY_SWEEP_ATTACK)) {
      return;
    }
    handles.apply(event.damager()).filter(handle -> handle.body() == GolemBody.BONE)
        .ifPresent(handle -> owned(handle, () -> wearWeapon(handle)));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent event) {
    if (event.damage() <= 0D) {
      return;
    }
    GolemHandle handle = handles.apply(event.entity()).filter(candidate -> candidate.body() == GolemBody.BONE).orElse(null);
    if (handle == null || bypassesArmor(event.damageType())) {
      return;
    }
    int amount = Math.max(1, (int) (event.damage() / 4D));
    owned(handle, () -> wearArmor(handle, amount));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityShootBowEvent event) {
    ItemRef bow = event.bow();
    if (bow == null || bow.isEmpty() || !BOW.equals(bow.type())) {
      return;
    }
    GolemEquipmentSlot slot = event.hand() == Hand.OFF_HAND ? GolemEquipmentSlot.OFF_HAND : GolemEquipmentSlot.MAIN_HAND;
    handles.apply(event.shooter()).filter(handle -> handle.body() == GolemBody.BONE)
        .ifPresent(handle -> owned(handle, () -> handle.damageEquipment(slot, 1)));
  }

  private static boolean bypassesArmor(Id damageType) {
    return damageType != null && AdaptApi.platform().host().registries().isTagged(RegistryKind.DAMAGE_TYPE, BYPASSES_ARMOR, damageType);
  }

  private static void wearWeapon(GolemHandle handle) {
    handle.equipped(GolemEquipmentSlot.MAIN_HAND).ifPresent(item ->
        handle.damageEquipment(GolemEquipmentSlot.MAIN_HAND, item.type().path().endsWith("_sword") ? 1 : 2));
  }

  private static void wearArmor(GolemHandle handle, int amount) {
    for (GolemEquipmentSlot slot : ARMOR) {
      if (handle.equipped(slot).filter(item -> !ELYTRA.equals(item.type())).isPresent()) {
        handle.damageEquipment(slot, amount);
      }
    }
  }

  private static void owned(GolemHandle handle, Runnable task) {
    GolemPlatform platform = handle.platform();
    if (platform.owns(handle.entity())) {
      task.run();
    } else {
      platform.runEntity(handle.entity(), task, 0);
    }
  }

  static final class PaperChanges {
    private final Function<EntityRef, Optional<GolemHandle>> handles;

    PaperChanges(Function<EntityRef, Optional<GolemHandle>> handles) {
      this.handles = Objects.requireNonNull(handles);
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(EntityEquipmentChangedEvent event) {
      handles.apply(event.entity()).filter(handle -> handle.body() == GolemBody.BONE).ifPresent(handle -> {
        for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
          EntityEquipmentChangedEvent.Change change = event.changes().get(slot.slot());
          if (change != null) {
            handle.equipmentChanged(slot, change.newItem());
          }
        }
      });
    }
  }
}
