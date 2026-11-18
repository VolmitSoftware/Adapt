package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import art.arcane.adapt.platform.event.player.PlayerAnimationEvent;
import art.arcane.adapt.platform.event.player.PlayerBedEnterEvent;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.event.world.LightningStrikeEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.Keyed;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Enum tables and conversions for the translations of the events added after the first bridge pass. */
final class GapEventMaps {
  private static final Map<SpawnReason, CreatureSpawnEvent.Reason> SPAWN_REASONS =
      BukkitEventMaps.byName(SpawnReason.class, CreatureSpawnEvent.Reason.class, CreatureSpawnEvent.Reason.DEFAULT);
  private static final Map<io.papermc.paper.event.entity.EntityKnockbackEvent.Cause, EntityKnockbackEvent.Cause> KNOCKBACK_CAUSES =
      BukkitEventMaps.byName(io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.class, EntityKnockbackEvent.Cause.class,
          EntityKnockbackEvent.Cause.UNKNOWN);
  private static final Map<ExhaustionReason, EntityExhaustionEvent.Reason> EXHAUSTION_REASONS =
      BukkitEventMaps.byName(ExhaustionReason.class, EntityExhaustionEvent.Reason.class, EntityExhaustionEvent.Reason.UNKNOWN);
  private static final Map<org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult, PlayerBedEnterEvent.Result> BED_RESULTS =
      BukkitEventMaps.byName(org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.class, PlayerBedEnterEvent.Result.class,
          PlayerBedEnterEvent.Result.OTHER_PROBLEM);
  private static final Map<PlayerAnimationType, PlayerAnimationEvent.Animation> ANIMATIONS =
      BukkitEventMaps.byName(PlayerAnimationType.class, PlayerAnimationEvent.Animation.class, PlayerAnimationEvent.Animation.ARM_SWING);
  private static final Map<org.bukkit.event.weather.LightningStrikeEvent.Cause, LightningStrikeEvent.Cause> LIGHTNING_CAUSES =
      BukkitEventMaps.byName(org.bukkit.event.weather.LightningStrikeEvent.Cause.class, LightningStrikeEvent.Cause.class,
          LightningStrikeEvent.Cause.UNKNOWN);
  private static final Map<io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause, ResourcesReloadedEvent.Cause> RELOAD_CAUSES =
      BukkitEventMaps.byName(io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.class, ResourcesReloadedEvent.Cause.class,
          ResourcesReloadedEvent.Cause.UNKNOWN);

  private GapEventMaps() {
  }

  static CreatureSpawnEvent.Reason spawnReason(SpawnReason reason) {
    return reason == null ? CreatureSpawnEvent.Reason.DEFAULT : SPAWN_REASONS.get(reason);
  }

  static EntityKnockbackEvent.Cause knockbackCause(io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause) {
    return cause == null ? EntityKnockbackEvent.Cause.UNKNOWN : KNOCKBACK_CAUSES.get(cause);
  }

  static EntityExhaustionEvent.Reason exhaustionReason(ExhaustionReason reason) {
    return reason == null ? EntityExhaustionEvent.Reason.UNKNOWN : EXHAUSTION_REASONS.get(reason);
  }

  static PlayerBedEnterEvent.Result bedResult(org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult result) {
    return result == null ? PlayerBedEnterEvent.Result.OTHER_PROBLEM : BED_RESULTS.get(result);
  }

  static PlayerAnimationEvent.Animation animation(PlayerAnimationType type) {
    return type == null ? PlayerAnimationEvent.Animation.ARM_SWING : ANIMATIONS.get(type);
  }

  static LightningStrikeEvent.Cause lightningCause(org.bukkit.event.weather.LightningStrikeEvent.Cause cause) {
    return cause == null ? LightningStrikeEvent.Cause.UNKNOWN : LIGHTNING_CAUSES.get(cause);
  }

  static ResourcesReloadedEvent.Cause reloadCause(io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause cause) {
    return cause == null ? ResourcesReloadedEvent.Cause.UNKNOWN : RELOAD_CAUSES.get(cause);
  }

  static Id key(Object keyed) {
    return keyed instanceof Keyed value ? BukkitRefs.id(value.getKey()) : null;
  }

  static ItemRef itemOrEmpty(ItemStack stack) {
    return BukkitRefs.item(stack == null ? ItemStack.empty() : stack);
  }

  static List<ItemRef> items(Collection<ItemStack> stacks) {
    List<ItemRef> refs = new ArrayList<>(stacks.size());
    for (ItemStack stack : stacks) {
      refs.add(itemOrEmpty(stack));
    }
    return refs;
  }

  static List<ItemRef> items(ItemStack[] stacks) {
    List<ItemRef> refs = new ArrayList<>(stacks.length);
    for (ItemStack stack : stacks) {
      refs.add(itemOrEmpty(stack));
    }
    return refs;
  }

  static List<EffectInstance> effects(Collection<PotionEffect> effects) {
    List<EffectInstance> converted = new ArrayList<>(effects.size());
    for (PotionEffect effect : effects) {
      EffectInstance instance = BukkitEventMaps.effect(effect);
      if (instance != null) {
        converted.add(instance);
      }
    }
    return converted;
  }

  static List<EntityRef> entities(Collection<? extends Entity> entities) {
    List<EntityRef> refs = new ArrayList<>(entities.size());
    for (Entity entity : entities) {
      refs.add(BukkitEventMaps.entity(entity));
    }
    return refs;
  }

  static List<BlockChange> changes(List<BlockState> states) {
    List<BlockChange> changes = new ArrayList<>(states.size());
    for (BlockState state : states) {
      changes.add(new BlockChange(BukkitRefs.block(state.getBlock()), BukkitRefs.blockState(state.getBlockData())));
    }
    return changes;
  }

  static ItemEntityRef itemEntity(Item item) {
    return item == null ? null : BukkitRefs.itemEntity(item);
  }

  static InventoryRef inventory(Inventory inventory) {
    return inventory == null ? null : BukkitRefs.inventory(inventory);
  }

  /** Block that holds the inventory; the left half for double chests; null for entity and virtual inventories. */
  static BlockRef holderBlock(Inventory inventory) {
    if (inventory == null) {
      return null;
    }
    InventoryHolder holder = inventory.getHolder(false);
    if (holder instanceof DoubleChest chest) {
      holder = chest.getLeftSide(false);
    }
    if (holder instanceof BlockInventoryHolder blockHolder) {
      Block block = blockHolder.getBlock();
      return block == null ? null : BukkitRefs.block(block);
    }
    return null;
  }

  /** Entity that holds the inventory (villager trades, minecarts, horses), or null. */
  static EntityRef holderEntity(Inventory inventory) {
    if (inventory instanceof org.bukkit.inventory.MerchantInventory merchant
        && merchant.getMerchant() instanceof Entity trader) {
      return BukkitEventMaps.entity(trader);
    }
    InventoryHolder holder = inventory == null ? null : inventory.getHolder(false);
    return holder instanceof Entity entity && !(entity instanceof Player) ? BukkitEventMaps.entity(entity) : null;
  }
}
