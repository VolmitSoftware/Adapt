package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.item;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.living;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;

/**
 * Paper before 26.x has no item crafted event. The craft statistic is awarded as each crafted result reaches the
 * player, so its increment stands in for it while a crafting grid is open. It has no smash attempt event either: a
 * melee hit with a mace whose attacker falls more than 1.5 blocks without gliding is a smash (vanilla's
 * MaceItem.canSmashAttack), seen in the hit's damage event while the fall distance still counts. The smash is already
 * decided there, so handlers observe it and a changed result is not applied.
 */
final class Paper26Translations {
  private Paper26Translations() {
  }

  static List<Translation<?>> all() {
    return List.of(CRAFTED, SMASH_ATTEMPT);
  }

  private static final NamespacedKey MACE = NamespacedKey.minecraft("mace");
  private static final float SMASH_FALL_DISTANCE = 1.5F;

  static final Translation<EntityDamageEvent> SMASH_ATTEMPT = new Translation<>(
      EntityDamageEvent.class, List.of(EntityAttemptSmashAttackEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(EntityDamageEvent event) {
      return smashAttacker(event) == null ? null : EntityAttemptSmashAttackEvent.class;
    }

    @Override
    public boolean cancelled(EntityDamageEvent event) {
      return false;
    }

    @Override
    public void bridge(EntityDamageEvent event, Poster poster) {
      LivingEntity attacker = smashAttacker(event);
      if (attacker != null) {
        poster.post(new EntityAttemptSmashAttackEvent(living(attacker), living((LivingEntity) event.getEntity()),
            item(attacker.getEquipment().getItemInMainHand()), true, UseResult.DEFAULT));
      }
    }
  };

  static LivingEntity smashAttacker(EntityDamageEvent event) {
    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
        || !(event instanceof EntityDamageByEntityEvent byEntity)
        || !(byEntity.getDamager() instanceof LivingEntity attacker)
        || !(event.getEntity() instanceof LivingEntity)
        || attacker.getEquipment() == null
        || !MACE.equals(attacker.getEquipment().getItemInMainHand().getType().getKey())) {
      return null;
    }
    return attacker.getFallDistance() > SMASH_FALL_DISTANCE && !attacker.isGliding() ? attacker : null;
  }

  static final Translation<PlayerStatisticIncrementEvent> CRAFTED = new Translation<>(
      PlayerStatisticIncrementEvent.class, List.of(ItemCraftedEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(PlayerStatisticIncrementEvent event) {
      if (event.getStatistic() != Statistic.CRAFT_ITEM || event.getNewValue() <= event.getPreviousValue()) {
        return null;
      }
      InventoryType grid = event.getPlayer().getOpenInventory().getTopInventory().getType();
      return grid == InventoryType.WORKBENCH || grid == InventoryType.CRAFTING ? ItemCraftedEvent.class : null;
    }

    @Override
    public boolean cancelled(PlayerStatisticIncrementEvent event) {
      return false;
    }

    @Override
    public void bridge(PlayerStatisticIncrementEvent event, Poster poster) {
      Player crafter = event.getPlayer();
      poster.post(new ItemCraftedEvent(player(crafter), item(crafted(crafter, event.getMaterial(),
          event.getNewValue() - event.getPreviousValue())), null));
    }
  };

  private static ItemStack crafted(Player crafter, Material material, int amount) {
    ItemStack cursor = crafter.getItemOnCursor();
    if (cursor.getType() != material) {
      return new ItemStack(material, amount);
    }
    ItemStack crafted = cursor.clone();
    crafted.setAmount(amount);
    return crafted;
  }
}
