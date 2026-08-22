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

package art.arcane.adapt.content.adaptation.crafting;

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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class CraftingProvisioner extends SimpleAdaptation<CraftingProvisioner.Config> {

  public CraftingProvisioner() {
    super("crafting-provisioner");
    registerConfiguration(Config.class);
    setIcon(Material.COOKED_BEEF);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BREAD)
        .key("challenge_crafting_provisioner_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CAKE)
            .key("challenge_crafting_provisioner_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_crafting_provisioner_500", "crafting.provisioner.bonus-portions", 500, 400);
    registerMilestone("challenge_crafting_provisioner_5k", "crafting.provisioner.bonus-portions", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getBonusChance(level), 0), 1);
    statLore(v, getBonusPortions(level), 2);
  }

  static double bonusChance(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  static int bonusPortions(int base, double factor, double levelPercent) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  private double getBonusChance(int level) {
    return bonusChance(getConfig().bonusChanceBase, getConfig().bonusChanceFactor, getConfig().bonusChanceMax, getLevelPercent(level));
  }

  private int getBonusPortions(int level) {
    return bonusPortions(getConfig().bonusPortionsBase, getConfig().bonusPortionsFactor, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Recipe recipe = e.getRecipe();
    if (recipe == null) {
      return;
    }

    ItemStack result = recipe.getResult();
    if (result == null || !result.getType().isEdible()) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getBonusChance(level)) {
      return;
    }

    int portions = getBonusPortions(level);
    Material food = result.getType();
    J.runEntity(p, () -> giveBonusFood(p, food, portions));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(FurnaceSmeltEvent e) {
    ItemStack result = e.getResult();
    if (result == null || !result.getType().isEdible()) {
      return;
    }

    Location furnace = e.getBlock().getLocation();
    Player beneficiary = nearestEligiblePlayer(furnace);
    if (beneficiary == null) {
      return;
    }

    int level = getActiveLevel(beneficiary);
    if (ThreadLocalRandom.current().nextDouble() > getBonusChance(level)) {
      return;
    }

    int portions = getBonusPortions(level);
    ItemStack boosted = result.clone();
    boosted.setAmount(Math.min(boosted.getMaxStackSize(), boosted.getAmount() + portions));
    int granted = boosted.getAmount() - result.getAmount();
    if (granted <= 0) {
      return;
    }
    e.setResult(boosted);
    J.runEntity(beneficiary, () -> addStat(beneficiary, "crafting.provisioner.bonus-portions", granted));
    Location above = furnace.add(0.5D, 1.0D, 0.5D);
    fx(above, FxPriority.AMBIENT)
        .particle(Particles.CRIT_MAGIC, 4, 0, 0.2D, 0, 0.2D, 0.15D)
        .sound(Sound.ENTITY_GENERIC_EAT, 0.4F, 1.4F);
  }

  private Player nearestEligiblePlayer(Location furnace) {
    double radius = getConfig().cookingRadius;
    double radiusSq = radius * radius;
    Player nearest = null;
    double nearestSq = Double.MAX_VALUE;
    for (Player player : furnace.getWorld().getPlayers()) {
      double distanceSq = player.getLocation().distanceSquared(furnace);
      if (distanceSq > radiusSq || distanceSq >= nearestSq || getActiveLevel(player) <= 0) {
        continue;
      }
      nearest = player;
      nearestSq = distanceSq;
    }
    return nearest;
  }

  private void giveBonusFood(Player p, Material food, int portions) {
    if (!p.isOnline() || getActiveLevel(p) <= 0) {
      return;
    }

    ItemStack give = new ItemStack(food, portions);
    Map<Integer, ItemStack> leftovers = p.getInventory().addItem(give);
    for (ItemStack leftover : leftovers.values()) {
      if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
        p.getWorld().dropItemNaturally(p.getLocation(), leftover);
      }
    }

    addStat(p, "crafting.provisioner.bonus-portions", portions);
    fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
        .particle(Particles.CRIT_MAGIC, 4, 0, 0.2D, 0, 0.2D, 0.15D)
        .sound(Sound.ENTITY_GENERIC_EAT, 0.4F, 1.5F);
  }

  @ConfigDescription("Crafting or cooking food has a chance to yield bonus portions.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus portion chance at level 1.", impact = "Higher values grant bonus food more often at low levels.")
    double bonusChanceBase = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus portion chance gained across levels.", impact = "Higher values scale the bonus chance more steeply.")
    double bonusChanceFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum bonus portion chance at full level.", impact = "Higher values raise the ceiling on bonus chance.")
    double bonusChanceMax = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus portions granted at level 1.", impact = "Higher values grant more food per proc even at low levels.")
    int bonusPortionsBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus portions gained across levels.", impact = "Higher values grant more food per proc at higher levels.")
    double bonusPortionsFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius in blocks searched for a player when cooking food.", impact = "Higher values credit players farther from the furnace.")
    double cookingRadius = 8.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.3;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
