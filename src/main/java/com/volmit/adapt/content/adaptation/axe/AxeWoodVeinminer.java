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
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.reflect.registries.Particles;
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

import static com.volmit.adapt.util.data.Metadata.VEIN_MINED;

public class AxeWoodVeinminer extends SimpleAdaptation<AxeWoodVeinminer.Config> {
    public AxeWoodVeinminer() {
        super("axe-wood-veinminer");
        registerConfiguration(AxeWoodVeinminer.Config.class);
        setDescription(Localizer.dLocalize("axe.wood_miner.description"));
        setDisplayName(Localizer.dLocalize("axe.wood_miner.name"));
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

    @Override
    public void addStats(int level, Element v) {
        v.addLore(Localizer.dLocalize("axe.wood_miner.lore", level + getConfig().baseRange));
    }

    private int getRadius(int lvl) {
        return lvl + getConfig().baseRange;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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

        if (!isAxe(p.getInventory().getItemInMainHand())) {
            return;
        }

        if (!isLog(new ItemStack(e.getBlock().getType()))) {
            return;
        }

        VEIN_MINED.add(e.getBlock());
        Block block = e.getBlock();
        Set<Block> blockMap = new HashSet<>();
        int blockCount = 0;
        int radius = getRadius(getLevel(p));
        int radiusSquared = radius * radius;
        for (int i = 0; i < radius; i++) {
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
                            if (block.getLocation().distanceSquared(b.getLocation()) > radiusSquared) {
                                Adapt.verbose("Block: " + b.getLocation() + " is too far away from " + block.getLocation() + " (" + radius + ")");
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
                PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("axes");
                PlayerAdaptation adaptation = line != null ? line.getAdaptation("axe-drop-to-inventory") : null;
                VEIN_MINED.add(blocks);
                if (adaptation != null && adaptation.getLevel() > 0) {
                    Collection<ItemStack> items = blocks.getDrops();
                    for (ItemStack item : items) {
                        safeGiveItem(p, item);
                        Adapt.verbose("Giving item: " + item);
                    }
                    blocks.setType(Material.AIR);
                } else {
                    blocks.breakNaturally(p.getItemInUse());
                    SoundPlayer spw = SoundPlayer.of(blocks.getWorld());
                    spw.play(e.getBlock().getLocation(), Sound.BLOCK_FUNGUS_BREAK, 0.01f, 0.25f);
                    if (getConfig().showParticles) {
                        blocks.getWorld().spawnParticle(Particle.ASH, blocks.getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
                    }
                }
                if (getConfig().showParticles) {
                    this.vfxCuboidOutline(blocks, Particles.ENCHANTMENT_TABLE);
                }
                VEIN_MINED.remove(blocks);
            }
            VEIN_MINED.remove(block);
        });
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
