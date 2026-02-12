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
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.config.ConfigDescription;
import com.volmit.adapt.util.reflect.registries.Attributes;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.Collection;
import java.util.UUID;

public class TamingDamage extends SimpleAdaptation<TamingDamage.Config> {
    private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-damage-boost".getBytes());
    private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString( "adapt:tame-damage-boost");

    public TamingDamage() {
        super("tame-damage");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("taming.damage.description"));
        setDisplayName(Localizer.dLocalize("taming.damage.name"));
        setIcon(Material.FLINT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(6119);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDamageBoost(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.damage.lore1"));
    }

    private double getDamageBoost(int level) {
        return ((getLevelPercent(level) * getConfig().damageFactor) + getConfig().baseDamage);
    }

    @Override
    public void onTick() {
        for (World world : Bukkit.getServer().getWorlds()) {
            Collection<Tameable> tameables = world.getEntitiesByClass(Tameable.class);
            for (Tameable tameable : tameables) {
                if (tameable.isTamed() && tameable.getOwner() instanceof Player p) {
                    update(tameable, getLevel(p));
                }
            }
        }
    }

    private void update(Tameable j, int level) {
        var attribute = Version.get().getAttribute(j, Attributes.GENERIC_ATTACK_DAMAGE);
        if (attribute == null) return;
        attribute.removeModifier(MODIFIER, MODIFIER_KEY);

        if (level > 0) {
            attribute.addModifier(MODIFIER, MODIFIER_KEY, getDamageBoost(level), AttributeModifier.Operation.ADD_SCALAR);
        }
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
    @ConfigDescription("Increase your tamed animal damage dealt.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Taming Damage adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseDamage = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Taming Damage adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageFactor = 0.65;
    }
}
