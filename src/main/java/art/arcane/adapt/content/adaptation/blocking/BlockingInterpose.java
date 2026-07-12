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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class BlockingInterpose extends SimpleAdaptation<BlockingInterpose.Config> {
  public BlockingInterpose() {
    super("blocking-interpose");
    registerConfiguration(Config.class);
    setIcon(Material.SHIELD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_interpose_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_INGOT)
            .key("challenge_blocking_interpose_2k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_blocking_interpose_250", "blocking.interpose.damage-redirected", 250, 500);
    registerMilestone("challenge_blocking_interpose_2k", "blocking.interpose.damage-redirected", 2000, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getRedirectShare(level), 0), 1);
    statLore(v, Form.f(getRange(level), 1), 2);
    statLore(v, Form.pc(getConfig().lowHealthThreshold, 0), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player ally)) {
      return;
    }

    double maxHealth = getMaxHealth(ally);
    if (maxHealth <= 0 || ally.getHealth() > maxHealth * getConfig().lowHealthThreshold) {
      return;
    }

    double scanRadius = getConfig().rangeBase + getConfig().rangeFactor;
    Location allyLocation = ally.getLocation();
    Player bestBlocker = null;
    int bestLevel = 0;
    double bestDistanceSquared = Double.MAX_VALUE;

    for (Player blocker : allyLocation.getWorld().getNearbyEntitiesByType(Player.class, allyLocation, scanRadius, scanRadius, scanRadius)) {
      if (blocker == ally || !blocker.isSneaking() || !blocker.isBlocking() || !hasShield(blocker)) {
        continue;
      }

      int level = getActiveLevel(blocker);
      if (level <= 0) {
        continue;
      }

      double range = getRange(level);
      double distanceSquared = blocker.getLocation().distanceSquared(allyLocation);
      if (distanceSquared > range * range || distanceSquared >= bestDistanceSquared) {
        continue;
      }

      bestDistanceSquared = distanceSquared;
      bestBlocker = blocker;
      bestLevel = level;
    }

    if (bestBlocker == null) {
      return;
    }

    double before = e.getDamage();
    double redirected = before * getRedirectShare(bestLevel);
    if (redirected <= 0) {
      return;
    }
    e.setDamage(Math.max(0, before - redirected));

    Location impact = allyLocation.add(0, 1.0D, 0);
    fx(impact, FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 4, 0.2D)
        .sound(Sound.ITEM_SHIELD_BLOCK, 0.5F, 1.2F);

    Player blocker = bestBlocker;
    J.runEntity(blocker, () -> absorbRedirect(blocker, redirected, impact));
  }

  private void absorbRedirect(Player blocker, double redirected, Location allyImpact) {
    if (!blocker.isOnline()) {
      return;
    }

    wearShield(blocker, redirected);
    blocker.setExhaustion(blocker.getExhaustion() + (float) getConfig().exhaustionPerRedirect);
    addStat(blocker, "blocking.interpose.damage-redirected", redirected);
    xp(blocker, redirected * getConfig().xpPerDamageRedirected);

    Location shield = blocker.getEyeLocation();
    fx(shield, FxPriority.COMBAT)
        .line(Particles.END_ROD, allyImpact.getX(), allyImpact.getY(), allyImpact.getZ(), 6)
        .chord(Sound.ITEM_SHIELD_BLOCK, 0.8F, 0.9F, Sound.BLOCK_ANVIL_LAND, 0.3F, 1.3F);
  }

  private void wearShield(Player blocker, double redirected) {
    PlayerInventory inventory = blocker.getInventory();
    ItemStack shield = inventory.getItemInOffHand();
    boolean offHand = isItem(shield) && shield.getType() == Material.SHIELD;
    if (!offHand) {
      shield = inventory.getItemInMainHand();
      if (!isItem(shield) || shield.getType() != Material.SHIELD) {
        return;
      }
    }

    ItemMeta meta = shield.getItemMeta();
    if (!(meta instanceof Damageable damageable)) {
      return;
    }

    int maxDurability = damageable.hasMaxDamage() ? damageable.getMaxDamage() : shield.getType().getMaxDurability();
    if (maxDurability <= 1) {
      return;
    }

    int cost = Math.max(1, (int) Math.ceil(redirected * getConfig().durabilityPerDamage));
    int newDamage = Math.min(maxDurability - 1, damageable.getDamage() + cost);
    if (newDamage <= damageable.getDamage()) {
      return;
    }
    damageable.setDamage(newDamage);
    shield.setItemMeta(damageable);
    if (offHand) {
      inventory.setItemInOffHand(shield);
    } else {
      inventory.setItemInMainHand(shield);
    }
  }

  private double getMaxHealth(Player p) {
    AttributeInstance attribute = p.getAttribute(Attribute.MAX_HEALTH);
    return attribute == null ? 20D : attribute.getValue();
  }

  private boolean hasShield(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private double getRedirectShare(int level) {
    return Math.min(getConfig().maxRedirectShare, getConfig().redirectShareBase + (getLevelPercent(level) * getConfig().redirectShareFactor));
  }


  @ConfigDescription("Sneak-block near a wounded ally to redirect part of the damage they take onto your shield.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Share Base for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectShareBase = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Share Factor for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectShareFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Redirect Share for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRedirectShare = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Base for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Factor for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 4.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Low Health Threshold for the Blocking Interpose adaptation.", impact = "Higher values let you shield allies at higher health fractions.")
    double lowHealthThreshold = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Per Damage for the Blocking Interpose adaptation.", impact = "Higher values wear your shield down faster per point of redirected damage.")
    double durabilityPerDamage = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Exhaustion Per Redirect for the Blocking Interpose adaptation.", impact = "Higher values drain more hunger each time you interpose.")
    double exhaustionPerRedirect = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage Redirected for the Blocking Interpose adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamageRedirected = 3.0;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
