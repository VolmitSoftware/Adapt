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

package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class StealthSight extends SimpleAdaptation<StealthSight.Config> {
    public StealthSight() {
        super("stealth-vision");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Stealth","StealthNightVision", "Description"));
        setDisplayName(Adapt.dLocalize("Stealth","StealthNightVision", "Name"));
        setIcon(Material.POTION);
        setBaseCost(getConfig().baseCost);
        setInterval(5252);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("Stealth","StealthNightVision", "Lore1") + C.GREEN + Adapt.dLocalize("Stealth","StealthNightVision", "Lore2") + C.GRAY + Adapt.dLocalize("Stealth","StealthNightVision", "Lore3"));
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        if (!e.getPlayer().isSneaking()) {
            Player p = e.getPlayer();
            p.playSound(p.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 1, 0.99f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20000, 0, false, false));
        } else {
            e.getPlayer().removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 2;
        int initialCost = 5;
        double costFactor = 0.6;
        double factor = 1.25;
        int maxLevel = 1;
    }
}
