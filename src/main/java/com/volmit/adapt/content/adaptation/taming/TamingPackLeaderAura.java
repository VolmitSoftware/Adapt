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

package com.volmit.adapt.content.adaptation.taming;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;

public class TamingPackLeaderAura extends SimpleAdaptation<TamingPackLeaderAura.Config> {
    public TamingPackLeaderAura() {
        super("tame-pack-leader-aura");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("taming.pack_leader_aura.description"));
        setDisplayName(Localizer.dLocalize("taming.pack_leader_aura.name"));
        setIcon(Material.BONE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(30);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + C.GRAY + " " + Localizer.dLocalize("taming.pack_leader_aura.lore1"));
        v.addLore(C.GREEN + "+ " + (1 + getAmplifier(level)) + C.GRAY + " " + Localizer.dLocalize("taming.pack_leader_aura.lore2"));
    }

    @Override
    public void onTick() {
        if (!Bukkit.isPrimaryThread()) {
            J.s(this::onTick);
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            Collection<Tameable> tameables = world.getEntitiesByClass(Tameable.class);
            for (Tameable tameable : tameables) {
                if (!tameable.isTamed() || !(tameable.getOwner() instanceof Player owner)) {
                    continue;
                }

                if (!hasAdaptation(owner)) {
                    continue;
                }

                int level = getLevel(owner);
                if (level <= 0) {
                    continue;
                }

                if (owner.getWorld() != tameable.getWorld()) {
                    continue;
                }

                double radius = getRadius(level);
                if (owner.getLocation().distanceSquared(tameable.getLocation()) > (radius * radius)) {
                    continue;
                }

                int amplifier = getAmplifier(level);
                tameable.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, getConfig().effectTicks, amplifier, false, false));
                tameable.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, getConfig().effectTicks, amplifier, false, false));
            }
        }
    }

    private double getRadius(int level) {
        return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    }

    private int getAmplifier(int level) {
        return Math.max(0, (int) Math.floor(getLevelPercent(level) * getConfig().maxAmplifier));
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
    @ConfigDescription("Nearby tamed companions gain speed and regeneration near their owner.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusBase = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 14;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Amplifier for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxAmplifier = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Effect Ticks for the Taming Pack Leader Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int effectTicks = 80;
    }
}
