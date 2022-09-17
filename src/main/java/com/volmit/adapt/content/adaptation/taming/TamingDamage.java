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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.Collection;
import java.util.UUID;

public class TamingDamage extends SimpleAdaptation<TamingDamage.Config> {
    private final UUID attUUID = UUID.nameUUIDFromBytes("tame-damage-boost".getBytes());
    private final String attid = "att-tame-damage-boost";


    public TamingDamage() {
        super("tame-damage");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("taming", "damage", "description"));
        setDisplayName(Adapt.dLocalize("taming", "damage", "name"));
        setIcon(Material.FLINT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(6119);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDamageBoost(level), 0) + C.GRAY + " " + Adapt.dLocalize("taming", "damage", "lore1"));
    }

    private double getDamageBoost(int level) {
        return ((getLevelPercent(level) * getConfig().damageFactor) + getConfig().baseDamage);
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
        AttributeModifier mod = new AttributeModifier(attUUID, attid, getDamageBoost(level), AttributeModifier.Operation.ADD_SCALAR);
        if (j.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            j.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).removeModifier(mod);
        } else {
            return;
        }

        if (level > 0) {
            j.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).addModifier(mod);
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 5;
        int initialCost = 5;
        double costFactor = 0.4;
        double baseDamage = 0.08;
        double damageFactor = 0.65;
    }
}
