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

package art.arcane.adapt.api;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.value.MaterialValue;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import com.francobm.magicosmetics.api.CosmeticType;
import com.francobm.magicosmetics.api.MagicAPI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface Component {
  Set<EntityDamageEvent.DamageCause> NON_ADAPTABLE_DAMAGE_CAUSES = Set.of(
      EntityDamageEvent.DamageCause.VOID,
      EntityDamageEvent.DamageCause.LAVA,
      EntityDamageEvent.DamageCause.HOT_FLOOR,
      EntityDamageEvent.DamageCause.CRAMMING,
      EntityDamageEvent.DamageCause.MELTING,
      EntityDamageEvent.DamageCause.SUFFOCATION,
      EntityDamageEvent.DamageCause.SUICIDE,
      EntityDamageEvent.DamageCause.WITHER,
      EntityDamageEvent.DamageCause.FLY_INTO_WALL,
      EntityDamageEvent.DamageCause.FALL,
      EntityDamageEvent.DamageCause.SONIC_BOOM,
      EntityDamageEvent.DamageCause.THORNS
  );

  default boolean areParticlesEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isParticlesEnabled();
  }

  default boolean areSoundsEnabled() {
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    return effects == null || effects.isSoundsEnabled();
  }

  default void wisdom(Player p, long w) {
    XP.wisdom(p, w);
  }

  /**
   * Attempts to "damage" an item. 1. If the item is null, null is returned 2.
   * If the item doesnt have durability, (damage) amount will be consumed from
   * the stack, null will be returned if more consumed than amount 3. If the
   * item has durability, the damage will be consuemd and return the item
   * affected, OR null if it broke
   *
   * @param item   the item (tool)
   * @param damage the damage to cause
   * @return the damaged item or null if destroyed
   */
  default ItemStack damage(ItemStack item, int damage) {
    if (item == null) {
      return null;
    }

    if (item.getItemMeta() == null) {
      if (item.getAmount() == 1) {
        return null;
      }

      item = item.clone();
      item.setAmount(item.getAmount() - 1);
      return item;
    }

    if (item.getItemMeta() instanceof Damageable d) {
      if (d.getDamage() + 1 > item.getType().getMaxDurability()) {
        return null;
      }

      d.setDamage(d.getDamage() + 1);
      item = item.clone();
      item.setItemMeta(d);
      return item;
    } else {
      if (item.getAmount() == 1) {
        return null;
      }

      item = item.clone();
      item.setAmount(item.getAmount() - 1);

      return item;
    }
  }

  default void decrementItemstack(ItemStack hand, Player p) {
    if (hand.getAmount() > 1) {
      hand.setAmount(hand.getAmount() - 1);
    } else {
      p.getInventory().setItemInMainHand(null);
    }
  }

  default double getArmorValue(Player player) {
    org.bukkit.inventory.PlayerInventory inv = player.getInventory();
    ItemStack boots = inv.getBoots();
    ItemStack helmet = inv.getHelmet();
    ItemStack chest = inv.getChestplate();
    ItemStack pants = inv.getLeggings();
    double armorValue = 0.0;
    if (helmet == null) armorValue = armorValue + 0.0;
    else if (Bukkit.getServer().getPluginManager().getPlugin("MagicCosmetics") != null && MagicAPI.hasEquipCosmetic(player, CosmeticType.HAT)) {
      armorValue = armorValue + 0;
    } else if (helmet.getType() == Material.LEATHER_HELMET)
      armorValue = armorValue + 0.04;
    else if (helmet.getType() == Material.GOLDEN_HELMET)
      armorValue = armorValue + 0.08;
    else if (helmet.getType() == Material.TURTLE_HELMET)
      armorValue = armorValue + 0.08;
    else if (helmet.getType() == Material.CHAINMAIL_HELMET)
      armorValue = armorValue + 0.08;
    else if (helmet.getType() == Material.IRON_HELMET)
      armorValue = armorValue + 0.08;
    else if (helmet.getType() == Material.DIAMOND_HELMET)
      armorValue = armorValue + 0.12;
    else if (helmet.getType() == Material.NETHERITE_HELMET)
      armorValue = armorValue + 0.12;
    //
    if (boots == null) armorValue = armorValue + 0.0;
    else if (boots.getType() == Material.LEATHER_BOOTS)
      armorValue = armorValue + 0.04;
    else if (boots.getType() == Material.GOLDEN_BOOTS)
      armorValue = armorValue + 0.04;
    else if (boots.getType() == Material.CHAINMAIL_BOOTS)
      armorValue = armorValue + 0.04;
    else if (boots.getType() == Material.IRON_BOOTS)
      armorValue = armorValue + 0.08;
    else if (boots.getType() == Material.DIAMOND_BOOTS)
      armorValue = armorValue + 0.12;
    else if (boots.getType() == Material.NETHERITE_BOOTS)
      armorValue = armorValue + 0.12;
    //
    if (pants == null) armorValue = armorValue + 0.0;
    else if (pants.getType() == Material.LEATHER_LEGGINGS)
      armorValue = armorValue + 0.08;
    else if (pants.getType() == Material.GOLDEN_LEGGINGS)
      armorValue = armorValue + 0.12;
    else if (pants.getType() == Material.CHAINMAIL_LEGGINGS)
      armorValue = armorValue + 0.16;
    else if (pants.getType() == Material.IRON_LEGGINGS)
      armorValue = armorValue + 0.20;
    else if (pants.getType() == Material.DIAMOND_LEGGINGS)
      armorValue = armorValue + 0.24;
    else if (pants.getType() == Material.NETHERITE_LEGGINGS)
      armorValue = armorValue + 0.24;
    //
    if (chest == null) armorValue = armorValue + 0.0;
    else if (Bukkit.getServer().getPluginManager().getPlugin("MagicCosmetics") != null && MagicAPI.hasEquipCosmetic(player, CosmeticType.BAG)) {
      armorValue = armorValue + 0;
    } else if (chest.getType() == Material.LEATHER_CHESTPLATE)
      armorValue = armorValue + 0.12;
    else if (chest.getType() == Material.GOLDEN_CHESTPLATE)
      armorValue = armorValue + 0.20;
    else if (chest.getType() == Material.CHAINMAIL_CHESTPLATE)
      armorValue = armorValue + 0.20;
    else if (chest.getType() == Material.IRON_CHESTPLATE)
      armorValue = armorValue + 0.24;
    else if (chest.getType() == Material.DIAMOND_CHESTPLATE)
      armorValue = armorValue + 0.32;
    else if (chest.getType() == Material.NETHERITE_CHESTPLATE)
      armorValue = armorValue + 0.32;
    return armorValue;
  }

  default boolean isAdaptableDamageCause(EntityDamageEvent event) {
    return !NON_ADAPTABLE_DAMAGE_CAUSES.contains(event.getCause());
  }

  default void addPotionStacks(Player p, PotionEffectType potionEffect, int amplifier, int duration, boolean overlap) {
    List<PotionEffect> activeEffects = new ArrayList<>(p.getActivePotionEffects());
    SoundPlayer sp = SoundPlayer.of(p);
    for (PotionEffect activeEffect : activeEffects) {
      if (activeEffect.getType() == potionEffect) {
        if (!overlap) {
          return; // don't modify the effect if overlap is false
        }
        // modify the effect if overlap is true
        int newDuration = activeEffect.getDuration() + duration;
        int newAmplifier = Math.max(activeEffect.getAmplifier(), amplifier);
        p.removePotionEffect(potionEffect);
        p.addPotionEffect(new PotionEffect(potionEffect, newDuration, newAmplifier));
        sp.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.25f);
        return;
      }
    }
    // if we didn't find an existing effect, add a new one
    J.s(() -> {
      p.addPotionEffect(new PotionEffect(potionEffect, duration, amplifier));
      sp.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.25f);
    }, 1);

  }


  default void potion(Player p, PotionEffectType type, int power, int duration) {
    p.addPotionEffect(new PotionEffect(type, power, duration, true, false, false));
  }

  default double blockXP(Block block, double xp) {
    try {
      return Math.round(xp * getBlockMultiplier(block));
    } catch (Exception e) {
      Adapt.verbose("Error in blockXP: " + e.getMessage());
    }
    return xp;
  }

  default double getBlockMultiplier(Block block) {
    return WorldData.of(block.getWorld()).reportEarnings(block);
  }

  default double getValue(Material material) {
    return MaterialValue.getValue(material);
  }

  default double getValue(BlockData block) {
    return MaterialValue.getValue(block.getMaterial());
  }

  default double getValue(ItemStack f) {
    return MaterialValue.getValue(f.getType());
  }

  default double getValue(Block block) {
    return MaterialValue.getValue(block.getType());
  }

  default boolean safeGiveItem(Player player, Entity itemEntity, ItemStack is) {
    if (!(itemEntity instanceof Item item) || !item.isValid() || is == null || is.getType().isAir() || is.getAmount() <= 0) {
      return false;
    }

    EntityPickupItemEvent e = new EntityPickupItemEvent(player, item, 0);
    Bukkit.getPluginManager().callEvent(e);
    if (e.isCancelled()) {
      return false;
    }

    int requested = is.getAmount();
    Map<Integer, ItemStack> leftover = player.getInventory().addItem(is.clone());
    if (leftover.isEmpty()) {
      item.remove();
      return true;
    }

    ItemStack remaining = leftover.values().iterator().next();
    if (remaining == null || remaining.getAmount() >= requested) {
      return false;
    }

    item.setItemStack(remaining);
    return true;
  }

  default boolean canSnatchItem(Player player, Item item) {
    if (!item.isValid() || item.isInvulnerable()) {
      return false;
    }

    if (item.getPickupDelay() >= Short.MAX_VALUE) {
      return false;
    }

    UUID owner = item.getOwner();
    if (owner != null && !owner.equals(player.getUniqueId())) {
      return false;
    }

    if (item.hasMetadata("NPC") || item.hasMetadata("shopitem") || item.hasMetadata("hologram")) {
      return false;
    }

    try {
      return item.canPlayerPickup();
    } catch (NoSuchMethodError ignored) {
      return true;
    }
  }


  default void safeGiveItem(Player player, ItemStack item) {
    if (!player.getInventory().addItem(item).isEmpty()) {
      player.getWorld().dropItem(player.getLocation(), item);
    }
  }

  default void damageHand(Player p, int damage) {
    ItemStack is = p.getInventory().getItemInMainHand();
    ItemMeta im = is.getItemMeta();

    if (im == null) {
      return;
    }

    if (im.isUnbreakable()) {
      return;
    }

    Damageable dm = (Damageable) im;
    dm.setDamage(dm.getDamage() + damage);

    if (dm.getDamage() > is.getType().getMaxDurability()) {
      p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
      SoundPlayer spw = SoundPlayer.of(p.getWorld());
      spw.play(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
      return;
    }

    is.setItemMeta(im);
    p.getInventory().setItemInMainHand(is);
  }

  default void damageOffHand(Player p, int damage) {
    ItemStack is = p.getInventory().getItemInOffHand();
    ItemMeta im = is.getItemMeta();

    if (im == null) {
      return;
    }

    if (im.isUnbreakable()) {
      return;
    }

    Damageable dm = (Damageable) im;
    dm.setDamage(dm.getDamage() + damage);

    if (dm.getDamage() > is.getType().getMaxDurability()) {
      p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
      SoundPlayer spw = SoundPlayer.of(p.getWorld());
      spw.play(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
      return;
    }

    is.setItemMeta(im);
    p.getInventory().setItemInOffHand(is);
  }

  default Block getRightBlock(Player p, Block b) {
    Location l = p.getLocation();
    float yaw = l.getYaw();
    // Make sure yaw is in the range 0 to 360
    while (yaw < 0) {
      yaw += 360;
    }
    yaw = yaw % 360;
    // The player's yaw is their rotation in the world,
    // so, we can use that to get the right face of a block!
    BlockFace rightFace;
    // if the player is facing SE to SW
    if (yaw < 45 || yaw >= 315) {
      rightFace = BlockFace.EAST;
      return b.getRelative(rightFace);
    }
    // if the player is facing SW to NW
    else if (yaw < 135) {
      rightFace = BlockFace.SOUTH;
      return b.getRelative(rightFace);
    }
    // if the player is facing NW to NE
    else if (yaw < 225) {
      rightFace = BlockFace.WEST;
      return b.getRelative(rightFace);
    }
    // if the player is facing NE to SE
    else if (yaw < 315) {
      rightFace = BlockFace.NORTH;
      return b.getRelative(rightFace);
    } else {
      return null;
    }
  }

  default Block getLeftBlock(Player p, Block b) {
    Location l = p.getLocation();
    float yaw = l.getYaw();

    // Make sure yaw is in the range 0 to 360
    while (yaw < 0) {
      yaw += 360;
    }
    yaw = yaw % 360;
    // The player's yaw is their rotation in the world,
    // so, we can use that to get the right face of a block!
    BlockFace leftFace;
    // if the player is facing SE to SW
    if (yaw < 45 || yaw >= 315) {
      leftFace = BlockFace.WEST;
      return b.getRelative(leftFace);
    }
    // if the player is facing SW to NW
    else if (yaw < 135) {
      leftFace = BlockFace.NORTH;
      return b.getRelative(leftFace);
    }
    // if the player is facing NW to NE
    else if (yaw < 225) {
      leftFace = BlockFace.EAST;
      return b.getRelative(leftFace);
    }
    // if the player is facing NE to SE
    else if (yaw < 315) {
      leftFace = BlockFace.SOUTH;
      return b.getRelative(leftFace);
    } else {
      return null;
    }
  }


  default void setExp(Player p, int exp) {
    p.setExp(0);
    p.setLevel(0);
    p.setTotalExperience(0);

    if (exp <= 0) {
      return;
    }

    giveExp(p, exp);
  }

  default void giveExp(Player p, int exp) {
    while (exp > 0) {
      int xp = getExpToLevel(p) - getExp(p);
      if (xp > exp) {
        xp = exp;
      }
      p.giveExp(xp);
      exp -= xp;
    }
  }

  default void takeExp(Player p, int exp) {
    takeExp(p, exp, true);
  }

  default void takeExp(Player p, int exp, boolean fromTotal) {
    int xp = getTotalExp(p);

    if (fromTotal) {
      xp -= exp;
    } else {
      int m = getExp(p) - exp;
      if (m < 0) {
        m = 0;
      }
      xp -= getExp(p) + m;
    }

    setExp(p, xp);
  }

  default int getExp(Player p) {
    return (int) (getExpToLevel(p) * p.getExp());
  }

  default int getTotalExp(Player p) {
    return getTotalExp(p, false);
  }

  default int getTotalExp(Player p, boolean recalc) {
    if (recalc) {
      recalcTotalExp(p);
    }
    return p.getTotalExperience();
  }

  default int getLevel(Player p) {
    return p.getLevel();
  }

  default int getExpToLevel(Player p) {
    return p.getExpToLevel();
  }

  default int getExpToLevel(int level) {
    return level >= 30 ? 62 + (level - 30) * 7 : (level >= 15 ? 17 + (level - 15) * 3 : 17);
  }

  default void recalcTotalExp(Player p) {
    int total = getExp(p);
    for (int i = 0; i < p.getLevel(); i++) {
      total += getExpToLevel(i);
    }
    p.setTotalExperience(total);
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
  default boolean takeAll(Inventory inv, ItemStack is, int amount) {
    ItemStack isf = is.clone();
    isf.setAmount(amount);
    return takeAll(inv, is);
  }

  /**
   * Take one of an exact type ignoring the item stack amount
   *
   * @param inv the inv
   * @param is  the item ignoring the amount
   * @return true if taken, false if diddnt
   */
  default boolean takeOne(Inventory inv, ItemStack is, int amount) {
    return takeAll(inv, is, 1);
  }

  /**
   * Take a specific amount of an EXACT META TYPE from an inventory
   *
   * @param inv the inv
   * @param is  uses the amount
   * @return returns false if it couldnt get enough (and none was taken)
   */
  default boolean takeAll(Inventory inv, ItemStack is) {
    ItemStack[] items = inv.getStorageContents();

    int take = is.getAmount();

    for (int ii = 0; ii < items.length; ii++) {
      ItemStack i = items[ii];

      if (i == null) {
        continue;
      }

      if (i.isSimilar(is)) {
        if (take > i.getAmount()) {
          i.setAmount(i.getAmount() - take);
          items[ii] = i;
          take = 0;
          break;
        } else {
          items[ii] = null;
          take -= i.getAmount();
        }
      }
    }

    if (take > 0) {
      return false;
    }

    inv.setStorageContents(items);
    return true;
  }
}
