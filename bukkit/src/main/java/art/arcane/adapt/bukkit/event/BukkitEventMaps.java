package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.Map;

/** Bukkit to core value conversions shared by the translations. Enum tables are built once by name. */
public final class BukkitEventMaps {
  private static final Map<org.bukkit.event.entity.EntityDamageEvent.DamageCause, DamageCause> DAMAGE_CAUSES =
      byName(org.bukkit.event.entity.EntityDamageEvent.DamageCause.class, DamageCause.class, DamageCause.CUSTOM);
  private static final Map<Action, InteractAction> ACTIONS = byName(Action.class, InteractAction.class, InteractAction.PHYSICAL);
  private static final Map<org.bukkit.block.BlockFace, BlockFace> FACES = byName(org.bukkit.block.BlockFace.class, BlockFace.class, null);
  private static final Map<Event.Result, UseResult> RESULTS = byName(Event.Result.class, UseResult.class, UseResult.DEFAULT);
  private static final Map<UseResult, Event.Result> NATIVE_RESULTS = byName(UseResult.class, Event.Result.class, Event.Result.DEFAULT);
  private static final Map<org.bukkit.GameMode, GameMode> GAME_MODES = byName(org.bukkit.GameMode.class, GameMode.class, GameMode.SURVIVAL);
  private static final Map<PlayerTeleportEvent.TeleportCause, TeleportCause> TELEPORT_CAUSES =
      byName(PlayerTeleportEvent.TeleportCause.class, TeleportCause.class, TeleportCause.UNKNOWN);
  private static final Map<org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason, EntityRegainHealthEvent.Reason> REGAIN_REASONS =
      byName(org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.class, EntityRegainHealthEvent.Reason.class,
          EntityRegainHealthEvent.Reason.CUSTOM);
  private static final Map<org.bukkit.event.entity.EntityTargetEvent.TargetReason, EntityTargetEvent.Reason> TARGET_REASONS =
      byName(org.bukkit.event.entity.EntityTargetEvent.TargetReason.class, EntityTargetEvent.Reason.class,
          EntityTargetEvent.Reason.UNKNOWN);
  private static final Map<org.bukkit.event.entity.EntityPotionEffectEvent.Action, EntityPotionEffectEvent.Action> POTION_ACTIONS =
      byName(org.bukkit.event.entity.EntityPotionEffectEvent.Action.class, EntityPotionEffectEvent.Action.class,
          EntityPotionEffectEvent.Action.CHANGED);
  private static final Map<org.bukkit.event.entity.EntityPotionEffectEvent.Cause, EntityPotionEffectEvent.Cause> POTION_CAUSES =
      byName(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.class, EntityPotionEffectEvent.Cause.class,
          EntityPotionEffectEvent.Cause.UNKNOWN);
  private static final Map<org.bukkit.event.player.PlayerFishEvent.State, PlayerFishEvent.State> FISH_STATES =
      byName(org.bukkit.event.player.PlayerFishEvent.State.class, PlayerFishEvent.State.class, PlayerFishEvent.State.FISHING);

  private BukkitEventMaps() {
  }

  static <S extends Enum<S>, T extends Enum<T>> Map<S, T> byName(Class<S> source, Class<T> target, T fallback) {
    Map<S, T> mapped = new EnumMap<>(source);
    for (S value : source.getEnumConstants()) {
      T match = fallback;
      for (T candidate : target.getEnumConstants()) {
        if (candidate.name().equals(value.name())) {
          match = candidate;
          break;
        }
      }
      if (match != null) {
        mapped.put(value, match);
      }
    }
    return mapped;
  }

  private static Map<InventoryType, InventoryKind> inventoryKinds() {
    Map<InventoryType, InventoryKind> kinds = byName(InventoryType.class, InventoryKind.class, InventoryKind.OTHER);
    kinds.put(InventoryType.DROPPER, InventoryKind.DISPENSER);
    return kinds;
  }

  /** Separate holder: InventoryType needs the server registries, so it loads only on the first inventory event. */
  private static final class Inventories {
    private static final Map<InventoryType, InventoryKind> INVENTORY_KINDS = inventoryKinds();
    private static final Map<InventoryType.SlotType, SlotKind> SLOT_KINDS = byName(InventoryType.SlotType.class, SlotKind.class, SlotKind.CONTAINER);
    private static final Map<ClickType, ClickKind> CLICK_KINDS = byName(ClickType.class, ClickKind.class, ClickKind.OTHER);
    private static final Map<org.bukkit.event.inventory.InventoryAction, InventoryAction> INVENTORY_ACTIONS =
        byName(org.bukkit.event.inventory.InventoryAction.class, InventoryAction.class, InventoryAction.UNKNOWN);
  }

  public static DamageCause cause(org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
    return cause == null ? DamageCause.CUSTOM : DAMAGE_CAUSES.getOrDefault(cause, DamageCause.CUSTOM);
  }

  static InteractAction action(Action action) {
    return ACTIONS.getOrDefault(action, InteractAction.PHYSICAL);
  }

  static Hand hand(EquipmentSlot slot) {
    if (slot == EquipmentSlot.HAND) {
      return Hand.MAIN_HAND;
    }
    return slot == EquipmentSlot.OFF_HAND ? Hand.OFF_HAND : null;
  }

  static BlockFace face(org.bukkit.block.BlockFace face) {
    return face == null ? null : FACES.get(face);
  }

  static UseResult result(Event.Result result) {
    return result == null ? UseResult.DEFAULT : RESULTS.get(result);
  }

  static Event.Result result(UseResult result) {
    return NATIVE_RESULTS.get(result);
  }

  static GameMode gameMode(org.bukkit.GameMode mode) {
    return GAME_MODES.getOrDefault(mode, GameMode.SURVIVAL);
  }

  static TeleportCause teleportCause(PlayerTeleportEvent.TeleportCause cause) {
    return cause == null ? TeleportCause.UNKNOWN : TELEPORT_CAUSES.get(cause);
  }

  static EntityRegainHealthEvent.Reason regainReason(org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason reason) {
    return reason == null ? EntityRegainHealthEvent.Reason.CUSTOM : REGAIN_REASONS.get(reason);
  }

  static EntityTargetEvent.Reason targetReason(org.bukkit.event.entity.EntityTargetEvent.TargetReason reason) {
    return reason == null ? EntityTargetEvent.Reason.UNKNOWN : TARGET_REASONS.get(reason);
  }

  static EntityPotionEffectEvent.Action potionAction(org.bukkit.event.entity.EntityPotionEffectEvent.Action action) {
    return POTION_ACTIONS.getOrDefault(action, EntityPotionEffectEvent.Action.CHANGED);
  }

  static EntityPotionEffectEvent.Cause potionCause(org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause) {
    return cause == null ? EntityPotionEffectEvent.Cause.UNKNOWN : POTION_CAUSES.get(cause);
  }

  static PlayerFishEvent.State fishState(org.bukkit.event.player.PlayerFishEvent.State state) {
    return FISH_STATES.getOrDefault(state, PlayerFishEvent.State.FISHING);
  }

  static InventoryKind inventoryKind(InventoryType type) {
    return type == null ? InventoryKind.OTHER : Inventories.INVENTORY_KINDS.getOrDefault(type, InventoryKind.OTHER);
  }

  static SlotKind slotKind(InventoryType.SlotType type) {
    return Inventories.SLOT_KINDS.getOrDefault(type, SlotKind.CONTAINER);
  }

  static ClickKind clickKind(ClickType click) {
    return Inventories.CLICK_KINDS.getOrDefault(click, ClickKind.OTHER);
  }

  static InventoryAction inventoryAction(org.bukkit.event.inventory.InventoryAction action) {
    return Inventories.INVENTORY_ACTIONS.getOrDefault(action, InventoryAction.UNKNOWN);
  }

  @SuppressWarnings("deprecation")
  static EffectInstance effect(PotionEffect effect) {
    if (effect == null) {
      return null;
    }
    return new EffectInstance(BukkitRefs.id(effect.getType().getKey()), effect.getDuration(), effect.getAmplifier(),
        effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
  }

  static Vec3 vec(Vector vector) {
    return vector == null ? null : new Vec3(vector.getX(), vector.getY(), vector.getZ());
  }

  static PlayerRef player(Player player) {
    return player == null ? null : BukkitRefs.player(player);
  }

  static EntityRef entity(Entity entity) {
    if (entity == null) {
      return null;
    }
    return entity instanceof Player player ? BukkitRefs.player(player) : BukkitRefs.entity(entity);
  }

  static LivingRef living(LivingEntity entity) {
    if (entity == null) {
      return null;
    }
    return entity instanceof Player player ? BukkitRefs.player(player) : BukkitRefs.living(entity);
  }

  static Entity unwrap(EntityRef entity) {
    return entity == null ? null : BukkitRefs.entity(entity);
  }

  static BlockRef block(Block block) {
    return block == null ? null : BukkitRefs.block(block);
  }

  static BlockStateRef state(BlockState state) {
    return state == null ? null : BukkitRefs.blockState(state.getBlockData());
  }

  static ItemRef item(ItemStack stack) {
    return stack == null ? null : BukkitRefs.item(stack);
  }

  static ItemStack stack(ItemRef item) {
    return item == null ? null : BukkitRefs.item(item);
  }
}
