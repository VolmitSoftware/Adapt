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

package com.volmit.adapt.content.adaptation.pickaxe;

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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class PickaxeVeinminer extends SimpleAdaptation<PickaxeVeinminer.Config> {
    public PickaxeVeinminer() {
        super("pickaxe-veinminer");
        registerConfiguration(PickaxeVeinminer.Config.class);
        setDescription(Adapt.dLocalize("Pickaxe", "Veinminer", "Description"));
        setDisplayName(Adapt.dLocalize("Pickaxe", "Veinminer", "Name"));
        setIcon(Material.IRON_PICKAXE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(8484);
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Adapt.dLocalize("Pickaxe", "Veinminer", "Lore1"));
        v.addLore(C.GREEN + "" + (level + getConfig().baseRange) + C.GRAY+ " " + Adapt.dLocalize("Pickaxe", "Veinminer", "Lore2"));
        v.addLore(C.ITALIC + Adapt.dLocalize("Pickaxe", "Veinminer", "Lore3"));
    }

    private int getRadius(int lvl) {
        return lvl + getConfig().baseRange;
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        if (!p.isSneaking()) {
            return;
        }

        BlockCanBuildEvent can = new BlockCanBuildEvent(e.getBlock(), e.getPlayer(), e.getBlock().getBlockData(), true);
        Bukkit.getServer().getPluginManager().callEvent(can);

        if (!can.isBuildable()) {
            return;
        }


        if (!e.getBlock().getBlockData().getMaterial().name().endsWith("_ORE") || e.getBlock().getBlockData().getMaterial().name().endsWith("OBSIDIAN")) {
            return;
        }
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
                if (getPlayer(p).getData().getSkillLines() != null && getPlayer(p).getData().getSkillLines().get("pickaxes").getAdaptations() != null && getPlayer(p).getData().getSkillLines().get("pickaxes").getAdaptations().get("pickaxe-autosmelt") != null && getPlayer(p).getData().getSkillLines().get("pickaxes").getAdaptations().get("pickaxe-autosmelt").getLevel() > 0) {
                    if (getPlayer(p).getData().getSkillLines() != null && getPlayer(p).getData().getSkillLines().get("pickaxes").getAdaptations() != null && getPlayer(p).getData().getSkillLines().get("pickaxes").getAdaptations().get("pickaxe-drop-to-inventory") != null && getPlayer(p).getData().getSkillLines().get("pickaxes").getAdaptations().get("pickaxe-drop-to-inventory").getLevel() > 0) {
                        PickaxeAutosmelt.autosmeltBlockDTI(b, p);
                    } else {
                        PickaxeAutosmelt.autosmeltBlock(b, p);
                    }
                } else {
                    if (getPlayer(p).getData().getSkillLines() != null && getPlayer(p).getData().getSkillLines().get("pickaxes").getAdaptations() != null && getPlayer(p).getData().getSkillLines().get("pickaxes").getAdaptations().get("pickaxe-drop-to-inventory") != null && getPlayer(p).getData().getSkillLines().get("pickaxes").getAdaptations().get("pickaxe-drop-to-inventory").getLevel() > 0) {
                    b.getDrops(e.getPlayer().getInventory().getItemInMainHand(), p).forEach(item -> {
                        HashMap<Integer, ItemStack> extra = p.getInventory().addItem(item);
                        extra.forEach((k, v) -> p.getWorld().dropItem(p.getLocation(), v));
                    });
                    b.setType(Material.AIR);
                    } else {
                        b.breakNaturally(p.getItemInUse());
                        e.getBlock().getWorld().playSound(e.getBlock().getLocation(), Sound.BLOCK_FUNGUS_BREAK, 0.4f, 0.25f);
                        e.getBlock().getWorld().spawnParticle(Particle.ASH, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
                    }
                }
            }
        });
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
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
        int baseRange = 2;
    }
}
