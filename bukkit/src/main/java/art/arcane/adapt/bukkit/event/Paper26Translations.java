package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityEquipmentChangedEvent;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import art.arcane.adapt.platform.event.entity.ItemTransportingEntityValidateTargetEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent.EquipmentChange;
import org.bukkit.entity.LivingEntity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.item;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.living;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

/** Translations of native events that only 26.x Paper fires. */
final class Paper26Translations {
  private Paper26Translations() {
  }

  static List<Translation<?>> all() {
    return List.of(SMASH_ATTEMPT, LUNGE, EQUIPMENT_CHANGED, TRANSPORT_TARGET, CRAFTED);
  }

  static final Translation<io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent> SMASH_ATTEMPT = BukkitTranslations.simple(
      io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent.class, EntityAttemptSmashAttackEvent.class, (event, poster) -> {
        UseResult result = BukkitEventMaps.result(event.getResult());
        EntityAttemptSmashAttackEvent core = new EntityAttemptSmashAttackEvent(living((LivingEntity) event.getEntity()),
            living(event.getTarget()), GapEventMaps.itemOrEmpty(event.getWeapon()), event.getOriginalResult(), result);
        poster.post(core);
        if (core.result() != result) {
          event.setResult(BukkitEventMaps.result(core.result()));
        }
      });

  static final Translation<io.papermc.paper.event.entity.EntityLungeEvent> LUNGE = BukkitTranslations.simple(
      io.papermc.paper.event.entity.EntityLungeEvent.class, EntityLungeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        int power = event.getLungePower();
        EntityLungeEvent core = new EntityLungeEvent(living((LivingEntity) event.getEntity()), power);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.power() != power) {
          event.setLungePower(core.power());
        }
      });

  static final Translation<io.papermc.paper.event.entity.EntityEquipmentChangedEvent> EQUIPMENT_CHANGED = BukkitTranslations.simple(
      io.papermc.paper.event.entity.EntityEquipmentChangedEvent.class, EntityEquipmentChangedEvent.class, (event, poster) -> {
        Map<EquipmentSlot, EntityEquipmentChangedEvent.Change> changes = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<org.bukkit.inventory.EquipmentSlot, EquipmentChange> entry : event.getEquipmentChanges().entrySet()) {
          EquipmentSlot slot = slot(entry.getKey());
          if (slot != null) {
            changes.put(slot, new EntityEquipmentChangedEvent.Change(GapEventMaps.itemOrEmpty(entry.getValue().oldItem()),
                GapEventMaps.itemOrEmpty(entry.getValue().newItem())));
          }
        }
        poster.post(new EntityEquipmentChangedEvent(living(event.getEntity()), changes));
      });

  static final Translation<io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent> TRANSPORT_TARGET =
      BukkitTranslations.simple(io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent.class,
          ItemTransportingEntityValidateTargetEvent.class, (event, poster) -> {
            boolean allowed = event.isAllowed();
            ItemTransportingEntityValidateTargetEvent core = new ItemTransportingEntityValidateTargetEvent(
                entity(event.getEntity()), block(event.getBlock()), allowed);
            poster.post(core);
            if (core.allowed() != allowed) {
              event.setAllowed(core.allowed());
            }
          });

  static final Translation<io.papermc.paper.event.inventory.ItemCraftedEvent> CRAFTED = BukkitTranslations.simple(
      io.papermc.paper.event.inventory.ItemCraftedEvent.class, ItemCraftedEvent.class,
      (event, poster) -> poster.post(new ItemCraftedEvent(player(event.getPlayer()), item(event.getCraftedItem()), null)));

  private static EquipmentSlot slot(org.bukkit.inventory.EquipmentSlot slot) {
    if (slot == null) {
      return null;
    }
    return switch (slot) {
      case HAND -> EquipmentSlot.MAIN_HAND;
      case OFF_HAND -> EquipmentSlot.OFF_HAND;
      case HEAD -> EquipmentSlot.HEAD;
      case CHEST -> EquipmentSlot.CHEST;
      case LEGS -> EquipmentSlot.LEGS;
      case FEET -> EquipmentSlot.FEET;
      case BODY -> EquipmentSlot.BODY;
      case SADDLE -> EquipmentSlot.SADDLE;
      default -> null;
    };
  }
}
