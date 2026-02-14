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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.config.ConfigDescription;
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

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;

import static art.arcane.adapt.util.data.Metadata.VEIN_MINED;

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
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.OAK_LOG)
                .key("challenge_axe_wood_vein_2500")
                .title(Localizer.dLocalize("advancement.challenge_axe_wood_vein_2500.title"))
                .description(Localizer.dLocalize("advancement.challenge_axe_wood_vein_2500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.DIAMOND_AXE)
                .key("challenge_axe_wood_vein_cascade")
                .title(Localizer.dLocalize("advancement.challenge_axe_wood_vein_cascade.title"))
                .description(Localizer.dLocalize("advancement.challenge_axe_wood_vein_cascade.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_axe_wood_vein_2500", "axe.wood-veinminer.logs-veinmined", 2500, 500);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("axe.wood_miner.lore1"));
        v.addLore(C.GREEN + "" + (level + getConfig().baseRange) + C.GRAY + " " + Localizer.dLocalize("axe.wood_miner.lore2"));
        v.addLore(C.ITALIC + Localizer.dLocalize("axe.wood_miner.lore3"));
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

        int logsVeinmined = blockMap.size();
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
                    if (areParticlesEnabled()) {
                        blocks.getWorld().spawnParticle(Particle.ASH, blocks.getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
                    }
                }
                if (areParticlesEnabled()) {
                    this.vfxCuboidOutline(blocks, Particles.ENCHANTMENT_TABLE);
                }
                VEIN_MINED.remove(blocks);
            }
            VEIN_MINED.remove(block);
        });
        if (logsVeinmined > 0) {
            getPlayer(p).getData().addStat("axe.wood-veinminer.logs-veinmined", logsVeinmined);
            if (logsVeinmined >= 15 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_axe_wood_vein_cascade")) {
                getPlayer(p).getAdvancementHandler().grant("challenge_axe_wood_vein_cascade");
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
    @ConfigDescription("Break bulk wood at once while sneaking.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Axe Wood Veinminer adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.95;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks for the Axe Wood Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int maxBlocks = 20;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Range for the Axe Wood Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int baseRange = 3;
    }
}
