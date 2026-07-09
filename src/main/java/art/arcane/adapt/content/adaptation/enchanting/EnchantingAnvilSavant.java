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

package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;

import java.lang.reflect.Method;

public class EnchantingAnvilSavant extends SimpleAdaptation<EnchantingAnvilSavant.Config> {
  private static volatile Method getRepairCostMethod;
  private static volatile Method setRepairCostMethod;
  private final Cooldowns actionbarCooldown = cooldowns();

  public EnchantingAnvilSavant() {
    super("enchanting-anvil-savant");
    registerConfiguration(Config.class);
    setIcon(Material.ANVIL);
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ANVIL)
        .key("challenge_enchanting_anvil_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ANVIL)
            .key("challenge_enchanting_anvil_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_anvil_200", "enchanting.anvil-savant.levels-saved", 200, 400);
    registerMilestone("challenge_enchanting_anvil_5k", "enchanting.anvil-savant.levels-saved", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getCostReduction(level), 0), 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareAnvilEvent e) {
    if (!(e.getView().getPlayer() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!(e.getInventory() instanceof AnvilInventory inventory)) {
      return;
    }

    Integer current = readRepairCost(inventory);
    if (current == null || current <= 0) {
      return;
    }

    int reduced = Math.max(getConfig().minimumCost, (int) Math.ceil(current * (1D - getCostReduction(level))));
    writeRepairCost(inventory, reduced);
    int saved = current - reduced;
    if (saved > 0) {
      addStat(p, "enchanting.anvil-savant.levels-saved", saved);
      if (actionbarCooldown.isReady(p.getUniqueId(), 350L)) {
        actionbarCooldown.mark(p.getUniqueId());
        Adapt.actionbar(p, C.GREEN + "- " + saved + " " + Localizer.dLocalize("enchanting.anvil_savant.saved"));
      }
    }
  }

  private Integer readRepairCost(AnvilInventory inventory) {
    try {
      Method method = getRepairCostMethod;
      if (method == null) {
        method = inventory.getClass().getMethod("getRepairCost");
        getRepairCostMethod = method;
      }

      Object value = method.invoke(inventory);
      if (value instanceof Number number) {
        return number.intValue();
      }
    } catch (Throwable ignored) {

    }

    return null;
  }

  private void writeRepairCost(AnvilInventory inventory, int cost) {
    try {
      Method method = setRepairCostMethod;
      if (method == null) {
        method = inventory.getClass().getMethod("setRepairCost", int.class);
        setRepairCostMethod = method;
      }

      method.invoke(inventory, cost);
    } catch (Throwable ignored) {

    }
  }

  private double getCostReduction(int level) {
    return Math.min(getConfig().maximumReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Reduce anvil XP cost when combining, repairing, and renaming.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Base for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Factor for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionFactor = 0.37;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Maximum Reduction for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maximumReduction = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Cost for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minimumCost = 1;

    public Config() {
      baseCost = 5;
      costFactor = 0.8;
      maxLevel = 4;
      initialCost = 5;
    }
  }
}
