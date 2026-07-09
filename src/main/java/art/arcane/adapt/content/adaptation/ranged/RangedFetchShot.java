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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

public class RangedFetchShot extends SimpleAdaptation<RangedFetchShot.Config> {

  public RangedFetchShot() {
    super("ranged-fetch-shot");
    registerConfiguration(Config.class);
    setIcon(Material.FISHING_ROD);
    setInterval(2751);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HOPPER)
        .key("challenge_ranged_fetch_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHEST)
            .key("challenge_ranged_fetch_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_fetch_500", "ranged.fetch-shot.items-fetched", 500, 400);
    registerMilestone("challenge_ranged_fetch_5k", "ranged.fetch-shot.items-fetched", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    Projectile projectile = e.getEntity();
    if (projectile instanceof FishHook || !(projectile.getShooter() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Location impact = projectile.getLocation();
    if (!canAccessChest(p, impact)) {
      return;
    }

    double radius = getRadius(level);
    int fetched = 0;
    int fxBudget = 3;
    for (Entity nearby : projectile.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
      if (!(nearby instanceof Item item) || !canSnatchItem(p, item)) {
        continue;
      }

      ItemStack is = item.getItemStack().clone();
      Location itemLocation = item.getLocation();
      if (!safeGiveItem(p, item, is)) {
        continue;
      }

      fetched++;
      addStat(p, "ranged.fetch-shot.items-fetched", 1);
      if (fxBudget-- > 0 && itemLocation.getWorld().equals(p.getWorld())) {
        Location eye = p.getEyeLocation();
        fx(itemLocation, FxPriority.TRAIL)
            .line(Particles.ENCHANTMENT_TABLE, eye.getX(), eye.getY(), eye.getZ(), 5)
            .chord(Sound.ENTITY_ITEM_PICKUP, 0.6F, 1.5F, Sound.ENTITY_ARROW_HIT_PLAYER, 0.3F, 1.8F);
      }
    }

    if (fetched > 0) {
      xp(p, fetched * getConfig().xpPerItemFetched);
    }
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Shoot dropped items with projectiles to pull them straight into your inventory.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Ranged Fetch Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 1.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Ranged Fetch Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Item Fetched for the Ranged Fetch Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerItemFetched = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.3;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
