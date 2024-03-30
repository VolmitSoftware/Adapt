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
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class PickaxeVeinminer extends SimpleAdaptation<PickaxeVeinminer.Config> {
    public PickaxeVeinminer() {
        super("pickaxe-veinminer");
        registerConfiguration(PickaxeVeinminer.Config.class);
        setDescription(Localizer.dLocalize("pickaxe", "veinminer", "description"));
        setDisplayName(Localizer.dLocalize("pickaxe", "veinminer", "name"));
        setIcon(Material.IRON_PICKAXE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(8484);
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("pickaxe", "veinminer", "lore1"));
        v.addLore(C.GREEN + "" + (level + getConfig().baseRange) + C.GRAY + " " + Localizer.dLocalize("pickaxe", "veinminer", "lore2"));
        v.addLore(C.ITALIC + Localizer.dLocalize("pickaxe", "veinminer", "lore3"));
    }

    private int getRadius(int lvl) {
        return lvl + getConfig().baseRange;
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        if (!p.isSneaking()) {
            return;
        }

        if (!e.getBlock().getBlockData().getMaterial().name().endsWith("_ORE")) {
            if (!e.getBlock().getType().equals(Material.OBSIDIAN)) {
                return;
            }
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
                            if (!canBlockBreak(p, e.getBlock().getLocation())) {
                                continue;
                            }
                            blockMap.put(b.getLocation(), b);
                        }
                    }
                }
            }
        }
        J.s(() -> {
            for (Location l : blockMap.keySet()) {
                if (!canBlockBreak(p, l)) {
                    Adapt.verbose("Player " + p.getName() + " doesn't have permission.");
                    continue;
                }
                Block b = e.getBlock().getWorld().getBlockAt(l);
                if (getPlayer(p).getData().getSkillLines() != null && getPlayer(p).getData().getSkillLines().get("pickaxe").getAdaptations() != null && getPlayer(p).getData().getSkillLines().get("pickaxe").getAdaptations().get("pickaxe-autosmelt") != null && getPlayer(p).getData().getSkillLines().get("pickaxe").getAdaptations().get("pickaxe-autosmelt").getLevel() > 0) {
                    if (getPlayer(p).getData().getSkillLines() != null && getPlayer(p).getData().getSkillLines().get("pickaxe").getAdaptations() != null && getPlayer(p).getData().getSkillLines().get("pickaxe").getAdaptations().get("pickaxe-drop-to-inventory") != null && getPlayer(p).getData().getSkillLines().get("pickaxe").getAdaptations().get("pickaxe-drop-to-inventory").getLevel() > 0) {
                        PickaxeAutosmelt.autosmeltBlockDTI(b, p);
                    } else {
                        PickaxeAutosmelt.autosmeltBlock(b, p);
                    }
                } else {
                    if (getPlayer(p).getData().getSkillLines() != null && getPlayer(p).getData().getSkillLines().get("pickaxe").getAdaptations() != null && getPlayer(p).getData().getSkillLines().get("pickaxe").getAdaptations().get("pickaxe-drop-to-inventory") != null && getPlayer(p).getData().getSkillLines().get("pickaxe").getAdaptations().get("pickaxe-drop-to-inventory").getLevel() > 0) {
                        b.getDrops(p.getInventory().getItemInMainHand(), p).forEach(item -> {
                            HashMap<Integer, ItemStack> extra = p.getInventory().addItem(item);
                            extra.forEach((k, v) -> p.getWorld().dropItem(p.getLocation(), v));
                        });
                        b.setType(Material.AIR);
                    } else {
                        b.breakNaturally(p.getItemInUse());
                        for (Player players : e.getBlock().getWorld().getPlayers()) {
                            players.playSound(e.getBlock().getLocation(), Sound.BLOCK_FUNGUS_BREAK, 0.4f, 0.25f);
                        }
                        if (getConfig().showParticles) {

                            e.getBlock().getWorld().spawnParticle(Particle.ASH, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
                        }
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

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        boolean showParticles = true;
        int baseCost = 6;
        int maxLevel = 5;
        int initialCost = 4;
        double costFactor = 2.325;
        int baseRange = 2;
    }
}
