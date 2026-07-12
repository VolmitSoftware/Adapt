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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;

import java.util.List;
import java.util.UUID;

public class TamingFetch extends SimpleAdaptation<TamingFetch.Config> {
  private static final int HARD_MAX_WOLVES = 12;
  private static final int HARD_MAX_CARRY_PER_TICK = 8;

  public TamingFetch() {
    super("tame-fetch");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.fetch");
    setIcon(Material.HOPPER);
    setInterval(1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HOPPER)
        .key("challenge_taming_fetch_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHEST)
            .key("challenge_taming_fetch_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_fetch_1k", "taming.fetch.items-fetched", 1000, 400);
    registerMilestone("challenge_taming_fetch_10k", "taming.fetch.items-fetched", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getFetchRange(level), 1), 1);
    statLore(v, Form.pc(getCarryRate(level), 0), 2);
  }

  @Override
  public void onTick() {
    if (!isEnabled()) {
      return;
    }

    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player owner = adaptPlayer.getPlayer();
      if (owner == null) {
        continue;
      }
      J.runEntity(owner, () -> fetchForOwner(owner));
    }
  }

  private void fetchForOwner(Player owner) {
    if (!owner.isOnline()) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    UUID ownerId = owner.getUniqueId();
    int wolfCount = countNearbyWolves(owner, ownerId);
    if (wolfCount <= 0) {
      return;
    }

    Location origin = owner.getLocation();
    if (origin.getWorld() == null) {
      return;
    }

    double range = getFetchRange(level);
    double carryRate = getCarryRate(level);
    int maxThisTick = Math.min(wolfCount, getCarryPerTickLimit());
    int fetched = 0;
    List<Item> items = origin.getWorld().getNearbyEntitiesByType(Item.class, origin, range, range, range)
        .stream().toList();
    for (Item item : items) {
      if (fetched >= maxThisTick) {
        break;
      }
      if (!isFetchable(item, ownerId)) {
        continue;
      }
      if (Math.random() > carryRate) {
        continue;
      }

      fetched++;
      Location deliverTo = origin.clone();
      J.runEntity(item, () -> deliverItem(owner, ownerId, deliverTo, item));
    }
  }

  private int countNearbyWolves(Player owner, UUID ownerId) {
    int limit = getWolfLimit();
    double radius = Math.max(1.0, getConfig().wolfSearchRadius);
    int count = 0;
    for (Entity entity : owner.getNearbyEntities(radius, radius, radius)) {
      if (entity instanceof Wolf wolf && wolf.isValid() && !wolf.isDead() && wolf.isTamed() && isOwnedBy(wolf, ownerId)) {
        count++;
        if (count >= limit) {
          break;
        }
      }
    }
    return count;
  }

  private void deliverItem(Player owner, UUID ownerId, Location ownerLocation, Item item) {
    if (!isFetchable(item, ownerId)) {
      return;
    }

    Location from = item.getLocation().clone();
    item.setPickupDelay(0);
    item.setOwner(ownerId);
    Location to = ownerLocation.clone().add(0, 0.5, 0);
    if (from.getWorld() != null && from.getWorld() == to.getWorld()) {
      fx(from, FxPriority.TRAIL)
          .line(Particles.ENCHANTMENT_TABLE, to.getX(), to.getY(), to.getZ(), 6)
          .sound(Sound.ENTITY_ITEM_PICKUP, 0.45F, 1.7F);
    }

    if (!J.teleport(item, to)) {
      return;
    }
    J.runEntity(owner, () -> creditFetch(owner));
  }

  private void creditFetch(Player owner) {
    if (!owner.isOnline()) {
      return;
    }
    addStat(owner, "taming.fetch.items-fetched", 1);
    xp(owner, getConfig().xpPerItemFetched);
  }

  private boolean isFetchable(Item item, UUID ownerId) {
    if (item == null || !item.isValid() || item.isDead() || item.getPickupDelay() >= Short.MAX_VALUE) {
      return false;
    }
    UUID owner = item.getOwner();
    if (owner != null && !owner.equals(ownerId)) {
      return false;
    }
    if (item.hasMetadata("NPC") || item.hasMetadata("shopitem") || item.hasMetadata("hologram")) {
      return false;
    }
    return item.canPlayerPickup();
  }

  private boolean isOwnedBy(Tameable tameable, UUID ownerId) {
    AnimalTamer owner = tameable.getOwner();
    return owner != null && ownerId.equals(owner.getUniqueId());
  }

  private double getFetchRange(int level) {
    return getConfig().fetchRangeBase + (getLevelPercent(level) * getConfig().fetchRangeFactor);
  }

  private double getCarryRate(int level) {
    return Math.min(getConfig().maxCarryRate, getConfig().carryRateBase + (getLevelPercent(level) * getConfig().carryRateFactor));
  }

  private int getWolfLimit() {
    return Math.max(1, Math.min(getConfig().maxWolves, HARD_MAX_WOLVES));
  }

  private int getCarryPerTickLimit() {
    return Math.max(1, Math.min(getConfig().maxCarryPerTick, HARD_MAX_CARRY_PER_TICK));
  }


  @ConfigDescription("Your tamed wolves gather nearby dropped items and bring them straight to you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fetch Range Base for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fetchRangeBase = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fetch Range Factor for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fetchRangeFactor = 10.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Carry Rate Base for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double carryRateBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Carry Rate Factor for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double carryRateFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Carry Rate for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxCarryRate = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wolf Search Radius for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double wolfSearchRadius = 24.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time a wolf fetches a dropped item.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerItemFetched = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum tamed wolves counted around the owner, capped internally at 12.", impact = "Higher values let larger packs fetch more items per pass but scan more entities.")
    int maxWolves = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum dropped items fetched per owner each pass, capped internally at 8.", impact = "Lower values reduce item scheduling in dense drops.")
    int maxCarryPerTick = 4;

    public Config() {
      baseCost = 3;
      costFactor = 0.4;
      initialCost = 3;
    }
  }
}
