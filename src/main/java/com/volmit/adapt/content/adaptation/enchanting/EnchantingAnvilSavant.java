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

package com.volmit.adapt.content.adaptation.enchanting;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;

public class EnchantingAnvilSavant extends SimpleAdaptation<EnchantingAnvilSavant.Config> {
    public EnchantingAnvilSavant() {
        super("enchanting-anvil-savant");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("enchanting.anvil_savant.description"));
        setDisplayName(Localizer.dLocalize("enchanting.anvil_savant.name"));
        setIcon(Material.ANVIL);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2200);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getCostReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("enchanting.anvil_savant.lore1"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PrepareAnvilEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        if (!(e.getInventory() instanceof AnvilInventory inventory)) {
            return;
        }

        Integer current = readRepairCost(inventory);
        if (current == null || current <= 0) {
            return;
        }

        int reduced = Math.max(getConfig().minimumCost, (int) Math.ceil(current * (1D - getCostReduction(getLevel(p)))));
        writeRepairCost(inventory, reduced);
    }

    private Integer readRepairCost(AnvilInventory inventory) {
        try {
            Object value = inventory.getClass().getMethod("getRepairCost").invoke(inventory);
            if (value instanceof Number number) {
                return number.intValue();
            }
        } catch (Throwable ignored) {

        }

        return null;
    }

    private void writeRepairCost(AnvilInventory inventory, int cost) {
        try {
            inventory.getClass().getMethod("setRepairCost", int.class).invoke(inventory, cost);
        } catch (Throwable ignored) {

        }
    }

    private double getCostReduction(int level) {
        return Math.min(getConfig().maximumReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Reduce anvil XP cost when combining, repairing, and renaming.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reduction Base for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reductionBase = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reduction Factor for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reductionFactor = 0.37;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Maximum Reduction for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maximumReduction = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Minimum Cost for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int minimumCost = 1;
    }
}
