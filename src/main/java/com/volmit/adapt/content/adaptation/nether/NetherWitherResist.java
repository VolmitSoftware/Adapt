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
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
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
        v.addLore(C.GREEN + "+ " + chance + "%" + C.GRAY + Localizer.dLocalize("nether.wither_resist.lore1"));
        v.addLore(C.GRAY + " " + Localizer.dLocalize("nether.wither_resist.lore1") + C.DARK_GRAY + Localizer.dLocalize("nether.wither_resist.lore2"));
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
    @ConfigDescription("Wearing Netherite Armor has a chance to negate the wither effect.")
    public static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        public boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        private boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        private int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        private double costFactor = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        private int maxLevel = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        private int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Piece Chance for the Nether Wither Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double basePieceChance = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Chance Addition for the Nether Wither Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        private double chanceAddition = 5;
    }
}
