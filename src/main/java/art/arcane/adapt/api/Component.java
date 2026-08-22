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
import art.arcane.adapt.api.potion.AdaptPotionRegistry;
import art.arcane.adapt.api.value.MaterialValue;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import com.francobm.magicosmetics.api.CosmeticType;
import com.francobm.magicosmetics.api.MagicAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

  default void decrementItemstack(ItemStack hand, Player p) {
    if (hand.getAmount() > 1) {
      hand.setAmount(hand.getAmount() - 1);
    } else {
      p.getInventory().setItemInMainHand(null);
    }
  }

  default double getArmorValue(Player player) {
    PlayerInventory inventory = player.getInventory();
    ItemStack helmet = inventory.getHelmet();
    ItemStack chestplate = inventory.getChestplate();
    boolean cosmeticsEnabled = Bukkit.getPluginManager().isPluginEnabled("MagicCosmetics");
    double helmetValue = cosmeticsEnabled && helmet != null && MagicAPI.hasEquipCosmetic(player, CosmeticType.HAT)
        ? 0D
        : helmetArmor(helmet);
    double chestplateValue = cosmeticsEnabled && chestplate != null && MagicAPI.hasEquipCosmetic(player, CosmeticType.BAG)
        ? 0D
        : chestplateArmor(chestplate);
    return helmetValue + chestplateValue + leggingsArmor(inventory.getLeggings()) + bootsArmor(inventory.getBoots());
  }

  private double helmetArmor(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return 0D;
    }
    return switch (item.getType()) {
      case LEATHER_HELMET -> 0.04D;
      case GOLDEN_HELMET, TURTLE_HELMET, CHAINMAIL_HELMET, IRON_HELMET -> 0.08D;
      case DIAMOND_HELMET, NETHERITE_HELMET -> 0.12D;
      default -> 0D;
    };
  }

  private double chestplateArmor(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return 0D;
    }
    return switch (item.getType()) {
      case LEATHER_CHESTPLATE -> 0.12D;
      case GOLDEN_CHESTPLATE, CHAINMAIL_CHESTPLATE -> 0.20D;
      case IRON_CHESTPLATE -> 0.24D;
      case DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE -> 0.32D;
      default -> 0D;
    };
  }

  private double leggingsArmor(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return 0D;
    }
    return switch (item.getType()) {
      case LEATHER_LEGGINGS -> 0.08D;
      case GOLDEN_LEGGINGS -> 0.12D;
      case CHAINMAIL_LEGGINGS -> 0.16D;
      case IRON_LEGGINGS -> 0.20D;
      case DIAMOND_LEGGINGS, NETHERITE_LEGGINGS -> 0.24D;
      default -> 0D;
    };
  }

  private double bootsArmor(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return 0D;
    }
    return switch (item.getType()) {
      case LEATHER_BOOTS, GOLDEN_BOOTS, CHAINMAIL_BOOTS -> 0.04D;
      case IRON_BOOTS -> 0.08D;
      case DIAMOND_BOOTS, NETHERITE_BOOTS -> 0.12D;
      default -> 0D;
    };
  }

  default boolean isAdaptableDamageCause(EntityDamageEvent event) {
    return !NON_ADAPTABLE_DAMAGE_CAUSES.contains(event.getCause());
  }

  default void addPotionStacks(Player p, PotionEffectType potionEffect, int amplifier, int duration, boolean overlap) {
    if (p == null || potionEffect == null) {
      return;
    }

    PotionEffect activeEffect = p.getPotionEffect(potionEffect);
    if (activeEffect != null) {
      addPotionStacksNow(p, potionEffect, amplifier, duration, overlap);
      return;
    }

    J.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }
      addPotionStacksNow(p, potionEffect, amplifier, duration, overlap);
    }, 1);
  }

  default boolean addPotionStacksNow(Player p, PotionEffectType potionEffect, int amplifier, int duration,
                                     boolean overlap) {
    if (p == null || potionEffect == null || !p.isOnline()) {
      return false;
    }

    PotionEffect activeEffect = p.getPotionEffect(potionEffect);
    if (activeEffect != null && !overlap) {
      return false;
    }

    int newDuration = Math.max(1, duration);
    int newAmplifier = amplifier;
    if (activeEffect != null) {
      long combinedDuration = (long) activeEffect.getDuration() + newDuration;
      newDuration = (int) Math.min(Integer.MAX_VALUE, combinedDuration);
      newAmplifier = Math.max(activeEffect.getAmplifier(), amplifier);
      p.removePotionEffect(potionEffect);
    }

    boolean applied = p.addPotionEffect(new PotionEffect(potionEffect, newDuration, newAmplifier));
    if (applied) {
      AdaptPotionRegistry.record(p.getUniqueId(), potionEffect);
      SoundPlayer.of(p).play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.25f);
    }
    return applied;
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
    if (!(itemEntity instanceof Item item)
        || (J.isFoliaThreading()
        && (!J.isOwnedByCurrentRegion(player) || !J.isOwnedByCurrentRegion(item)))
        || !canSnatchItem(player, item)
        || is == null
        || is.getType().isAir()
        || is.getAmount() <= 0) {
      return false;
    }

    int pickupRemaining = ProtectionEventProbe.remainingAfterPickup(player.getInventory(), is);
    if (!ProtectionEventProbe.attemptItemPickup(player, item, pickupRemaining)
        || !item.isValid()
        || item.isDead()) {
      return false;
    }

    ItemStack current = item.getItemStack();
    if (!current.isSimilar(is) || current.getAmount() != is.getAmount()) {
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
