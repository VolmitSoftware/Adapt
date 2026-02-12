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
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.*;
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

import static com.volmit.adapt.util.data.Metadata.VEIN_MINED;

public class PickaxeVeinminer extends SimpleAdaptation<PickaxeVeinminer.Config> {
    public PickaxeVeinminer() {
        super("pickaxe-veinminer");
        registerConfiguration(PickaxeVeinminer.Config.class);
        setDescription(Localizer.dLocalize("pickaxe.vein_miner.description"));
        setDisplayName(Localizer.dLocalize("pickaxe.vein_miner.name"));
        setIcon(Material.IRON_PICKAXE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(8484);
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.vein_miner.lore1"));
        v.addLore(C.GREEN + "" + (level + getConfig().baseRange) + C.GRAY + " " + Localizer.dLocalize("pickaxe.vein_miner.lore2"));
        v.addLore(C.ITALIC + Localizer.dLocalize("pickaxe.vein_miner.lore3"));
    }

    private int getRadius(int lvl) {
        return lvl + getConfig().baseRange;
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (VEIN_MINED.get(e.getBlock())) {
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
        VEIN_MINED.add(e.getBlock());

        Block block = e.getBlock();
        Map<Location, Block> blockMap = new HashMap<>();
        blockMap.put(block.getLocation(), block);

        int radius = getRadius(getLevel(p));
        for (int i = 0; i < radius; i++) {
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
                Block b = block.getWorld().getBlockAt(l);
                PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("pickaxe");
                PlayerAdaptation autoSmelt = line != null ? line.getAdaptation("pickaxe-autosmelt") : null;
                PlayerAdaptation drop2Inv = line != null ? line.getAdaptation("pickaxe-drop-to-inventory") : null;
                VEIN_MINED.add(b);
                if (autoSmelt != null && autoSmelt.getLevel() > 0 && ItemListings.getSmeltOre().contains(b.getType())) {
                    if (drop2Inv != null && drop2Inv.getLevel() > 0) {
                        PickaxeAutosmelt.autosmeltBlockDTI(b, p);
                    } else {
                        PickaxeAutosmelt.autosmeltBlock(b, p);
                    }
                } else {
                    if (drop2Inv != null && drop2Inv.getLevel() > 0) {
                        b.getDrops(p.getInventory().getItemInMainHand(), p).forEach(item -> {
                            HashMap<Integer, ItemStack> extra = p.getInventory().addItem(item);
                            extra.forEach((k, v) -> p.getWorld().dropItem(p.getLocation(), v));
                        });
                        b.setType(Material.AIR);
                    } else {
                        b.breakNaturally(p.getItemInUse());
                        SoundPlayer spw = SoundPlayer.of(block.getWorld());
                        spw.play(block.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 0.4f, 0.25f);
                        if (getConfig().showParticles) {
                            block.getWorld().spawnParticle(Particle.ASH, b.getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
                        }
                    }
                }
                VEIN_MINED.remove(b);
            }
            VEIN_MINED.remove(block);
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Pickaxe Veinminer adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 2.325;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Range for the Pickaxe Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int baseRange = 2;
    }
}
