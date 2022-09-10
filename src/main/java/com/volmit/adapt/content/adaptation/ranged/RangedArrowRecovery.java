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

package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RangedArrowRecovery extends SimpleAdaptation<RangedArrowRecovery.Config> {
    private final Map<UUID, Integer> arrows = new HashMap<>();

    public RangedArrowRecovery() {
        super("ranged-arrow-recovery");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Ranged", "ArrowRecovery", "Description"));
        setDisplayName(Adapt.dLocalize("Ranged", "ArrowRecovery", "Name"));
        setIcon(Material.TIPPED_ARROW);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(4009);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    private double getChance(double factor) {
        return factor * getConfig().chanceFactor;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getChance(getLevelPercent(level)), 0) + C.GRAY + " " + Adapt.dLocalize("Ranged", "ArrowRecovery", "Lore1"));
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Projectile && ((Projectile) e.getDamager()).getShooter() instanceof Player p) {

            if (getLevel(p) > 0) {
                if (e.getDamager() instanceof Arrow a && Math.random() < getChance(getLevelPercent(p))) {
                    int hits = 0;

                    if (a.getPierceLevel() > 0) {
                        NamespacedKey k = new NamespacedKey(Adapt.instance, "arrow-hits");
                        hits = a.getPersistentDataContainer().getOrDefault(k, PersistentDataType.INTEGER, 0);
                        a.getPersistentDataContainer().set(k, PersistentDataType.INTEGER, hits + 1);
                    }
                    xp(p, 5);
                    if (hits + 1 >= a.getPierceLevel()) {
                        arrows.compute(e.getEntity().getUniqueId(), (k, v) -> {
                            if (v == null) {
                                return 1;
                            }

                            return v + 1;
                        });

                        if (hits > 1) {
                            a.remove();
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e) {
        Integer c = arrows.remove(e.getEntity().getUniqueId());

        if (c != null) {
            e.getDrops().add(new ItemStack(Material.ARROW, c));
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
        boolean enabled = true;
        int baseCost = 3;
        int maxLevel = 3;
        int initialCost = 6;
        double costFactor = 0.725;
        double chanceFactor = 1;
    }
}
