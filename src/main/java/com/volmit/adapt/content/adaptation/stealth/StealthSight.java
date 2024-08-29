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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class StealthSight extends SimpleAdaptation<StealthSight.Config> {
    private final List<Player> sneaking;


    public StealthSight() {
        super("stealth-vision");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("stealth", "nightvision", "description"));
        setDisplayName(Localizer.dLocalize("stealth", "nightvision", "name"));
        setIcon(Material.POTION);
        setBaseCost(getConfig().baseCost);
        setInterval(1500);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        sneaking = new ArrayList<>();

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("stealth", "nightvision", "lore1") + C.GREEN + Localizer.dLocalize("stealth", "nightvision", "lore2") + C.GRAY + Localizer.dLocalize("stealth", "nightvision", "lore3"));
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        if (!hasAdaptation(p)) {
            return;
        }
        sneaking.add(p);
        if (!p.isSneaking()) {
            sp.play(p.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 1, 0.99f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 1000, 0, false, false));
        } else {
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }


    @Override
    public void onTick() {
        List<Player> toRemove = new ArrayList<>();
        for (Player p : sneaking) {
            if (hasAdaptation(p) && !p.isSneaking()) {
                toRemove.add(p);
                J.s(() -> p.removePotionEffect(PotionEffectType.NIGHT_VISION));
            }
        }
        sneaking.removeAll(toRemove);
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
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 2;
        int initialCost = 5;
        double costFactor = 0.6;
        int maxLevel = 1;
    }
}
