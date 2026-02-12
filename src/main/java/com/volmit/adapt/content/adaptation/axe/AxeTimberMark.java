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
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AxeTimberMark extends SimpleAdaptation<AxeTimberMark.Config> {
    public AxeTimberMark() {
        super("axe-timber-mark");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("axe.timber_mark.description"));
        setDisplayName(Localizer.dLocalize("axe.timber_mark.name"));
        setIcon(Material.OAK_LOG);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getMaxBlocks(level) + C.GRAY + " " + Localizer.dLocalize("axe.timber_mark.lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getMarkDurationMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("axe.timber_mark.lore2"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        setStorage(e.getPlayer(), "timberMarkBlock", null);
        setStorage(e.getPlayer(), "timberMarkUntil", 0L);
        setStorage(e.getPlayer(), "timberMarkOwner", id.toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled() || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking() || !isAxe(p.getInventory().getItemInMainHand())) {
            return;
        }

        Block clicked = e.getClickedBlock();
        if (!isLog(new org.bukkit.inventory.ItemStack(clicked.getType()))) {
            return;
        }

        setStorage(p, "timberMarkBlock", clicked.getLocation().toString());
        setStorage(p, "timberMarkUntil", System.currentTimeMillis() + getMarkDurationMillis(getLevel(p)));
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        e.setCancelled(true);
        SoundPlayer.of(p.getWorld()).play(clicked.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.8f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !isAxe(p.getInventory().getItemInMainHand())) {
            return;
        }

        Long until = getStorageLong(p, "timberMarkUntil", 0L);
        String marked = getStorageString(p, "timberMarkBlock", "");
        if (until == null || until < System.currentTimeMillis() || marked == null || marked.isEmpty()) {
            return;
        }

        if (!e.getBlock().getLocation().toString().equals(marked)) {
            return;
        }

        Material type = e.getBlock().getType();
        int maxBlocks = getMaxBlocks(getLevel(p));
        Set<Block> connected = floodLogs(e.getBlock(), type, maxBlocks);
        for (Block b : connected) {
            if (b.equals(e.getBlock())) {
                continue;
            }

            if (!canBlockBreak(p, b.getLocation())) {
                continue;
            }

            b.breakNaturally(p.getInventory().getItemInMainHand());
            xp(p, getConfig().xpPerExtraLog);
        }

        int level = getLevel(p);
        Set<Block> leaves = floodLeaves(connected, getMaxLeaves(level));
        for (Block leaf : leaves) {
            if (!canBlockBreak(p, leaf.getLocation())) {
                continue;
            }

            leaf.breakNaturally(p.getInventory().getItemInMainHand());
            xp(p, getConfig().xpPerLeafCleared);
        }

        SoundPlayer.of(p.getWorld()).play(e.getBlock().getLocation(), Sound.BLOCK_WOOD_BREAK, 0.6f, 0.8f);
        setStorage(p, "timberMarkUntil", 0L);
        setStorage(p, "timberMarkBlock", "");
    }

    private Set<Block> floodLogs(Block start, Material type, int maxBlocks) {
        Set<Block> visited = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Block b = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block n = b.getRelative(x, y, z);
                        if (n.getType() != type || visited.contains(n)) {
                            continue;
                        }

                        visited.add(n);
                        queue.add(n);
                        if (visited.size() >= maxBlocks) {
                            return visited;
                        }
                    }
                }
            }
        }
        return visited;
    }

    private Set<Block> floodLeaves(Set<Block> logs, int maxLeaves) {
        Set<Block> visited = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();

        for (Block log : logs) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block n = log.getRelative(x, y, z);
                        if (!isLeafMaterial(n.getType()) || visited.contains(n)) {
                            continue;
                        }

                        visited.add(n);
                        queue.add(n);
                        if (visited.size() >= maxLeaves) {
                            return visited;
                        }
                    }
                }
            }
        }

        while (!queue.isEmpty() && visited.size() < maxLeaves) {
            Block b = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block n = b.getRelative(x, y, z);
                        if (!isLeafMaterial(n.getType()) || visited.contains(n)) {
                            continue;
                        }

                        visited.add(n);
                        queue.add(n);
                        if (visited.size() >= maxLeaves) {
                            return visited;
                        }
                    }
                }
            }
        }

        return visited;
    }

    private boolean isLeafMaterial(Material type) {
        return type.name().endsWith("_LEAVES");
    }

    private int getMaxBlocks(int level) {
        return Math.max(8, (int) Math.round(getConfig().maxBlocksBase + (getLevelPercent(level) * getConfig().maxBlocksFactor)));
    }

    private long getMarkDurationMillis(int level) {
        return (long) Math.max(1000, Math.round(getConfig().markDurationMillisBase + (getLevelPercent(level) * getConfig().markDurationMillisFactor)));
    }

    private int getMaxLeaves(int level) {
        return Math.max(0, (int) Math.round(getConfig().maxLeavesBase + (getLevelPercent(level) * getConfig().maxLeavesFactor)));
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
    @ConfigDescription("Mark a trunk, then break the marked log to fell connected wood.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Blocks Base for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxBlocksBase = 12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Blocks Factor for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxBlocksFactor = 56;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mark Duration Millis Base for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double markDurationMillisBase = 6000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mark Duration Millis Factor for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double markDurationMillisFactor = 9000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Extra Log for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerExtraLog = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Leaves Base for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxLeavesBase = 24;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Leaves Factor for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxLeavesFactor = 180;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Leaf Cleared for the Axe Timber Mark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerLeafCleared = 0.4;
    }
}
