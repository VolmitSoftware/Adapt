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

package com.volmit.adapt.content.adaptation.agility;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.registries.Particles;
import com.volmit.adapt.util.reflect.registries.PotionEffectTypes;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;


public class AgilitySuperJump extends SimpleAdaptation<AgilitySuperJump.Config> {
    private final Map<Player, Long> lastJump;

    public AgilitySuperJump() {
        super("agility-super-jump");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility.super_jump.description"));
        setDisplayName(Localizer.dLocalize("agility.super_jump.name"));
        setIcon(Material.LEATHER_BOOTS);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(9999);
        lastJump = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_BOOTS)
                .key("challenge_agility_super_jump_100")
                .title(Localizer.dLocalize("advancement.challenge_agility_super_jump_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_super_jump_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.GOLDEN_BOOTS)
                        .key("challenge_agility_super_jump_5k")
                        .title(Localizer.dLocalize("advancement.challenge_agility_super_jump_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_agility_super_jump_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_agility_super_jump_100", "agility.super-jump.jumps", 100, 300);
        registerMilestone("challenge_agility_super_jump_5k", "agility.super-jump.jumps", 5000, 1500);
    }

    private double getJumpHeight(int level) {
        return getConfig().baseJumpMultiplier + (getConfig().jumpLevelMultiplier * level);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getJumpHeight(level), 0) + C.GRAY + " " + Localizer.dLocalize("agility.super_jump.lore1"));
        v.addLore(C.LIGHT_PURPLE + " " + Localizer.dLocalize("agility.super_jump.lore2"));

    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        if (e.isSneaking() && p.isOnGround()) {
            SoundPlayer sp = SoundPlayer.of(p);
            sp.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3f, 0.35f);
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        lastJump.remove(p);
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSprinting()) {
            return;
        }

        if (p.isSneaking() && hasAdaptation(p) && canUse(getPlayer(p))) {
            Vector velocity = p.getVelocity();

            if (velocity.getY() > 0) {
                double jumpVelocity = 0.4;
                PotionEffect jumpPotion = p.getPotionEffect(PotionEffectTypes.JUMP);

                if (jumpPotion != null) {
                    jumpVelocity += (double) ((float) jumpPotion.getAmplifier() + 1) * 0.1F;
                }

                if (lastJump.get(p) != null && M.ms() - lastJump.get(p) < 1000) {
                    return;
                } else if (lastJump.get(p) != null && M.ms() - lastJump.get(p) > 1500) {
                    lastJump.remove(p);
                }
                if (p.getLocation().getBlock().getType() != Material.LADDER && velocity.getY() > jumpVelocity && p.isOnline()) {
                    SoundPlayer spw = SoundPlayer.of(p.getWorld());
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25f, 0.7f);
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25f, 1.7f);
                    if (areParticlesEnabled()) {
                        p.getWorld().spawnParticle(Particles.BLOCK_CRACK, p.getLocation().clone().add(0, 0.3, 0), 15, 0.1, 0.8, 0.1, 0.1, p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData());
                    }
                    p.setVelocity(p.getVelocity().setY(getJumpHeight(getLevel(p))));
                    lastJump.put(p, M.ms());
                    getPlayer(p).getData().addStat("agility.super-jump.jumps", 1);
                }
            }
        }
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
    @ConfigDescription("Sneak and jump for exceptional height advantage.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Agility Super Jump adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.55;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Jump Multiplier for the Agility Super Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseJumpMultiplier = 0.23;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Jump Level Multiplier for the Agility Super Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double jumpLevelMultiplier = 0.23;
    }
}