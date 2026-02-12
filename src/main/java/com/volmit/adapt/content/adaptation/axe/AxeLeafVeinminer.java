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
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.registries.Particles;
import com.volmit.adapt.util.config.ConfigDescription;
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

import java.util.*;

import static com.volmit.adapt.util.data.Metadata.VEIN_MINED;

public class AxeLeafVeinminer extends SimpleAdaptation<AxeLeafVeinminer.Config> {
    public AxeLeafVeinminer() {
        super("axe-leaf-veinminer");
        registerConfiguration(AxeLeafVeinminer.Config.class);
        setDescription(Localizer.dLocalize("axe.leaf_miner.description"));
        setDisplayName(Localizer.dLocalize("axe.leaf_miner.name"));
        setIcon(Material.BIRCH_LEAVES);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(5849);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.OAK_LEAVES)
                .key("challenge_axe_leaf_5k")
                .title(Localizer.dLocalize("advancement.challenge_axe_leaf_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_axe_leaf_5k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_axe_leaf_5k").goal(5000).stat("axe.leaf-veinminer.leaves-broken").reward(400).build());
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("axe.leaf_miner.lore1"));
        v.addLore(C.GREEN + "" + (level + getConfig().baseRange) + C.GRAY + " " + Localizer.dLocalize("axe.leaf_miner.lore2"));
        v.addLore(C.ITALIC + Localizer.dLocalize("axe.leaf_miner.lore3"));
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
        SoundPlayer sp = SoundPlayer.of(p);
        if (!hasAdaptation(p)) {
            return;
        }

        if (!p.isSneaking()) {
            return;
        }

        if (!isAxe(p.getInventory().getItemInMainHand())) {
            return;
        }

        Material blockType = e.getBlock().getType();
        if (!blockType.isItem() || !isLeaves(new ItemStack(blockType))) {
            return;
        }

        VEIN_MINED.add(e.getBlock());

        Block block = e.getBlock();
        Map<Location, Block> blockMap = new HashMap<>();
        Deque<Block> stack = new LinkedList<>();
        stack.push(block);
        int radius = getRadius(getLevel(p));
        int radiusSquared = radius * radius;
        while (!stack.isEmpty() && blockMap.size() < radius) {
            Block currentBlock = stack.pop();
            if (blockMap.containsKey(currentBlock.getLocation())) continue;
            blockMap.put(currentBlock.getLocation(), currentBlock);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block b = currentBlock.getRelative(x, y, z);
                        if (b.getType() != block.getType()
                                || blockMap.containsKey(b.getLocation())
                                || stack.contains(b))
                            continue;
                        if (currentBlock.getLocation().distanceSquared(b.getLocation()) <= radiusSquared && canBlockBreak(p, b.getLocation())) {
                            stack.push(b);
                        }
                    }
                }
            }
        }

        int leavesBroken = blockMap.size();
        J.s(() -> {
            for (Location l : blockMap.keySet()) {
                Block b = block.getWorld().getBlockAt(l);
                PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("axes");
                PlayerAdaptation adaptation = line != null ? line.getAdaptation("axe-drop-to-inventory") : null;

                VEIN_MINED.add(b);
                if (adaptation != null && adaptation.getLevel() > 0) {
                    Collection<ItemStack> items = block.getDrops();
                    for (ItemStack i : items) {
                        sp.play(p.getLocation(), Sound.BLOCK_CALCITE_HIT, 0.01f, 0.01f);
                        HashMap<Integer, ItemStack> extra = p.getInventory().addItem(i);
                        if (!extra.isEmpty()) {
                            p.getWorld().dropItem(p.getLocation(), extra.get(0));
                        }
                    }
                    p.breakBlock(b);
                } else {
                    b.breakNaturally(p.getItemInUse());
                    SoundPlayer spw = SoundPlayer.of(block.getWorld());
                    spw.play(b.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 0.01f, 0.25f);
                    if (getConfig().showParticles) {
                        b.getWorld().spawnParticle(Particle.ASH, b.getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
                    }
                }
                if (getConfig().showParticles) {
                    this.vfxCuboidOutline(b, Particles.ENCHANTMENT_TABLE);
                }
                VEIN_MINED.remove(b);
            }
            VEIN_MINED.remove(block);
        });
        if (leavesBroken > 0) {
            getPlayer(p).getData().addStat("axe.leaf-veinminer.leaves-broken", leavesBroken);
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
    @ConfigDescription("Break bulk leaves at once while sneaking.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Axe Leaf Veinminer adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.325;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Range for the Axe Leaf Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int baseRange = 5;
    }
}
