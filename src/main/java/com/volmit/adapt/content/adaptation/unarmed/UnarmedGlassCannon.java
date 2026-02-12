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

package com.volmit.adapt.content.adaptation.unarmed;

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
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class UnarmedGlassCannon extends SimpleAdaptation<UnarmedGlassCannon.Config> {
    public UnarmedGlassCannon() {
        super("unarmed-glass-cannon");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("unarmed.glass_cannon.description"));
        setDisplayName(Localizer.dLocalize("unarmed.glass_cannon.name"));
        setIcon(Material.DISC_FRAGMENT_5);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(4544);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + (getConfig().maxDamageFactor + (level * getConfig().maxDamagePerLevelMultiplier)) + C.GRAY + " " + Localizer.dLocalize("unarmed.glass_cannon.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(level * getConfig().perLevelBonusMultiplier) + C.GRAY + " " + Localizer.dLocalize("unarmed.glass_cannon.lore2"));
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p) {
            if (!hasAdaptation(p)) {
                return;
            }


            if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
                return;
            }

            double armor = getArmorValue(p);
            double damage = e.getDamage();

            if (armor == 0) {
                e.setDamage(damage * getConfig().maxDamageFactor);
            } else {
                e.setDamage(damage - (damage * armor));
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
    @ConfigDescription("Bonus unarmed damage the lower your armor value is.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.425;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Per Level Bonus Multiplier for the Unarmed Glass Cannon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double perLevelBonusMultiplier = 0.25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Damage Factor for the Unarmed Glass Cannon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxDamageFactor = 4.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Damage Per Level Multiplier for the Unarmed Glass Cannon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxDamagePerLevelMultiplier = 0.15;
    }

}
