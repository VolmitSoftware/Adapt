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
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AxeWoodVeinminer extends SimpleAdaptation<AxeWoodVeinminer.Config> {
    public AxeWoodVeinminer() {
        super("axe-wood-veinminer");
        registerConfiguration(AxeWoodVeinminer.Config.class);
        setDescription(Localizer.dLocalize("axe", "woodminer", "description"));
        setDisplayName(Localizer.dLocalize("axe", "woodminer", "name"));
        setIcon(Material.DIAMOND_AXE);
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
        v.addLore(C.GREEN + Localizer.dLocalize("axe", "woodminer", "lore1"));
        v.addLore(C.GREEN + "" + (level + getConfig().baseRange) + C.GRAY + " " + Localizer.dLocalize("axe", "woodminer", "lore2"));
        v.addLore(C.ITALIC + Localizer.dLocalize("axe", "woodminer", "lore3"));
    }

    private int getRadius(int lvl) {
        return lvl + getConfig().baseRange;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (hasAdaptation(p)) {

            if (!p.isSneaking()) {
                return;
            }

            if (!isAxe(p.getInventory().getItemInMainHand())) {
                return;
            }

            if (isLog(new ItemStack(e.getBlock().getType()))) {
                Block block = e.getBlock();
                Set<Block> blockMap = new HashSet<>();
                int blockCount = 0;
                for (int i = 0; i < getRadius(getLevel(p)); i++) {
                    for (int x = -i; x <= i; x++) {
                        for (int y = -i; y <= i; y++) {
                            for (int z = -i; z <= i; z++) {
                                Block b = block.getRelative(x, y, z);
                                if (b.getType() == block.getType()) {
                                    blockCount++;
                                    if (blockCount > getConfig().maxBlocks) {
                                        Adapt.verbose("Block: " + blockCount + " > " + getConfig().maxBlocks);
                                        continue;
                                    }
                                    if (block.getLocation().distance(b.getLocation()) > getRadius(getLevel(p))) {
                                        Adapt.verbose("Block: " + b.getLocation() + " is too far away from " + block.getLocation() + " (" + getRadius(getLevel(p)) + ")");
                                        continue;
                                    }
                                    if (!canBlockBreak(p, b.getLocation())) {
                                        Adapt.verbose("Player " + p.getName() + " doesn't have permission.");
                                        continue;
                                    }
                                    blockMap.add(b);
                                }
                            }
                        }
                    }
                }

                J.s(() -> {
                    for (Block blocks : blockMap) {
                        if (getPlayer(p).getData().getSkillLines().get("axes").getAdaptations().get("axe-drop-to-inventory") != null && getPlayer(p).getData().getSkillLines().get("axes").getAdaptations().get("axe-drop-to-inventory").getLevel() > 0) {
                            Collection<ItemStack> items = blocks.getDrops();
                            for (ItemStack item : items) {
                                safeGiveItem(p, item);
                                Adapt.verbose("Giving item: " + item);
                            }
                            blocks.setType(Material.AIR);
                        } else {
                            blocks.breakNaturally(p.getItemInUse());
                            for (Player players : blocks.getWorld().getPlayers()) {
                                players.playSound(e.getBlock().getLocation(), Sound.BLOCK_FUNGUS_BREAK, 0.01f, 0.25f);
                            }
                            if (getConfig().showParticles) {
                                blocks.getWorld().spawnParticle(Particle.ASH, blocks.getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
                            }
                        }
                        if (getConfig().showParticles) {
                            this.vfxCuboidOutline(blocks, Particle.ENCHANTMENT_TABLE);
                        }
                    }
                });
            }
        }
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        boolean showParticles = true;
        int baseCost = 3;
        int maxLevel = 5;
        int initialCost = 4;
        double costFactor = 2.325;
        int maxBlocks = 20;
        int baseRange = 3;
    }
}
