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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
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
import java.util.HashMap;
import java.util.Map;

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
                        xp(p, 10);
                        if (getPlayer(p).getData().getSkillLines() != null
                                && getPlayer(p).getData().getSkillLines().get("axes").getAdaptations() != null
                                && getPlayer(p).getData().getSkillLines().get("axes").getAdaptations().get("axe-drop-to-inventory") != null
                                && getPlayer(p).getData().getSkillLines().get("axes").getAdaptations().get("axe-drop-to-inventory").getLevel() > 0) {
                            Collection<ItemStack> items = e.getBlock().getDrops();
                            if (!isLog(new ItemStack(b.getType()))) {
                                for (ItemStack i : items) {
                                    p.playSound(p.getLocation(), Sound.BLOCK_CALCITE_HIT, 0.01f, 0.01f);
                                    xp(p, 2);
                                    HashMap<Integer, ItemStack> extra = p.getInventory().addItem(i);
                                    if (!extra.isEmpty()) {
                                        p.getWorld().dropItem(p.getLocation(), extra.get(0));
                                    }
                                }
                            } else {
                                if (!p.getInventory().addItem(new ItemStack(b.getType())).isEmpty()) {
                                    p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(b.getType()));
                                }
                            }
                            p.breakBlock(l.getBlock());
                        } else {
                            b.breakNaturally(p.getItemInUse());
                            e.getBlock().getWorld().playSound(e.getBlock().getLocation(), Sound.BLOCK_FUNGUS_BREAK, 0.01f, 0.25f);
                            if (getConfig().showParticles) {
                                e.getBlock().getWorld().spawnParticle(Particle.ASH, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
                            }
                        }
                        if (getConfig().showParticles) {
                            vfxSingleCubeOutlineR(b, Particle.ENCHANTMENT_TABLE);
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
        int baseRange = 3;
    }
}
