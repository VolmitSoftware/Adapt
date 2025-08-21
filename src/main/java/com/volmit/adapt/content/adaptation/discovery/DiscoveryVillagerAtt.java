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
import com.volmit.adapt.util.collection.KMap;
import de.slikey.effectlib.effect.BleedEffect;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.UUID;

public class DiscoveryVillagerAtt extends SimpleAdaptation<DiscoveryVillagerAtt.Config> {
    private final KMap<UUID, Integer> active = new KMap<>();

    public DiscoveryVillagerAtt() {
        super("discovery-villager-att");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery", "villager", "description"));
        setDisplayName(Localizer.dLocalize("discovery", "villager", "name"));
        setIcon(Material.GLASS_BOTTLE);
        setInterval(2432);
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

    @EventHandler(priority = EventPriority.MONITOR)
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
                    int level = getLevel(p);
                    active.put(p.getUniqueId(), level);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, level, true, true));
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) {
            return;
        }
        int level = active.getOrDefault(p.getUniqueId(), 0);
        if (level == 0) return;

        if (event.isCancelled()) {
            active.remove(p.getUniqueId());
            p.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
        } else {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, level, true, true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p) || !active.containsKey(p.getUniqueId())) {
            return;
        }

        active.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
    }

    @EventHandler
    public void on(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onTick() {
        J.s(() -> active.forEach((p, lvl) -> {
            var player = Bukkit.getPlayer(p);
            if (player == null) return;
            player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, lvl, true, true));
        }));
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
        int baseCost = 1;
        int initialCost = 5;
        double costFactor = 0.01;
        int maxLevel = 5;
        double effectivenessBase = 0.005;
        double maxEffectiveness = 100;
        int levelDrain = 2;
        int levelCostAdd = 10;
        double amplifier = 1.0;
    }
}
