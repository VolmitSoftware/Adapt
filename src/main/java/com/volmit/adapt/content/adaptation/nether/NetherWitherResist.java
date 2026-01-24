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

package com.volmit.adapt.content.adaptation.nether;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class NetherWitherResist extends SimpleAdaptation<NetherWitherResist.Config> {

    private static final Random RANDOM = new Random();

    public NetherWitherResist() {
        super("nether-wither-resist");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("nether.wither_resist.description"));
        setDisplayName(Localizer.dLocalize("nether.wither_resist.name"));
        setIcon(Material.NETHERITE_CHESTPLATE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(9283);
    }

    @Override
    public void addStats(int level, Element v) {
        int chance = (int) (getConfig().basePieceChance + getConfig().getChanceAddition() * level);
        v.addLore(Localizer.dLocalize("nether.wither_resist.lore", chance));
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.WITHER && e.getEntity() instanceof Player p) {
            if (!hasAdaptation(p))
                return;
            double chance = getTotalChange(p);
            if (RANDOM.nextInt(0, 101) <= chance)
                e.setCancelled(true);
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().isEnabled();
    }

    @Override
    public void onTick() {
    }

    private double getTotalChange(Player p) {
        return getChance(p, EquipmentSlot.HEAD) + getChance(p, EquipmentSlot.CHEST) + getChance(p, EquipmentSlot.LEGS) + getChance(p, EquipmentSlot.FEET);
    }

    private double getChance(Player p, EquipmentSlot slot) {
        if (p.getEquipment() == null)
            return 0.0;
        ItemStack item = p.getEquipment().getItem(slot);
        if (item.getType() == Material.NETHERITE_HELMET || item.getType() == Material.NETHERITE_CHESTPLATE || item.getType() == Material.NETHERITE_LEGGINGS || item.getType() == Material.NETHERITE_BOOTS)
            return getConfig().basePieceChance + getConfig().chanceAddition * getLevel(p);
        return 0.0D;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @Data
    @NoArgsConstructor
    public static class Config {
        public boolean permanent = false;
        private boolean enabled = true;
        private int baseCost = 3;
        private double costFactor = 1;
        private int maxLevel = 3;
        private int initialCost = 5;
        private double basePieceChance = 10;
        private double chanceAddition = 5;
    }
}
