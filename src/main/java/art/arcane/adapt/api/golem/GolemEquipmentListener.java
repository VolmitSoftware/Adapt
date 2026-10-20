package art.arcane.adapt.api.golem;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.tag.DamageTypeTags;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

final class GolemEquipmentListener implements Listener {
  private static final GolemEquipmentSlot[] ARMOR = {
      GolemEquipmentSlot.HELMET, GolemEquipmentSlot.CHESTPLATE, GolemEquipmentSlot.LEGGINGS, GolemEquipmentSlot.BOOTS
  };

  private final Function<Entity, Optional<GolemHandle>> handles;

  GolemEquipmentListener(Function<Entity, Optional<GolemHandle>> handles) {
    this.handles = Objects.requireNonNull(handles);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent event) {
    if (event.getDamage() <= 0D || !(event.getDamager() instanceof LivingEntity)
        || (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
      return;
    }
    handles.apply(event.getDamager()).filter(handle -> handle.body() == GolemBody.BONE)
        .ifPresent(handle -> owned(handle, () -> wearWeapon(handle)));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    if (event.getDamage() <= 0D || !event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
      return;
    }
    GolemHandle handle = handles.apply(event.getEntity()).filter(candidate -> candidate.body() == GolemBody.BONE).orElse(null);
    if (handle == null || (DamageTypeTags.BYPASSES_ARMOR != null
        && DamageTypeTags.BYPASSES_ARMOR.isTagged(event.getDamageSource().getDamageType()))) {
      return;
    }
    int amount = Math.max(1, (int) (event.getDamage() / 4D));
    owned(handle, () -> wearArmor(handle, amount));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityShootBowEvent event) {
    ItemStack bow = event.getBow();
    if (bow == null || bow.getType() != Material.BOW) {
      return;
    }
    GolemEquipmentSlot slot = event.getHand() == EquipmentSlot.OFF_HAND ? GolemEquipmentSlot.OFF_HAND : GolemEquipmentSlot.MAIN_HAND;
    handles.apply(event.getEntity()).filter(handle -> handle.body() == GolemBody.BONE)
        .ifPresent(handle -> owned(handle, () -> handle.damageEquipment(slot, 1)));
  }

  private static void wearWeapon(GolemHandle handle) {
    handle.equipped(GolemEquipmentSlot.MAIN_HAND).ifPresent(item ->
        handle.damageEquipment(GolemEquipmentSlot.MAIN_HAND, item.getType().name().endsWith("_SWORD") ? 1 : 2));
  }

  private static void wearArmor(GolemHandle handle, int amount) {
    for (GolemEquipmentSlot slot : ARMOR) {
      if (handle.equipped(slot).filter(item -> item.getType() != Material.ELYTRA).isPresent()) {
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

  static final class PaperChanges implements Listener {
    private final Function<Entity, Optional<GolemHandle>> handles;

    PaperChanges(Function<Entity, Optional<GolemHandle>> handles) {
      this.handles = Objects.requireNonNull(handles);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityEquipmentChangedEvent event) {
      handles.apply(event.getEntity()).filter(handle -> handle.body() == GolemBody.BONE).ifPresent(handle -> {
        for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
          EntityEquipmentChangedEvent.EquipmentChange change = event.getEquipmentChanges().get(slot.slot());
          if (change != null) {
            handle.equipmentChanged(slot, change.newItem());
          }
        }
      });
    }
  }
}
