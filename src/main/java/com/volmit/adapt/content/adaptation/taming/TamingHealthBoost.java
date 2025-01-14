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
import com.volmit.adapt.util.reflect.enums.Attributes;
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

public class TamingHealthBoost extends SimpleAdaptation<TamingHealthBoost.Config> {
    private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-tame-health-boost".getBytes());
    private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString( "adapt:tame-health-boost");

    public TamingHealthBoost() {
        super("tame-health");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("taming", "health", "description"));
        setDisplayName(Localizer.dLocalize("taming", "health", "name"));
        setIcon(Material.COOKED_BEEF);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(4753);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getHealthBoost(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming", "health", "lore1"));
    }

    private double getHealthBoost(int level) {
        return ((getLevelPercent(level) * getConfig().healthBoostFactor) + getConfig().healthBoostBase);
    }

    @Override
    public void onTick() {
        for (World i : Bukkit.getServer().getWorlds()) {
            J.s(() -> {
                Collection<Tameable> gl = i.getEntitiesByClass(Tameable.class);

                J.a(() -> {
                    for (Tameable j : gl) {
                        if (j.isTamed() && j.getOwner() instanceof Player p) {
                            update(j, getLevel(p));
                        }
                    }
                });
            });
        }
    }

    private void update(Tameable j, int level) {
        var attribute = Version.get().getAttribute(j, Attributes.GENERIC_MAX_HEALTH);
        if (attribute == null) return;
        attribute.removeModifier(MODIFIER, MODIFIER_KEY);

        if (level > 0) {
            attribute.addModifier(MODIFIER, MODIFIER_KEY, getHealthBoost(level), AttributeModifier.Operation.ADD_SCALAR);
        }
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
        final int baseCost = 6;
        final int maxLevel = 5;
        final int initialCost = 3;
        final double costFactor = 0.4;
        final double healthBoostFactor = 2.5;
        final double healthBoostBase = 0.57;
    }
}
