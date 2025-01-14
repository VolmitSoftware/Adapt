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
import com.volmit.adapt.util.Localizer;
import de.slikey.effectlib.effect.BleedEffect;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class SwordsPoisonedBlade extends SimpleAdaptation<SwordsPoisonedBlade.Config> {
    private final Map<Player, Long> cooldowns;

    public SwordsPoisonedBlade() {
        super("sword-poison-blade");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("sword", "poisonedblade", "description"));
        setDisplayName(Localizer.dLocalize("sword", "poisonedblade", "name"));
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
        v.addLore(C.GREEN + "+ " + C.GRAY + " " + Localizer.dLocalize("sword", "poisonedblade", "lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getDurationOfEffect(level), 1) + C.GRAY + " " + Localizer.dLocalize("sword", "poisonedblade", "lore2"));
        v.addLore(C.RED + "* " + Form.duration(getCooldown(level), 1) + C.GRAY + " " + Localizer.dLocalize("sword", "poisonedblade", "lore3"));
    }

    public long getCooldown(int level) {
        return getConfig().cooldown * level;
    }

    public long getDurationOfEffect(int level) {
        return getConfig().effectDuration * level;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && hasAdaptation(p) && ItemListings.getToolSwords().contains(p.getInventory().getItemInMainHand().getType())) {
            Long cooldown = cooldowns.get(p);
            if (cooldown != null && cooldown > System.currentTimeMillis())
                return;
            Entity victim = e.getEntity();
            cooldowns.put(p, System.currentTimeMillis() + getCooldown(getLevel(p)));
            if (victim instanceof Player pvic) {
                if (canPVP(p, pvic.getLocation())) return;
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
                if (canPVE(p, victim.getLocation())) return;
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
        return !getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        public final long cooldown = 5000;
        public final long effectDuration = 1000;
        final boolean permanent = false;
        final boolean enabled = true;
        final int baseCost = 7;
        final int maxLevel = 7;
        final int initialCost = 7;
        final double costFactor = 0.325;
    }
}
