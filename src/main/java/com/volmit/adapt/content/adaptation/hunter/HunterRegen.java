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
import com.volmit.adapt.util.config.ConfigDescription;
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
        setDescription(Localizer.dLocalize("hunter.regen.description"));
        setDisplayName(Localizer.dLocalize("hunter.regen.name"));
        setIcon(Material.AXOLOTL_BUCKET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(9744);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("hunter.regen.lore1"));
        v.addLore(C.GREEN + "+ " + level + C.GRAY + Localizer.dLocalize("hunter.regen.lore2"));
        v.addLore(C.RED + "- " + (getConfig().basePoisonFromLevel - level) + C.GRAY + Localizer.dLocalize("hunter.regen.lore3"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + " " + Localizer.dLocalize("hunter.regen.lore4"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + " " + Localizer.dLocalize("hunter.regen.lore5"));
        v.addLore(C.GRAY + "- " + level + C.RED + " " + Localizer.dLocalize("hunter.penalty.lore1"));

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
                if (getConfig().consumable != null && Material.getMaterial(getConfig().consumable) != null) {
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
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Gain regeneration when struck, at the cost of hunger.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Use Consumable for the Hunter Regen adaptation.", impact = "True enables this behavior and false disables it.")
        boolean useConsumable = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Poison Penalty for the Hunter Regen adaptation.", impact = "True enables this behavior and false disables it.")
        boolean poisonPenalty = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stack Hunger Penalty for the Hunter Regen adaptation.", impact = "True enables this behavior and false disables it.")
        boolean stackHungerPenalty = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stack Poison Penalty for the Hunter Regen adaptation.", impact = "True enables this behavior and false disables it.")
        boolean stackPoisonPenalty = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stack Buff for the Hunter Regen adaptation.", impact = "True enables this behavior and false disables it.")
        boolean stackBuff = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Effectby Level for the Hunter Regen adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int baseEffectbyLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Hunger From Level for the Hunter Regen adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int baseHungerFromLevel = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Hunger Duration for the Hunter Regen adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int baseHungerDuration = 50;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Poison From Level for the Hunter Regen adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int basePoisonFromLevel = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Consumable for the Hunter Regen adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
        String consumable = "ROTTEN_FLESH";
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.4;
    }
}
