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

package com.volmit.adapt.content.adaptation.sword;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.adaptation.sword.effects.DamagingBleedEffect;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import de.slikey.effectlib.effect.BleedEffect;
import io.netty.channel.Channel;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class SwordsPoisonedBlade extends SimpleAdaptation<SwordsPoisonedBlade.Config> {
    private final Map<Player, Long> cooldowns;

    public SwordsPoisonedBlade() {
        super("sword-poison-blade");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Sword", "PoisonedBlade", "Description"));
        setDisplayName(Adapt.dLocalize("Sword", "PoisonedBlade", "Name"));
        setIcon(Material.GREEN_DYE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(4984);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + " " + Adapt.dLocalize("Sword", "PoisonedBlade", "Lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getDurationOfEffect(level), 1) + C.GRAY + " " + Adapt.dLocalize("Sword", "PoisonedBlade", "Lore2"));
        v.addLore(C.RED + "* " + Form.duration(getCooldown(level), 1) + C.GRAY + " " + Adapt.dLocalize("Sword", "PoisonedBlade", "Lore3"));
    }

    public long getCooldown(int level) {
        return getConfig().cooldown * level;
    }

    public long getDurationOfEffect(int level) {
        return getConfig().effectDuration * level;
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && hasAdaptation(p) && ItemListings.getToolSwords().contains(p.getInventory().getItemInMainHand().getType())) {
            if (cooldowns.containsKey(p)) {
                if (cooldowns.get(p) > System.currentTimeMillis()) {
                    return;
                } else {
                    cooldowns.remove(p);
                }
            }
            Entity victim = e.getEntity();
            cooldowns.put(p, System.currentTimeMillis() + getCooldown(getLevel(p)));
            if (victim instanceof Player pvic) {
                BleedEffect blood = new BleedEffect(Adapt.instance.adaptEffectManager);
                blood.setEntity(pvic);
                blood.material = Material.LARGE_FERN;
                blood.height = -1;
                blood.iterations = Math.toIntExact(2 * (3 + (getDurationOfEffect(getLevel(p)) / 1000)));
                blood.period = 5; //5 Every second, make a proc
                blood.hurt = false;
                blood.start();
                addPotionStacks(pvic, PotionEffectType.POISON, 2, 50 * getLevel(p), true);
            } else {
                BleedEffect blood = victim instanceof LivingEntity l ? new DamagingBleedEffect(Adapt.instance.adaptEffectManager, 1, l) : new BleedEffect(Adapt.instance.adaptEffectManager);
                blood.setEntity(victim);
                blood.material = Material.LARGE_FERN;
                blood.height = -1;
                blood.iterations = Math.toIntExact(2 * (3 + (getDurationOfEffect(getLevel(p)) / 1000)));
                blood.period = 5; //5 Every second, make a proc
                blood.hurt = false;
                blood.start();
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

    @NoArgsConstructor
    protected static class Config {
        public long cooldown = 5000;
        public long effectDuration = 1000;
        boolean enabled = true;
        int baseCost = 7;
        int maxLevel = 7;
        int initialCost = 7;
        double costFactor = 0.325;
    }
}

