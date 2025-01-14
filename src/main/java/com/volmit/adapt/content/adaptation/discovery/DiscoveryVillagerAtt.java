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

package com.volmit.adapt.content.adaptation.discovery;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import de.slikey.effectlib.effect.BleedEffect;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class DiscoveryVillagerAtt extends SimpleAdaptation<DiscoveryVillagerAtt.Config> {
    public DiscoveryVillagerAtt() {
        super("discovery-villager-att");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery", "villager", "description"));
        setDisplayName(Localizer.dLocalize("discovery", "villager", "name"));
        setIcon(Material.GLASS_BOTTLE);
        setInterval(5832);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("discovery", "villager", "lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("discovery", "villager", "lore2"));
        v.addLore(C.GREEN + "+ " + getXpTaken(level) + " " + C.GRAY + Localizer.dLocalize("discovery", "villager", "lore3"));
    }

    private double getEffectiveness(double multiplier) {
        return Math.min(getConfig().maxEffectiveness, multiplier * multiplier + getConfig().effectivenessBase);
    }

    private int getXpTaken(double level) {
        double d = (getConfig().levelCostAdd * getConfig().amplifier) - (level * getConfig().levelDrain);
        return (int) d;
    }

    @EventHandler
    public void on(PlayerInteractEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        if (e.getRightClicked() instanceof Villager v && hasAdaptation(p)) {
            Random r = new Random();
            if (r.nextDouble() <= getEffectiveness(getLevelPercent(getLevel(p)))) {
                if (p.getLevel() - getXpTaken(getLevel(p)) > 0) {
                    BleedEffect blood = new BleedEffect(Adapt.instance.adaptEffectManager);  // Enemy gets blood
                    blood.material = Material.EMERALD;
                    blood.setEntity(v);
                    p.setLevel((p.getLevel() - getXpTaken(getLevel(p))));
                    sp.play(p.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 1f);
                    sp.play(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 10, getLevel(p), true, true));
                } else {
                    BleedEffect blood = new BleedEffect(Adapt.instance.adaptEffectManager);  // Enemy gets blood
                    blood.material = Material.STONE;
                    v.shakeHead();
                    blood.setEntity(v);
                    sp.play(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
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
        final int baseCost = 1;
        final int initialCost = 5;
        final double costFactor = 0.01;
        final int maxLevel = 5;
        final double effectivenessBase = 0.005;
        final double maxEffectiveness = 100;
        final int levelDrain = 2;
        final int levelCostAdd = 10;
        final double amplifier = 1.0;
    }
}
