/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.engine.component;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.engine.data.WorldDataPort;
import art.arcane.adapt.engine.potion.AdaptPotionRegistry;
import art.arcane.adapt.engine.value.MaterialValue;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.engine.ComponentPort;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Item, armor, potion and experience helpers behind the component runtime. */
public final class ComponentSupport {
  private ComponentSupport() {
  }

  private static final int PLAYER_STORAGE_SLOTS = 36;

  private static final Set<DamageCause> NON_ADAPTABLE_DAMAGE_CAUSES = Set.of(
      DamageCause.VOID,
      DamageCause.LAVA,
      DamageCause.HOT_FLOOR,
      DamageCause.CRAMMING,
      DamageCause.MELTING,
      DamageCause.SUFFOCATION,
      DamageCause.SUICIDE,
      DamageCause.WITHER,
      DamageCause.FLY_INTO_WALL,
      DamageCause.FALL,
      DamageCause.SONIC_BOOM,
      DamageCause.THORNS
  );

  public static boolean areParticlesEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isParticlesEnabled();
  }

  public static boolean areSoundsEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isSoundsEnabled();
  }

  public static void wisdom(PlayerRef p, long w) {
    XP.wisdom(p, w);
  }

  public static void decrementItemstack(ItemRef hand, PlayerRef p) {
    if (hand.amount() > 1) {
      hand.setAmount(hand.amount() - 1);
      p.inventory().setMainHand(hand);
    } else {
      p.inventory().setMainHand(null);
    }
  }

  public static double getArmorValue(PlayerRef player) {
    PlayerInventoryRef inventory = player.inventory();
    ItemRef helmet = inventory.armor(EquipmentSlot.HEAD);
    ItemRef chestplate = inventory.armor(EquipmentSlot.CHEST);
    ComponentPort port = componentPort();
    double helmetValue = present(helmet) && port.cosmeticCovers(player, EquipmentSlot.HEAD)
        ? 0D
        : helmetArmor(helmet);
    double chestplateValue = present(chestplate) && port.cosmeticCovers(player, EquipmentSlot.CHEST)
        ? 0D
        : chestplateArmor(chestplate);
    return helmetValue + chestplateValue + leggingsArmor(inventory.armor(EquipmentSlot.LEGS))
        + bootsArmor(inventory.armor(EquipmentSlot.FEET));
  }

  private static boolean present(ItemRef item) {
    return item != null && !item.isEmpty() && !item.type().isAir();
  }

  private static String armorType(ItemRef item) {
    return present(item) && item.type().isMinecraft() ? item.type().path() : "";
  }

  private static double helmetArmor(ItemRef item) {
    return switch (armorType(item)) {
      case "leather_helmet" -> 0.04D;
      case "golden_helmet", "turtle_helmet", "chainmail_helmet", "iron_helmet" -> 0.08D;
      case "diamond_helmet", "netherite_helmet" -> 0.12D;
      default -> 0D;
    };
  }

  private static double chestplateArmor(ItemRef item) {
    return switch (armorType(item)) {
      case "leather_chestplate" -> 0.12D;
      case "golden_chestplate", "chainmail_chestplate" -> 0.20D;
      case "iron_chestplate" -> 0.24D;
      case "diamond_chestplate", "netherite_chestplate" -> 0.32D;
      default -> 0D;
    };
  }

  private static double leggingsArmor(ItemRef item) {
    return switch (armorType(item)) {
      case "leather_leggings" -> 0.08D;
      case "golden_leggings" -> 0.12D;
      case "chainmail_leggings" -> 0.16D;
      case "iron_leggings" -> 0.20D;
      case "diamond_leggings", "netherite_leggings" -> 0.24D;
      default -> 0D;
    };
  }

  private static double bootsArmor(ItemRef item) {
    return switch (armorType(item)) {
      case "leather_boots", "golden_boots", "chainmail_boots" -> 0.04D;
      case "iron_boots" -> 0.08D;
      case "diamond_boots", "netherite_boots" -> 0.12D;
      default -> 0D;
    };
  }

  public static boolean isAdaptableDamageCause(EntityDamageEvent event) {
    return !NON_ADAPTABLE_DAMAGE_CAUSES.contains(event.cause());
  }

  public static void addPotionStacks(PlayerRef p, Id potionEffect, int amplifier, int duration, boolean overlap) {
    if (p == null || potionEffect == null) {
      return;
    }

    EffectInstance activeEffect = p.effect(potionEffect);
    if (activeEffect != null) {
      addPotionStacksNow(p, potionEffect, amplifier, duration, overlap);
      return;
    }

    Tasks.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }
      addPotionStacksNow(p, potionEffect, amplifier, duration, overlap);
    }, 1);
  }

  public static boolean addPotionStacksNow(PlayerRef p, Id potionEffect, int amplifier, int duration, boolean overlap) {
    if (p == null || potionEffect == null || !p.isOnline()) {
      return false;
    }

    EffectInstance activeEffect = p.effect(potionEffect);
    if (activeEffect != null && !overlap) {
      return false;
    }

    int newDuration = Math.max(1, duration);
    int newAmplifier = amplifier;
    if (activeEffect != null) {
      long combinedDuration = (long) activeEffect.durationTicks() + newDuration;
      newDuration = (int) Math.min(Integer.MAX_VALUE, combinedDuration);
      newAmplifier = Math.max(activeEffect.amplifier(), amplifier);
      p.removeEffect(potionEffect);
    }

    boolean applied = p.addEffect(EffectInstance.of(potionEffect, newDuration, newAmplifier));
    if (applied) {
      AdaptPotionRegistry.record(p.uuid(), potionEffect);
      SoundPlayer.of(p).play(p.location(), Sounds.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.25f);
    }
    return applied;
  }

  public static double blockXP(BlockRef block, double xp) {
    try {
      return Math.round(xp * getBlockMultiplier(block));
    } catch (Exception e) {
      AdaptLog.verbose("Error in blockXP: " + e.getMessage());
    }
    return xp;
  }

  public static double getBlockMultiplier(BlockRef block) {
    return AdaptPorts.get(WorldDataPort.class, WorldDataPort.NONE).reportEarnings(block);
  }

  public static double getValue(Id material) {
    return MaterialValue.getValue(material);
  }

  public static double getValue(BlockStateRef block) {
    return MaterialValue.getValue(block.type());
  }

  public static double getValue(ItemRef f) {
    return MaterialValue.getValue(f.type());
  }

  public static double getValue(BlockRef block) {
    return MaterialValue.getValue(block.type());
  }

  public static boolean safeGiveItem(PlayerRef player, EntityRef itemEntity, ItemRef is) {
    if (!(itemEntity instanceof ItemEntityRef item)
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(item)))
        || !canSnatchItem(player, item)
        || is == null
        || is.isEmpty()
        || is.type().isAir()
        || is.amount() <= 0) {
      return false;
    }

    if (!componentPort().attemptItemPickup(player, item, is)
        || !item.isValid()
        || item.isDead()) {
      return false;
    }

    ItemRef current = item.item();
    if (current == null || !current.isSimilar(is) || current.amount() != is.amount()) {
      return false;
    }

    int requested = is.amount();
    List<ItemRef> leftover = player.inventory().addItem(is.copy());
    if (leftover == null || leftover.isEmpty()) {
      item.remove();
      return true;
    }

    ItemRef remaining = leftover.getFirst();
    if (remaining == null || remaining.amount() >= requested) {
      return false;
    }

    item.setItem(remaining);
    return true;
  }

  public static boolean canSnatchItem(PlayerRef player, ItemEntityRef item) {
    if (!item.isValid() || item.isInvulnerable()) {
      return false;
    }

    if (item.pickupDelay() >= Short.MAX_VALUE) {
      return false;
    }

    UUID owner = item.owner();
    if (owner != null && !owner.equals(player.uuid())) {
      return false;
    }

    return !componentPort().isPickupLocked(item);
  }

  public static void safeGiveItem(PlayerRef player, ItemRef item) {
    List<ItemRef> leftover = player.inventory().addItem(item);
    if (leftover == null) {
      return;
    }
    for (ItemRef remaining : leftover) {
      if (remaining != null && !remaining.isEmpty()) {
        player.world().dropItem(player.location().position(), remaining);
      }
    }
  }

  public static void damageHand(PlayerRef p, int damage) {
    if (damage > 0) {
      componentPort().damageItem(p, EquipmentSlot.MAIN_HAND, damage);
    }
  }

  public static void damageOffHand(PlayerRef p, int damage) {
    if (damage > 0) {
      componentPort().damageItem(p, EquipmentSlot.OFF_HAND, damage);
    }
  }

  public static BlockRef getRightBlock(PlayerRef p, BlockRef b) {
    float yaw = normalizedYaw(p.location());
    if (yaw < 45 || yaw >= 315) {
      return b.relative(BlockFace.EAST);
    } else if (yaw < 135) {
      return b.relative(BlockFace.SOUTH);
    } else if (yaw < 225) {
      return b.relative(BlockFace.WEST);
    } else if (yaw < 315) {
      return b.relative(BlockFace.NORTH);
    }
    return null;
  }

  public static BlockRef getLeftBlock(PlayerRef p, BlockRef b) {
    float yaw = normalizedYaw(p.location());
    if (yaw < 45 || yaw >= 315) {
      return b.relative(BlockFace.WEST);
    } else if (yaw < 135) {
      return b.relative(BlockFace.NORTH);
    } else if (yaw < 225) {
      return b.relative(BlockFace.EAST);
    } else if (yaw < 315) {
      return b.relative(BlockFace.SOUTH);
    }
    return null;
  }

  private static float normalizedYaw(Loc location) {
    float yaw = location.yaw();
    while (yaw < 0) {
      yaw += 360;
    }
    return yaw % 360;
  }

  public static void setExp(PlayerRef p, int exp) {
    p.setExpProgress(0);
    p.setLevel(0);
    componentPort().setTotalExperience(p, 0);

    if (exp <= 0) {
      return;
    }

    p.giveExp(exp);
  }

  public static void giveExp(PlayerRef p, int exp) {
    componentPort().awardExperience(p, exp);
  }

  public static void takeExp(PlayerRef p, int exp) {
    takeExp(p, exp, true);
  }

  public static void takeExp(PlayerRef p, int exp, boolean fromTotal) {
    int xp = getTotalExp(p);

    if (fromTotal) {
      xp -= exp;
    } else {
      xp -= Math.min(Math.max(0, exp), getExp(p));
    }

    setExp(p, Math.max(0, xp));
  }

  public static int getExp(PlayerRef p) {
    return (int) (getExpToLevel(p) * p.expProgress());
  }

  public static int getTotalExp(PlayerRef p) {
    return getTotalExp(p, false);
  }

  public static int getTotalExp(PlayerRef p, boolean recalc) {
    if (recalc) {
      recalcTotalExp(p);
    }
    return p.totalExperience();
  }

  public static int getLevel(PlayerRef p) {
    return p.level();
  }

  public static int getExpToLevel(PlayerRef p) {
    return getExpToLevel(p.level());
  }

  public static int getExpToLevel(int level) {
    int normalizedLevel = Math.max(0, level);
    long required = normalizedLevel >= 30
        ? (9L * normalizedLevel) - 158L
        : normalizedLevel >= 15 ? (5L * normalizedLevel) - 38L : (2L * normalizedLevel) + 7L;
    return (int) Math.min(Integer.MAX_VALUE, required);
  }

  public static void recalcTotalExp(PlayerRef p) {
    ComponentPort port = componentPort();
    port.setTotalExperience(p, port.totalExperiencePoints(p));
  }

  /**
   * Takes a custom amount of the item stack exact type (Ignores the item
   * amount)
   *
   * @param inv    the inv
   * @param is     the item ignore the amount
   * @param amount the amount to use
   * @return true if taken, false if not (missing)
   */
  public static boolean takeAll(InventoryRef inv, ItemRef is, int amount) {
    if (inv == null || is == null || amount <= 0) {
      return false;
    }
    return takeAll(inv, is.copyWithAmount(amount));
  }

  /**
   * Take one of an exact type ignoring the item stack amount
   *
   * @param inv the inv
   * @param is  the item ignoring the amount
   * @return true if taken, false if diddnt
   */
  public static boolean takeOne(InventoryRef inv, ItemRef is, int amount) {
    return takeAll(inv, is, 1);
  }

  /**
   * Take a specific amount of an EXACT META TYPE from an inventory
   *
   * @param inv the inv
   * @param is  uses the amount
   * @return returns false if it couldnt get enough (and none was taken)
   */
  public static boolean takeAll(InventoryRef inv, ItemRef is) {
    if (inv == null || is == null || is.amount() <= 0) {
      return false;
    }

    int slots = inv instanceof PlayerInventoryRef ? Math.min(PLAYER_STORAGE_SLOTS, inv.size()) : inv.size();
    int available = 0;
    for (int slot = 0; slot < slots; slot++) {
      ItemRef item = inv.item(slot);
      if (item != null && !item.isEmpty() && item.isSimilar(is)) {
        available += item.amount();
        if (available >= is.amount()) {
          break;
        }
      }
    }
    if (available < is.amount()) {
      return false;
    }

    int remaining = is.amount();
    for (int slot = 0; slot < slots && remaining > 0; slot++) {
      ItemRef item = inv.item(slot);
      if (item == null || item.isEmpty() || !item.isSimilar(is)) {
        continue;
      }

      int removed = Math.min(remaining, item.amount());
      remaining -= removed;
      if (removed == item.amount()) {
        inv.clear(slot);
      } else {
        inv.setItem(slot, item.copyWithAmount(item.amount() - removed));
      }
    }
    return true;
  }

  /**
   * The face of the first non-air block in view (within {@code maxrange} blocks) that points back at the block the
   * sight line passed through last, or null when that target is not solid.
   */
  public static BlockFace blockFace(PlayerRef player, int maxrange) {
    Loc eye = player.eyeLocation();
    WorldRef world = eye.world();
    Vec3 direction = eye.direction();
    if (world == null || direction == null) {
      return null;
    }
    double dx = direction.x();
    double dy = direction.y();
    double dz = direction.z();
    int x = (int) Math.floor(eye.x());
    int y = (int) Math.floor(eye.y());
    int z = (int) Math.floor(eye.z());
    int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
    int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
    int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
    double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / dx);
    double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / dy);
    double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / dz);
    double maxX = stepX == 0 ? Double.POSITIVE_INFINITY : (stepX > 0 ? (x + 1D - eye.x()) : (eye.x() - x)) * deltaX;
    double maxY = stepY == 0 ? Double.POSITIVE_INFINITY : (stepY > 0 ? (y + 1D - eye.y()) : (eye.y() - y)) * deltaY;
    double maxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (stepZ > 0 ? (z + 1D - eye.z()) : (eye.z() - z)) * deltaZ;
    BlockFace entered = null;
    for (int steps = 0; steps <= maxrange; steps++) {
      BlockRef block = world.blockAt(x, y, z);
      if (block != null && !block.isAir()) {
        if (entered == null || !block.isSolid()) {
          return null;
        }
        return entered;
      }
      if (maxX <= maxY && maxX <= maxZ) {
        x += stepX;
        maxX += deltaX;
        entered = stepX > 0 ? BlockFace.WEST : BlockFace.EAST;
      } else if (maxY <= maxZ) {
        y += stepY;
        maxY += deltaY;
        entered = stepY > 0 ? BlockFace.DOWN : BlockFace.UP;
      } else {
        z += stepZ;
        maxZ += deltaZ;
        entered = stepZ > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
      }
    }
    return null;
  }

  private static ComponentPort componentPort() {
    return AdaptPorts.get(ComponentPort.class, ComponentPort.NONE);
  }
}
