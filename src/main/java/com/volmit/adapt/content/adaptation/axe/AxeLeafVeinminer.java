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

package com.volmit.adapt.content.adaptation.axe;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AxeLeafVeinminer extends SimpleAdaptation<AxeLeafVeinminer.Config> {
    public AxeLeafVeinminer() {
        super("axe-leaf-veinminer");
        registerConfiguration(AxeLeafVeinminer.Config.class);
        setDescription(Adapt.dLocalize("Axe", "LeafMiner", "Description"));
        setDisplayName(Adapt.dLocalize("Axe", "LeafMiner", "Name"));
        setIcon(Material.BIRCH_LEAVES);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(5849);

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Adapt.dLocalize("Axe", "LeafMiner", "Lore1"));
        v.addLore(C.GREEN + "" + (level + getConfig().baseRange) + C.GRAY + Adapt.dLocalize("Axe", "LeafMiner", "Lore2"));
        v.addLore(C.ITALIC + Adapt.dLocalize("Axe", "LeafMiner", "Lore3"));
    }

    private int getRadius(int lvl) {
        return lvl + getConfig().baseRange;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (hasAdaptation(e.getPlayer())) {

            Player p = e.getPlayer();
            if (!p.isSneaking()) {
                return;
            }

            if (!isAxe(p.getInventory().getItemInMainHand())) {
                return;
            }

            BlockCanBuildEvent can = new BlockCanBuildEvent(e.getBlock(), e.getPlayer(), e.getBlock().getBlockData(), true);
            Bukkit.getServer().getPluginManager().callEvent(can);

            if (!can.isBuildable()) {
                return;
            }

            if (isLeaves(new ItemStack(e.getBlock().getType()))) {

                Block block = e.getBlock();
                Map<Location, Block> blockMap = new HashMap<>();
                blockMap.put(block.getLocation(), block);

                for (int i = 0; i < getRadius(getLevel(p)); i++) {
                    for (int x = -i; x <= i; x++) {
                        for (int y = -i; y <= i; y++) {
                            for (int z = -i; z <= i; z++) {
                                Block b = block.getRelative(x, y, z);
                                if (b.getType() == block.getType()) {
                                    blockMap.put(b.getLocation(), b);
                                }
                            }
                        }
                    }
                }

                J.s(() -> {
                    for (Location l : blockMap.keySet()) {
                        Block b = e.getBlock().getWorld().getBlockAt(l);
                        xp(e.getPlayer(), 3);
                        if (getPlayer(p).getData().getSkillLines() != null && getPlayer(p).getData().getSkillLines().get("axes").getAdaptations() != null && getPlayer(p).getData().getSkillLines().get("axes").getAdaptations().get("axe-drop-to-inventory") != null && getPlayer(p).getData().getSkillLines().get("axes").getAdaptations().get("axe-drop-to-inventory").getLevel() > 0) {
                            Collection<ItemStack> items = e.getBlock().getDrops();
                            for (ItemStack i : items) {
                                p.playSound(p.getLocation(), Sound.BLOCK_CALCITE_HIT, 0.05f, 0.01f);
                                xp(p, 2);
                                HashMap<Integer, ItemStack> extra = p.getInventory().addItem(i);
                                if (!extra.isEmpty()) {
                                    p.getWorld().dropItem(p.getLocation(), extra.get(0));
                                }
                            }
                            l.getWorld().getBlockAt(l).setType(Material.AIR);
                            e.getBlock().getDrops().clear();
                        } else {
                            b.breakNaturally(p.getItemInUse());
                            e.getBlock().getWorld().playSound(e.getBlock().getLocation(), Sound.BLOCK_FUNGUS_BREAK, 0.4f, 0.25f);
                            e.getBlock().getWorld().spawnParticle(Particle.ASH, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
                        }
                        vfxSingleCubeOutlineR(b, Particle.ENCHANTMENT_TABLE);
                    }
                });
            }
        }
    }


    @Override
    public void onTick() {
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 5;
        int initialCost = 4;
        double costFactor = 2.325;
        int baseRange = 5;
    }
}
