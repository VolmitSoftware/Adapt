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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.content.matter.BrewingStandOwner;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class BrewingSuperHeated extends SimpleAdaptation<BrewingSuperHeated.Config> {

    private static final int MAX_CHECKS_BEFORE_REMOVE = 20;
    private final Map<Block, Integer> activeStands = new HashMap<>();

    public BrewingSuperHeated() {
        super("brewing-super-heated");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("brewing", "superheated", "description"));
        setDisplayName(Localizer.dLocalize("brewing", "superheated", "name"));
        setIcon(Material.LAVA_BUCKET);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(253);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getFireBoost(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("brewing", "superheated", "lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getLavaBoost(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("brewing", "superheated", "lore2"));
    }

    public double getLavaBoost(double factor) {
        return (getConfig().lavaMultiplier) * (getConfig().multiplierFactor * factor);
    }

    public double getFireBoost(double factor) {
        return (getConfig().fireMultiplier) * (getConfig().multiplierFactor * factor);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(InventoryMoveItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        J.s(() -> {
            if (e.getDestination().getType().equals(InventoryType.BREWING)) {
                activeStands.put(e.getDestination().getLocation().getBlock(), MAX_CHECKS_BEFORE_REMOVE);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BrewEvent e) {
        if (e.isCancelled()) {
            return;
        }
        J.s(() -> {
            if (((BrewingStand) e.getBlock().getState()).getBrewingTime() > 0) {
                activeStands.put(e.getBlock(), MAX_CHECKS_BEFORE_REMOVE);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.isCancelled()) {
            return;
        }
        if (e.getView().getTopInventory().getType().equals(InventoryType.BREWING)) {
            activeStands.put(e.getView().getTopInventory().getLocation().getBlock(), MAX_CHECKS_BEFORE_REMOVE);
        }
    }


    @Override
    public void onTick() {
        if (activeStands.isEmpty()) {
            return;
        }

        Iterator<Block> it = activeStands.keySet().iterator();

        J.s(() -> {
            while (it.hasNext()) {
                BlockState s = it.next().getState();

                if (s instanceof BrewingStand b) {
                    if (b.getBrewingTime() <= 0) {
                        J.s(() -> {
                            BrewingStand bb = (BrewingStand) s.getBlock().getState();
                            if (bb.getBrewingTime() <= 0) {
                                if (activeStands.get(b.getBlock()) == 0) {
                                    activeStands.remove(b.getBlock());
                                }
                                if (activeStands.containsKey(b.getBlock())) {
                                    activeStands.put(b.getBlock(), activeStands.get(b.getBlock()) - 1);
                                }
                            }
                        });
                        continue;
                    }

                    BrewingStandOwner owner = WorldData.of(b.getWorld()).getMantle().get(b.getX(), b.getY(), b.getZ(), BrewingStandOwner.class);

                    if (owner == null) {
                        it.remove();
                        continue;
                    }

                    PlayerData p = getServer().peekData(owner.getOwner());

                    if (p.getSkillLines().get(getSkill().getName()) != null && p.getSkillLines().get(getSkill().getName()).getAdaptations().containsKey(getName())
                            && p.getSkillLines().get(getSkill().getName()).getAdaptations().get(getName()).getLevel() > 0) {
                        updateHeat(b, getLevelPercent(p.getSkillLines().get(getSkill().getName()).getAdaptations().get(getName()).getLevel()));
                    } else {
                        it.remove();
                    }
                } else {
                    it.remove();
                }
            }
        });
    }

    private void updateHeat(BrewingStand b, double factor) {
        double l = 0;
        double f = 0;

        switch (b.getBlock().getRelative(BlockFace.DOWN).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }
        switch (b.getBlock().getRelative(BlockFace.NORTH).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }
        switch (b.getBlock().getRelative(BlockFace.SOUTH).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }
        switch (b.getBlock().getRelative(BlockFace.EAST).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }
        switch (b.getBlock().getRelative(BlockFace.WEST).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }

        double pct = (getFireBoost(factor) * f) + (getLavaBoost(factor) * l) + 1;
        int warp = (int) ((getInterval() / 50D) * pct);
        b.setBrewingTime(Math.max(1, b.getBrewingTime() - warp));
        b.update();

        if (M.r(1D / (333D / getInterval()))) {
            for (Player players : b.getWorld().getPlayers()) {
                players.playSound(b.getBlock().getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1f, 1f + RNG.r.f(0.3f, 0.6f));
            }
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
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 3;
        double costFactor = 0.75;
        int maxLevel = 5;
        int initialCost = 5;
        double multiplierFactor = 1.33;
        double fireMultiplier = 0.14;
        double lavaMultiplier = 0.69;
    }
}
