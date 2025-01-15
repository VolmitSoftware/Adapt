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

package com.volmit.adapt.content.adaptation.hunter;

import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

public class HunterRegen extends SimpleAdaptation<HunterRegen.Config> {
    public HunterRegen() {
        super("hunter-regen");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("hunter", "regen", "description"));
        setDisplayName(Localizer.dLocalize("hunter", "regen", "name"));
        setIcon(Material.AXOLOTL_BUCKET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(9744);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("hunter", "regen", "lore1"));
        v.addLore(C.GREEN + "+ " + level + C.GRAY + Localizer.dLocalize("hunter", "regen", "lore2"));
        v.addLore(C.RED + "- " + (5 + level) + C.GRAY + Localizer.dLocalize("hunter", "regen", "lore3"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + " " + Localizer.dLocalize("hunter", "regen", "lore4"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + " " + Localizer.dLocalize("hunter", "regen", "lore5"));
        v.addLore(C.GRAY + "- " + level + C.RED + " " + Localizer.dLocalize("hunter", "penalty", "lore1"));

    }


    @EventHandler
    public void on(EntityDamageEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof org.bukkit.entity.Player p && isAdaptableDamageCause(e) && hasAdaptation(p)) {
            if (AdaptConfig.get().isPreventHunterSkillsWhenHungerApplied() && p.hasPotionEffect(PotionEffectType.HUNGER)) {
                return;
            }

            if (!getConfig().useConsumable) {
                if (p.getFoodLevel() == 0) {
                    if (getConfig().poisonPenalty) {
                        addPotionStacks(p, PotionEffectType.POISON, getConfig().basePoisonFromLevel - getLevel(p), getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
                    }

                } else {
                    addPotionStacks(p, PotionEffectType.HUNGER, getConfig().baseHungerFromLevel - getLevel(p), getConfig().baseHungerDuration * getLevel(p), getConfig().stackHungerPenalty);
                    addPotionStacks(p, PotionEffectType.REGENERATION, getLevel(p), getConfig().baseEffectbyLevel * getLevel(p), getConfig().stackBuff);
                }
            } else {
                getConfig();
                if (Material.getMaterial(getConfig().consumable) != null) {
                    Material mat = Material.getMaterial(getConfig().consumable);
                    if (mat != null && p.getInventory().contains(mat)) {
                        p.getInventory().removeItem(new ItemStack(mat, 1));
                        addPotionStacks(p, PotionEffectType.REGENERATION, getLevel(p), getConfig().baseEffectbyLevel * getLevel(p), getConfig().stackBuff);
                    } else {
                        if (getConfig().poisonPenalty) {
                            addPotionStacks(p, PotionEffectType.POISON, getConfig().basePoisonFromLevel - getLevel(p), getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return !getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        final boolean permanent = false;
        final boolean enabled = true;
        final boolean useConsumable = false;
        final boolean poisonPenalty = true;
        final boolean stackHungerPenalty = false;
        final boolean stackPoisonPenalty = false;
        final boolean stackBuff = false;
        final int baseEffectbyLevel = 5;
        final int baseHungerFromLevel = 10;
        final int baseHungerDuration = 50;
        final int basePoisonFromLevel = 6;
        final String consumable = "ROTTEN_FLESH";
        final int baseCost = 4;
        final int maxLevel = 5;
        final int initialCost = 8;
        final double costFactor = 0.4;
    }
}
