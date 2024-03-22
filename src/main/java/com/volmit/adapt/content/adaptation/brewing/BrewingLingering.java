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

package com.volmit.adapt.content.adaptation.brewing;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.content.matter.BrewingStandOwner;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

public class BrewingLingering extends SimpleAdaptation<BrewingLingering.Config> {
    public BrewingLingering() {
        super("brewing-lingering");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("brewing", "lingering", "description"));
        setDisplayName(Localizer.dLocalize("brewing", "lingering", "name"));
        setIcon(Material.CLOCK);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(4788);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.duration((long) getDurationBoost(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("brewing", "lingering", "lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getPercentBoost(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("brewing", "lingering", "lore2"));
    }

    public double getDurationBoost(double factor) {
        return (getConfig().durationBoostFactorTicks * factor) + getConfig().baseDurationBoostTicks;
    }

    public double getPercentBoost(double factor) {
        return 1 + ((factor * factor * getConfig().durationMultiplierFactor) + getConfig().baseDurationMultiplier);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BrewEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getBlock().getType().equals(Material.BREWING_STAND)) {
            BrewingStandOwner owner = WorldData.of(e.getBlock().getWorld()).getMantle().get(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(), BrewingStandOwner.class);

            if (owner != null) {
                J.s(() -> {
                    PlayerData data = null;
                    ItemStack[] c = ((BrewingStand) e.getBlock().getState()).getInventory().getStorageContents();
                    boolean ef = false;
                    for (int i = 0; i < c.length; i++) {
                        ItemStack is = c[i];

                        if (is != null && is.getItemMeta() != null && is.getItemMeta() instanceof PotionMeta p) {
                            is = is.clone();
                            data = data == null ? getServer().peekData(owner.getOwner()) : data;

                            if (data.getSkillLines().containsKey(getSkill().getName()) && data.getSkillLine(getSkill().getName()).getAdaptations().containsKey(getName())) {
                                PlayerAdaptation a = data.getSkillLine(getSkill().getName()).getAdaptations().get(getName());

                                if (a.getLevel() > 0) {
                                    double factor = getLevelPercent(a.getLevel());
                                    ef = enhance(factor, is, p) || ef;
                                    c[i] = is;
                                }
                            }
                        }
                    }

                    if (ef) {
                        ((BrewingStand) e.getBlock().getState()).getInventory().setStorageContents(c);
                        for (Player players : e.getBlock().getWorld().getPlayers()) {
                            players.playSound(e.getBlock().getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 0.75f);
                            players.playSound(e.getBlock().getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1.75f);
                        }
                    }
                });
            } else {
                Adapt.verbose("No Owner");
            }
        }
    }

    private boolean enhance(double factor, ItemStack is, PotionMeta p) {
        if (!p.getBasePotionData().getType().isInstant()) {
            PotionEffect effect = getRawPotionEffect(is);

            if (effect != null) {
                p.addCustomEffect(new PotionEffect(effect.getType(),

                        (int) (getDurationBoost(factor) + (effect.getDuration() * getPercentBoost(factor))),

                        effect.getAmplifier()), true);
                is.setItemMeta(p);
                return true;
            }
        }

        return false;
    }


    @Override
    public void onTick() {

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
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 3;
        double costFactor = 0.75;
        int maxLevel = 5;
        int initialCost = 5;
        double baseDurationBoostTicks = 100;
        double durationBoostFactorTicks = 500;
        double durationMultiplierFactor = 0.45;
        double baseDurationMultiplier = 0.05;
    }
}
