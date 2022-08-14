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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.VectorMath;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;

public class DiscoveryArmor extends SimpleAdaptation<DiscoveryArmor.Config> {
    public DiscoveryArmor() {
        super("discovery-world-armor");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Discovery", "DiscoveryArmor", "Description"));
        setDisplayName(Adapt.dLocalize("Discovery", "DiscoveryArmor", "Name"));
        setIcon(Material.TURTLE_HELMET);
        setInterval(1125);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Adapt.dLocalize("Discovery","DiscoveryArmor", "Lore1") + C.GRAY + ", " +Adapt.dLocalize("Discovery", "DiscoveryArmor", "Lore2"));
        v.addLore(Adapt.dLocalize("Discovery", "DiscoveryArmor", "Lore3") + C.BLUE + level * 0.25);
    }

    public double getArmorPoints(Material m) {
        return Math.log(Math.min(2000, m.getBlastResistance() * m.getBlastResistance())) + Math.log((m.getHardness() < 0 ? 50 : Math.min(50, m.getHardness() + 25)) * 0.33);
    }

    public double getArmor(Location l, int level) {
        Block center = l.getBlock();
        double armorValue = 0.0;
        double count = 0;
        int r = 5;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = center.getRelative(x, y, z);
                    if (center.getLocation().distanceSquared(b.getLocation()) <= r * r) {
                        if (b.getType() != Material.AIR && !b.isLiquid()) {
                            count++;
                            double a = getArmorPoints(b.getType());
                            if (Double.isNaN(a) || a < 0) {
                                a = 0;
                            }
                            armorValue += a;

                            if (a > 2 && M.r(0.005 * a)) {
                                Vector v = VectorMath.directionNoNormal(l, b.getLocation().add(0.5, 0.5, 0.5));
                                l.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, l.clone().add(0, 1, 0), 0, v.getX(), v.getY(), v.getZ());

                            }
                        }
                    }
                }
            }
        }


        return Math.min((armorValue / count) * (level / 2D) * 0.65, 10);
    }


    private double getRadius(double factor) {
        return factor * getConfig().radiusFactor;
    }

    private double getStrength(double factor) {
        return Math.pow(factor, getConfig().strengthExponent);
    }


    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hasAdaptation(p)) {
                Collection<AttributeModifier> c = p.getAttribute(Attribute.GENERIC_ARMOR).getModifiers();
                for (AttributeModifier i : new ArrayList<>(c)) {
                    if (i.getName().equals("adapt-discovery-armor")) {
                        p.getAttribute(Attribute.GENERIC_ARMOR).removeModifier(i);
                    }
                }
                continue;
            }
            double oldArmor = 0;
            double armor = getArmor(p.getLocation(), getLevel(p));
            armor = Double.isNaN(armor) ? 0 : armor;

            Collection<AttributeModifier> c = p.getAttribute(Attribute.GENERIC_ARMOR).getModifiers();
            for (AttributeModifier i : new ArrayList<>(c)) {
                if (i.getName().equals("adapt-discovery-armor")) {
                    oldArmor = i.getAmount();
                    oldArmor = Double.isNaN(oldArmor) ? 0 : oldArmor;
                    p.getAttribute(Attribute.GENERIC_ARMOR).removeModifier(i);
                }

            }
            double lArmor = M.lerp(oldArmor, armor, 0.3);
            lArmor = Double.isNaN(lArmor) ? 0 : lArmor;
            p.getAttribute(Attribute.GENERIC_ARMOR).addModifier(new AttributeModifier("adapt-discovery-armor", lArmor, AttributeModifier.Operation.ADD_NUMBER));


        }

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        public int radiusFactor = 3;
        public double strengthExponent = 1.25;

        boolean enabled = true;
        int baseCost = 2;
        int initialCost = 3;
        double costFactor = 0.3;
        int maxLevel = 3;
    }
}
