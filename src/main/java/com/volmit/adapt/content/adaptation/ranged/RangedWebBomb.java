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

package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.recipe.MaterialChar;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.item.BoundSnowBall;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.config.ConfigDescription;
import com.volmit.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.*;

public class RangedWebBomb extends SimpleAdaptation<RangedWebBomb.Config> {
    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final BlockData BLOCK = Material.COBWEB.createBlockData();
    private final Map<Entity, Player> activeSnowballs;
    private final Set<Block> activeBlocks;

    public RangedWebBomb() {
        super("ranged-webshot");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged.web_shot.description"));
        setDisplayName(Localizer.dLocalize("ranged.web_shot.name"));
        setIcon(Material.COBWEB);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(4900);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shaped()
                .key("ranged-web-bomb")
                .ingredient(new MaterialChar('I', Material.COBWEB))
                .ingredient(new MaterialChar('S', Material.SNOWBALL))
                .shapes(List.of(
                        "III",
                        "ISI",
                        "III"))
                .result(BoundSnowBall.io.withData(new BoundSnowBall.Data(null)))
                .build());
        activeBlocks = new HashSet<>();
        activeSnowballs = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.COBWEB)
                .key("challenge_ranged_web_200")
                .title(Localizer.dLocalize("advancement.challenge_ranged_web_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_web_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_ranged_web_200", "ranged.web-bomb.mobs-trapped", 200, 300);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("ranged.web_shot.lore1"));
        v.addLore(C.YELLOW + "+ " + level + C.GRAY + " " + Localizer.dLocalize("ranged.web_shot.lore2"));
    }


    @EventHandler
    public void on(ProjectileHitEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Block block;

        if (e.getHitEntity() != null) {
            block = e.getHitEntity().getLocation().add(0, 1, 0).getBlock();
        } else if (e.getHitBlock() != null) {
            block = e.getHitBlock().getLocation().add(0, 1, 0).getBlock();
        } else {
            block = e.getEntity().getLocation().add(0, 1, 0).getBlock();
        }

        if (e.getEntity().getShooter() instanceof Player p && e.getEntity() instanceof Snowball snowball && hasAdaptation(p)) {
            vfxCuboidOutline(block, Particle.REVERSE_PORTAL);
            Adapt.verbose("Snowball Got: " + snowball.getEntityId() + " " + snowball.getUniqueId());
            if (activeSnowballs.containsKey(Bukkit.getEntity(snowball.getUniqueId()))) {
                Adapt.verbose("Detected snowball hit");
                if (e.getHitEntity() != null) {
                    getPlayer(p).getData().addStat("ranged.web-bomb.mobs-trapped", 1);
                }
                activeSnowballs.remove(snowball);
                snowball.remove();
                Set<Block> locs = new HashSet<>();
                locs.add(block.getLocation().add(0, 1, 0).getBlock());
                locs.add(block.getLocation().add(0, -1, 0).getBlock());
                locs.add(block.getLocation().add(0, 0, 1).getBlock());
                locs.add(block.getLocation().add(0, 0, -1).getBlock());
                locs.add(block.getLocation().add(1, 0, 0).getBlock());
                locs.add(block.getLocation().add(-1, 0, 0).getBlock());

                for (Block i : locs) {
                    addWebFoundation(i, getLevel(p));
                }

            }
        }
    }


    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity().getShooter() instanceof Player p && e.getEntity() instanceof Snowball snowball && hasAdaptation(p)) {
            Adapt.verbose("Snowball Launched: " + snowball.getEntityId() + " " + snowball.getUniqueId());
            if (BoundSnowBall.isBindableItem(snowball.getItem())) {
                Adapt.verbose("Snowball is bound");
                activeSnowballs.put(snowball, p);
            } else {
                Adapt.verbose("Snowball is not bound");
            }
        }
    }

    public void addWebFoundation(Block block, int seconds) {
        if (!block.getType().isAir()) {
            return;
        }

        J.s(() -> {
            block.setBlockData(BLOCK);
            activeBlocks.add(block);
        });
        SoundPlayer spw = SoundPlayer.of(block.getWorld());
        spw.play(block.getLocation(), Sound.BLOCK_ROOTED_DIRT_PLACE, 1.0f, 1.0f);
        if (areParticlesEnabled()) {

            vfxCuboidOutline(block, Particle.CLOUD);
            vfxCuboidOutline(block, Particle.WHITE_ASH);
        }
        J.s(() -> removeFoundation(block), seconds * 16);
    }

    public void removeFoundation(Block block) {
        if (!block.getBlockData().equals(BLOCK)) {
            return;
        }

        J.s(() -> {
            block.setBlockData(AIR);
            activeBlocks.remove(block);
        });
        SoundPlayer spw = SoundPlayer.of(block.getWorld());
        spw.play(block.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 1.0f);
        if (areParticlesEnabled()) {
            vfxCuboidOutline(block, Particles.ENCHANTMENT_TABLE);
        }
    }


    //prevent piston from moving blocks // Dupe fix
    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockPistonExtendEvent e) {
        if (e.isCancelled()) {
            return;
        }
        e.getBlocks().forEach(b -> {
            if (activeBlocks.contains(b)) {
                Adapt.verbose("Cancelled Piston Extend on Adaptation Foundation Block");
                e.setCancelled(true);
            }
        });
    }

    //prevent piston from pulling blocks // Dupe fix
    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockPistonRetractEvent e) {
        if (e.isCancelled()) {
            return;
        }
        e.getBlocks().forEach(b -> {
            if (activeBlocks.contains(b)) {
                Adapt.verbose("Cancelled Piston Retract on Adaptation Foundation Block");
                e.setCancelled(true);
            }
        });
    }

    //prevent TNT from destroying blocks // Dupe fix
    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockExplodeEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (activeBlocks.contains(e.getBlock())) {
            Adapt.verbose("Cancelled Block Explosion on Adaptation Foundation Block");
            e.setCancelled(true);
        }
    }

    //prevent block from being destroyed // Dupe fix
    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (activeBlocks.contains(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    //prevent Entities from destroying blocks // Dupe fix
    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityExplodeEvent e) {
        if (e.isCancelled()) {
            return;
        }
        e.blockList().removeIf(activeBlocks::contains);
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
    @ConfigDescription("Throw a crafted web snare to trap targets in cobwebs.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Ranged Web Bomb adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.9;
    }
}
